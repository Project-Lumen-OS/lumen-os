// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * The kind of content on screen, as the stock settings name it in the {@code support} lists of their menu tree
 * (PictureUtils.getCurSourceType: DOLBY, IMAX, HdrVivid, Filmmaker, HDR10, HDR, SDR). A profile option with a
 * {@code support} list is offered only for these kinds ({@link SignalRule}).
 */
public enum Signal {
    SDR("sdr"), HDR("hdr"), HDR10("hdr10"), DOLBY("dolby"), HDR_VIVID("hdr_vivid"), IMAX("imax"), FILMMAKER("filmmaker");


    public final String token;

    Signal(String token) { this.token = token; }

    /** The kind named by a profile token, or null. */
    public static Signal of(String token) {
        for (Signal s : values()) if (s.token.equals(token)) return s;
        return null;
    }

    /**
     * The kind for a getHdrType read, in the stock order: Dolby Vision, HDR Vivid, HDR10, any other HDR (HLG, HDR10+)
     * = HDR, else SDR. Null when nothing was read. Filmmaker needs a read no adapter makes yet: such content counts as
     * its HDR kind; IMAX Enhanced content: {@link #of(HdrFormat, boolean)}.
     */
    public static Signal ofHdrType(Integer hdrType) {
        HdrFormat f = HdrFormat.ofHdrType(hdrType);                  // one table of the vendor values
        return f == null ? null : f.kind;
    }

    /**
     * The kind for a getHdrType read and the stock IMAX Enhanced content flag (getExtDecInfo "format&amp;isimax" = 1), in
     * the stock order of PictureUtils.getCurSourceType: Dolby Vision first, then IMAX Enhanced, then the HDR kind.
     */
    public static Signal of(HdrFormat f, boolean imax) {
        if (f != null && f.kind == DOLBY) return DOLBY;
        if (imax) return IMAX;
        return f == null ? null : f.kind;
    }
}
