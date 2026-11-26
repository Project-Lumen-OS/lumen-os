// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/**
 * One read of the signal panel. Each HAL item costs exactly one vendor call (SignalBudget counts them), the Android
 * items cost none. Which HAL items a model has is data: devices/&lt;c&gt;/signal.tsv (assets/lumen_signal/items.tsv).
 * An HDMI item is read only while an HDMI input is on screen (on the apps it would show an old signal).
 */
public enum SignalItem {
    /** input timing: width, height, interlace, HDMI or DVI, frame rate in 1/100 Hz (Z9X 662, HB / X50 TV 38) */
    HDMI_INFO("hdmi_info", true, ReadSpeed.CARD, true),
    /** HDR kind of the content: SDR, HDR10, HDR10+, HLG, Dolby Vision, HDR Vivid (Z9X 620, HB / X50 TV 78) */
    HDR("hdr", true, ReadSpeed.CARD, true),
    /** picture on the chip: size and refresh rate (Z9X 157, HB DM 57) */
    CHIP("chip", true, ReadSpeed.CARD, false),
    /** refresh rate on the chip alone (X50 DM 179) */
    CHIP_RATE("chip_rate", true, ReadSpeed.CARD, false),
    /** colour range setting: auto, full, limited (Z9X 634, HB / X50 TV 64) */
    RANGE("range", true, ReadSpeed.CARD_SLOW, true),
    /** the aspect ratio setting: 0 4:3, 1 16:9, 2 auto, 4 original, 5 21:9, 6 32:9 (Z9X / Play 6 / HB GET_ASPECT_RATIO,
     *  X50 TV 36); the signal's own aspect comes from its size */
    ASPECT("aspect", true, ReadSpeed.CARD_SLOW, true),
    /** the HDMI mode the input announces (EDID: Z9X 661, HB / X50 TV 39) */
    EDID("edid", true, ReadSpeed.ONCE, true),
    /** ALLM flag the source sends now (Z9X IGmpf2 310, HB / X50 TV 131, Play 6 IGmpf2 212) */
    ALLM("allm", true, ReadSpeed.DETAIL, true),
    /** VRR on this input (Z9X IGmpf2 300, HB / X50 TV 146) */
    VRR("vrr", true, ReadSpeed.DETAIL, true),
    /** AMD FreeSync state (Z9X IGmpf2 271, HB / X50 TV 173) */
    FREESYNC("freesync", true, ReadSpeed.DETAIL, true),
    /** IMAX Enhanced content (getExtDecInfo "format&amp;isimax": Z9X IGmpf2 279, HB / X50 TV 163) */
    IMAX("imax", true, ReadSpeed.DETAIL, true),
    /** Filmmaker Mode content (getExtDecInfo "format&amp;isfmm": Z9X IGmpf2 279, HB / X50 TV 163) */
    FILMMAKER("filmmaker", true, ReadSpeed.DETAIL, true),
    /** the HDMI port's CEC, ARC and physical address (HB / X50 TV 153 getHdmiHwConfig) */
    PORT_HW("port_hw", true, ReadSpeed.ONCE, true),
    /** Dolby Vision picture mode: 0 bright, 1 dark (HB / X50 TV 75; the Z9X has no Dolby Vision) */
    DV_MODE("dv_mode", true, ReadSpeed.DETAIL, true),
    /** the projector's game mode runs now (Z9X IGmpf2 55, HB / X50 DM 148) */
    GAME("game", true, ReadSpeed.DETAIL, false),
    /** HGiG HDR game mode (HB / X50 TV 192) */
    HGIG("hgig", true, ReadSpeed.DETAIL, false),
    /** HDR dynamic tone mapping switch (Z9X IGmpf2 269, HB / X50 TV 175) */
    TONE_MAP("tone_map", true, ReadSpeed.DETAIL, false),
    /** light source temperature in degrees C (Z9X LED sensor 5 of 439, X50 family red laser sensor 5 of TEMPERATURE 7) */
    TEMP_LIGHT("temp_light", true, ReadSpeed.DETAIL, false),
    /** temperature inside the projector in degrees C (Z9X / Play 6 438, HB / X50 TEMPERATURE 6) */
    TEMP_SYSTEM("temp_system", true, ReadSpeed.DETAIL, false),
    /**
     * the projector's picture mode as the menu reads it (Z9X / Play 6 IGmpf 666, HB / X50 family TV 34), its menu.json
     * option token; on the small card too, last line (the stock RS30 card's order)
     */
    PICTURE_MODE("picture_mode", true, ReadSpeed.CARD_SLOW, false),
    /** motion smoothing (MEMC) as the menu reads it (Z9X / Play 6 IGmpf 668, HB / X50 family TV 32), its option token */
    MEMC("memc", true, ReadSpeed.DETAIL, false),
    /**
     * the colour space line of the stock signal OSD (X50 family on XGIMI v1.3.38: GmTvManager 204
     * getHdmiColorSpaceInfo, 1 BT.2020, 2 DCI-P3, 3 Rec.709, 4 native, 7 Adobe RGB), named with the menu's colour space
     * words, on the small card too, after the HDR kind (stock order)
     */
    COLOR_SPACE("color_space", true, ReadSpeed.CARD_SLOW, true),
    /**
     * colour format and chroma, bit depth, colorimetry and the range the signal asks for, from the HDMI receiver itself
     * (MStar /proc/utopia_mdb/HDMIRX through the image's root bridge lumen_hdmirx, HdmiRxBridge, HdmiRxInfo): no vendor
     * HAL call, its own pace (FormatWatch). Only a model whose image ships the bridge lists it (Z9X).
     */
    HDMI_FORMAT("hdmi_format", false, ReadSpeed.CARD, true, true),
    /** Android display mode (Display.getMode): no vendor call */
    SCREEN("screen", false, ReadSpeed.CARD, false),
    /** where media sound goes (AudioManager): no vendor call */
    AUDIO_OUT("audio_out", false, ReadSpeed.CARD, false),
    /** the sound format setting (Settings.Global encoded_surround_output): no vendor call */
    SOUND_FORMAT("sound_format", false, ReadSpeed.DETAIL, false);

    public final String token;
    /** true = one vendor HAL call, counted by the budget */
    public final boolean hal;
    public final ReadSpeed speed;
    /** true = read only while an HDMI input is on screen */
    public final boolean hdmiOnly;
    /** true = read through the image's root bridge (HdmiRxBridge), not the reader, not counted by the HAL budget */
    public final boolean bridge;

    SignalItem(String token, boolean hal, ReadSpeed speed, boolean hdmiOnly) { this(token, hal, speed, hdmiOnly, false); }

    SignalItem(String token, boolean hal, ReadSpeed speed, boolean hdmiOnly, boolean bridge) {
        this.token = token;
        this.hal = hal;
        this.speed = speed;
        this.hdmiOnly = hdmiOnly;
        this.bridge = bridge;
    }

    /** true = read for the small card too, false = only while the full panel is open */
    public boolean card() { return speed == ReadSpeed.CARD || speed == ReadSpeed.CARD_SLOW; }

    public static SignalItem of(String token) {
        for (SignalItem i : values()) if (i.token.equals(token)) return i;
        return null;
    }
}
