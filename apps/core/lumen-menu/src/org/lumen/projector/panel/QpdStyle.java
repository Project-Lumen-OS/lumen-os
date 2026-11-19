package org.lumen.projector.panel;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.Log;
import android.util.SparseArray;
import android.view.animation.PathInterpolator;

import org.lumen.projector.ui.Theme;
import org.lumen.ui.Palette;

/**
 * Look of the quick panel layout D (prototype qp_design/proto_d3.py): colours, the Inter font (assets/fonts/
 * InterVariable.ttf, SIL OFL, licence in assets/fonts/OFL-Inter.txt) and the two easing curves.
 */
final class QpdStyle {
    private static final String TAG = "Z9xPanel";

    private QpdStyle() {}

    static final int SHADOW_ACCENT = 0x731E50BE;  // rgba(30,80,190,.45)
    static final int BOOST_A = 0xFFFFE07A, BOOST_B = 0xFFFFB300, BOOST_KNOB = 0xFFFFD24A, BOOST_TEXT = 0xFFFFC93A,
            BOOST_RING = 0xFFFFC21A;
    static final int SWITCH_ON_FOCUSED = 0xF2FFFFFF;  // rgba(255,255,255,.95)
    static final int KNOB_SHADOW = 0x4D000000;        // rgba(0,0,0,.3)
    static final int ROW_TRACK_FOCUSED = 0x4DFFFFFF, RAIL_CIRCLE_FOCUSED = 0x33FFFFFF;
    /**
     * The colours of the light and the dark menu (2026-11-18: the menu follows the theme button, the shared
     * lumen-ui theme; the dark one is the approved layout D look, the light one is made from the lumen-ui light
     * palette, see {@link #light}): {PANEL, TILE, TILE_HI, INK, MUTE, TRACK, RING, DOT_OFF, SWITCH_OFF, DIVIDER,
     * ROW_TRACK, RAIL_CIRCLE, RAIL_CURRENT, ICON, FILL, SELECTED, FAINT, ACCENT, VALUE_FOCUSED, ROW_VALUE_FOCUSED}.
     * ACCENT is the filled focus under white text (lumen-ui Palette.focusFill: #3B78E7 dark, #2F62D0 light).
     */
    private static final int[] DARK = {0xF016171A, 0xFF34353A, 0xFF3E3F45, 0xFFF4F4F6, 0x8FF4F4F6, 0x24FFFFFF, 0xD9FFFFFF,
            0x73FFFFFF, 0x33FFFFFF, 0x14FFFFFF, 0x33FFFFFF, 0x14FFFFFF, 0x14FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF8FB4FF,
            0x57F4F4F6, Palette.of(true).focusFill, 0xD1FFFFFF, 0xD9FFFFFF};   // values .82 / .85 white
    private static final int[] LIGHT = light(Palette.of(false));

    /**
     * The light menu from the shared palette (the approved Sand mock, light-palette/gen.py menu()): the card is the
     * panel at .97 over video, tiles the surface, a lit slider row the raised surface, ink the panel text, quiet texts
     * and lines are that ink at fixed strengths (MUTE .68 keeps 4.5:1 on the card), ring and selection the light
     * focus ring blue, the filled focus the deeper Lumen blue (white on it 5.6:1) with its value lines at .88 white
     * (4.7:1, the dark menu's .82 / .85 would give 4.3 / 4.5).
     */
    static int[] light(Palette p) {
        int ink = p.ptext1;
        return new int[] {Palette.alpha(p.panel, 0.97f), p.surface, p.surface2, ink, Palette.alpha(ink, 0.68f),
                Palette.alpha(ink, 0.14f), p.focusRing, Palette.alpha(ink, 0.45f), Palette.alpha(ink, 0.2f),
                Palette.alpha(ink, 0.08f), Palette.alpha(ink, 0.2f), Palette.alpha(ink, 0.08f), Palette.alpha(ink, 0.06f),
                ink, ink, p.focusRing, Palette.alpha(ink, 0.4f), p.focusFill, LIGHT_VALUE_FOCUSED, LIGHT_VALUE_FOCUSED};
    }

