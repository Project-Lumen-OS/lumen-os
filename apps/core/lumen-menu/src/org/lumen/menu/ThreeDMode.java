// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/**
 * The stock 3D modes (newsettings image_3d_* nodes, the same code on every model with 3D): the profile token, the
 * format the stock sends (enable3D, or enable3DTo2D for the two "as 2D" modes) and how stock reads the mode back
 * (PictureRepository.get3dModeToResult). A model lists its own subset in its stock order (menu.json picture.3d).
 */
public enum ThreeDMode {
    OFF("off", 1, false),
    SBS("sbs", 2, false),
    TB("tb", 3, false),
    SBS_2D("sbs_2d", 2, true),
    TB_2D("tb_2d", 3, true),
    FRAME_PACKING("frame_packing", 4, false);

    public final String token;
    /** The stock format byte: 1 off, 2 side by side, 3 top and bottom, 4 frame packing. */
    public final int format;
    /** True for the modes stock sends as enable3DTo2D (one eye's picture, no glasses). */
    public final boolean to2d;

    ThreeDMode(String token, int format, boolean to2d) {
        this.token = token;
        this.format = format;
        this.to2d = to2d;
    }

    /** The mode of a profile token, or null. */
    public static ThreeDMode of(String token) {
        for (ThreeDMode m : values()) if (m.token.equals(token)) return m;
        return null;
    }

    /**
     * The mode the projector reads, as stock get3dModeToResult (the same bytecode in the X50 1.2.64, TITAN 1.2.99 and
     * RS30 newsettings): format 2 side by side, 3 top and bottom, each "to 2D" when 3D to 2D is on, 4 frame packing,
     * anything else off, whatever the flag says. Null while either value is unknown (the menu then shows no mode,
     * never a guessed one).
     */
    public static ThreeDMode read(Integer format, Boolean to2d) {
        if (format == null || to2d == null) return null;
        switch (format) {
            case 2: return to2d ? SBS_2D : SBS;
            case 3: return to2d ? TB_2D : TB;
            case 4: return FRAME_PACKING;
            default: return OFF;
        }
    }

    /** True for every mode but off. */
    public boolean on() { return this != OFF; }

    /**
     * While 3D is on, how often the projector app looks whether a video still plays. No XGIMI vendor turns 3D off when
     * the video ends (libporting PlatformPanel::isAutoClose3DEnable returns 0 on the Z9X, the X50 and the RS30), so
     * without this the menus and the home screen would stay split.
     */
    public static final long WATCH_MS = 5_000;

    /** True when the app turns 3D off: it reads on and no video plays any more. */
    public static boolean videoEnded(ThreeDMode now, boolean videoPlaying) {
        return now != null && now.on() && !videoPlaying;
    }
}
