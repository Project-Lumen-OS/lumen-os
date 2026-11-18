// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

/** Numbers and vendor values as the panel writes them. Pure, host tested. */
public final class SignalFormat {
    private SignalFormat() {}

    /** Vendor getHdrType values (stock PictureUtils). */
    static final int HDR_SDR = 0, HDR_HDR10 = 1, HDR_DOLBY = 2, HDR_HLG = 4, HDR_HDR10_PLUS = 5, HDR_VIVID = 6;
    /** Vendor getDolbyVisionPicMode values (stock image_mode_picture_dolby_bright / _soft). */
    static final int DV_BRIGHT = 0, DV_DARK = 1;
    /** Plausible temperatures in degrees C (a sensor that is not there answers 0). */
    static final int TEMP_MIN = 1, TEMP_MAX = 150;
    /** "3840×2160 @ 23.97 Hz": the owner's format (2026-11-18), the size sign and the spaced at sign. */
    public static final String TIMES = "\u00d7", AT = " @ ";

    /** Aspect tokens: the signal's own (from its size) and the setting (vendor getAspectRatio values). */
    public static final String ASPECT_4_3 = "4:3", ASPECT_16_9 = "16:9", ASPECT_21_9 = "21:9", ASPECT_32_9 = "32:9",
            ASPECT_16_10 = "16:10", ASPECT_5_4 = "5:4", ASPECT_AUTO = "auto", ASPECT_ORIGINAL = "original";
    /** {ratio x 1000 low, high, token}: a size whose width / height falls inside names that aspect. */
    private static final Object[][] RATIOS = {{1240, 1260, ASPECT_5_4}, {1320, 1345, ASPECT_4_3},
            {1590, 1610, ASPECT_16_10}, {1765, 1790, ASPECT_16_9}, {2320, 2400, ASPECT_21_9}, {3540, 3570, ASPECT_32_9}};

    /** Media output tokens (SignalValues.audioOut). */
    public static final String OUT_SPEAKER = "speaker", OUT_ARC = "arc", OUT_EARC = "earc", OUT_BT = "bt",
            OUT_USB = "usb", OUT_WIRED = "wired", OUT_HDMI = "hdmi", OUT_OTHER = "other";
    /** Sound format tokens (SignalValues.soundFormat, Settings.Global encoded_surround_output 0..3). */
    public static final String FMT_AUTO = "auto", FMT_PCM = "pcm", FMT_PASS = "passthrough", FMT_MANUAL = "manual";
    /** Range and EDID tokens (SignalValues.range / edid). */
    public static final String RANGE_AUTO = "auto", RANGE_FULL = "full", RANGE_LIMITED = "limited",
            EDID_14 = "1.4", EDID_20 = "2.0", EDID_21 = "2.1", EDID_AUTO = "auto";

    /** 2397 -> "23.97", 6000 -> "60", 5994 -> "59.94", 2390 -> "23.9" (dec = the language's separator). */
    public static String rate(int centi, char dec) {
        if (centi <= 0) return "";
        int whole = centi / 100, frac = centi % 100;
        if (frac == 0) return Integer.toString(whole);
        String f = frac < 10 ? "0" + frac : Integer.toString(frac);
        if (f.endsWith("0")) f = f.substring(0, 1);
        return whole + String.valueOf(dec) + f;
    }

    /** 3840, 2160 -> "3840×2160". */
    public static String size(int w, int h) { return w + TIMES + h; }

    /** "3840×2160" and "60 Hz" -> "3840×2160 @ 60 Hz"; no rate -> the size alone. Never glued. */
    public static String mode(String size, String rate) {
        if (size == null) return null;
        return rate == null || rate.isEmpty() ? size : size + AT + rate;
    }

