// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * What the panel draws, built from the values alone (no Android): the small card's lines and the full panel's
 * sections. A row whose item this model does not read, or whose value did not come, is not there at all. Sizes and
 * rates read "3840×2160 @ 23.976 Hz" everywhere (2026-11-18). Pure, host tested ({@link #build}).
 */
public final class SignalSheet {
    /** One line of the full panel: a section header (label only, section = true) or label and value. */
    public static final class Row {
        public final boolean section;
        public final String key, label, value, small;
        public final RowTone tone;

        Row(boolean section, String key, String label, String value, String small, RowTone tone) {
            this.section = section;
            this.key = key;
            this.label = label;
            this.value = value;
            this.small = small;
            this.tone = tone;
        }

        @Override public String toString() {
            return section ? "[" + label + "]" : label + " = " + value + (small == null ? "" : " (" + small + ")")
                    + (tone == RowTone.PLAIN ? "" : " " + tone);
        }
    }

    /** A badge of the small card (HDR kind, HDMI mode, ALLM, VRR, FreeSync, IMAX, Filmmaker); accent = HDR. */
    public static final class Chip {
        public final String text;
        public final boolean accent;

        Chip(String text, boolean accent) {
            this.text = text;
            this.accent = accent;
        }

        @Override public String toString() { return accent ? "*" + text : text; }
    }

    /** The menu feature ids of the rows the panel shares with the menu (named with the menu's own words). */
    static final String MENU_PICTURE_MODE = "picture.mode", MENU_MEMC = "picture.memc", MENU_GAMUT = "picture.gamut";

    /** Fixed badge names (brands, not translated). */
    static final String ALLM = "ALLM", VRR = "VRR", FREESYNC = "FreeSync", IMAX = "IMAX Enhanced",
            FILMMAKER = "Filmmaker Mode";

    /** "HDMI 1" or "Projector apps"; the big line of the card, "3840×2160 @ 23.976 Hz" (null when nothing to say). */
    public String port, big;
    public final List<Chip> chips = new ArrayList<>();
    public Judder judder = Judder.UNKNOWN;
    /** the judder line in words, null for UNKNOWN */
    public String verdict;
    /** "Sound: projector speakers", null when not read */
    public String sound;
    public final List<Row> rows = new ArrayList<>();
    /**
     * The small card's lines under the input line, in the owner's order (2026-11-18): aspect, colour format and chroma,
     * range, colorimetry, bit depth, HDR kind, then the picture mode (last, as on the stock RS30 card). A line this model
     * cannot read is not there (colour format, colorimetry and bit depth only where the image has the HDMI receiver
     * bridge, HDMI_FORMAT).
     */
    public final List<Row> cardRows = new ArrayList<>();

    /**
     * The sheet for {@code v}: {@code items} = what this model reads (HAL items of its items.tsv and the reader,
     * plus the Android items).
     */
    public static SignalSheet build(SignalValues v, Set<SignalItem> items, SignalText.Words w) {
        SignalSheet s = new SignalSheet();
        char dec = w.decimal();
        boolean hdmi = v.port > 0;
        s.port = hdmi ? w.get(SignalText.PORT_HDMI, v.port) : w.get(SignalText.PORT_APPS);
        boolean input = hdmi && items.contains(SignalItem.HDMI_INFO) && v.hasInput();
        String inputMode = null;
        if (input) {
            String size = SignalFormat.size(v.width, v.height) + (Boolean.TRUE.equals(v.interlaced) ? "i" : "");
            String rate = v.rateCenti > 0 ? w.get(SignalText.HZ, SignalFormat.rate(v.rateCenti, dec))
                    : v.vrrMoving ? VRR : null;
            inputMode = SignalFormat.mode(size, rate);
            s.big = inputMode;
        } else if (hdmi && items.contains(SignalItem.HDMI_INFO)) {
            s.big = w.get(SignalText.NO_SIGNAL);
        } else if (!hdmi && items.contains(SignalItem.SCREEN) && v.screenWidth > 0) {
            s.big = SignalFormat.mode(SignalFormat.size(v.screenWidth, v.screenHeight),
                    v.screenRateCenti > 0 ? w.get(SignalText.HZ, SignalFormat.rate(v.screenRateCenti, dec)) : null);
        }
        String hdr = hdmi && items.contains(SignalItem.HDR) && v.hdrType != null ? SignalFormat.hdrKey(v.hdrType) : null;
        if (hdr != null) s.chips.add(new Chip(w.get(hdr), SignalFormat.isHdr(v.hdrType)));
        String edid = hdmi && items.contains(SignalItem.EDID) ? SignalFormat.edidKey(v.edid) : null;
        if (edid != null && !SignalText.EDID_AUTO.equals(edid)) s.chips.add(new Chip(w.get(edid), false));
        Boolean allm = flag(hdmi, items, SignalItem.ALLM, v.allm), vrr = flag(hdmi, items, SignalItem.VRR, v.vrr),
                freesync = flag(hdmi, items, SignalItem.FREESYNC, v.freesync),
                imax = flag(hdmi, items, SignalItem.IMAX, v.imax),
                filmmaker = flag(hdmi, items, SignalItem.FILMMAKER, v.filmmaker);
        if (Boolean.TRUE.equals(imax)) s.chips.add(new Chip(IMAX, false));
        if (Boolean.TRUE.equals(filmmaker)) s.chips.add(new Chip(FILMMAKER, false));
        if (Boolean.TRUE.equals(allm)) s.chips.add(new Chip(ALLM, false));
        if (Boolean.TRUE.equals(vrr)) s.chips.add(new Chip(VRR, false));
        if (Boolean.TRUE.equals(freesync)) s.chips.add(new Chip(FREESYNC, false));
        boolean chip = (items.contains(SignalItem.CHIP) || items.contains(SignalItem.CHIP_RATE)) && v.hasChip();
        if (input && chip) {
            s.judder = Judder.of(v.rateCenti, v.rateStepCenti, v.chipRateCenti, v.chipStepCenti);
            if (s.judder == Judder.SMOOTH) s.verdict = w.get(SignalText.VERDICT_SMOOTH);
            else if (s.judder == Judder.JUDDER) {
                s.verdict = w.get(SignalText.VERDICT_JUDDER, SignalFormat.rate(v.chipRateCenti, dec),
                        SignalFormat.rate(v.rateCenti, dec));
            }
        }
        String out = items.contains(SignalItem.AUDIO_OUT) ? SignalFormat.outKey(v.audioOut) : null;
        if (out != null) s.sound = w.get(SignalText.SOUND_LINE, w.get(out));
        // the receiver's own report: only for the signal on screen now (FormatWatch drops old or foreign answers,
        // the size must match too), else its rows are not there
        HdmiRxInfo rx = input && items.contains(SignalItem.HDMI_FORMAT) && v.rx != null && v.rx.sameSize(v.width, v.height)
                ? v.rx : null;
        Row aspect = input ? aspectRow(v, items, rx, w) : null;
        String range = hdmi && items.contains(SignalItem.RANGE)
                ? SignalFormat.rangeKey(v.range, rx == null ? null : rx.quant) : null;
        String format = rx == null ? null : SignalFormat.colorFormat(rx.format);
        String space = rx == null ? null : SignalFormat.colorimetry(rx.colorimetry);
        String spaceNote = space != null && rx.colorimetryDefault ? w.get(SignalText.COLOR_DEFAULT) : null;
        String depth = rx == null || rx.depth <= 0 ? null : w.get(SignalText.DEPTH_BITS, rx.depth);
        // the owner's order (2026-11-18): aspect, colour format and chroma, range, colorimetry, bit depth, HDR kind
        if (aspect != null) s.cardRows.add(aspect);
        if (format != null) s.cardRows.add(row(w, SignalText.R_COLOR_FORMAT, format, null, RowTone.PLAIN));
        if (range != null) s.cardRows.add(row(w, SignalText.R_RANGE, w.get(range), null, RowTone.PLAIN));
        if (space != null) s.cardRows.add(row(w, SignalText.R_COLORIMETRY, space, null, RowTone.PLAIN));
        if (depth != null) s.cardRows.add(row(w, SignalText.R_DEPTH, depth, null, RowTone.PLAIN));
        if (hdr != null) s.cardRows.add(row(w, SignalText.R_HDR, w.get(hdr), null, RowTone.PLAIN));
        // the stock OSD's colour space (vendor read, HDMI only), with the menu's colour space words
        Row gamut = hdmi ? menuRow(w, items, SignalItem.COLOR_SPACE, MENU_GAMUT, v.colorSpace) : null;
        if (gamut != null) s.cardRows.add(gamut);
        // the picture mode and motion smoothing as the menu reads and names them (over apps too)
        Row picture = menuRow(w, items, SignalItem.PICTURE_MODE, MENU_PICTURE_MODE, v.pictureMode);
        Row memc = menuRow(w, items, SignalItem.MEMC, MENU_MEMC, v.memc);
        if (picture != null) s.cardRows.add(picture);

        // ---- the full panel
        List<Row> in = new ArrayList<>();
        if (hdmi) {
            if (input) {
                String scan = v.interlaced == null ? null
                        : w.get(v.interlaced ? SignalText.INTERLACED : SignalText.PROGRESSIVE);
                in.add(row(w, SignalText.R_INPUT, inputMode, scan, RowTone.PLAIN));
                if (v.dvi != null) {
                    in.add(row(w, SignalText.R_LINK, w.get(v.dvi ? SignalText.LINK_DVI : SignalText.LINK_HDMI),
                            v.dvi ? null : linkText(rx, w), RowTone.PLAIN));
                }
                String device = rx == null ? null : SignalFormat.device(rx.sourceVendor, rx.sourceProduct);
                if (device != null) in.add(row(w, SignalText.R_DEVICE, device, kindName(rx.sourceKind, w), RowTone.PLAIN));
            } else if (items.contains(SignalItem.HDMI_INFO)) {
                in.add(row(w, SignalText.R_INPUT, s.big, null, RowTone.WARN));
            }
            if (aspect != null) in.add(aspect);
            if (format != null) in.add(row(w, SignalText.R_COLOR_FORMAT, format, null, RowTone.PLAIN));
            if (range != null) in.add(row(w, SignalText.R_RANGE, w.get(range), null, RowTone.PLAIN));
            if (space != null) in.add(row(w, SignalText.R_COLORIMETRY, space, spaceNote, RowTone.PLAIN));
            if (depth != null) in.add(row(w, SignalText.R_DEPTH, depth, null, RowTone.PLAIN));
            if (hdr != null) in.add(row(w, SignalText.R_HDR, w.get(hdr), null, RowTone.PLAIN));
            if (gamut != null) in.add(gamut);                         // the stock OSD's colour space, after the HDR kind
            // the HDR packet's static metadata, only beside an HDR kind the HAL reports and a PQ packet
            boolean pq = rx != null && hdr != null && SignalFormat.isHdr(v.hdrType) && rx.eotf == InfoFrames.EOTF_PQ;
            if (pq && rx.masterMax > 0) {
                String prim = SignalFormat.colorimetry(rx.masterPrimaries);
                in.add(row(w, SignalText.R_MASTERING, w.get(SignalText.NITS, rx.masterMax) + (prim == null ? "" : ", " + prim),
                        rx.masterMin > 0 ? w.get(SignalText.MASTER_MIN, SignalFormat.minNits(rx.masterMin, dec)) : null,
                        RowTone.PLAIN));
            }
            String light = pq ? lightText(rx, w) : null;
            if (light != null) in.add(row(w, SignalText.R_LIGHT, light, null, RowTone.PLAIN));
            yesNo(in, w, SignalText.R_IMAX, imax);
            yesNo(in, w, SignalText.R_FILMMAKER, filmmaker);
            if (edid != null) in.add(row(w, SignalText.R_EDID, w.get(edid), null, RowTone.PLAIN));
            String port = items.contains(SignalItem.PORT_HW) ? portText(v, w) : null;
            String address = SignalFormat.physical(v.physicalAddress);
            if (port != null) {
                in.add(row(w, SignalText.R_PORT, port, address == null ? null : w.get(SignalText.PORT_ADDRESS, address),
                        RowTone.PLAIN));
            }
            yesNo(in, w, SignalText.R_ALLM, allm);
            onOff(in, w, SignalText.R_VRR, vrr);
            onOff(in, w, SignalText.R_FREESYNC, freesync);
        } else {
            in.add(row(w, SignalText.R_SOURCE, w.get(SignalText.SOURCE_APPS), null, RowTone.PLAIN));
            if (s.big != null && items.contains(SignalItem.SCREEN)) {
                in.add(row(w, SignalText.R_SCREEN, s.big, null, RowTone.PLAIN));
            }
        }
        List<Row> proj = new ArrayList<>();
        if (picture != null) proj.add(picture);
        if (memc != null) proj.add(memc);
        if (chip) {
            String rate = w.get(SignalText.HZ, SignalFormat.rate(v.chipRateCenti, dec));
            String size = v.chipWidth > 0 && v.chipHeight > 0 ? SignalFormat.size(v.chipWidth, v.chipHeight) : null;
            proj.add(row(w, SignalText.R_CHIP, size == null ? rate : SignalFormat.mode(size, rate), null, RowTone.PLAIN));
        }
        if (s.judder == Judder.SMOOTH) proj.add(row(w, SignalText.R_CADENCE, w.get(SignalText.CADENCE_SMOOTH), null, RowTone.OK));
        if (s.judder == Judder.JUDDER) proj.add(row(w, SignalText.R_CADENCE, w.get(SignalText.CADENCE_JUDDER), null, RowTone.WARN));
        if (items.contains(SignalItem.GAME) && v.game != null) {
            proj.add(row(w, SignalText.R_GAME_MODE, w.get(v.game ? SignalText.ON : SignalText.OFF), null,
                    v.game ? RowTone.OK : RowTone.PLAIN));
        }
        onOff(proj, w, SignalText.R_HGIG, items.contains(SignalItem.HGIG) ? v.hgig : null);
        onOff(proj, w, SignalText.R_TONE_MAP, items.contains(SignalItem.TONE_MAP) ? v.toneMap : null);
        String dv = hdmi && items.contains(SignalItem.DV_MODE) && SignalFormat.isDolby(v.hdrType)
                ? SignalFormat.dvKey(v.dvMode) : null;
        if (dv != null) proj.add(row(w, SignalText.R_DV_MODE, w.get(dv), null, RowTone.PLAIN));
        temp(proj, w, SignalText.R_TEMP_LIGHT, items.contains(SignalItem.TEMP_LIGHT) ? v.tempLight : null);
        temp(proj, w, SignalText.R_TEMP_SYSTEM, items.contains(SignalItem.TEMP_SYSTEM) ? v.tempSystem : null);
        List<Row> snd = new ArrayList<>();
        if (out != null) snd.add(row(w, SignalText.R_OUT, w.get(out), null, RowTone.PLAIN));
        String fmt = items.contains(SignalItem.SOUND_FORMAT) ? SignalFormat.soundFormatKey(v.soundFormat) : null;
        if (fmt != null) snd.add(row(w, SignalText.R_SOUND_FORMAT, w.get(fmt), null, RowTone.PLAIN));
        section(s.rows, w, SignalText.SEC_INPUT, in);
        section(s.rows, w, SignalText.SEC_PROJECTOR, proj);
        section(s.rows, w, SignalText.SEC_SOUND, snd);
        return s;
    }

    /** The rows of the full panel without the section headers (tests, logs). */
    public List<Row> valueRows() {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) if (!r.section) out.add(r);
        return Collections.unmodifiableList(out);
    }

    /**
     * The signal's aspect from its size ("16:9"), the setting beside it when read ("setting Auto"); only the setting
     * when the size does not tell the aspect (standard definition).
     */
    private static Row aspectRow(SignalValues v, Set<SignalItem> items, HdmiRxInfo rx, SignalText.Words w) {
        String own = SignalFormat.aspectOf(v.width, v.height);
        if (own == null && rx != null) own = rx.aspect;          // standard definition: the AVI InfoFrame tells it
        String set = items.contains(SignalItem.ASPECT) ? aspectName(v.aspectSetting, w) : null;
        if (own == null && set == null) return null;
        if (own == null) return row(w, SignalText.R_ASPECT, set, null, RowTone.PLAIN);
        return row(w, SignalText.R_ASPECT, own, set == null ? null : w.get(SignalText.ASPECT_SETTING, set), RowTone.PLAIN);
    }

    private static String aspectName(String token, SignalText.Words w) {
        if (token == null) return null;
        if (SignalFormat.ASPECT_AUTO.equals(token)) return w.get(SignalText.ASPECT_AUTO);
        if (SignalFormat.ASPECT_ORIGINAL.equals(token)) return w.get(SignalText.ASPECT_ORIGINAL);
        return token;
    }

    /** "FRL 8 Gbit/s × 4", "TMDS, HDMI 2.0", "TMDS" from the receiver's report; null when it has none. */
    private static String linkText(HdmiRxInfo rx, SignalText.Words w) {
        if (rx == null || rx.link == null) return null;
        if (HdmiRxInfo.LINK_FRL.equals(rx.link)) return w.get(SignalText.LINK_FRL, rx.frlGbps, rx.frlLanes);
        return w.get(HdmiRxInfo.LINK_TMDS20.equals(rx.link) ? SignalText.LINK_TMDS20 : SignalText.LINK_TMDS);
    }

    /** CTA-861 source information of the SPD InfoFrame: the kinds a projector meets, null for the rest. */
    private static String kindName(int kind, SignalText.Words w) {
        switch (kind) {
            case 1: return w.get(SignalText.KIND_STB);
            case 2: return w.get(SignalText.KIND_DVD);
            case 8: return w.get(SignalText.KIND_GAME);
            case 9: return w.get(SignalText.KIND_PC);
            case 10: return w.get(SignalText.KIND_BLURAY);
            case 13: return w.get(SignalText.KIND_PLAYER);
            default: return null;
        }
    }

    /** MaxCLL and MaxFALL, the ones the source told; null when it told neither. */
    private static String lightText(HdmiRxInfo rx, SignalText.Words w) {
        if (rx.maxCll > 0 && rx.maxFall > 0) return w.get(SignalText.LIGHT_BOTH, rx.maxCll, rx.maxFall);
        if (rx.maxCll > 0) return w.get(SignalText.LIGHT_CLL, rx.maxCll);
        return rx.maxFall > 0 ? w.get(SignalText.LIGHT_FALL, rx.maxFall) : null;
    }

    /** A row named and valued with the menu's own words; none when not read or the menu has no word for it. */
    private static Row menuRow(SignalText.Words w, Set<SignalItem> items, SignalItem i, String feature, String token) {
        if (!items.contains(i) || token == null) return null;
        String label = w.menuTitle(feature), value = w.menuOption(feature, token);
        return label == null || value == null ? null : new Row(false, i.token, label, value, null, RowTone.PLAIN);
    }

    private static Boolean flag(boolean hdmi, Set<SignalItem> items, SignalItem i, Boolean value) {
        return hdmi && items.contains(i) ? value : null;
    }

    /** "CEC, ARC" / "CEC" / "ARC" / "without CEC and ARC"; null when not read. */
    private static String portText(SignalValues v, SignalText.Words w) {
        if (v.cec == null || v.arc == null) return null;
        if (!v.cec && !v.arc) return w.get(SignalText.PORT_PLAIN);
        if (v.cec && v.arc) return w.get(SignalText.PORT_CEC) + ", " + w.get(SignalText.PORT_ARC);
        return w.get(v.cec ? SignalText.PORT_CEC : SignalText.PORT_ARC);
    }

    private static void yesNo(List<Row> out, SignalText.Words w, String key, Boolean v) {
        if (v != null) out.add(row(w, key, w.get(v ? SignalText.YES : SignalText.NO), null, v ? RowTone.OK : RowTone.PLAIN));
    }

    private static void onOff(List<Row> out, SignalText.Words w, String key, Boolean v) {
        if (v != null) out.add(row(w, key, w.get(v ? SignalText.ON : SignalText.OFF), null, v ? RowTone.OK : RowTone.PLAIN));
    }

    private static void temp(List<Row> out, SignalText.Words w, String key, Integer c) {
        if (c != null) out.add(row(w, key, w.get(SignalText.DEGREES, c), null, RowTone.PLAIN));
    }

    private static Row row(SignalText.Words w, String key, String value, String small, RowTone tone) {
        return new Row(false, key, w.get(key), value, small, tone);
    }

    private static void section(List<Row> out, SignalText.Words w, String key, List<Row> rows) {
        if (rows.isEmpty()) return;
        out.add(new Row(true, key, w.get(key), null, null, RowTone.PLAIN));
        out.addAll(rows);
    }
}
