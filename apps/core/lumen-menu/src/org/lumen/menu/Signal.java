// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * The kind of content on screen, as the stock settings name it in the {@code support} lists of their menu tree
 * (PictureUtils.getCurSourceType: DOLBY, IMAX, HdrVivid, Filmmaker, HDR10, HDR, SDR). A profile option with a
 * {@code support} list is offered only for these kinds ({@link SignalRule}).
 */
public enum Signal {
    SDR("sdr"), HDR("hdr"), HDR10("hdr10"), DOLBY("dolby"), HDR_VIVID("hdr_vivid"), IMAX("imax"), FILMMAKER("filmmaker");

    /** getHdrType values of the vendor (stock PictureUtils isSdr / isPlayingHDR10 / isDolbyPlaying / isPlayingHDRVivid). */
    private static final int HDR_TYPE_SDR = 0, HDR_TYPE_HDR10 = 1, HDR_TYPE_DOLBY = 2, HDR_TYPE_VIVID = 6;

    public final String token;

    Signal(String token) { this.token = token; }

    /** The kind named by a profile token, or null. */
    public static Signal of(String token) {
        for (Signal s : values()) if (s.token.equals(token)) return s;
        return null;
    }

    /**
     * The kind for a getHdrType read, in the stock order: Dolby Vision, HDR Vivid, HDR10, any other HDR (HLG, HDR10+)
     * = HDR, else SDR. Null when nothing was read. IMAX and Filmmaker need reads no adapter makes yet: such content
     * counts as its HDR kind.
     */
    public static Signal ofHdrType(Integer hdrType) {
        if (hdrType == null || hdrType < HDR_TYPE_SDR) return null;
        switch (hdrType) {
            case HDR_TYPE_SDR: return SDR;
            case HDR_TYPE_HDR10: return HDR10;
            case HDR_TYPE_DOLBY: return DOLBY;
            case HDR_TYPE_VIVID: return HDR_VIVID;
            default: return HDR;
        }
    }
}
