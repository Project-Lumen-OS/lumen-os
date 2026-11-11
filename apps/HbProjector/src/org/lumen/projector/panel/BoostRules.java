package org.lumen.projector.panel;

/**
 * Z9X HB: when Boost (lens display mode 1, DISPLAY 151 / 152) can be left, and what a refused 52(0) means. Plain Java
 * for the host test (apps/HbProjector/test/boost).
 *
 * Stock evidence (G0130 V6.16.24):
 * - The vendor's own rule table /mnt/vendor/xgimiconfig/G0130/pq/xgimipq.db: settings_default row 17 gives lens_mode 1
 *   to every key of picture mode 5 (Performance) and 25 (Office) ("[0-9]+-[0-9]+-(5|25)-.*" -> 1), and
 *   settings_sto_mapping row 2 / settings_field_info row 2 store lens_mode only for the OTHER picture modes
 *   ("(?!(5|25)...)"). So in Performance and Office Boost belongs to the picture mode: libxgimi
 *   DispDispatchV4::setLensDispMode answers false with "lens_mode not support change" (live log 2026-11-10 22:17:25,
 *   hb_session/live_b5/logcat_2220.txt) and the unit stays in Boost until another picture mode is chosen; that mode
 *   brings back its own stored lens_mode.
 * - Stock newsettings never sends 51 / 52(0) there: platform/common.json "dlpLumensLevel" (brightness adjustment, the
 *   path that leaves Boost) is refused by "colorModePerformance" (toast performance_mode_not_support_dlp_level) and
 *   "colorModeOffice" (office_mode_not_support_dlp_level).
 * - Stock leaves Boost with GmDisplayManager.setLensDispMode(0) and IGNORES its return value
 *   (BrightnessRepository$setLedLight$index$2$onKeyDown$1, BrightnessDialog$exitBoostMode$1$onKeyDown$1): the read-back
 *   of 152 decides, not the boolean.
 */
public final class BoostRules {
    private BoostRules() {}

    /** Vendor picture mode wires (GmpfClient.PictureMode PERFORMANCE / OFFICE) whose Boost is fixed by the vendor. */
    static final int PM_PERFORMANCE = 5;
    static final int PM_OFFICE = 25;

    /** True when the current picture mode holds Boost itself (Performance, Office): 52(0) is refused by the vendor. */
    public static boolean lockedByPictureMode(Integer pictureModeWire) {
        if (pictureModeWire == null) return false;
        int pm = pictureModeWire;
        return pm == PM_PERFORMANCE || pm == PM_OFFICE;
    }

    /** What a LEFT press at "Boost" does: arm / send 52(0), or only explain (no vendor call). */
    public enum Leave { ARM, EXPLAIN_PICTURE_MODE }

    public static Leave onLeavePress(Boolean boost, Integer pictureModeWire) {
        return Boolean.TRUE.equals(boost) && lockedByPictureMode(pictureModeWire) ? Leave.EXPLAIN_PICTURE_MODE : Leave.ARM;
    }

    /** Outcome of a 52 call judged as stock does: by the 152 read-back after it, never by the boolean alone. */
    public enum Outcome { DONE, LOCKED_BY_PICTURE_MODE, FAILED }

    /**
     * @param wantOn       the value sent to 52
     * @param ret          what 52 answered
     * @param lensAfter    152 read right after (1 = Boost, 0 = normal, -1 = unreadable)
     * @param pictureMode  667-side picture mode wire read right after, null when unknown
     */
    public static Outcome judge(boolean wantOn, boolean ret, int lensAfter, Integer pictureMode) {
        int want = wantOn ? 1 : 0;
        if (lensAfter == want) return Outcome.DONE;                 // the vendor did it (whatever it answered)
        if (lensAfter < 0) return ret ? Outcome.DONE : Outcome.FAILED;
        if (!wantOn && lockedByPictureMode(pictureMode)) return Outcome.LOCKED_BY_PICTURE_MODE;
        return Outcome.FAILED;
    }

    /**
     * Colour accuracy picture mode while Boost is on (hb-t7 rule, stock lumenLevelBoost): refused only when the Boost
     * is the user's own. A Boost that belongs to Performance / Office goes away with that mode (xgimipq.db
     * settings_sto_mapping row 2), so it must not block leaving the mode; otherwise the user is trapped there.
     */
    public static boolean blocksColorAccuracy(Boolean boost, Integer pictureModeWire) {
        return Boolean.TRUE.equals(boost) && !lockedByPictureMode(pictureModeWire);
    }
}
