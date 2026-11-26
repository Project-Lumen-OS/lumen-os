// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SleepCec against two traces of the same room going to sleep with HDMI 2 on screen: the stock X50 on Slava's bus
 * sniffer and Lumen beta14 live. The framework part of the sleep (HdmiCecLocalDeviceTv.disableDevice: ARC
 * termination while ARC is up, then onStandby: {@code <Standby>} with tv_send_standby_on_sleep) is AOSP, the same in
 * the stock services.jar; this test models it and checks the model on the Lumen trace first.
 */
public final class SleepCecTest {
    private static int checks, fails;

    private static void eq(String what, Object want, Object got) {
        checks++;
        if (!want.equals(got)) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    static final String AS_TV = "0F:82:00:00", ARC_END = "05:C4", ARC_ENDED = "05:C2", STANDBY = "0F:36";

    /** The framework's own frames when the screen goes off (AOSP, stock and Lumen alike). */
    static List<String> framework(boolean arcUp, boolean sendStandby) {
        List<String> out = new ArrayList<>();
        if (arcUp) {
            out.add(ARC_END);
            out.add(ARC_ENDED);                                 // the soundbar does not answer: 2 s timeout
        }
        if (sendStandby) out.add(STANDBY);
        return out;
    }

    /** What Lumen puts on the bus from the sleep request to the framework's standby. */
    static List<String> lumen(boolean cecOn, boolean hdmiShown, boolean arcUp, boolean sendStandby) {
        List<String> out = new ArrayList<>();
        if (!cecOn) return out;
        if (SleepCec.tvTakesSource(cecOn, hdmiShown)) out.add(AS_TV);
        out.addAll(framework(arcUp, sendStandby));
        return out;
    }

    /** The TV's (logical 0) own frames in a sniffer log after the power press. */
    static List<String> sniffer(Path f) throws Exception {
        List<String> out = new ArrayList<>();
        Pattern p = Pattern.compile("\\[received\\] ([0-9A-F:]+) ");
        for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            Matcher m = p.matcher(l);
            if (!m.find()) continue;
            String hex = m.group(1);
            if (hex.startsWith("0") && !hex.equals("05:83") && !hex.startsWith("04:8F") && !hex.startsWith("05:8F")) {
                out.add(hex);                                   // without the TV's polls and address requests
            }
        }
        return out;
    }

    /** The frames Lumen sent in a dumpsys history ([S] lines), without its answers to the soundbar. */
    static List<String> history(Path f) throws Exception {
        List<String> out = new ArrayList<>();
        for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            CecLog.Entry e = CecLog.parse(l);
            if (e == null || e.dir != 'S' || e.opcode == 0x00) continue;   // Feature Abort: an answer, not the sleep
            int m = l.indexOf("> ", l.indexOf("message=<"));
            out.add(l.substring(m + 2).split(" ")[0]);
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);
        List<String> stock = sniffer(dir.resolve("cec_stock_x50_sleep.txt"));
        List<String> live = history(dir.resolve("cec_x50_live_sleep.txt"));
        eq("stock trace: Active Source 0.0.0.0, ARC termination, Standby", List.of(AS_TV, ARC_END, ARC_ENDED, STANDBY),
                stock);
        eq("Lumen beta14 trace: no Active Source", List.of(ARC_END, ARC_ENDED, STANDBY), live);
        eq("the framework model gives the Lumen beta14 trace", live, framework(true, true));
        // the fix
        eq("HDMI on screen: the stock order", stock, lumen(true, true, true, true));
        eq("HDMI on screen, standby switch off: Active Source still first (stock goes Home on every sleep)",
                List.of(AS_TV, ARC_END, ARC_ENDED), lumen(true, true, true, false));
        eq("HDMI on screen, no soundbar", List.of(AS_TV, STANDBY), lumen(true, true, false, true));
        eq("Home on screen: nothing more", List.of(ARC_END, ARC_ENDED, STANDBY), lumen(true, false, true, true));
        eq("CEC off: nothing", List.of(), lumen(false, true, true, true));
        eq("rule: shown", true, SleepCec.tvTakesSource(true, true));
        eq("rule: not shown", false, SleepCec.tvTakesSource(true, false));
        eq("rule: CEC off", false, SleepCec.tvTakesSource(false, true));
        System.out.println("SleepCecTest: " + checks + " checks, " + fails + " failures");
        if (fails > 0) System.exit(1);
    }
}
