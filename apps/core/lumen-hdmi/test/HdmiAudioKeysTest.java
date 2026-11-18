package org.lumen.hdmi;

import java.util.ArrayList;
import java.util.List;

/**
 * Host test of HdmiAudioKeys (apps/core/lumen-hdmi/test/run.sh): the audio HAL keys stock XRMService sends for the
 * GmTvManager events 0 (signal status), 4 and 1 (hot plug), with the stock literals written out.
 */
public final class HdmiAudioKeysTest {
    private static int checks, failures;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    /** What one event sent ("now KEY" / "later MS KEY") and how often the current source was read. */
    private static final class Run implements HdmiAudioKeys.Out, HdmiAudioKeys.Source {
        final List<String> sent = new ArrayList<>();
        final int current;
        final boolean fails;
        int reads;

        Run(int current, boolean fails) {
            this.current = current;
            this.fails = fails;
        }

        @Override public void now(String keyValue) { sent.add("now " + keyValue); }

        @Override public void later(String keyValue, long delayMs) { sent.add("later " + delayMs + " " + keyValue); }

        @Override public int current() throws Exception {
            reads++;
            if (fails) throw new Exception("no HAL");
            return current;
        }
    }

    private static Run event(int type, String msg, int current, boolean fails) {
        Run r = new Run(current, fails);
        HdmiAudioKeys.onTvEvent(type, msg, r, r);
        return r;
    }

    private static String sent(int type, String msg) {
        return String.join(" | ", event(type, msg, 1, false).sent);
    }

    public static void main(String[] args) {
        final String off = "now xgimi_source_hdmi=0", lost = off + " | later 500 xgimi_source_hdmi_delay=0";

        // event 0: stable = on, every other status = off and the delayed key 500 ms later
        check(sent(0, "status:3").equals("now xgimi_source_hdmi=1"), "stable signal: on (" + sent(0, "status:3") + ")");
        for (String s : new String[] {"status:1", "status:2", "status:0", "status:4"}) {
            check(sent(0, s).equals(lost), s + ": off, then the delayed key (" + sent(0, s) + ")");
        }
        for (String s : new String[] {"status", "", "state:3", "picturemode:3", "3"}) {
            check(sent(0, s).isEmpty(), "event 0 '" + s + "' sends nothing");
        }
        check(sent(0, null).isEmpty(), "event 0 without a message sends nothing");
        check(event(0, "status:1", 1, false).reads == 0, "a signal event never reads the source");

        // event 4: off, whatever the message
        check(sent(4, "anything").equals(off), "event 4: off");
        check(sent(4, null).equals(off), "event 4 without a message: off");

        // event 1: cable out of the current source = off
        check(sent(1, "x:1,state:0").equals(off), "cable out of the shown port: off");
        check(String.join("", event(1, "x:2,state:0", 1, false).sent).isEmpty(), "cable out of another port: nothing");
        check(String.join("", event(1, "x:2,state:0", 2, false).sent).equals(off), "cable out of HDMI 2 while shown: off");
        Run in = event(1, "x:1,state:1", 1, false);
        check(in.sent.isEmpty() && in.reads == 0, "cable in: nothing, the source is not read");
        check(event(1, "x:1,state:0", 1, false).reads == 1, "cable out reads the source once");
        check(event(1, "x:1,state:0", 1, true).sent.isEmpty(), "source read failed: nothing");
        for (String s : new String[] {"x:1", "x:a,state:0", "x,state:0", "x:1,plug:0", "x:1,state", ""}) {
            Run r = event(1, s, 1, false);
            check(r.sent.isEmpty(), "event 1 '" + s + "' sends nothing");
        }
        check(sent(1, null).isEmpty(), "event 1 without a message sends nothing");

        // the other GmTvManager events (picture mode 5, eARC 11, HDR 13, game 16, ALLM 19 / 20) have no key
        for (int t : new int[] {2, 3, 5, 11, 13, 14, 16, 19, 20, -1}) {
            check(sent(t, "status:3").isEmpty(), "event " + t + " sends nothing");
            check(!HdmiAudioKeys.handles(t), "event " + t + " needs no hop");
        }
        for (int t : new int[] {0, 1, 4}) check(HdmiAudioKeys.handles(t), "event " + t + " is handled");
        check(HdmiAudioKeys.DELAY_MS == 500, "the stock delay is 500 ms");

        System.out.println("HdmiAudioKeysTest: " + checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
