// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.tvinput;

import java.util.ArrayList;
import java.util.List;

/**
 * Replay of evleo790 LR-5MVYWT (HB G0148 beta25, 2026-11-26 18:23:32: HDMI 2 opened from Home after the TV had taken
 * the active source, Yandex Module la 0x0B, CEC 1.4, power on, 2.0.0.0 on port 2): the frames the framework sends for
 * the routing call Route picks, modelled on the LineageOS 21 services classes (DeviceSelectActionFromTv start /
 * selectDevice, HdmiCecLocalDeviceTv.doManualPortSwitching, RoutingControlAction.handleTimerEvent).
 */
public final class RouteTest {
    private static int checks, fails;

    private static void ok(String what, boolean v) {
        checks++;
        if (!v) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    /** One CEC device as HdmiCecNetwork lists it. */
    private static final class Dev {
        final int id, cec, power, phys;
        Dev(int id, int cec, int power, int phys) { this.id = id; this.cec = cec; this.power = power; this.phys = phys; }
    }

    static final int ON = 0, STANDBY = 1, UNKNOWN = -1;
    static final int TV_CEC = 5;                 // hdmi.txt: local device cec_version 5

    /** The frames (opcode + operands) of one routing call; activePort: the TV's active port, 0 = the TV itself. */
    private static List<String> frames(int kind, Dev d, int port, int portPath, int activePort) {
        List<String> f = new ArrayList<>();
        if (kind == Route.PORT) {
            if (port == activePort) return f;                              // doManualPortSwitching: same port
            f.add(String.format("80:00:00:%02X:%02X", portPath >> 8, portPath & 0xff));
            f.add(String.format("86:%02X:%02X", portPath >> 8, portPath & 0xff));   // timer: one Set Stream Path
            return f;
        }
        boolean cec20 = TV_CEC >= 6 && d.cec >= 6;
        String ssp = String.format("86:%02X:%02X", d.phys >> 8, d.phys & 0xff);
        f.add(ssp);                                                        // start()
        if (!cec20 || d.power == UNKNOWN) f.add("8F");
        if (d.power == STANDBY) { f.add("44:40"); f.add("45"); f.add("44:6D"); f.add("45"); }
        if (!cec20) f.add(ssp);                                            // selectDevice() after the power answer
        return f;
    }

    private static List<String> run(Dev d, int port, int portPath, int activePort) {
        boolean on = d != null && d.power == ON;
        boolean direct = d != null && d.phys == portPath;
        int kind = Route.forMain(d == null ? -1 : d.id, on, direct);
        return frames(kind, d, port, portPath, activePort);
    }

    private static int count(List<String> f, String prefix) {
        int n = 0;
        for (String s : f) if (s.startsWith(prefix)) n++;
        return n;
    }

    public static void main(String[] a) {
        Dev yandex = new Dev(11, 5, ON, 0x2000);
        // the log itself: deviceSelect(11) gave [S] 0F:86:20:00, 0B:8F, 0F:86:20:00 (hdmi.txt 18:23:34-36)
        List<String> logged = frames(Route.DEVICE, yandex, 2, 0x2000, 0);
        ok("model reproduces the logged deviceSelect frames: " + logged,
                logged.toString().equals("[86:20:00, 8F, 86:20:00]"));
        List<String> f = run(yandex, 2, 0x2000, 0);
        ok("LR-5MVYWT 18:23:32: one <Set Stream Path> to 2.0.0.0: " + f, count(f, "86:20:00") == 1);
        ok("LR-5MVYWT 18:23:32: the box is routed (Routing Change to 2.0.0.0): " + f, f.contains("80:00:00:20:00"));
        f = run(yandex, 2, 0x2000, 2);
        ok("box on, its port already active: no frame: " + f, f.isEmpty());
        f = run(new Dev(11, 5, STANDBY, 0x2000), 2, 0x2000, 0);
        ok("box in standby: deviceSelect wakes it (power key): " + f, f.contains("44:40"));
        f = run(new Dev(11, 5, UNKNOWN, 0x2000), 2, 0x2000, 0);
        ok("box power unknown: deviceSelect asks it: " + f, f.contains("8F"));
        f = run(new Dev(12, 5, ON, 0x2100), 2, 0x2000, 0);
        ok("box behind a switch on 2.1.0.0: deviceSelect to its own path: " + f, f.contains("86:21:00"));
        f = run(new Dev(13, 6, ON, 0x2000), 2, 0x2000, 0);
        ok("CEC 2.0 box on: one <Set Stream Path>: " + f, count(f, "86:20:00") == 1);
        ok("port input: portSelect", Route.forMain(-1, false, false) == Route.PORT);
        ok("port input with stray device facts: portSelect", Route.forMain(-1, true, true) == Route.PORT);
        System.out.println("RouteTest: " + checks + " checks, " + fails + " failures");
        if (fails > 0) System.exit(1);
    }
}
