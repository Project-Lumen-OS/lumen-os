// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * Senior colour (stock more_elderly_color_enhancement, newsettings ElderlyColorDialog over MoreManager): one switch
 * (GmTvManager 180 setElderColorTone) and a strength 0..2 (182 setElderColorLevel) sent only while the switch is on,
 * then the read (179 / 181). Stock SettingMutexManager refuses it in the office, performance and colour accuracy
 * picture modes. The stock code is the same on the Z9X HB and the X50 family: each adapter only gives the four calls.
 * The dialog's second switch (auto senior colour in XGIMI's elder home mode, persist.sys.home.mode 2) is a Secure flag
 * read only by XGIMI's launcher, so Lumen has no such row.
 */
public final class ElderColorRule {
    private ElderColorRule() {}

    /** The vendor calls of the row. */
    public interface Port {
        /** GmTvManager 179 getElderColorTone(). */
        int elderTone() throws Exception;

        /** GmTvManager 180 setElderColorTone(on ? 1 : 0). */
        boolean elderToneSet(boolean on) throws Exception;

        /** GmTvManager 181 getElderColorLevel(). */
        int elderLevel() throws Exception;

        /** GmTvManager 182 setElderColorLevel(level), level 0..{@link #MAX_LEVEL}. */
        boolean elderLevelSet(int level) throws Exception;
    }

    /** eye.elder_color tokens: off = the switch off, else the strength 0..2 (ElderlyColorDialog levelMap) plus one. */
    public static final String[] TOKENS = {"off", "low", "medium", "high"};
    /** The top strength the vendor takes. */
    public static final int MAX_LEVEL = TOKENS.length - 2;
    /** The refusal note in the modes the mutex holds (stock cur_mode_not_support_elder_adjustment). */
    public static final String NOTE = "lumen_m_elder_mode_lock";
    /** getElderColorTone of the switch on. */
    public static final int ON = 1;
    /** Stock picture mode ids of the mutex: office (6, the X50 family's 25), performance, colour accuracy. */
    public static final int MODE_OFFICE = 6, MODE_OFFICE_X50 = 25, MODE_PERFORMANCE = 5, MODE_COLOR_ACCURACY = 29;

    /** An office picture mode (stock image_mode_office_mutex_*). */
    public static boolean officeMode(int pictureMode) {
        return pictureMode == MODE_OFFICE || pictureMode == MODE_OFFICE_X50;
    }

    /** Stock SettingMutexManager: no senior colour in the office, performance and colour accuracy modes. */
    public static boolean allowed(int pictureMode) {
        return !officeMode(pictureMode) && pictureMode != MODE_PERFORMANCE && pictureMode != MODE_COLOR_ACCURACY;
    }

    /** off while the switch reads off, else the strength as low / medium / high (null for a strength out of range). */
    public static String read(Port p) throws Exception {
        if (p.elderTone() != ON) return TOKENS[0];
        int i = p.elderLevel() + 1;
        return i > 0 && i < TOKENS.length ? TOKENS[i] : null;
    }

    /** The switch, the strength only while it is on, then the read. */
    public static String set(Port p, String token) throws Exception {
        int i = java.util.Arrays.asList(TOKENS).indexOf(token);
        if (i < 0) throw new IllegalArgumentException("senior colour " + token);
        boolean on = p.elderTone() == ON;
        if (i == 0) {
            if (on) p.elderToneSet(false);
        } else {
            if (!on) p.elderToneSet(true);
            p.elderLevelSet(i - 1);
        }
        return read(p);
    }
}
