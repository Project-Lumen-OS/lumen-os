package org.lumen.projector.hal;

/**
 * The current HDMI input timing of getHdmiInfo (stock IGmTvType stHdmiInfo: width, height, interlace, HDMI or DVI,
 * frame rate in 1/100 Hz). Each projector's client decodes its own reply (Z9X: the 16-byte HIDL struct, HB: the
 * stable parcelable). Off HDMI it keeps the last signal; callers gate every action on {@link #plausible()}.
 */
public final class HdmiSignal {
    private static final int WIDTH_MIN = 640, WIDTH_MAX = 7680, HEIGHT_MIN = 480, HEIGHT_MAX = 4320;
    private static final int RATE_MIN_CENTI = 2300, RATE_MAX_CENTI = 24500;
    /** 100 Hz (stock hdmi_game_bt_toast: top speed is meant for inputs above 100 Hz). */
    private static final int HIGH_RATE_CENTI = 10000;
    private static final float CENTI = 100f;

    public final int width, height, frameRateCenti;
    public final boolean interlace, hdmiMode;
    /** The reply as read, for logs. */
    public final String rawHex;

    HdmiSignal(int width, int height, boolean interlace, boolean hdmiMode, int frameRateCenti, String rawHex) {
        this.width = width;
        this.height = height;
        this.interlace = interlace;
        this.hdmiMode = hdmiMode;
        this.frameRateCenti = frameRateCenti;
        this.rawHex = rawHex;
    }

    /** Refuse to act on a misdecoded struct or on "no signal" (all zero). */
    public boolean plausible() {
        return width >= WIDTH_MIN && width <= WIDTH_MAX && height >= HEIGHT_MIN && height <= HEIGHT_MAX
                && frameRateCenti >= RATE_MIN_CENTI && frameRateCenti <= RATE_MAX_CENTI;
    }

    /** >= 100 Hz. */
    public boolean isHighFrameRate() { return plausible() && frameRateCenti >= HIGH_RATE_CENTI; }

    @Override
    public String toString() {
        return width + "x" + height + (interlace ? "i" : "p") + "@" + frameRateCenti / CENTI
                + (hdmiMode ? " hdmi" : " dvi") + " raw=" + rawHex;
    }
}
