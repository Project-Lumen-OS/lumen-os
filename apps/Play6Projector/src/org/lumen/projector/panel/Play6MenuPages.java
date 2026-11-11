package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;
import android.view.View;

import org.lumen.menu.Feature;
import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.SliderRow;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.ToggleRow;

/**
 * XGIMI Play 6 (dragon_healthy): the device pages shown INSIDE the shared menu (MenuController.Pages
 * section 8). They carry the Play 6 rows the module has no id for, with the calls and rules of the old Play 6 panel:
 *  - picture.pro: the stock dayao rows of the current picture mode (noise reduction 670 / 671, white balance gains
 *    and offsets 672-683 with the Custom temperature, gamma IGmpf2 228 / 229, local contrast 651 / 655, HDR 649 / 650,
 *    picture style 175 / 174 with the R / G / B light levels 179 / 178 and the light reset 176(10)); a row the mode
 *    does not have is GONE (rule), never disabled;
 *  - sound.eq: the Lumen equalizer presets on the Play 6 speaker (audio/P6SoundEq), only while it can work;
 *  - settings.ambient_speaker (pending in menu.json until the module has the id): the stock ambient speaker page.
 * Built once per menu (Play6Menu drops the menu, and these pages with it, when its window closes); the menu's pages API
 * (R6) for repaint, close and the setup gate. Main thread; every HAL call on p6-hal through
 * Hal.query.
 */
final class Play6MenuPages implements MenuController.Pages {
    private static final String TAG = "P6Menu";
    private static final long CHOICE_COMMIT_MS = 500;
    /** Picture styles in row order (174 values, stock color_style_*). */
    private static final int[] STYLE_ORDER = {Play6Gmpf.STYLE_STANDARD, Play6Gmpf.STYLE_MOVIE_D65, Play6Gmpf.STYLE_OFFICE,
            Play6Gmpf.STYLE_CUSTOM};

    private final Context app;
    /** The menu these pages belong to (MenuController.Pages: given with every page call). */
    private MenuController menu;
    private Page pro, eq, speaker;
    private Play6Snapshot snap = new Play6Snapshot();

    // picture.pro rows
    private ChoiceRow nrRow, gammaRow, lcRow, styleRow;
    private final SliderRow[] wbRows = new SliderRow[6];
    private final SliderRow[] rgbRows = new SliderRow[3];
    private ToggleRow hdrRow;
    private NavRow rgbReset;
    private TextRow proEmpty;
    // sound.eq
    private ChoiceRow eqRow;
    // ambient speaker
    private NavRow spkOn;
    private ChoiceRow ambColor, ambEffect, ambLevel, lidRow;
    private TextRow ambDesc;

    Play6MenuPages(Context c) { app = c.getApplicationContext(); }

    @Override
    public Page page(String id, MenuController menu) {
        this.menu = menu;
        switch (id) {
            case Feature.PICTURE_PRO: return pro();
            case Feature.SOUND_EQ: return eq();
            case Feature.SETTINGS_AMBIENT_SPEAKER: return speaker();
            default: return null;                     // input, mirroring and the rest: Play6Features.open
        }
    }

    private String s(int res) { return app.getString(res); }

    private static CharSequence[] labels(Context c, int... res) {
        CharSequence[] out = new CharSequence[res.length];
        for (int i = 0; i < res.length; i++) out[i] = c.getString(res[i]);
        return out;
    }

