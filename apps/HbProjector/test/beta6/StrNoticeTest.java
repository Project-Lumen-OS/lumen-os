package org.lumen.projector.power;

/** Host test of StrNoticeRules (HB beta6: why deep sleep is blocked) against the real StrGate blocker texts. */
public final class StrNoticeTest {
    static int n, bad;
    static void eq(String what, Object got, Object want) {
        n++;
        if (want == null ? got != null : !want.equals(got)) { bad++; System.out.println("FAIL " + what + ": got " + got + ", want " + want); }
    }

    public static void main(String[] a) {
        eq("none", StrNoticeRules.kind(null), null);
        eq("usb udc", StrNoticeRules.kind(StrGate.B_USB + " (udc configured)"), StrNoticeRules.Kind.USB);
        eq("usb bare", StrNoticeRules.kind(StrGate.B_USB), StrNoticeRules.Kind.USB);
        eq("update", StrNoticeRules.kind(StrGate.B_OTA_BUSY), StrNoticeRules.Kind.UPDATE);
        eq("update check", StrNoticeRules.kind(StrGate.B_OTA_UNMARKED), StrNoticeRules.Kind.UPDATE_CHECK);
        eq("kill switch", StrNoticeRules.kind(StrGate.B_ALLOW_STR), StrNoticeRules.Kind.OFF);
        eq("unknown text", StrNoticeRules.kind("something else"), null);
        // USB notice once per connection
        eq("usb first", StrNoticeRules.usbNoticeDue(StrNoticeRules.Kind.USB, false), true);
        eq("usb again", StrNoticeRules.usbNoticeDue(StrNoticeRules.Kind.USB, true), false);
        eq("no usb", StrNoticeRules.usbNoticeDue(null, false), false);
        eq("update is not usb", StrNoticeRules.usbNoticeDue(StrNoticeRules.Kind.UPDATE, false), false);
        // power menu: first repeat of POWER / STB_POWER / TV_POWER held >= 700 ms, once per press
        long L = 700;
        eq("stb held", StrNoticeRules.powerMenuPress(179, 0, 1, 700, L, 5, -1), true);
        eq("power held", StrNoticeRules.powerMenuPress(26, 0, 3, 900, L, 5, -1), true);
        eq("tv power held", StrNoticeRules.powerMenuPress(177, 0, 2, 800, L, 5, -1), true);
        eq("short press", StrNoticeRules.powerMenuPress(179, 0, 1, 699, L, 5, -1), false);
        eq("first down", StrNoticeRules.powerMenuPress(179, 0, 0, 900, L, 5, -1), false);
        eq("up", StrNoticeRules.powerMenuPress(179, 1, 1, 900, L, 5, -1), false);
        eq("same press again", StrNoticeRules.powerMenuPress(179, 0, 5, 1200, L, 5, 5), false);
        eq("next press", StrNoticeRules.powerMenuPress(179, 0, 1, 750, L, 9, 5), true);
        eq("other key", StrNoticeRules.powerMenuPress(23, 0, 1, 900, L, 5, -1), false);
        System.out.println("StrNoticeTest: " + (n - bad) + "/" + n + " passed");
        if (bad != 0) System.exit(1);
    }
}
