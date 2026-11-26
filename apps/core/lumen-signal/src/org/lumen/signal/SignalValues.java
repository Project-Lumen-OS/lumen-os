// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import java.util.EnumMap;
import java.util.Map;

/**
 * What the panel knows now. A reader fills only what it read and what is plausible; an empty field hides its row.
 * Rates are in 1/100 Hz everywhere (the vendor's own unit of getHdmiInfo), temperatures in whole degrees C.
 */
public final class SignalValues {
    /** HDMI port on screen (1..n), 0 = apps (no HDMI input on screen) */
    public int port;
    /** input timing (HDMI_INFO) */
    public int width, height, rateCenti;
    /**
     * The receiver's last measured rate; rateCenti is what the card shows (SteadyRate: the nominal table rate once the
     * reads agree, 0 while a new one waits). vrrMoving: VRR runs and the reads move, the card says "VRR".
     */
    public int measuredCenti;
    /**
     * The read step of the input rate and of the chip rate, 1/100 Hz, set by the reader that knows its vendor unit (0 =
     * unknown: no judder line). Input: {@link #STEP_TENTH_HZ} (59.94 Hz reads 5990), chip: {@link #STEP_WHOLE_HZ} (cut down).
     */
    public int rateStepCenti, chipStepCenti;
    /** The read steps of our vendors in 1/100 Hz: the MI_EXTIN input timing (0.1 Hz) and a whole Hz chip rate. */
    public static final int STEP_TENTH_HZ = 10, STEP_WHOLE_HZ = 100;
    public boolean vrrMoving;
    private final SteadyRate steady = new SteadyRate();
    public Boolean interlaced, dvi;
    /** vendor getHdrType: 0 SDR, 1 HDR10, 2 Dolby Vision, 4 HLG, 5 HDR10+, 6 HDR Vivid */
    public Integer hdrType;
    /** chip (CHIP / CHIP_RATE) */
    public int chipWidth, chipHeight, chipRateCenti;
    /** tokens: range auto / full / limited, edid 1.4 / 2.0 / 2.1 / auto */
    public String range, edid;
    /** the aspect ratio setting token (ASPECT): SignalFormat.ASPECT_* */
    public String aspectSetting;
    public Boolean allm, vrr, freesync, imax, filmmaker, game, hgig, toneMap;
    /** the HDMI port (PORT_HW): CEC on, ARC there, CEC physical address (0 = none) */
    public Boolean cec, arc;
    public int physicalAddress;
    /** Dolby Vision picture mode: 0 bright, 1 dark (DV_MODE) */
    public Integer dvMode;
    /**
     * the menu's option tokens of the picture mode (PICTURE_MODE, devices/&lt;c&gt;/menu.json "picture.mode") and of
     * motion smoothing (MEMC, "picture.memc"); the panel names them with the menu's own words
     */
    public String pictureMode, memc;
    /** the menu's colour space option token of the stock OSD's colour space read (COLOR_SPACE, "picture.gamut") */
    public String colorSpace;
    /** degrees C (TEMP_LIGHT, TEMP_SYSTEM) */
    public Integer tempLight, tempSystem;
    /** Android display mode (SCREEN) */
    public int screenWidth, screenHeight, screenRateCenti;
    /** media sound output (AUDIO_OUT): SignalFormat.OUT_*; sound format setting (SOUND_FORMAT): SignalFormat.FMT_* */
    public String audioOut, soundFormat;
    /** the HDMI receiver's own report (HDMI_FORMAT), null = hide its rows (FormatWatch never leaves an old one here) */
    public HdmiRxInfo rx;

    private final Map<SignalItem, Long> readAt = new EnumMap<>(SignalItem.class);

    public boolean hasInput() { return width > 0 && height > 0 && (rateCenti > 0 || measuredCenti > 0); }

    /** A new input rate waits for the read that confirms it (SteadyRate.CONFIRM_MS). */
    public boolean ratePending() { return steady.pending(); }

    public boolean hasChip() { return chipRateCenti > 0; }

    /** Takes every field {@code fresh} read (items), keeps the rest. */
    public void merge(SignalValues fresh, Iterable<SignalItem> items, long now) {
        for (SignalItem i : items) {
            readAt.put(i, now);
            switch (i) {
                case HDMI_INFO:
                    width = fresh.width; height = fresh.height; measuredCenti = fresh.rateCenti;
                    rateStepCenti = fresh.rateStepCenti;
                    rateCenti = steady.next(fresh.width, fresh.height, fresh.rateCenti, Boolean.TRUE.equals(vrr));
                    vrrMoving = steady.vrrMoving();
                    interlaced = fresh.interlaced; dvi = fresh.dvi;
                    break;
                case HDR: hdrType = fresh.hdrType; break;
                case CHIP:
                    chipWidth = fresh.chipWidth; chipHeight = fresh.chipHeight;
                    if (fresh.chipRateCenti > 0 || chipWidth == 0) {
                        chipRateCenti = fresh.chipRateCenti;
                        chipStepCenti = fresh.chipStepCenti;
                    }
                    break;
                case CHIP_RATE: chipRateCenti = fresh.chipRateCenti; chipStepCenti = fresh.chipStepCenti; break;
                case RANGE: range = fresh.range; break;
                case EDID: edid = fresh.edid; break;
                case ASPECT: aspectSetting = fresh.aspectSetting; break;
                case ALLM: allm = fresh.allm; break;
                case VRR: vrr = fresh.vrr; break;
                case FREESYNC: freesync = fresh.freesync; break;
                case IMAX: imax = fresh.imax; break;
                case FILMMAKER: filmmaker = fresh.filmmaker; break;
                case PORT_HW: cec = fresh.cec; arc = fresh.arc; physicalAddress = fresh.physicalAddress; break;
                case DV_MODE: dvMode = fresh.dvMode; break;
                case GAME: game = fresh.game; break;
                case HGIG: hgig = fresh.hgig; break;
                case TONE_MAP: toneMap = fresh.toneMap; break;
                case TEMP_LIGHT: tempLight = fresh.tempLight; break;
                case TEMP_SYSTEM: tempSystem = fresh.tempSystem; break;
                case PICTURE_MODE: pictureMode = fresh.pictureMode; break;
                case MEMC: memc = fresh.memc; break;
                case COLOR_SPACE: colorSpace = fresh.colorSpace; break;
                case SCREEN:
                    screenWidth = fresh.screenWidth; screenHeight = fresh.screenHeight;
                    screenRateCenti = fresh.screenRateCenti;
                    break;
                case AUDIO_OUT: audioOut = fresh.audioOut; break;
                case SOUND_FORMAT: soundFormat = fresh.soundFormat; break;
                case HDMI_FORMAT: rx = fresh.rx; break;
                default: break;
            }
        }
    }

    /** True when the signal the user sees changed: size, rate, HDR kind (a new film, a new console mode). */
    public static boolean changed(SignalValues a, SignalValues b) {
        if (a == null || b == null) return a != b;
        return a.width != b.width || a.height != b.height || a.rateCenti != b.rateCenti
                || !same(a.hdrType, b.hdrType) || a.port != b.port;
    }

    private static boolean same(Object x, Object y) { return x == null ? y == null : x.equals(y); }
}