    /**
     * The aspect of a signal from its size: 1920x1080 -> 16:9, 1440x1080 -> 4:3, 2560x1080 -> 21:9. Null where the size
     * does not tell it: standard definition (720x480, 720x576 carry 4:3 or 16:9 with the same pixels) or an odd size.
     */
    public static String aspectOf(int w, int h) {
        if (w <= 0 || h <= 0 || w == 720 || w == 704) return null;
        int r = (int) Math.round(w * 1000.0 / h);
        for (Object[] a : RATIOS) if (r >= (int) a[0] && r <= (int) a[1]) return (String) a[2];
        return null;
    }

    /** Vendor getAspectRatio: 0 4:3, 1 16:9, 2 auto, 4 original, 5 21:9, 6 32:9 (HB game bar values). */
    public static String aspectSettingToken(int v) {
        switch (v) {
            case 0: return ASPECT_4_3;
            case 1: return ASPECT_16_9;
            case 2: return ASPECT_AUTO;
            case 4: return ASPECT_ORIGINAL;
            case 5: return ASPECT_21_9;
            case 6: return ASPECT_32_9;
            default: return null;
        }
    }

    public static String hdrKey(int type) {
        switch (type) {
            case HDR_SDR: return SignalText.HDR_SDR;
            case HDR_HDR10: return SignalText.HDR_HDR10;
            case HDR_DOLBY: return SignalText.HDR_DOLBY;
            case HDR_HLG: return SignalText.HDR_HLG;
            case HDR_HDR10_PLUS: return SignalText.HDR_HDR10_PLUS;
            case HDR_VIVID: return SignalText.HDR_VIVID;
            default: return type > 0 ? SignalText.HDR_OTHER : null;
        }
    }

    public static boolean isHdr(Integer type) { return type != null && type != HDR_SDR && hdrKey(type) != null; }

    public static boolean isDolby(Integer type) { return type != null && type == HDR_DOLBY; }

    public static String dvKey(Integer mode) {
        if (mode == null) return null;
        return mode == DV_BRIGHT ? SignalText.DV_BRIGHT : mode == DV_DARK ? SignalText.DV_DARK : null;
    }

    /** A sensor answer in degrees C, null when it is not plausible (0 = no such sensor). */
    public static Integer temp(double c) {
        if (Double.isNaN(c) || c < TEMP_MIN || c > TEMP_MAX) return null;
        return (int) Math.round(c);
    }

    /** CEC physical address 0x1000 -> "1.0.0.0"; null for 0 (none) or outside 16 bits. */
    public static String physical(int a) {
        if (a <= 0 || a > 0xFFFF) return null;
        return ((a >> 12) & 0xF) + "." + ((a >> 8) & 0xF) + "." + ((a >> 4) & 0xF) + "." + (a & 0xF);
    }

    /** Vendor getHdmiRange: 0 auto, 1 full, 2 limited (Z9X 634, HB / X50 TV 64, Play 6 634). */
    public static String rangeToken(int v) {
        switch (v) {
            case 0: return RANGE_AUTO;
            case 1: return RANGE_FULL;
            case 2: return RANGE_LIMITED;
            default: return null;
        }
    }

    public static String rangeKey(String token) {
        if (RANGE_AUTO.equals(token)) return SignalText.RANGE_AUTO;
        if (RANGE_FULL.equals(token)) return SignalText.RANGE_FULL;
        if (RANGE_LIMITED.equals(token)) return SignalText.RANGE_LIMITED;
        return null;
    }

    public static String edidKey(String token) {
        if (EDID_14.equals(token)) return SignalText.EDID_14;
        if (EDID_20.equals(token)) return SignalText.EDID_20;
        if (EDID_21.equals(token)) return SignalText.EDID_21;
        if (EDID_AUTO.equals(token)) return SignalText.EDID_AUTO;
        return null;
    }

    /** Vendor getEdidVersion: 0 HDMI 1.4, 1 HDMI 2.0, 2 chosen by the game mode, 3 HDMI 2.1 (Z9X, HB, X50, Play 6). */
    public static String edidToken(int v) {
        switch (v) {
            case 0: return EDID_14;
            case 1: return EDID_20;
            case 2: return EDID_AUTO;
            case 3: return EDID_21;
            default: return null;
        }
    }

