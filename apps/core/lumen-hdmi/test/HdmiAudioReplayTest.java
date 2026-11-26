// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import org.lumen.hdmi.sim.Sim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays a recorded Z9X session (session_lr5wq7m9.tsv: the HAL thread's vendor calls with the time the vendor took for
 * each) in virtual time, with slow HAL calls injected next to the recorded ones (another module's read queued on the
 * same signal change), and measures how long the stock HDMI audio keys wait after the vendor event. Report LR-5WQ7M9:
 * a loud pop from the speaker when the PS5 picture drops. Stock XRMService sends the keys inside its GmTvManager
 * listener, so a signal key (lost, stable, event 4) goes out at the event with no HAL call ahead of it, a cable out
 * after its one getCurrentInputSource read, never later than one call, in the vendor's order, the delayed key 500 ms
 * after a lost signal. Runs HdmiAudio itself against the android stubs of test/sim.
 *   sh apps/core/lumen-hdmi/test/run.sh
 */
public final class HdmiAudioReplayTest {
    private static int checks, failures;

    private static final Handler HAL = new Handler(new HandlerThread("model-hal").getLooper());
    private static final Handler BINDER = new Handler(new HandlerThread("vendor-binder").getLooper());
    private static final int HDMI1 = 23;
    private static final long SLOW_MS = 250;
    private static long sReadMs;
    private static int sReads;

    private record Call(long at, String name, long ms) {
        @Override public String toString() { return name + " at " + at + " ms (" + ms + " ms)"; }
    }

    /** The vendor's input source read (getCurrentInputSource): HDMI 1, taking the time of one recorded call. */
    private static final HdmiAudioKeys.Source SOURCE = () -> {
        sReads++;
        Sim.call(sReadMs);
        return HDMI1;
    };

    /** The install of the code under test, its only line that names the install API. */
    private static void install() {
        HdmiAudio.install(new Context(), SOURCE);
    }

