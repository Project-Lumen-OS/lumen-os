// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * The signal format on screen as the picture page names it (stock newsettings PictureUtils.getCurrentHdrType, shown
 * by the picture title image_title_v4 and by the two line title of the picture mode list and of the professional
 * settings, image_mode_two_title): Dolby Vision, HDR Vivid, HLG, HDR10, HDR10+, any other HDR, SDR. Read from the
 * vendor getHdrType of every XGIMI model (Z9X / Play 6 IGmpf 620, HB / X50 family GmTvManager 78): 0 SDR, 1 HDR10,
 * 2 Dolby Vision, 4 HLG, 5 HDR10+, 6 HDR Vivid, another value above 0 = HDR. Plain Java.
 */
public enum HdrFormat {
    SDR("lumen_m_fmt_sdr", Signal.SDR), HDR("lumen_m_fmt_other", Signal.HDR), HDR10("lumen_m_fmt_hdr10", Signal.HDR10),
    HDR10_PLUS("lumen_m_fmt_hdr10_plus", Signal.HDR), HLG("lumen_m_fmt_hlg", Signal.HDR),
    DOLBY_VISION("lumen_m_fmt_dolby", Signal.DOLBY), HDR_VIVID("lumen_m_fmt_vivid", Signal.HDR_VIVID);

    /** Vendor getHdrType values (stock PictureUtils). */
    static final int TYPE_SDR = 0, TYPE_HDR10 = 1, TYPE_DOLBY = 2, TYPE_HLG = 4, TYPE_HDR10_PLUS = 5, TYPE_VIVID = 6;

    /** String resource name of the label (lumen-menu, the same proper name in every language). */
    public final String label;
    /** The content kind of the profile's support lists (SignalRule): HLG and HDR10+ count as HDR, as on stock. */
    public final Signal kind;

    HdrFormat(String label, Signal kind) {
        this.label = label;
        this.kind = kind;
    }

    /** The format of a getHdrType read, null when nothing was read (a negative value is a failed read). */
    public static HdrFormat ofHdrType(Integer type) {
        if (type == null || type < TYPE_SDR) return null;
        switch (type) {
            case TYPE_SDR: return SDR;
            case TYPE_HDR10: return HDR10;
            case TYPE_DOLBY: return DOLBY_VISION;
            case TYPE_HLG: return HLG;
            case TYPE_HDR10_PLUS: return HDR10_PLUS;
            case TYPE_VIVID: return HDR_VIVID;
            default: return HDR;
        }
    }
}