    /** Settings.Global encoded_surround_output: 0 auto, 1 never (PCM), 2 always (passthrough), 3 manual. */
    public static String soundFormatToken(int v) {
        switch (v) {
            case 0: return FMT_AUTO;
            case 1: return FMT_PCM;
            case 2: return FMT_PASS;
            case 3: return FMT_MANUAL;
            default: return null;
        }
    }

    public static String soundFormatKey(String token) {
        if (FMT_AUTO.equals(token)) return SignalText.FMT_AUTO;
        if (FMT_PCM.equals(token)) return SignalText.FMT_PCM;
        if (FMT_PASS.equals(token)) return SignalText.FMT_PASS;
        if (FMT_MANUAL.equals(token)) return SignalText.FMT_MANUAL;
        return null;
    }

    public static String outKey(String token) {
        if (token == null) return null;
        switch (token) {
            case OUT_SPEAKER: return SignalText.OUT_SPEAKER;
            case OUT_ARC: return SignalText.OUT_ARC;
            case OUT_EARC: return SignalText.OUT_EARC;
            case OUT_BT: return SignalText.OUT_BT;
            case OUT_USB: return SignalText.OUT_USB;
            case OUT_WIRED: return SignalText.OUT_WIRED;
            case OUT_HDMI: return SignalText.OUT_HDMI;
            default: return SignalText.OUT_OTHER;
        }
    }

    /** HdmiRxInfo colour format -> "RGB 4:4:4", "YUV 4:2:2" (the owner's words, 2026-11-18); null for unknown. */
    public static String colorFormat(String token) {
        if (token == null) return null;
        switch (token) {
            case HdmiRxInfo.RGB: return "RGB 4:4:4";
            case HdmiRxInfo.YUV444: return "YUV 4:4:4";
            case HdmiRxInfo.YUV422: return "YUV 4:2:2";
            case HdmiRxInfo.YUV420: return "YUV 4:2:0";
            default: return null;
        }
    }

    /** HdmiRxInfo colorimetry -> its standard name (not translated); null for unknown. */
    public static String colorimetry(String token) {
        if (token == null) return null;
        switch (token) {
            case HdmiRxInfo.BT601: return "BT.601";
            case HdmiRxInfo.BT709: return "BT.709";
            case HdmiRxInfo.BT2020: return "BT.2020";
            case HdmiRxInfo.BT2020_CL: return "BT.2020 CL";
            case HdmiRxInfo.XVYCC601: return "xvYCC 601";
            case HdmiRxInfo.XVYCC709: return "xvYCC 709";
            case HdmiRxInfo.SYCC601: return "sYCC 601";
            case HdmiRxInfo.OPYCC601: return "opYCC 601";
            case HdmiRxInfo.OPRGB: return "opRGB";
            case HdmiRxInfo.P3: return "DCI-P3";
            default: return null;
        }
    }

    /**
     * The range row's text key: the setting, and with Auto the range the signal asks for when the receiver told it
     * ("Auto (Full)", the words of lumen-menu HdmiRangeRule); null when the setting was not read.
     */
    public static String rangeKey(String setting, String applied) {
        if (RANGE_AUTO.equals(setting) && HdmiRxInfo.FULL.equals(applied)) return SignalText.RANGE_AUTO_FULL;
        if (RANGE_AUTO.equals(setting) && HdmiRxInfo.LIMITED.equals(applied)) return SignalText.RANGE_AUTO_LIMITED;
        return rangeKey(setting);
    }

    /** Refuse a misdecoded or empty timing (the same bounds as the menu's HdmiSignal.plausible). */
    public static boolean plausible(int w, int h, int centi) {
        return w >= 640 && w <= 7680 && h >= 480 && h <= 4320 && centi >= 2300 && centi <= 24500;
    }
}