    public static void main(String[] args) throws Exception {
        List<Call> session = load(Path.of(args[0], "session_lr5wq7m9.tsv"));
        List<Call> slow = new ArrayList<>();
        for (Call c : session) if (c.ms >= SLOW_MS) slow.add(c);
        check(slow.size() >= 10, "the recorded session has its slow calls: " + slow.size());
        sReadMs = slow.get(slow.size() / 2).ms;

        // a lost signal while each slow recorded call runs, the next slow one queued behind it on the same change
        long worst = 0;
        for (int i = 0; i < slow.size(); i++) {
            Call c = slow.get(i);
            long t = start(session, c, slow.get((i + 1) % slow.size()).ms);
            BINDER.postAt(() -> HdmiAudio.onTvEvent(HdmiAudioKeys.EVENT_SIGNAL, "status:0"), t);
            Sim.run(t + 5_000);
            List<Sim.Key> k = keysFrom(t);
            String at = "lost signal during " + c;
            if (!check(k.size() == 2 && k.get(0).kv.equals(HdmiAudioKeys.OFF)
                    && k.get(1).kv.equals(HdmiAudioKeys.OFF_DELAYED), at + ": the stock keys " + k)) continue;
            long wait = k.get(0).at - t;
            worst = Math.max(worst, wait);
            check(wait <= c.ms, at + ": the mute waits at most the one call in flight, waited " + wait + " ms");
            check(wait == 0 && halDone(t, k.get(0).at) == 0,
                    at + ": the mute goes out at the event, no HAL call ahead (stock), waited " + wait + " ms");
            check(k.get(1).at - k.get(0).at == HdmiAudioKeys.DELAY_MS,
                    at + ": the delayed key 500 ms after the mute, got " + (k.get(1).at - k.get(0).at));
        }
        System.out.println("lost signal: worst mute wait " + worst + " ms over " + slow.size()
                + " slow recorded calls (the slowest " + max(slow) + " ms)");

        // a stable signal while the slowest call runs: the key on at the event
        Call top = slowest(slow);
        long t = start(session, top, sReadMs);
        BINDER.postAt(() -> HdmiAudio.onTvEvent(HdmiAudioKeys.EVENT_SIGNAL, "status:3"), t);
        Sim.run(t + 5_000);
        List<Sim.Key> k = keysFrom(t);
        check(k.size() == 1 && k.get(0).kv.equals(HdmiAudioKeys.ON) && k.get(0).at == t,
                "stable signal during " + top + ": the key on at the event, got " + k);

        // the recorded event 4 of the session, with a slow read injected right before it
        Call first = session.get(0);
        long e4 = first.at - 95;
        Sim.reset();
        install();
        replay(session, e4 - 2_500, e4 + 2_500);
        HAL.postAt(() -> Sim.call(first.ms), e4 - 1);
        HAL.postAt(() -> Sim.call(sReadMs), e4);
        BINDER.postAt(() -> HdmiAudio.onTvEvent(HdmiAudioKeys.EVENT_OFF, ""), e4);
        Sim.run(e4 + 5_000);
        k = keysFrom(e4);
        check(k.size() == 1 && k.get(0).kv.equals(HdmiAudioKeys.OFF) && k.get(0).at == e4,
                "the recorded event 4: the mute at the event, got " + k);

        // a cable out of the current port, then the lost signal: one read, then both keys in the vendor's order
        t = start(session, top, sReadMs);
        BINDER.postAt(() -> HdmiAudio.onTvEvent(HdmiAudioKeys.EVENT_HOT_PLUG, "x:" + HDMI1 + ",state:0"), t);
        BINDER.postAt(() -> HdmiAudio.onTvEvent(HdmiAudioKeys.EVENT_SIGNAL, "status:0"), t + 1);
        Sim.run(t + 5_000);
        k = keysFrom(t);
        check(sReads == 1, "cable out: one input source read, got " + sReads);
        if (check(k.size() == 3, "cable out then lost signal: three keys, got " + k)) {
            check(k.get(0).kv.equals(HdmiAudioKeys.OFF) && k.get(0).at - t == sReadMs,
                    "cable out: the mute after its one read (" + sReadMs + " ms), got " + k.get(0));
            check(k.get(0).at - t <= top.ms, "cable out: the mute waits at most one call, got " + k.get(0));
            check(k.get(1).kv.equals(HdmiAudioKeys.OFF) && k.get(1).at == k.get(0).at,
                    "the lost signal after the cable out, in the vendor's order: " + k.get(1));
            check(k.get(2).kv.equals(HdmiAudioKeys.OFF_DELAYED) && k.get(2).at - k.get(1).at == HdmiAudioKeys.DELAY_MS,
                    "the delayed key 500 ms after the lost signal: " + k.get(2));
        }

        // a cable out of another port: one read, no key; a cable in: no read, no key
        t = start(session, top, sReadMs);
        BINDER.postAt(() -> HdmiAudio.onTvEvent(HdmiAudioKeys.EVENT_HOT_PLUG, "x:" + (HDMI1 + 1) + ",state:0"), t);
        BINDER.postAt(() -> HdmiAudio.onTvEvent(HdmiAudioKeys.EVENT_HOT_PLUG, "x:" + HDMI1 + ",state:1"), t + 1);
        Sim.run(t + 5_000);
        check(keysFrom(t).isEmpty() && sReads == 1, "cable out of another port, cable in: no key, one read, got "
                + keysFrom(t) + ", " + sReads + " reads");

        System.out.println("HdmiAudioReplayTest: " + checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    /** A fresh run: the install, the recorded calls around {@code c}, one slow read injected behind it. */
    private static long start(List<Call> session, Call c, long injectedMs) {
        Sim.reset();
        sReads = 0;
        install();
        replay(session, c.at - 2_500, c.at + 2_500);
        long t = c.at + 1;
        HAL.postAt(() -> Sim.call(injectedMs), t);
        return t;
    }

    private static void replay(List<Call> session, long from, long to) {
        for (Call c : session) if (c.at >= from && c.at < to) HAL.postAt(() -> Sim.call(c.ms), c.at);
    }

    private static List<Sim.Key> keysFrom(long t) {
        List<Sim.Key> out = new ArrayList<>();
        for (Sim.Key k : Sim.KEYS) if (k.at >= t) out.add(k);
        out.sort((a, b) -> Long.compare(a.at, b.at));
        return out;
    }

    /** The model HAL thread's calls finished after the event and up to the key. */
    private static int halDone(long from, long to) {
        int n = 0;
        for (long d : Sim.thread("model-hal").done) if (d > from && d <= to) n++;
        return n;
    }

    private static Call slowest(List<Call> calls) {
        Call top = calls.get(0);
        for (Call c : calls) if (c.ms > top.ms) top = c;
        return top;
    }

    private static long max(List<Call> calls) { return slowest(calls).ms; }

    private static List<Call> load(Path p) throws Exception {
        List<Call> out = new ArrayList<>();
        for (String line : Files.readAllLines(p)) {
            String[] f = line.split("\t");
            if (f.length == 4 && f[0].equals("call")) out.add(new Call(Long.parseLong(f[1]), f[2], Long.parseLong(f[3])));
        }
        return out;
    }

    private static boolean check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
        return ok;
    }
}
