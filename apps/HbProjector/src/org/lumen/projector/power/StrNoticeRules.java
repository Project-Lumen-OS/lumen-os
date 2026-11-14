package org.lumen.projector.power;

/**
 * HB beta6: the plain-Java decisions of {@link StrNotice} (host test test/beta6): which STR blocker it is, when the
 * USB notice is due, and which key event is the power menu opening (PowerKey: POWER held past LONG_PRESS_MS).
 */
final class StrNoticeRules {
    /** android.view.KeyEvent values (stable API constants). */
    static final int KEYCODE_POWER = 26, KEYCODE_TV_POWER = 177, KEYCODE_STB_POWER = 179, ACTION_DOWN = 0;

    private StrNoticeRules() {}

    /** The kind of a StrGate.blocker() text, null = no blocker. */
    static StrNoticeKind kind(String blocker) {
        if (blocker == null) return null;
        if (blocker.startsWith(StrGate.B_USB)) return StrNoticeKind.USB;
        if (blocker.equals(StrGate.B_OTA_BUSY)) return StrNoticeKind.UPDATE;
        if (blocker.equals(StrGate.B_OTA_UNMARKED)) return StrNoticeKind.UPDATE_CHECK;
        if (blocker.equals(StrGate.B_ALLOW_STR)) return StrNoticeKind.OFF;
        return null;
    }

    /** A USB_STATE change shows the USB notice once per connection. */
    static boolean usbNoticeDue(StrNoticeKind now, boolean shownForThisConnection) {
        return now == StrNoticeKind.USB && !shownForThisConnection;
    }

    /** The first repeat of a power key held past the long-press time (PowerKey opens the power menu there). */
    static boolean powerMenuPress(int code, int action, int repeat, long heldMs, long longPressMs, long downTime,
            long notedFor) {
        boolean power = code == KEYCODE_STB_POWER || code == KEYCODE_POWER || code == KEYCODE_TV_POWER;
        return power && action == ACTION_DOWN && repeat > 0 && heldMs >= longPressMs && downTime != notedFor;
    }
}
