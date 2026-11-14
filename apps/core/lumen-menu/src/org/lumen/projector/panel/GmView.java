package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;

import org.lumen.projector.R;
import org.lumen.projector.SafeHandler;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the game menu of design A on one canvas in 1920 x 1080 design units (qp_design/game_menu.py A_CSS): the bottom
 * shade, the status card (controller frames or a computer icon on a soft glow, name, input, live chips), the presets,
 * the dock with group labels and tiles, the popup above the focused tile, the Turbo question, the note and the first
 * time hint. Motion as the quick menu D: focus scale 1.08 in 300 ms on the stock ease, an own icon animation per tile
 * (AnimatedVectorDrawables of tools/gm_icons.py), the console turn then sway: drawn in real time on the GPU by
 * Gm3dView over the picture slot (GmStage), or from the frame strip (GmModel.frameAt) when that is off or fails
 * (Gm3dGate).
 */
final class GmView extends View implements Gm3dView.Listener {
    private static final String TAG = GameMenu.TAG;
    // colours of the prototype (COMMON_CSS)
    static final int ACCENT = 0xFF3B78E7, TILE = 0xFF34353A, TILE_HI = 0xFF3E3F45, PANEL = 0xF016171A,
            INK = 0xFFF4F4F6, MUTE = 0x8FF4F4F6, PERF = 0xFFFFC93A, PERF_DARK = 0xFF1B1400;
    private static final long FOCUS_MS = 300;

