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

/**
 * Look of the quick panel layout D (prototype qp_design/proto_d3.py): colours, the Inter font (assets/fonts/
 * InterVariable.ttf, SIL OFL, licence in assets/fonts/OFL-Inter.txt) and the two easing curves.
 */
final class QpdStyle {
    private static final String TAG = "Z9xPanel";

    private QpdStyle() {}

    static final int PANEL = 0xF016171A;          // rgba(22,23,26,.94)
    static final int TILE = 0xFF34353A;
    static final int TILE_HI = 0xFF3E3F45;
    static final int ACCENT = Theme.ACCENT;       // #3B78E7
    static final int INK = 0xFFF4F4F6;
    static final int MUTE = 0x8FF4F4F6;           // rgba(244,244,246,.56)
    static final int VALUE_FOCUSED = 0xD1FFFFFF;  // rgba(255,255,255,.82)
    static final int SHADOW_ACCENT = 0x731E50BE;  // rgba(30,80,190,.45)
    static final int TRACK = 0x24FFFFFF;          // rgba(255,255,255,.14)
    static final int RING = 0xD9FFFFFF;           // rgba(255,255,255,.85)
    static final int BOOST_A = 0xFFFFE07A, BOOST_B = 0xFFFFB300, BOOST_KNOB = 0xFFFFD24A, BOOST_TEXT = 0xFFFFC93A,
            BOOST_RING = 0xFFFFC21A;
    // popup and list rows (prototype .po / .r / .dot / .tg / .ln), All settings rail (.it)
    static final int ROW_VALUE_FOCUSED = 0xD9FFFFFF;  // rgba(255,255,255,.85)
    static final int DOT_OFF = 0x73FFFFFF;            // rgba(255,255,255,.45)
    static final int SWITCH_OFF = 0x33FFFFFF;         // rgba(255,255,255,.2)
    static final int SWITCH_ON_FOCUSED = 0xF2FFFFFF;  // rgba(255,255,255,.95)
    static final int KNOB_SHADOW = 0x4D000000;        // rgba(0,0,0,.3)
    static final int DIVIDER = 0x14FFFFFF;            // rgba(255,255,255,.08)
    static final int ROW_TRACK = 0x33FFFFFF, ROW_TRACK_FOCUSED = 0x4DFFFFFF;
    static final int RAIL_CIRCLE = 0x14FFFFFF, RAIL_CIRCLE_FOCUSED = 0x33FFFFFF, RAIL_CURRENT = 0x14FFFFFF;

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