    /** White at .88: the value lines on the light menu's filled focus. */
    static final int LIGHT_VALUE_FOCUSED = 0xE0FFFFFF;
    // the menu's colours now (theme()): popup and list rows (prototype .po / .r / .dot / .tg / .ln), All settings rail (.it)
    static int PANEL, TILE, TILE_HI, INK, MUTE, TRACK, RING, DOT_OFF, SWITCH_OFF, DIVIDER, ROW_TRACK, RAIL_CIRCLE,
            RAIL_CURRENT, ICON, FILL, SELECTED, FAINT, ACCENT, VALUE_FOCUSED, ROW_VALUE_FOCUSED;
    private static boolean light;

    static {
        theme(false);
    }

    /** Takes the light or the dark colours; true when they changed (the views redraw). */
    static boolean theme(boolean lightNow) {
        int[] p = lightNow ? LIGHT : DARK;
        boolean changed = lightNow != light || PANEL != p[0];
        light = lightNow;
        PANEL = p[0]; TILE = p[1]; TILE_HI = p[2]; INK = p[3]; MUTE = p[4]; TRACK = p[5]; RING = p[6]; DOT_OFF = p[7];
        SWITCH_OFF = p[8]; DIVIDER = p[9]; ROW_TRACK = p[10]; RAIL_CIRCLE = p[11]; RAIL_CURRENT = p[12]; ICON = p[13];
        FILL = p[14]; SELECTED = p[15]; FAINT = p[16]; ACCENT = p[17]; VALUE_FOCUSED = p[18]; ROW_VALUE_FOCUSED = p[19];
        return changed;
    }

    static boolean light() { return light; }

    /** Stock focus curve, cubic-bezier(0,0,.58,1). */
    static TimeInterpolator ease() { return new PathInterpolator(0f, 0f, 0.58f, 1f); }

    /** Icon spring, cubic-bezier(.3,1.45,.55,1). */
    static TimeInterpolator spring() { return new PathInterpolator(0.3f, 1.45f, 0.55f, 1f); }

    private static final String FONT = "fonts/InterVariable.ttf";
    private static final SparseArray<Typeface> FACES = new SparseArray<>();

    /** Inter at a weight (100..900), optical size 14 (body text); the system font when the asset cannot be read. */
    static Typeface inter(Context c, int weight) {
        return inter(c, weight, weight <= 300 ? 32 : 14);
    }