    private final GmController ctl;
    private final Map<String, Long> focusAt = new HashMap<>(), blurAt = new HashMap<>();
    private final Map<String, QpdStyle.Icon> icons = new HashMap<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), aimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final TextPaint tName, tSub, tChip, tGroup, tTitle, tValue, tPreset, tPresetLabel, tPopHead, tPopDesc, tRow,
            tBadge, tConfirmTitle, tConfirmText, tButton, tToast, tHint, tSmallLabel;
    private long enterAt, exitAt = -1;
    private Runnable exitEnd;
    private long popupAt;
    /** Popup focus move (prototype .po transition: the accent fill fades between rows in 200 ms). */
    private static final long ROW_MS = 200;
    private int rowFrom = -1;
    private long rowAt;
    private final Frames frames = new Frames();
    private String framesTheme;
    /** The real-time controller over the picture slot (GmStage), null without it; the gate picks model or strip. */
    private Gm3dView model;
    private final Gm3dGate gate = new Gm3dGate();
    private float modelShift = Float.NaN;

    GmView(Context c, GmController ctl) {
        super(c);
        this.ctl = ctl;
        tName = paint(c, 30, 650, INK);
        tSub = paint(c, 18, 500, MUTE);
        tChip = paint(c, 17, 550, 0xDBF4F4F6);
        tChip.setFontFeatureSettings("'tnum', 'cv11', 'ss03'");
        tGroup = paint(c, 16, 600, MUTE);
        tTitle = paint(c, 17, 600, INK);
        tValue = paint(c, 16, 500, MUTE);
        tPreset = paint(c, 20, 600, INK);
        tPresetLabel = paint(c, 17, 600, MUTE);
        tPopHead = paint(c, 18, 600, MUTE);
        tPopDesc = paint(c, 18, 400, 0xBDF4F4F6);
        tRow = paint(c, 23, 500, INK);
        tBadge = paint(c, 14, 700, PERF);
        tConfirmTitle = paint(c, 28, 650, INK);
        tConfirmText = paint(c, 20, 400, 0xBDF4F4F6);
        tButton = paint(c, 22, 600, INK);
        tToast = paint(c, 21, 500, INK);
        tHint = paint(c, 19, 500, 0xDBF4F4F6);
        tSmallLabel = paint(c, 16, 600, MUTE);
        setWillNotDraw(false);
        focusAt.put(ctl.focusId(), SystemClock.uptimeMillis());
    }

    /** Inter at a design size (px of the 1920 wide canvas) and weight. */
    static TextPaint paint(Context c, float size, int weight, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(QpdStyle.inter(c, weight, Math.round(size)));
        p.setTextSize(size);
        p.setColor(color);
        p.setFontFeatureSettings("'cv11', 'ss03'");
        return p;
    }

    void attachModel(Gm3dView m) { model = m; }

    /** The view that fades: the stage (with the model in it) when there is one. */
    private View fadeTarget() {
        return getParent() instanceof GmStage ? (View) getParent() : this;
    }

    // ================================================================== lifecycle
    void enter() {
        enterAt = SystemClock.uptimeMillis();
        View fade = fadeTarget();
        fade.setAlpha(0f);
        fade.animate().alpha(1f).setDuration(240).setInterpolator(QpdStyle.ease()).start();
        if (!ctl.theme.equals(framesTheme)) startPicture(ctl.theme);   // a first draw before enter() may have started it
        startIcon(ctl.focusId());
        invalidate();
    }

    void exit(Runnable end) {
        exitAt = SystemClock.uptimeMillis();
        exitEnd = end;
        View fade = fadeTarget();
        fade.animate().cancel();
        fade.animate().alpha(0f).setDuration(200).setInterpolator(QpdStyle.ease()).withEndAction(this::finishExit).start();
        postDelayed(this::finishExit, 400);
    }

    /**
     * The card's picture for {@code theme}: a console turns on the GPU (Gm3dView) unless the gate sends it to the frame
     * strip (model off, no GPU drawing in this window, an earlier failure); a computer gets its icon.
     */
    private void startPicture(String theme) {
        framesTheme = theme;
        if (!GmModel.isConsole(theme)) {
            gate.off();
            if (model != null) model.hide();
            frames.start(theme);
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (model != null && gate.begin(theme, true, isHardwareAccelerated(), now)) {
            frames.startHero(theme);           // only the hero frame, holding the card until the model's first frame
            model.show(theme);
            return;
        }
        if (model != null) model.hide();
        frames.start(theme);
    }

    /** The strip takes over from the model (it failed or missed its time). */
    private void toFrames(String why) {
        Log.i(TAG, "controller picture: frame strip (" + why + ")");
        if (model != null) model.hide();
        frames.start(framesTheme);
        invalidate();
    }

    @Override public void onModelReady(String theme) {
        gate.ready(theme);
        if (gate.live()) frames.release();
        invalidate();
    }

    @Override public void onModelFailed(String theme, String why, boolean hard) {
        if (gate.failed(theme, hard)) toFrames(why);
    }

    private void finishExit() {
        Runnable e = exitEnd;
        exitEnd = null;
        if (e != null) e.run();
    }

    void release() {
        frames.release();
        if (model != null) model.release();
        for (QpdStyle.Icon i : icons.values()) i.release();
        icons.clear();
    }

    void onFocusMoved(String from, String to) {
        long now = SystemClock.uptimeMillis();
        blurAt.put(from, now);
        focusAt.remove(from);
        focusAt.put(to, now);
        blurAt.remove(to);
        QpdStyle.Icon old = icons.get(from);
        if (old != null) old.rest();
        startIcon(to);
        invalidate();
    }

    /** The focused element changed without a key (a tile was hidden): move the focus look to it. */
    void syncFocus(String id) {
        String cur = null;
        for (Map.Entry<String, Long> e : focusAt.entrySet()) cur = e.getKey();
        if (cur == null || !cur.equals(id)) onFocusMoved(cur == null ? "none" : cur, id);
    }

    void onPopup(boolean open) {
        popupAt = SystemClock.uptimeMillis();
        rowFrom = -1;
        rowAt = 0;
        invalidate();
    }

    /** The focused popup row moved from {@code from} to {@code to} (= ctl.prow). */
    void onPopupRow(int from, int to) {
        rowFrom = from;
        rowAt = SystemClock.uptimeMillis();
        invalidate();
    }

    /** Accent fill 0..1 of popup row {@code i}: the focused row fades in, the row it left fades out. */
    private float rowFocus(int i, long now) {
        float e = rowAt == 0 ? 1f : ease((now - rowAt) / (float) ROW_MS);
        if (i == ctl.prow) return rowFrom == i ? 1f : e;
        if (i == rowFrom) return 1f - e;
        return 0f;
    }

    private void startIcon(String id) {
        QpdStyle.Icon i = icon(id);
        if (i != null) i.start();
    }

    private QpdStyle.Icon icon(String id) {
        QpdStyle.Icon i = icons.get(id);
        if (i != null) return i;
        int res = iconRes(id);
        if (res == 0) return null;
        i = new QpdStyle.Icon(this, res);
        icons.put(id, i);
        return i;
    }

    private static int iconRes(String id) {
        switch (id) {
            case "t:lat": case "p:0": case "chip:zap": return R.drawable.lumen_gm_ic_zap;
            case "t:vrr": return R.drawable.lumen_gm_ic_vrr;
            case "t:hfr": return R.drawable.lumen_gm_ic_hfr;
            case "t:pic": return R.drawable.lumen_gm_ic_pic;
            case "t:cs": return R.drawable.lumen_gm_ic_sliders;
            case "t:hdr": return R.drawable.lumen_gm_ic_sr;
            case "t:dark": return R.drawable.lumen_gm_ic_dark;
            case "t:sr": return R.drawable.lumen_gm_ic_sr;
            case "t:ar": return R.drawable.lumen_gm_ic_ar;
            case "t:snd": return R.drawable.lumen_gm_ic_snd;
            case "t:aim": return R.drawable.lumen_gm_ic_aim;
            case "t:fps": return R.drawable.lumen_gm_ic_fps;
            case "p:1": return R.drawable.lumen_gm_ic_sliders;
            case "p:2": return R.drawable.lumen_gm_ic_film;
            case "move": return R.drawable.lumen_gm_ic_move;
            case "info": return R.drawable.lumen_gm_ic_info;
            case "gear": return R.drawable.lumen_gm_ic_gear;
            case "pc:" + GmModel.TH_APPLE: return R.drawable.lumen_gm_ic_apple;
            case "pc:" + GmModel.TH_WINDOWS: return R.drawable.lumen_gm_ic_windows;
            case "pc:" + GmModel.TH_COMPUTER: return R.drawable.lumen_gm_ic_laptop;
            default: return 0;
        }
    }

    @Override protected boolean verifyDrawable(Drawable who) {
        for (QpdStyle.Icon i : icons.values()) if (i.is(who)) return true;
        return super.verifyDrawable(who);
    }

    // ================================================================== motion helpers
    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        // cubic-bezier(0,0,.58,1) is close to an ease-out cubic
        float u = 1 - t;
        return 1 - u * u * u;
    }

    /** Focus progress 0..1 of element {@code id}. */
    private float focus(String id, long now) {
        Long f = focusAt.get(id);
        if (f != null) return ease((now - f) / (float) FOCUS_MS);
        Long b = blurAt.get(id);
        if (b != null) {
            float t = (now - b) / (float) FOCUS_MS;
            if (t >= 1) { blurAt.remove(id); return 0; }
            return 1 - ease(t);
        }
        return 0;
    }

    private boolean animating(long now) {
        for (Long f : focusAt.values()) if (now - f < FOCUS_MS + 20) return true;
        if (!blurAt.isEmpty()) return true;
        if (now - enterAt < 400 || now - popupAt < 300) return true;
        if (rowAt != 0 && now - rowAt < ROW_MS + 20) return true;
        return false;
    }

    // ================================================================== layout (design units)
    private static final float TILE_W = 164, TILE_H = 172, TILE_GAP = 12, GROUP_GAP = 26, DOCK_PAD_X = 22,
            DOCK_LEFT = 43, DOCK_BOTTOM = 1044, DOCK_PAD_B = 22, LABEL_H = 33, DOCK_PAD_T = 16;
    private final RectF dock = new RectF();
    private final Map<String, RectF> tileRects = new HashMap<>();

    private void layoutDock() {
        List<String[]> groups = GmModel.visibleGroups(GameMenu.CAPS);
        int n = 0;
        for (String[] g : groups) n += g.length - 1;
        float avail = 1920 - 2 * DOCK_LEFT - 2 * DOCK_PAD_X - GROUP_GAP * Math.max(0, groups.size() - 1)
                - TILE_GAP * Math.max(0, n - groups.size());
        float tw = n == 0 ? TILE_W : Math.min(TILE_W, avail / n);
        float tilesBottom = DOCK_BOTTOM - DOCK_PAD_B, tilesTop = tilesBottom - TILE_H;
        float x = DOCK_LEFT + DOCK_PAD_X;
        tileRects.clear();
        for (int g = 0; g < groups.size(); g++) {
            String[] row = groups.get(g);
            for (int i = 1; i < row.length; i++) {
                tileRects.put(row[i], new RectF(x, tilesTop, x + tw, tilesBottom));
                x += tw + (i < row.length - 1 ? TILE_GAP : 0);
            }
            if (g < groups.size() - 1) x += GROUP_GAP;
        }
        dock.set(DOCK_LEFT, tilesTop - LABEL_H - DOCK_PAD_T, x + DOCK_PAD_X, DOCK_BOTTOM);
    }

    // ================================================================== draw
    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float k = getWidth() / 1920f;
        if (k <= 0) return;
        c.save();
        c.scale(k, k);
        if (!ctl.theme.equals(framesTheme)) startPicture(ctl.theme);
        if (gate.timedOut(now)) toFrames("no first frame in " + Gm3dGate.READY_MS + " ms");
        layoutDock();
        drawShade(c);
        float rise = (1 - ease((now - enterAt) / 340f));
        c.save();
        c.translate(0, -16 * rise);
        drawStatus(c, now);
        c.restore();
        c.save();
        c.translate(0, 24 * rise);
        if (ctl.popup == null) drawPresets(c, now);
        drawDock(c, now);
        c.restore();
        if (ctl.popup != null) drawPopup(c, now);
        if (ctl.first && ctl.popup == null) drawHint(c);
        if (ctl.confirmOk != null) drawConfirm(c);
        if (ctl.toast != null && now < ctl.toastUntil) drawToast(c);
        c.restore();
        if (animating(now)) postInvalidateOnAnimation();
        else if (ctl.toast != null && now < ctl.toastUntil) postInvalidateDelayed(ctl.toastUntil - now + 10);
        // the strip needs a redraw per frame; the model draws itself (only its first-frame wait is checked here)
        boolean strip = GmModel.isConsole(ctl.theme) && gate.frames();
        if (strip || allmLive()) {
            long t = now - frames.t0;
            postInvalidateDelayed(strip && t < GmModel.TURN_MS ? 33 : 140);
        }
        if (gate.pending()) postInvalidateDelayed(50);
        placeModel(k, rise);
    }

    /** The model moves with the card (its rise on entry) and darkens under the question's shade. */
    private void placeModel(float k, float rise) {
        if (model == null) return;
        float shift = -16 * rise * k;
        if (shift != modelShift) {
            modelShift = shift;
            model.setTranslationY(shift);
        }
        model.dim(ctl.confirmOk != null);
    }

    private boolean allmLive() { return ctl.snap != null && Boolean.TRUE.equals(ctl.snap.allm); }

    private void card(Canvas c, RectF rr, float radius) {
        fill.setShader(null);
        fill.setColor(PANEL);
        fill.setShadowLayer(40, 0, 20, 0x8C000000);
        c.drawRoundRect(rr, radius, radius, fill);
        fill.clearShadowLayer();
    }

    private void drawShade(Canvas c) {
        fill.setShader(new LinearGradient(0, 1080, 0, 620, new int[] {0xB8000000, 0x59000000, 0x00000000},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 620, 1920, 1080, fill);
        fill.setShader(null);
    }

    // ---- status card
    private void drawStatus(Canvas c, long now) {
        Context cx = getContext();
        String name = ctl.cecName != null ? ctl.cecName : "HDMI " + ctl.port;
        String sub = "HDMI " + ctl.port + (GmModel.isConsole(ctl.theme) ? ", " + cx.getString(R.string.lumen_gm_kind_console)
                : GmModel.isKnownComputer(ctl.theme) ? ", " + cx.getString(R.string.lumen_gm_kind_computer) : "");
        List<Chip> chips = chips();
        float chipsW = 0;
        for (Chip ch : chips) chipsW += ch.w + 8;
        float textW = Math.max(Math.max(tName.measureText(name), tSub.measureText(sub)), chipsW - 8);
        float x0 = 40, y0 = 40, w = 10 + 250 + 22 + textW + 30, h = 178;
        r.set(x0, y0, x0 + w, y0 + h);
        card(c, r, 40);
        // picture slot with the glow
        RectF slot = new RectF(x0 + 10, y0 + 14, x0 + 260, y0 + 164);
        int glow = GmModel.glow(ctl.theme);
        float gx = slot.centerX(), gy = slot.top + slot.height() * 0.46f;
        fill.setShader(new RadialGradient(gx, gy, slot.width() * 0.62f,
                new int[] {(0x61 << 24) | (glow & 0xFFFFFF), (glow & 0xFFFFFF)}, new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.clipRect(slot);
        c.drawRoundRect(slot, 30, 30, fill);
        fill.setShader(null);
        if (GmModel.isConsole(ctl.theme) && (model == null || gate.frames() || gate.pending())) {
            Bitmap b = frames.frame(now);
            if (b != null) {
                float s = Math.min(slot.width() / b.getWidth(), slot.height() / b.getHeight());
                float bw = b.getWidth() * s, bh = b.getHeight() * s;
                r.set(slot.centerX() - bw / 2, slot.centerY() - bh / 2, slot.centerX() + bw / 2, slot.centerY() + bh / 2);
                fill.setFilterBitmap(true);
                c.drawBitmap(b, null, r, fill);
            }
        } else {
            QpdStyle.Icon i = icon("pc:" + ctl.theme);
            Drawable d = i == null ? null : i.drawable();
            if (d != null) {
                float sz = 92;
                d.setBounds(Math.round(slot.centerX() - sz / 2), Math.round(slot.centerY() - sz / 2),
                        Math.round(slot.centerX() + sz / 2), Math.round(slot.centerY() + sz / 2));
                d.setTint(0xFFEDEFF4);
                d.draw(c);
            }
        }
        c.restore();
        float tx = x0 + 10 + 250 + 22;
        c.drawText(name, tx, y0 + 14 + 44, tName);
        c.drawText(sub, tx, y0 + 14 + 76, tSub);
        float cx0 = tx, cy0 = y0 + 14 + 96;
        for (Chip ch : chips) {
            drawChip(c, ch, cx0, cy0, now);
            cx0 += ch.w + 8;
        }
    }

    private static final class Chip {
        String text;
        int kind;          // 0 plain, 1 on (accent), 2 perf (yellow)
        boolean live, zap;
        float w;
    }

    private Chip chip(String t, int kind, boolean live, boolean zap) {
        Chip ch = new Chip();
        ch.text = t;
        ch.kind = kind;
        ch.live = live;
        ch.zap = zap;
        ch.w = 26 + tChip.measureText(t) + (live ? 15 : 0) + (zap ? 25 : 0);
        return ch;
    }

    /** Only what the HAL reports: signal (662), HDR (620), ALLM (310), VRR (300), speed (647). */
    private List<Chip> chips() {
        java.util.ArrayList<Chip> l = new java.util.ArrayList<>();
        Context cx = getContext();
        GameMenu.Snap s = ctl.snap;
        if (s == null) return l;
        if (s.width > 0 && s.height > 0) {
            int hz = GmModel.fpsShown(s.frameCenti);
            // the resolution as one left-to-right run (an Arabic chip showed 2160 x 3840 without the isolate)
            String res = "\u2066" + s.width + " \u00D7 " + s.height + "\u2069"
                    + (hz > 0 ? ", " + cx.getString(R.string.lumen_gm_chip_hz, hz) : "");
            l.add(chip(res, 0, false, false));
        }
        String hdr = hdrName(s.hdr);
        if (hdr != null) l.add(chip(hdr, s.hdr == GmModel.HDR_SDR ? 0 : 1, false, false));
        if (Boolean.TRUE.equals(s.allm)) l.add(chip("ALLM", 1, true, false));
        if (GameMenu.CAPS.visible(GmModel.VRR) && ctl.state.get(GmModel.VRR) == 1) l.add(chip("VRR", 2, false, false));
        int lat = ctl.state.get(GmModel.LAT);
        if (lat != GmModel.BAD) {
            l.add(chip(cx.getString(lat == 1 ? R.string.lumen_gm_lat_1 : R.string.lumen_gm_lat_0), lat == 1 ? 2 : 0, false, true));
        }
        return l;
    }

    private static String hdrName(int t) {
        switch (t) {
            case GmModel.HDR_SDR: return "SDR";
            case GmModel.HDR_HDR10: return "HDR10";
            case GmModel.HDR_DOLBY_VISION: return "Dolby Vision";
            case GmModel.HDR_HLG: return "HLG";
            case GmModel.HDR_HDR10_PLUS: return "HDR10+";
            case GmModel.HDR_VIVID: return "HDR Vivid";
            default: return null;
        }
    }

    private void drawChip(Canvas c, Chip ch, float x, float y, long now) {
        int bg = ch.kind == 1 ? 0x383B78E7 : ch.kind == 2 ? 0x29FFC93A : 0x14FFFFFF;
        int fg = ch.kind == 1 ? 0xFFCFE0FF : ch.kind == 2 ? PERF : 0xDBF4F4F6;
        r.set(x, y, x + ch.w, y + 34);
        fill.setColor(bg);
        c.drawRoundRect(r, 17, 17, fill);
        float tx = x + 13;
        if (ch.live) {
            float ph = (now % 1600) / 1600f;
            float op = 0.25f + 0.75f * (float) (0.5 + 0.5 * Math.cos(ph * 2 * Math.PI));
            fill.setColor(fg);
            fill.setAlpha(Math.round(255 * op));
            c.drawCircle(tx + 4, y + 17, 4, fill);
            fill.setAlpha(255);
            tx += 15;
        }
        if (ch.zap) {
            QpdStyle.Icon i = icon("chip:zap");
            Drawable d = i == null ? null : i.drawable();
            if (d != null) {
                d.setBounds(Math.round(tx - 2), Math.round(y + 8), Math.round(tx + 16), Math.round(y + 26));
                d.setTint(fg);
                d.draw(c);
            }
            tx += 25;
        }
        tChip.setColor(fg);
        c.drawText(ch.text, tx, y + 23.5f, tChip);
    }

    // ---- presets
    private void drawPresets(Canvas c, long now) {
        Context cx = getContext();
        String label = cx.getString(R.string.lumen_gm_presets);
        float x0 = 43, bottom = 780, h = 76;
        float w = 10 + 14 + tPresetLabel.measureText(label) + 10;
        float[] pw = new float[GmModel.PRESETS];
        for (int p = 0; p < GmModel.PRESETS; p++) {
            pw[p] = 16 + 26 + 10 + tPreset.measureText(ctl.presetName(p)) + 22;
            w += pw[p] + (p > 0 ? 10 : 0);
        }
        w += 10;
        r.set(x0, bottom - h, x0 + w, bottom);
        card(c, r, 32);
        c.drawText(label, x0 + 10 + 14, bottom - h / 2 + 6, tPresetLabel);
        float x = x0 + 10 + 14 + tPresetLabel.measureText(label) + 10;
        int match = GmModel.presetMatch(ctl.state, GameMenu.CAPS);
        for (int p = 0; p < GmModel.PRESETS; p++) {
            String id = "p:" + p;
            float f = focus(id, now);
            RectF pr = new RectF(x, bottom - h + 10, x + pw[p], bottom - 10);
            float sc = 1 + 0.08f * f;
            c.save();
            c.scale(sc, sc, pr.centerX(), pr.centerY());
            fill.setColor(QpdStyle.lerp(TILE, ACCENT, f));
            if (f > 0.01f) fill.setShadowLayer(15 * f, 0, 12 * f, (Math.round(0x73 * f) << 24) | 0x1E50BE);
            c.drawRoundRect(pr, 22, 22, fill);
            fill.clearShadowLayer();
            if (match == p && f < 0.5f) ring(c, pr, 22, 2, 0x80FFFFFF);
            boolean perf = p == GmModel.P_COMPETE;
            drawIcon(c, id, pr.left + 16, pr.centerY() - 13, 26, perf ? QpdStyle.lerp(PERF, 0xFFFFE08A, f) : INK);
            tPreset.setColor(INK);
            c.drawText(ctl.presetName(p), pr.left + 16 + 26 + 10, pr.centerY() + 7, tPreset);
            c.restore();
            x += pw[p] + 10;
        }
    }

    private void ring(Canvas c, RectF rr, float radius, float width, int colour) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(colour);
        RectF i = new RectF(rr.left + width / 2, rr.top + width / 2, rr.right - width / 2, rr.bottom - width / 2);
        c.drawRoundRect(i, radius - width / 2, radius - width / 2, p);
    }

    private void drawIcon(Canvas c, String id, float x, float y, float size, int colour) {
        QpdStyle.Icon i = icon(id);
        Drawable d = i == null ? null : i.drawable();
        if (d == null) return;
        d.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size));
        d.setTint(colour);
        d.draw(c);
    }

    // ---- dock
    private void drawDock(Canvas c, long now) {
        card(c, dock, 40);
        Context cx = getContext();
        // the group of the focused tile has a white label, also while its popup is open (prototype .gl.cur)
        String focused = ctl.zone == GmModel.Z_DOCK ? ctl.focusedTile() : null;
        int[] groupNames = {R.string.lumen_gm_group_speed, R.string.lumen_gm_group_picture, R.string.lumen_gm_group_sound, R.string.lumen_gm_group_overlay};
        for (String[] row : GmModel.visibleGroups(GameMenu.CAPS)) {
            RectF first = tileRects.get(row[1]);
            boolean cur = false;
            for (int i = 1; i < row.length; i++) if (row[i].equals(focused)) cur = true;
            tGroup.setColor(cur ? 0xFFFFFFFF : MUTE);
            c.drawText(cx.getString(groupNames[Integer.parseInt(row[0])]), first.left + 4, first.top - 12 - 4, tGroup);
        }
        // unfocused tiles first, the focused one last (it grows over its neighbours)
        String top = null;
        for (String k : GmModel.visibleOrder(GameMenu.CAPS)) {
            if (focusAt.containsKey("t:" + k) && ctl.zone == GmModel.Z_DOCK) { top = k; continue; }
            drawTile(c, k, now);
        }
        if (top != null) drawTile(c, top, now);
    }

    private void drawTile(Canvas c, String k, long now) {
        RectF tr = tileRects.get(k);
        if (tr == null) return;
        String id = "t:" + k;
        float f = focus(id, now);
        // the tile of the open popup keeps its focus scale and glow on the lighter tile colour (prototype .t.f.open)
        boolean open = ctl.popup != null && k.equals(ctl.popup);
        int v = ctl.state.get(k);
        boolean perf = GmModel.isPerf(k, v);
        float sc = 1 + 0.08f * f;
        c.save();
        c.scale(sc, sc, tr.centerX(), tr.centerY());
        fill.setColor(open ? QpdStyle.lerp(TILE, TILE_HI, f) : QpdStyle.lerp(TILE, ACCENT, f));
        if (f > 0.01f) fill.setShadowLayer(18 * f, 0, 16 * f, (Math.round(0x73 * f) << 24) | 0x1E50BE);
        c.drawRoundRect(tr, 26, 26, fill);
        fill.clearShadowLayer();
        int iconCol = perf ? QpdStyle.lerp(PERF, 0xFFFFFFFF, f) : INK;
        drawIcon(c, id, tr.left + 15, tr.top + 18, 40, iconCol);
        // title: up to two lines, smaller type if a language needs it (never cut)
        String title = getContext().getString(titleRes(k));
        float boxW = tr.width() - 30;
        StaticLayout sl = fitTitle(title, boxW);
        String val = valueText(k, v);
        float valY = tr.bottom - 16;
        float titleBottom = valY - 16 - 7;
        c.save();
        c.translate(tr.left + 15, titleBottom - sl.getHeight());
        sl.draw(c);
        c.restore();
        if (perf && f > 0.5f) {
            float w = tValue.measureText(val) + 18;
            r.set(tr.left + 15, valY - 17, tr.left + 15 + w, valY + 6);
            fill.setColor(PERF);
            c.drawRoundRect(r, 9, 9, fill);
            tValue.setColor(PERF_DARK);
            tValue.setFakeBoldText(true);
            c.drawText(val, tr.left + 15 + 9, valY, tValue);
            tValue.setFakeBoldText(false);
        } else {
            tValue.setColor(perf ? PERF : QpdStyle.lerp(MUTE, 0xD9FFFFFF, f));
            c.drawText(ctl.pending.contains(k) ? val + " \u2026" : val, tr.left + 15, valY, tValue);
        }
        c.restore();
    }

    private final Map<String, StaticLayout> titleCache = new HashMap<>();

    private StaticLayout fitTitle(String t, float w) {
        String key = t + "|" + Math.round(w);
        StaticLayout l = titleCache.get(key);
        if (l != null) return l;
        for (float size = 17; size >= 13; size -= 1) {
            TextPaint p = new TextPaint(tTitle);
            p.setTextSize(size);
            l = StaticLayout.Builder.obtain(t, 0, t.length(), p, Math.max(1, Math.round(w)))
                    .setLineSpacing(0, 1.12f).setIncludePad(false)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build();
            boolean fits = l.getLineCount() <= 2;
            for (int i = 0; i < l.getLineCount() && fits; i++) {
                if (l.getLineWidth(i) > w + 0.5f) fits = false;
                // a line may only end at a space, a hyphen or a soft hyphen: never a word cut in the middle
                int end = l.getLineEnd(i);
                if (i < l.getLineCount() - 1 && end > 0 && end < t.length()) {
                    char ch = t.charAt(end - 1);
                    if (!Character.isWhitespace(ch) && ch != '-' && ch != '\u00AD' && !isCjk(t.charAt(end))) fits = false;
                }
            }
            if (fits) break;
        }
        titleCache.put(key, l);
        return l;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript sc = Character.UnicodeScript.of(c);
        return sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA
                || sc == Character.UnicodeScript.KATAKANA || sc == Character.UnicodeScript.HANGUL;
    }

    static int titleRes(String k) {
        switch (k) {
            case GmModel.LAT: return R.string.lumen_gm_lat;
            case GmModel.VRR: return R.string.lumen_gm_vrr;
            case GmModel.HFR: return R.string.lumen_gm_hfr;
            case GmModel.PIC: return R.string.lumen_gm_pic;
            case GmModel.CS: return R.string.lumen_gm_cs;
            case GmModel.HDR: return R.string.lumen_gm_hdr;
            case GmModel.DARK: return R.string.lumen_gm_dark;
            case GmModel.SR: return R.string.lumen_gm_sr;
            case GmModel.AR: return R.string.lumen_gm_ar;
            case GmModel.SND: return R.string.lumen_gm_snd;
            case GmModel.AIM: return R.string.lumen_gm_aim;
            default: return R.string.lumen_gm_fps;
        }
    }

    /** Label of value {@code v} of item {@code k}. */
    String optionText(String k, int v) {
        Context c = getContext();
        switch (k) {
            case GmModel.LAT: return c.getString(v == 1 ? R.string.lumen_gm_lat_1 : R.string.lumen_gm_lat_0);
            case GmModel.HFR: return c.getString(v == 1 ? R.string.lumen_gm_auto : R.string.lumen_gm_off);
            case GmModel.VRR: case GmModel.DARK: case GmModel.SR: case GmModel.AIM:
                return c.getString(v == 1 ? R.string.lumen_gm_on : R.string.lumen_gm_off);
            case GmModel.PIC: {
                int[] p = {R.string.lumen_gm_pic_0, R.string.lumen_gm_pic_1, R.string.lumen_gm_pic_2, R.string.lumen_gm_pic_3, R.string.lumen_gm_pic_4};
                return v >= 0 && v < p.length ? c.getString(p[v]) : "";
            }
            case GmModel.AR: {
                if (v == 0) return c.getString(R.string.lumen_gm_auto);
                if (v == 1) return "16:9";
                if (v == 2) return "21:9";
                if (v == 3) return "32:9";
                return "";
            }
            case GmModel.CS: {
                int[] p = {R.string.lumen_gm_cs_0, R.string.lumen_gm_cs_1, R.string.lumen_gm_cs_2, R.string.lumen_gm_cs_3, R.string.lumen_gm_cs_4};
                return v >= 0 && v < p.length ? c.getString(p[v]) : "";
            }
            case GmModel.HDR: {
                int[] p = {R.string.lumen_gm_off, R.string.lumen_gm_hdr_1, R.string.lumen_gm_hdr_2};
                return v >= 0 && v < p.length ? c.getString(p[v]) : "";
            }
            case GmModel.SND: {
                int[] p = {R.string.lumen_gm_off, R.string.lumen_gm_snd_1, R.string.lumen_gm_snd_2, R.string.lumen_gm_snd_3,
                        R.string.lumen_gm_snd_4};
                return v >= 0 && v < p.length && v < GmModel.count(GmModel.SND) ? c.getString(p[v]) : "";
            }
            case GmModel.FPS: {
                int[] p = {R.string.lumen_gm_off, R.string.lumen_gm_fps_1, R.string.lumen_gm_fps_2, R.string.lumen_gm_fps_3};
                return v >= 0 && v < p.length ? c.getString(p[v]) : "";
            }
            default: return "";
        }
    }

    private String valueText(String k, int v) {
        if (GmModel.AR.equals(k) && v < 0) {
            int raw = ctl.arRaw;
            if (raw == GmModel.AR_4_3) return "4:3";
            if (raw == GmModel.AR_ORIGINAL) return getContext().getString(R.string.lumen_m_panel_aspect_original);
        }
        if (v == GmModel.BAD) return ctl.loaded ? "" : "\u2026";
        return optionText(k, v);
    }

    private String description(String k, int v) {
        Context c = getContext();
        switch (k) {
            case GmModel.LAT: return c.getString(v == 1 ? R.string.lumen_gm_lat_d1 : R.string.lumen_gm_lat_d0);
            case GmModel.VRR: return c.getString(v == 1 ? R.string.lumen_gm_vrr_d1 : R.string.lumen_gm_vrr_d0);
            case GmModel.HFR: return c.getString(v == 1 ? R.string.lumen_gm_hfr_d1 : R.string.lumen_gm_hfr_d0);
            case GmModel.PIC: {
                int[] d = {R.string.lumen_gm_pic_d0, R.string.lumen_gm_pic_d1, R.string.lumen_gm_pic_d2, R.string.lumen_gm_pic_d3, R.string.lumen_gm_pic_d4};
                return c.getString(d[Math.max(0, Math.min(4, v))]);
            }
            case GmModel.DARK: return c.getString(v == 1 ? R.string.lumen_gm_dark_d1 : R.string.lumen_gm_dark_d0);
            case GmModel.SR: return c.getString(v == 1 ? R.string.lumen_gm_sr_d1 : R.string.lumen_gm_sr_d0);
            case GmModel.AR: return c.getString(v == 1 ? R.string.lumen_gm_ar_d1 : R.string.lumen_gm_ar_d0);
            case GmModel.SND: return c.getString(v == 0 ? R.string.lumen_gm_snd_d0 : R.string.lumen_gm_snd_d1);
            case GmModel.FPS: return c.getString(R.string.lumen_gm_fps_d);
            case GmModel.CS: case GmModel.HDR: return "";      // the stock bar has no description for these two
            default: return c.getString(R.string.lumen_gm_aim_d);
        }
    }

    // ---- popup
    private StaticLayout text(String t, TextPaint p, float w, float spacing) {
        return StaticLayout.Builder.obtain(t, 0, t.length(), p, Math.max(1, Math.round(w)))
                .setLineSpacing(0, spacing).setIncludePad(false).build();
    }

    private void drawPopup(Canvas c, long now) {
        String k = ctl.popup;
        RectF tr = tileRects.get(k);
        if (tr == null) return;
        boolean aim = GmModel.AIM.equals(k);
        float w = aim ? 520 : 420, pad = 16, inner = w - 2 * pad;
        Context cx = getContext();
        String head = cx.getString(titleRes(k));
        List<Integer> opts = GmModel.options(k);
        int shown = aim ? 0 : (ctl.prow >= 0 && ctl.prow < opts.size() ? opts.get(ctl.prow) : Math.max(0, ctl.state.get(k)));
        StaticLayout desc = text(description(k, shown), tPopDesc, inner - 36, 1.3f);
        float h = pad + 8 + 22 + 4 + 2 + Math.max(56, desc.getHeight() + 12);
        if (aim) h += 68 + 30 + 2 * 72 + 10 + 16 + 30 + 44 + 14 + 17 + 68;
        else h += opts.size() * 68;
        h += pad;
        float bottom = dock.top - 16;
        float x = Math.max(40, Math.min(tr.left - 8, 1880 - w));
        float t = ease((now - popupAt) / 260f);
        c.save();
        c.translate(0, 14 * (1 - t));
        RectF pr = new RectF(x, bottom - h, x + w, bottom);
        card(c, pr, 34);
        float y = pr.top + pad + 8 + 18;
        c.drawText(head, x + pad + 18, y, tPopHead);
        y += 4 + 2 + 8;
        c.save();
        c.translate(x + pad + 18, y);
        desc.draw(c);
        c.restore();
        y += Math.max(56, desc.getHeight() + 12);
        if (aim) drawAimRows(c, x + pad, y, inner);
        else {
            for (int i = 0; i < opts.size(); i++) {
                int ov = opts.get(i);
                boolean sel = ov == ctl.state.get(k), perf = GmModel.isPerf(k, ov);
                RectF row = new RectF(x + pad, y, x + pad + inner, y + 68);
                drawOptionRow(c, row, optionText(k, ov), rowFocus(i, now), sel, perf);
                y += 68;
            }
        }
        c.restore();
    }

    /**
     * One value row of a popup: {@code f} = accent fill 0..1 (the focused row, prototype .po.f), {@code sel} = the
     * value in use (the thick dot, prototype .po.sel: white on the accent fill, accent or yellow elsewhere).
     */
    private void drawOptionRow(Canvas c, RectF row, String label, float fo, boolean sel, boolean perf) {
        if (fo > 0.01f) {
            fill.setColor(ACCENT);
            fill.setAlpha(Math.round(255 * fo));
            c.drawRoundRect(row, 22, 22, fill);
            fill.setAlpha(255);
        }
        boolean f = fo >= 0.5f;
        float dx = row.left + 18, cy = row.centerY();
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.STROKE);
        if (sel) {
            dot.setStrokeWidth(7);
            dot.setColor(QpdStyle.lerp(perf ? PERF : ACCENT, 0xFFFFFFFF, fo));
            c.drawCircle(dx + 11, cy, 11 - 3.5f, dot);
        } else {
            dot.setStrokeWidth(2);
            dot.setColor(0x73FFFFFF);
            c.drawCircle(dx + 11, cy, 10, dot);
        }
        tRow.setColor(perf ? QpdStyle.lerp(PERF, INK, fo) : INK);
        float tx = dx + 22 + 16;
        c.drawText(label, tx, cy + 8, tRow);
        if (perf) {
            String b = getContext().getString(R.string.lumen_gm_faster);
            float bx = tx + tRow.measureText(label) + 12, bw = tBadge.measureText(b) + 18;
            r.set(bx, cy - 12, bx + bw, cy + 12);
            fill.setColor(f ? PERF : 0x29FFC93A);
            c.drawRoundRect(r, 9, 9, fill);
            tBadge.setColor(f ? PERF_DARK : PERF);
            c.drawText(b, bx + 9, cy + 5, tBadge);
        }
    }

    private void drawAimRows(Canvas c, float x, float y, float w) {
        GmOverlays ov = ctl.overlays;
        boolean on = ov != null && ov.aimOn();
        int style = ov == null ? 1 : ov.aimStyle(), col = ov == null ? 0 : ov.aimColour();
        int colour = GmModel.AIM_COLOURS[col];
        Context cx = getContext();
        // switch row
        RectF row = new RectF(x, y, x + w, y + 68);
        boolean f0 = ctl.aimRow == GmModel.AIM_ROW_TOGGLE;
        if (f0) { fill.setColor(ACCENT); c.drawRoundRect(row, 22, 22, fill); }
        tRow.setColor(INK);
        c.drawText(cx.getString(R.string.lumen_gm_aim), x + 18, row.centerY() + 8, tRow);
        RectF sw = new RectF(row.right - 18 - 56, row.centerY() - 16, row.right - 18, row.centerY() + 16);
        fill.setColor(on ? (f0 ? 0xF2FFFFFF : ACCENT) : 0x33FFFFFF);
        c.drawRoundRect(sw, 16, 16, fill);
        fill.setColor(on && f0 ? ACCENT : 0xFFFFFFFF);
        c.drawCircle(on ? sw.right - 16 : sw.left + 16, sw.centerY(), 13, fill);
        y += 68;
        // styles
        boolean f1 = ctl.aimRow == GmModel.AIM_ROW_STYLE;
        tSmallLabel.setColor(f1 ? 0xFFFFFFFF : MUTE);
        c.drawText(cx.getString(R.string.lumen_gm_aim_style), x + 18, y + 22, tSmallLabel);
        y += 30;
        float gap = 10, cw = (w - 16 - 4 * gap) / 5;
        for (int i = 0; i < GmModel.AIM_STYLES; i++) {
            float gx = x + 8 + (i % 5) * (cw + gap), gy = y + (i / 5) * (72 + gap);
            RectF cell = new RectF(gx, gy, gx + cw, gy + 72);
            boolean fc = f1 && i == style;
            if (fc) {
                c.save();
                c.scale(1.08f, 1.08f, cell.centerX(), cell.centerY());
            }
            fill.setColor(fc ? ACCENT : 0x0FFFFFFF);
            c.drawRoundRect(cell, 20, 20, fill);
            if (i == style && !fc) ring(c, cell, 20, 2.5f, 0x8CFFFFFF);
            // the blue colour vanishes on the accent fill of the focused cell: draw that one white
            int glyph = fc && col == 2 ? 0xFFFFFFFF : colour;
            GmOverlays.drawAim(c, i, glyph, cell.centerX(), cell.centerY(), 46, aimPaint, false);
            if (fc) c.restore();
        }
        y += 2 * 72 + gap + 16;
        // colours
        boolean f2 = ctl.aimRow == GmModel.AIM_ROW_COLOUR;
        tSmallLabel.setColor(f2 ? 0xFFFFFFFF : MUTE);
        c.drawText(cx.getString(R.string.lumen_gm_aim_colour), x + 18, y + 22, tSmallLabel);
        y += 30;
        for (int i = 0; i < GmModel.AIM_COLOURS.length; i++) {
            float sx = x + 18 + 22 + i * (44 + 14), sy = y + 22;
            boolean fc = f2 && i == col;
            float rad = fc ? 22 * 1.18f : 22;
            if (i == col || fc) {
                fill.setColor(fc ? ACCENT : 0xBFFFFFFF);
                c.drawCircle(sx, sy, rad + 5.5f, fill);
                fill.setColor(0xFF16171A);
                c.drawCircle(sx, sy, rad + 3, fill);
            }
            fill.setColor(GmModel.AIM_COLOURS[i]);
            c.drawCircle(sx, sy, rad, fill);
        }
        y += 44 + 14;
        fill.setColor(0x14FFFFFF);
        c.drawRect(x + 18, y + 8, x + w - 18, y + 9, fill);
        y += 17;
        // move row
        boolean f3 = ctl.aimRow == GmModel.AIM_ROW_MOVE;
        RectF mr = new RectF(x, y, x + w, y + 68);
        if (f3) { fill.setColor(ACCENT); c.drawRoundRect(mr, 22, 22, fill); }
        tRow.setColor(INK);
        c.drawText(cx.getString(R.string.lumen_gm_aim_move), x + 18, mr.centerY() + 8, tRow);
        if (ctl.moving) {
            String hint = cx.getString(R.string.lumen_gm_aim_move_hint);
            tValue.setColor(f3 ? 0xD9FFFFFF : MUTE);
            c.drawText(hint, mr.right - 18 - tValue.measureText(hint), mr.centerY() + 6, tValue);
        } else {
            drawIcon(c, "move", mr.right - 18 - 22, mr.centerY() - 11, 22, f3 ? 0xD9FFFFFF : MUTE);
        }
    }

    // ---- question, note, hint
    private void drawConfirm(Canvas c) {
        Context cx = getContext();
        fill.setColor(0x66000000);
        c.drawRect(0, 0, 1920, 1080, fill);
        float w = 640, pad = 30;
        StaticLayout msg = text(cx.getString(R.string.lumen_gm_turbo_msg), tConfirmText, w - 2 * pad - 16, 1.35f);
        float h = pad + 40 + 10 + msg.getHeight() + 22 + 68 + pad;
        RectF cr = new RectF(960 - w / 2, 540 - h / 2, 960 + w / 2, 540 + h / 2);
        card(c, cr, 40);
        c.drawText(cx.getString(R.string.lumen_gm_turbo_title), cr.left + pad + 8, cr.top + pad + 30, tConfirmTitle);
        c.save();
        c.translate(cr.left + pad + 8, cr.top + pad + 50);
        msg.draw(c);
        c.restore();
        float by = cr.bottom - pad - 68, bw = (w - 2 * pad - 14) / 2;
        String[] labels = {cx.getString(R.string.lumen_gm_turbo_ok), cx.getString(R.string.lumen_m_cancel)};
        for (int i = 0; i < 2; i++) {
            RectF b = new RectF(cr.left + pad + i * (bw + 14), by, cr.left + pad + i * (bw + 14) + bw, by + 68);
            boolean f = i == ctl.confirmIdx;
            c.save();
            if (f) c.scale(1.04f, 1.04f, b.centerX(), b.centerY());
            fill.setColor(f ? ACCENT : 0x14FFFFFF);
            c.drawRoundRect(b, 22, 22, fill);
            tButton.setColor(INK);
            c.drawText(labels[i], b.centerX() - tButton.measureText(labels[i]) / 2, b.centerY() + 8, tButton);
            c.restore();
        }
    }

    private void drawToast(Canvas c) {
        String t = ctl.toast;
        float tw = Math.min(1200, tToast.measureText(t));
        StaticLayout l = text(t, tToast, tw + 1, 1.25f);
        float w = 26 + 28 + 12 + tw + 26, h = Math.max(60, l.getHeight() + 32);
        RectF tr = new RectF(960 - w / 2, 250, 960 + w / 2, 250 + h);
        card(c, tr, 24);
        drawIcon(c, "info", tr.left + 26, tr.centerY() - 14, 28, INK);
        c.save();
        c.translate(tr.left + 26 + 28 + 12, tr.centerY() - l.getHeight() / 2f);
        l.draw(c);
        c.restore();
    }

    private void drawHint(Canvas c) {
        String t = getContext().getString(R.string.lumen_gm_hint_first);
        float w = 22 + 26 + 10 + tHint.measureText(t) + 22;
        RectF hr = new RectF(1880 - w, 40, 1880, 40 + 56);
        card(c, hr, 22);
        drawIcon(c, "gear", hr.left + 22, hr.centerY() - 13, 26, INK);
        c.drawText(t, hr.left + 22 + 26 + 10, hr.centerY() + 7, tHint);
    }

    // ================================================================== controller frames
    /**
     * The 30 turn + 24 sway frames of the console (assets/gamemenu/<theme>/, 600 x 360 WebP with alpha, decoded at half
     * size: 300 x 180 fills the 250 x 150 slot) loaded on z9x-gm-frames, hero frame first. Released when the menu closes.
     */
    private static SafeHandler sLoader;

    /**
     * One controller frame as the menu draws it (static WebP with alpha, half size, ARGB_8888), null when the decoder
     * cannot read it. Any thread. The render harness decodes every frame of every theme through this on Android.
     */
    static Bitmap decodeFrame(android.content.res.AssetManager am, String theme, int f) throws java.io.IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = am.open(GmModel.framePath(theme, f))) {
            return BitmapFactory.decodeStream(in, null, o);
        }
    }

    /** One loader thread for every menu shown (a menu is a new view each time). */
    private static synchronized SafeHandler loader() {
        if (sLoader == null) sLoader = SafeHandler.newThread("z9x-gm-frames");
        return sLoader;
    }

    private final class Frames {
        final Bitmap[] turn = new Bitmap[GmModel.TURN_FRAMES], idle = new Bitmap[GmModel.IDLE_FRAMES];
        long t0;
        private int gen;

        void start(String theme) {
            release();
            t0 = SystemClock.uptimeMillis();
            if (!GmModel.isConsole(theme)) return;
            final int g = ++gen;
            final android.content.res.AssetManager am = getContext().getAssets();
            loader().post(() -> {
                int[] order = new int[GmModel.TURN_FRAMES + GmModel.IDLE_FRAMES];
                for (int i = 0; i < GmModel.TURN_FRAMES; i++) order[i] = i;
                for (int i = 0; i < GmModel.IDLE_FRAMES; i++) order[GmModel.TURN_FRAMES + i] = GmModel.IDLE_BASE + i;
                long t = SystemClock.uptimeMillis();
                int good = 0;
                for (int f : order) {
                    if (g != gen) return;
                    try {
                        Bitmap b = decodeFrame(am, theme, f);
                        if (b == null) { Log.w(TAG, "frame " + theme + " " + f + " not decoded"); continue; }
                        good++;
                        final int ff = f;
                        post(() -> {
                            if (g != gen) { if (b != null) b.recycle(); return; }
                            if (ff >= GmModel.IDLE_BASE) idle[ff - GmModel.IDLE_BASE] = b; else turn[ff] = b;
                        });
                    } catch (Throwable e) {
                        Log.w(TAG, "frame " + theme + " " + f + ": " + e);
                    }
                }
                Log.i(TAG, "frames " + theme + ": " + good + "/" + order.length + " in "
                        + (SystemClock.uptimeMillis() - t) + " ms");
            });
        }

        Bitmap frame(long now) {
            int f = GmModel.frameAt(now - t0);
            Bitmap b = f >= GmModel.IDLE_BASE ? idle[f - GmModel.IDLE_BASE] : turn[f];
            return b != null ? b : turn[0];
        }

        /** Only the hero frame (turn_00): the card's picture while the real-time model loads. */
        void startHero(String theme) {
            release();
            t0 = SystemClock.uptimeMillis();
            final int g = gen;
            final android.content.res.AssetManager am = getContext().getAssets();
            loader().post(() -> {
                try {
                    Bitmap b = decodeFrame(am, theme, 0);
                    if (b != null) post(() -> {
                        if (g != gen) { b.recycle(); return; }
                        turn[0] = b;
                        invalidate();
                    });
                } catch (Throwable e) {
                    Log.w(TAG, "hero frame " + theme + ": " + e);
                }
            });
        }

        void release() {
            gen++;
            for (int i = 0; i < turn.length; i++) { if (turn[i] != null) turn[i].recycle(); turn[i] = null; }
            for (int i = 0; i < idle.length; i++) { if (idle[i] != null) idle[i].recycle(); idle[i] = null; }
        }
    }
}