    private static void visible(View v, boolean show) {
        if (v != null) v.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** A choice row shown with its value 0..max, or GONE (unknown value or not offered). */
    private static void choiceIf(ChoiceRow r, Integer v, int max) {
        boolean show = v != null && v >= 0 && v <= max;
        visible(r, show);
        if (show) {
            r.setSelected(v);
            r.setRowEnabled(true);
        }
    }

    // ================================================================== reads and writes

    private void refresh(int parts) {
        Hal.query((g, g2) -> Play6Snapshot.read(g, g2, parts), v -> {
            snap = v == null || !Hal.isReady() ? new Play6Snapshot() : v;
            apply();
        });
    }

    /** A user change on p6-hal, then {@code parts} read back. */
    private void change(String what, int parts, Hal.Task t) {
        if (!Hal.isReady()) { Ui.toast(app, R.string.toast_not_ready); return; }
        boolean queued = Hal.run((g, g2) -> {
            try {
                t.run(g, g2);
                Log.i(TAG, what);
            } catch (Throwable e) {
                Log.w(TAG, what + ": " + e);
                Ui.main().post(() -> Ui.toast(app, R.string.toast_failed));
            }
        });
        if (!queued) Ui.toast(app, R.string.toast_not_ready);
        refresh(parts);
    }

    private void apply() {
        if (pro != null) applyPro();
        if (eq != null) applyEq();
        if (speaker != null) applySpeaker();
        if (menu != null) menu.refreshPage(null);
    }

    private boolean custom() { return Play6MenuGates.custom(snap.pictureMode); }

    private boolean customStyle() {
        return custom() && snap.style != null && snap.style == Play6Gmpf.STYLE_CUSTOM && Play6Snapshot.rgbOk(snap.rgb);
    }

    // ================================================================== picture.pro

    private Page pro() {
        if (pro != null) return pro;
        Page p = new Page(s(R.string.lumen_m_pro_title));
        nrRow = p.add(new P6ChoiceRow(app, s(R.string.pro_noise), labels(app, R.string.panel_off, R.string.panel_low,
                R.string.panel_medium, R.string.panel_high, R.string.panel_auto), (row, i) -> {
                    if (!custom() || i < 0 || i > 4) { refresh(Play6Snapshot.PICTURE); return; }
                    change("671 noise reduction " + i, Play6Snapshot.PICTURE, (g, g2) -> g.setNoiseReduction(i));
                }));
        int[] wbTitles = {R.string.pro_red_gain, R.string.pro_green_gain, R.string.pro_blue_gain, R.string.pro_red_offset,
                R.string.pro_green_offset, R.string.pro_blue_offset};
        for (int k = 0; k < wbRows.length; k++) {
            final int item = k;
            wbRows[k] = p.add(new P6SliderRow(app, s(wbTitles[k]), 0, 100, (row, value) -> onWb(item, value)));
        }
        CharSequence[] gammas = new CharSequence[9];
        for (int k = 0; k < 9; k++) gammas[k] = String.format(java.util.Locale.ROOT, "%.1f", 1.8f + 0.1f * k);
        gammaRow = p.add(new P6ChoiceRow(app, s(R.string.pro_gamma), gammas, (row, i) -> {
            if (!custom() || i < 0 || i > 8) { refresh(Play6Snapshot.PICTURE); return; }
            change("IGmpf2 229 gamma " + i, Play6Snapshot.PICTURE, (g, g2) -> g2.setGamma(i));
        }));
        lcRow = p.add(new P6ChoiceRow(app, s(R.string.pro_local_contrast), labels(app, R.string.panel_off, R.string.panel_low,
                R.string.panel_medium, R.string.panel_high), (row, i) -> {
                    if (!Play6MenuGates.memcMode(snap.pictureMode) || i < 0 || i > 3) { refresh(Play6Snapshot.PICTURE); return; }
                    change("655 local contrast " + i, Play6Snapshot.PICTURE, (g, g2) -> g.setLocalContrast(i));
                }));
        // stock PictureViewModel: no HDR change on the HDMI input in HDMI 1.4 mode (EDID 1), a notice instead
        hdrRow = p.add(new ToggleRow(app, s(R.string.p6_hdr), (row, w) ->
                change("650 HDR auto " + w, Play6Snapshot.PICTURE, (g, g2) -> {
                    if (g.getCurrentInputSource() == Play6Gmpf.SOURCE_HDMI1 && g.getEdidVersion() == Play6Gmpf.EDID_14) {
                        Log.i(TAG, "650 not sent: HDMI 1.4 mode");
                        Ui.main().post(() -> Ui.toast(app, R.string.p6_hdr_hdmi_compat));
                        return;
                    }
                    g.setHdrAuto(w);
                })));
        styleRow = p.add(new P6ChoiceRow(app, s(R.string.p6_style), labels(app, R.string.panel_pm_standard,
                R.string.p6_style_movie, R.string.panel_pm_office, R.string.p6_pm_custom), (row, i) -> {
                    if (!custom() || i < 0 || i >= STYLE_ORDER.length) { refresh(Play6Snapshot.PICTURE); return; }
                    final int st = STYLE_ORDER[i];
                    change("174 picture style " + st, Play6Snapshot.PICTURE | Play6Snapshot.LAMP, (g, g2) -> g.setPictureStyle(st));
                }));
        int[] rgbTitles = {R.string.p6_light_red, R.string.p6_light_green, R.string.p6_light_blue};
        for (int k = 0; k < rgbRows.length; k++) {
            final int item = k;
            rgbRows[k] = p.add(new P6SliderRow(app, s(rgbTitles[k]), 1, 10, (row, value) -> onRgb(item, value)));
        }
        rgbReset = p.add(new NavRow(app, s(R.string.p6_light_reset), () -> {
            if (!customStyle()) return;
            change("176 lamp level 10 (stock light reset)", Play6Snapshot.PICTURE | Play6Snapshot.LAMP,
                    (g, g2) -> g.setLampLevel(10));
        }).setChevron(false));
        proEmpty = p.add(new TextRow(app, s(R.string.p6_pic_game_hint)));
        for (ChoiceRow r : new ChoiceRow[] {nrRow, gammaRow, lcRow, styleRow}) r.setCommitDelay(CHOICE_COMMIT_MS);
        for (View v : new View[] {nrRow, gammaRow, lcRow, hdrRow, styleRow, rgbReset, proEmpty}) v.setVisibility(View.GONE);
        for (SliderRow r : wbRows) r.setVisibility(View.GONE);
        for (SliderRow r : rgbRows) r.setVisibility(View.GONE);
        p.setOnShown(() -> refresh(Play6Snapshot.PICTURE));
        pro = p;
        return p;
    }

    /** RGB gain / offset (0 red gain 683, 1 green 681, 2 blue 679, 3 red offset 677, 4 green 675, 5 blue 673). */
    private void onWb(int item, int value) {
        if (!custom() || snap.colorTemp == null || snap.colorTemp != Play6MenuGates.CT_CUSTOM || value < 0 || value > 100) {
            refresh(Play6Snapshot.PICTURE);
            return;
        }
        change("wb " + item + " " + value, Play6Snapshot.PICTURE, (g, g2) -> {
            boolean r;
            switch (item) {
                case 0: r = g.setRedGain(value); break;
                case 1: r = g.setGreenGain(value); break;
                case 2: r = g.setBlueGain(value); break;
                case 3: r = g.setRedOffset(value); break;
                case 4: r = g.setGreenOffset(value); break;
                default: r = g.setBlueOffset(value); break;
            }
            if (!r) throw new IllegalStateException("white balance item " + item + " returned false");
        });
    }

    /** One light level (0 red, 1 green, 2 blue): the other two from a fresh 179 read, then 178 with all three (stock). */
    private void onRgb(int item, int value) {
        if (!customStyle() || value < 1 || value > 10) { refresh(Play6Snapshot.PICTURE); return; }
        change("178 light level " + item + " " + value, Play6Snapshot.PICTURE, (g, g2) -> {
            int[] cur = g.getRgbLevels();
            if (!Play6Snapshot.rgbOk(cur)) throw new IllegalStateException("179 reply " + java.util.Arrays.toString(cur));
            int r = item == 0 ? value : cur[0], gr = item == 1 ? value : cur[1], b = item == 2 ? value : cur[2];
            g.setRgbLevels(r, gr, b);
        });
    }

    private void applyPro() {
        final Play6Snapshot sn = snap;
        final boolean ok = Hal.isReady();
        final boolean g2 = ok && sn.gmpf2 && Hal.hasGmpf2();
        boolean custom = ok && custom();
        boolean memcOk = ok && Play6MenuGates.memcMode(sn.pictureMode);
        choiceIf(nrRow, custom ? sn.nr : null, 4);
        boolean ctCustom = custom && sn.colorTemp != null && sn.colorTemp == Play6MenuGates.CT_CUSTOM;
        for (int k = 0; k < wbRows.length; k++) {
            Integer v = ctCustom ? sn.wb[k] : null;
            wbRows[k].setValue(v);
            wbRows[k].setRowEnabled(v != null);
            visible(wbRows[k], v != null);
        }
        choiceIf(gammaRow, custom && g2 ? sn.gamma : null, 8);
        choiceIf(lcRow, memcOk ? sn.localContrast : null, 3);
        Boolean hdr = ok && Play6MenuGates.hdrMode(sn.pictureMode) ? sn.hdrAuto : null;
        visible(hdrRow, hdr != null);
        if (hdr != null) {
            hdrRow.setChecked(hdr);
            hdrRow.setRowEnabled(true);
        }
        int st = custom && sn.style != null ? indexOf(STYLE_ORDER, sn.style) : -1;
        visible(styleRow, custom && sn.style != null);
        styleRow.setSelected(st);
        styleRow.setRowEnabled(custom && sn.style != null);
        boolean rgb = custom && customStyle();
        for (int k = 0; k < rgbRows.length; k++) {
            Integer v = rgb ? sn.rgb[k] : null;
            rgbRows[k].setValue(v);
            rgbRows[k].setRowEnabled(v != null);
            visible(rgbRows[k], v != null);
        }
        visible(rgbReset, rgb);
        // the Game picture mode (666 = 4) has none of these rows: the stock hint says how to leave it
        visible(proEmpty, ok && Play6MenuGates.gamePicture(sn.pictureMode));
    }

    private static int indexOf(int[] arr, Integer v) {
        if (v == null) return -1;
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return -1;
    }

    // ================================================================== sound.eq

    private Page eq() {
        if (eq != null) return eq;
        Page p = new Page(s(R.string.eq_title));
        // 2026-10-21: the equalizer of the Z9X on the Play 6 speaker (audio/P6SoundEq), only while it can work
        eqRow = p.add(new P6ChoiceRow(app, s(R.string.eq_title), labels(app, R.string.eq_preset_flat, R.string.eq_preset_bass,
                R.string.eq_preset_voice, R.string.eq_preset_night), (row, i) -> {
                    if (!Boolean.TRUE.equals(snap.eqOk)) { refresh(Play6Snapshot.SOUND); return; }
                    org.lumen.projector.audio.P6SoundEq.choose(app, i);
                }));
        eqRow.setCommitDelay(CHOICE_COMMIT_MS);
        eqRow.setVisibility(View.GONE);
        p.setOnShown(() -> refresh(Play6Snapshot.SOUND));
        eq = p;
        return p;
    }

    private void applyEq() {
        boolean on = Hal.isReady() && Boolean.TRUE.equals(snap.eqOk);
        visible(eqRow, on);                           // DTS on or not the speaker: no row (P5 asks to hide the entry)
        if (on) {
            eqRow.setSelected(org.lumen.projector.audio.P6SoundEq.preset(app));
            eqRow.setRowEnabled(true);
        }
    }

    // ================================================================== the ambient speaker

    private Page speaker() {
        if (speaker != null) return speaker;
        Page p = new Page(s(R.string.p6_spk_page));
        spkOn = p.add(new NavRow(app, s(R.string.p6_ambsp_on), () -> {
            if (menu == null || !menu.setupDone("ambient speaker")) return;
            menu.closeThen(() -> org.lumen.projector.speaker.SpeakerMode.openAmbient(app));
        }).setChevron(false));
        ambColor = p.add(new P6ChoiceRow(app, s(R.string.p6_amb_color), labels(app, R.string.p6_amb_c_orange,
                R.string.p6_amb_c_blue, R.string.p6_amb_c_yellow, R.string.p6_amb_c_green, R.string.p6_amb_c_pink,
                R.string.p6_amb_c_purple, R.string.p6_amb_c_colorful, R.string.p6_amb_c_none),
                (row, i) -> org.lumen.projector.ambient.AmbientLight.setColor(app, i)));
        ambEffect = p.add(new P6ChoiceRow(app, s(R.string.p6_amb_effect), labels(app, R.string.p6_amb_e_standard,
                R.string.p6_amb_e_always, R.string.p6_amb_e_rhythm),
                (row, i) -> org.lumen.projector.ambient.AmbientLight.setMode(app, i)));
        CharSequence[] levels = new CharSequence[11];
        levels[0] = s(R.string.p6_amb_level_same);
        for (int i = 1; i <= 10; i++) levels[i] = Integer.toString(i);
        ambLevel = p.add(new P6ChoiceRow(app, s(R.string.p6_amb_level), levels,
                (row, i) -> org.lumen.projector.ambient.AmbientLight.setLevel(app, i)));
        ambLevel.setCommitDelay(CHOICE_COMMIT_MS);
        lidRow = p.add(new P6ChoiceRow(app, s(R.string.p6_lid), labels(app, R.string.p6_lid_off, R.string.p6_lid_speaker),
                (row, i) -> {
                    if (snap.slideFunc == null || i < 0 || i > 1) { refresh(Play6Snapshot.AMBIENT); return; }
                    change("IGmpf2 69 slide function " + i, Play6Snapshot.AMBIENT, (g, g2) -> {
                        if (!g2.setSlideFunction(i)) throw new IllegalStateException("IGmpf2 69 returned false");
                    });
                }));
        lidRow.setCommitDelay(CHOICE_COMMIT_MS);
        ambDesc = p.add(new TextRow(app, s(R.string.p6_amb_desc_one)));
        for (View v : new View[] {spkOn, ambColor, ambEffect, ambLevel, lidRow, ambDesc}) v.setVisibility(View.GONE);
        p.setOnShown(() -> refresh(Play6Snapshot.ROOT | Play6Snapshot.AMBIENT));
        speaker = p;
        return p;
    }

    /** The speaker rows with the image's A2DP sink role, the light rows while IGmpf2 71 reads true, the lid row once 70 read. */
    private void applySpeaker() {
        boolean ok = Hal.isReady();
        boolean amb = ok && snap.gmpf2 && Hal.hasGmpf2() && Boolean.TRUE.equals(snap.ambientOk);
        boolean spk = ok && org.lumen.projector.speaker.SpeakerMode.supported();
        visible(spkOn, spk || amb);
        visible(ambDesc, spk || amb);
        for (View v : new View[] {ambColor, ambEffect, ambLevel}) visible(v, amb);
        ambColor.setSelected(org.lumen.projector.ambient.AmbientLight.colorIndex(app));
        ambEffect.setSelected(org.lumen.projector.ambient.AmbientLight.modeIndex(app));
        ambLevel.setSelected(org.lumen.projector.ambient.AmbientLight.level(app));
        Integer lid = amb ? snap.slideFunc : null;
        visible(lidRow, lid != null);
        if (lid != null) lidRow.setSelected(lid == 0 ? 0 : 1);
    }
}
