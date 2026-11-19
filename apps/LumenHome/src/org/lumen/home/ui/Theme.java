package org.lumen.home.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.TextView;

import org.lumen.ui.LumenTheme;
import org.lumen.ui.Motion;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

import java.util.Locale;

/**
 * Lumen Home 2.0 look: the shared palette of org.lumen.ui ({@link LumenTheme}: Светлая by default,
 * Тёмная, Авто) and Inter for every text. Every size is in px of the 1920x1080 UI ("design px"), scaled to the real
 * display with {@link #px} ({@link UiScale}). The colour fields are filled by {@link #apply} from the palette on
 * screen, so Files, the panels and search follow the theme with Home.
 */
public final class Theme {
    private Theme() {}

    // ------------------------------------------------------------------ colours of the palette on screen
    public static Palette P = LumenTheme.LIGHT;
    public static int BG, BG_GLOW, BG_LOW, SURFACE, SURFACE2, SURFACE3, PANEL, PANEL_LINE, TEXT1, TEXT2, TEXT3,
            TEXT_DIM, PTEXT1, PTEXT2, PTEXT3, FOCUS, ON_FOCUS, PFOCUS, ON_PFOCUS, RING, RING_GAP, ACCENT,
            ACCENT_STRONG, ACCENT_ON_BG, PROGRESS, TRACK, LIVE, OK, ERROR, SHADOW, SCRIM, BANNER_LINE, PILL,
            NAV_ACTIVE, NAV_TEXT, NAV_ACTIVE_TEXT, GLOW_A, GLOW_B, FACET_A, FACET_B;

    // ------------------------------------------------------------------ type (Inter, weight and opsz per style)
    public static Typeface REGULAR, MEDIUM, SEMIBOLD, BOLD, LIGHT, DISPLAY;

    // ------------------------------------------------------------------ geometry (design px)
    public static final int MARGIN = Tokens.MARGIN, SAFE = Tokens.SAFE, GAP = 24, RADIUS = Tokens.R_CARD,
            RADIUS_APP = Tokens.R_BANNER;
    public static final float FOCUS_SCALE = Tokens.SCALE_TILE;

    // ------------------------------------------------------------------ motion
    public static final Interpolator EMPHASIZED = Motion.EMPHASIZED;
    public static final Interpolator DECEL = Motion.ENTER;
    public static final long FOCUS_IN_MS = Tokens.MS_FOCUS_IN, FOCUS_OUT_MS = Tokens.MS_FOCUS_OUT,
            ROW_SCROLL_MS = Tokens.MS_ROW_SCROLL, ROW_REPEAT_MS = Tokens.MS_KEY_REPEAT,
            PAGE_SCROLL_MS = Tokens.MS_PAGE_SCROLL, BAR_FADE_MS = Tokens.MS_HEADER, PANEL_MS = Tokens.MS_PANEL,
            BACKDROP_DWELL_MS = Tokens.MS_BACKDROP_DWELL;

    private static final String FONT = "fonts/InterVariable.ttf";
    private static final SparseArray<Typeface> sFonts = new SparseArray<>();
    private static AssetManager sAssets;
    private static boolean sInter;
    private static float sScale = 1f;
    private static float sDensity = 2f;
    private static boolean sReduced;

    public static void init(Context c) {
        Resources r = c.getResources();
        DisplayMetrics dm = r.getDisplayMetrics();
        sScale = UiScale.scale(dm.widthPixels, dm.heightPixels);
        sDensity = dm.density;
        sReduced = Motion.reduced(c);
        if (sAssets == null) {
            sAssets = c.getApplicationContext().getAssets();
            sInter = hasAsset(sAssets, FONT);
            LIGHT = font(300, 24);
            REGULAR = font(400, 24);
            MEDIUM = font(520, 22);
            SEMIBOLD = font(600, 22);
            BOLD = font(700, 28);
            DISPLAY = font(700, 32);
        }
        apply(LumenTheme.palette(c));
    }

