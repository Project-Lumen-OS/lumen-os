// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/** Host checks of the control API decisions (Control): sh test/run_core.sh runs them after CoreTest. */
public final class ControlTest {
    private static int pass, fail;

    private static void eq(String name, Object want, Object got) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name + ": want " + want + " got " + got);
        }
    }

    public static void main(String[] a) {
        // which setting a call changes
        eq("set_enabled changes enabled", Control.ENABLED, Control.settingOf(Control.SET_ENABLED));
        eq("set_ask changes ask", Control.ASK, Control.settingOf(Control.SET_ASK));
        eq("state changes nothing", null, Control.settingOf(Control.STATE));
        eq("unknown method changes nothing", null, Control.settingOf("set_name"));
        eq("null method changes nothing", null, Control.settingOf(null));
        eq("method names are exact", null, Control.settingOf("SET_ENABLED"));
        // switch values
        eq("1 is on", Boolean.TRUE, Control.parseSwitch("1"));
        eq("0 is off", Boolean.FALSE, Control.parseSwitch("0"));
        eq("true is not a value", null, Control.parseSwitch("true"));
        eq("empty is not a value", null, Control.parseSwitch(""));
        eq("null is not a value", null, Control.parseSwitch(null));
        eq("2 is not a value", null, Control.parseSwitch("2"));
        // status line
        eq("off wins over everything", ReceiverStatus.OFF, Control.status(false, true, true, "VISIBLE"));
        eq("off without a service", ReceiverStatus.OFF, Control.status(false, false, false, null));
        eq("on, service not up yet", ReceiverStatus.STARTING, Control.status(true, false, false, null));
        eq("a running session", ReceiverStatus.IN_USE, Control.status(true, true, true, "CONNECTED"));
        eq("a session wins over a Wi-Fi state", ReceiverStatus.IN_USE, Control.status(true, true, true, "WIFI_OFF"));
        eq("visible = ready", ReceiverStatus.READY, Control.status(true, true, false, "VISIBLE"));
        eq("Wi-Fi off", ReceiverStatus.NO_WIFI, Control.status(true, true, false, "WIFI_OFF"));
        eq("no Wi-Fi Direct", ReceiverStatus.UNSUPPORTED, Control.status(true, true, false, "UNSUPPORTED"));
        eq("starting", ReceiverStatus.STARTING, Control.status(true, true, false, "STARTING"));
        eq("connected without a session yet", ReceiverStatus.STARTING, Control.status(true, true, false, "CONNECTED"));
        eq("stopped", ReceiverStatus.STARTING, Control.status(true, true, false, "STOPPED"));
        eq("unknown state", ReceiverStatus.STARTING, Control.status(true, true, false, null));
        System.out.println("control: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