    /**
     * Inter at a weight and an optical size (the browser's font-optical-sizing: auto gives the text size in px,
     * 14..32): the prototype's glyph widths.
     */
    static Typeface inter(Context c, int weight, int opsz) {
        int o = Math.max(14, Math.min(32, opsz));
        int key = weight * 100 + o;
        synchronized (FACES) {
            Typeface t = FACES.get(key);
            if (t != null) return t;
            try {
                t = new Typeface.Builder(c.getAssets(), FONT)
                        .setFontVariationSettings("'wght' " + weight + ", 'opsz' " + o)
                        .setWeight(Math.max(1, Math.min(1000, weight))).build();
            } catch (Throwable e) {
                Log.w(TAG, "Inter: " + e);
            }
            if (t == null) t = Typeface.create(Typeface.DEFAULT, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            FACES.put(key, t);
            return t;
        }
    }

    static TextPaint text(Context c, float designSize, int weight, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(inter(c, weight, weight <= 300 ? 32 : Math.round(designSize)));
        p.setTextSize(Theme.pxf(c, designSize));
        p.setColor(color);
        p.setFontFeatureSettings("'cv11', 'ss03'");
        return p;
    }

    /**
     * An animated tile icon (AnimatedVectorDrawable from tools/qpd_icons.py). At rest it draws the vector's own
     * values, which are the prototype's resting icon; focus plays the animation. AnimatedVectorDrawable.reset() would
     * leave it on the animation's FIRST frame (the picture circles collapsed, draw-on waves hidden), so rest() swaps
     * in a fresh, never started copy instead.
     */
    /** False while the system animations are off (developer or accessibility setting "remove animations"). */
    static boolean motion() { return android.animation.ValueAnimator.areAnimatorsEnabled(); }

    static final class Icon {
        private final android.view.View owner;
        private final int res;
        private android.graphics.drawable.Drawable d;
        private boolean started;

        Icon(android.view.View owner, int res) {
            this.owner = owner;
            this.res = res;
            d = load();
        }

        private android.graphics.drawable.Drawable load() {
            android.graphics.drawable.Drawable x = null;
            try {
                x = owner.getContext().getDrawable(res);
                if (x != null) x = x.mutate();
            } catch (Throwable t) {
                Log.w(TAG, "icon " + res + ": " + t);
            }
            if (x != null) x.setCallback(owner);
            return x;
        }

        android.graphics.drawable.Drawable drawable() { return d; }

        boolean is(android.graphics.drawable.Drawable who) { return who != null && who == d; }

        void start() {
            if (!motion()) return;                      // animations off (accessibility): the resting icon stays
            if (d instanceof android.graphics.drawable.Animatable) {
                if (started) rest();
                ((android.graphics.drawable.Animatable) d).start();
                started = true;
            }
        }

        void rest() {
            if (!started) return;
            started = false;
            if (d instanceof android.graphics.drawable.Animatable) ((android.graphics.drawable.Animatable) d).stop();
            if (d != null) d.setCallback(null);
            android.graphics.drawable.Drawable fresh = load();
            d = fresh;
            owner.invalidate();
        }

        void release() {
            if (d != null) d.setCallback(null);
        }
    }

    private static final Paint MASK = new Paint(Paint.ANTI_ALIAS_FLAG);
    static {
        MASK.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
    }

    /**
     * One line of text in the box [x0, x0 + boxW], shifted by {@code dx} (a running title), with soft edges
     * (prototype mask) on each side where the text is cut: the left edge once it has moved, the right edge while more
     * text follows. Text that fits is drawn plainly.
     */
    static void drawFaded(Canvas c, CharSequence t, TextPaint p, float x0, float boxW, float top, float bottom,
                          float base, float dx, float fade) {
        float tw = p.measureText(t, 0, t.length());
        boolean cutL = dx < -0.5f, cutR = dx + tw > boxW + 0.5f;
        if (!cutL && !cutR) {
            c.drawText(t, 0, t.length(), x0 + dx, base, p);
            return;
        }
        int save = c.saveLayer(x0, top, x0 + boxW, bottom, null);
        c.drawText(t, 0, t.length(), x0 + dx, base, p);
        float f = Math.min(fade / Math.max(1f, boxW), 0.45f);
        MASK.setShader(new android.graphics.LinearGradient(x0, 0, x0 + boxW, 0,
                new int[]{cutL ? 0 : 0xFF000000, 0xFF000000, 0xFF000000, cutR ? 0 : 0xFF000000},
                new float[]{0f, f, 1f - f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(x0, top, x0 + boxW, bottom, MASK);
        MASK.setShader(null);
        c.restoreToCount(save);
    }

    /** Inside a saveLayer: fades out the right {@code fade} px of the box [l, r] x [top, bottom] (DST_IN). */
    static void fadeEnd(Canvas c, float l, float top, float r, float bottom, float fade) {
        float from = Math.max(l, r - fade);
        MASK.setShader(new android.graphics.LinearGradient(from, 0, r, 0, 0xFF000000, 0,
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(from, top, r, bottom, MASK);
        MASK.setShader(null);
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int ba = b >>> 24, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