    /** Fills the colour fields from {@code p}; views read them on their next bind or draw. */
    public static void apply(Palette p) {
        P = p;
        BG = p.bg; BG_GLOW = p.bgGlow; BG_LOW = p.bgLow; SURFACE = p.surface; SURFACE2 = p.surface2;
        SURFACE3 = p.panelLine; PANEL = p.panel; PANEL_LINE = p.panelLine; TEXT1 = p.text1; TEXT2 = p.text2;
        TEXT3 = p.text3; TEXT_DIM = p.text3; PTEXT1 = p.ptext1; PTEXT2 = p.ptext2; PTEXT3 = p.ptext3;
        FOCUS = p.focus; ON_FOCUS = p.onFocus; PFOCUS = p.pfocus; ON_PFOCUS = p.onPfocus; RING = p.ring;
        RING_GAP = p.ringGap; ACCENT = p.accent; ACCENT_STRONG = p.accent; ACCENT_ON_BG = p.accentOnBg;
        PROGRESS = p.progress; TRACK = p.track; LIVE = p.error; OK = p.ok; ERROR = p.error; SHADOW = p.shadow;
        SCRIM = p.scrim; BANNER_LINE = p.bannerLine; PILL = p.pill; NAV_ACTIVE = p.navActive; NAV_TEXT = p.navText;
        NAV_ACTIVE_TEXT = p.navActiveText; GLOW_A = p.bgGlow; GLOW_B = p.bgLow; FACET_A = p.markA; FACET_B = p.markB;
    }

    public static boolean dark() {
        return P.dark;
    }

    /**
     * Inter at a weight with its optical size axis set to the text size (design px, Inter's opsz runs 14..32): the
     * browser does the same in the approved renders, and a fixed opsz makes text visibly wider (menu D lesson).
     */
    public static Typeface font(int weight, float sizeDesign) {
        int opsz = Math.round(Math.max(14f, Math.min(32f, sizeDesign)));
        int key = weight * 100 + opsz;
        Typeface t = sFonts.get(key);
        if (t != null) return t;
        if (sInter && sAssets != null) {
            try {
                t = new Typeface.Builder(sAssets, FONT)
                        .setFontVariationSettings("'wght' " + weight + ", 'opsz' " + opsz).setWeight(weight).build();
            } catch (Throwable e) {
                Log.w("Z9xHome", "font " + weight + "/" + opsz + ": " + e);
            }
        }
        if (t == null) t = Typeface.create(Typeface.SANS_SERIF, Math.max(100, Math.min(900, weight)), false);
        sFonts.put(key, t);
        return t;
    }

    private static boolean hasAsset(AssetManager am, String path) {
        try {
            am.open(path).close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Design px (1920 wide) to real px. */
    public static int px(float design) {
        return UiScale.px(design, sScale);
    }

    /** Real px per design px: 1 at 1080p, 1.33 at 2K, 2 at 4K. */
    public static float scale() {
        return sScale;
    }

    /** Pixels of a soft bitmap per real pixel it covers ({@link UiScale#SOFT_MAX}). */
    public static float softFactor() {
        return UiScale.soft(sScale) / sScale;
    }

    public static float pxf(float design) {
        return design * sScale;
    }

    /** Focus ring width (design 4 px). */
    public static float ring() {
        return pxf(Tokens.RING_W);
    }

    /** Text in design px, set as COMPLEX_UNIT_PX: a font scale above 1.3 cannot grow layouts. */
    public static void text(TextView t, float designPx, Typeface tf, int color) {
        float fs = fontScale(t.getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(designPx) * fs);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    }

    /** A text style of the 2.0 type scale: size, weight (opsz follows the size), colour, tracking in design px. */
    public static void style(TextView t, float size, int weight, int color, float trackingPx) {
        text(t, size, font(weight, size), color);
        if (trackingPx != 0) t.setLetterSpacing(trackingPx / size);
    }

    public static void heading(TextView t, float designPx, int color) {
        text(t, designPx, SEMIBOLD, color);
    }

    public static String upper(CharSequence s) {
        return s == null ? "" : s.toString().toUpperCase(Locale.getDefault());
    }

    public static float fontScale(Context c) {
        return Math.min(1.3f, Math.max(0.85f, c.getResources().getConfiguration().fontScale));
    }

    public static Drawable icon(Context c, int res, int tint) {
        Drawable d = c.getDrawable(res);
        if (d == null) return null;
        d = d.mutate();
        if (tint != 0) d.setTint(tint);
        return d;
    }

    public static boolean rtl(View v) {
        return v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    public static int alpha(int color, float a) {
        return Palette.alpha(color, a);
    }

    /** False when the user turned animations off: moves and scales become short fades. */
    public static boolean animations() {
        return !sReduced;
    }

    public static float density() {
        return sDensity;
    }
}
