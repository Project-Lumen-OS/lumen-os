package org.lumen.ui;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * The D-pad focus look of every Lumen TV app (module lumen-ui; it replaced six copies in LumenInstaller, LumenFlasher,
 * LumenDsuInstall, X50EyeGuard, X50Projector and X50TvInput): the focused button is an accent fill with a white
 * outline, white text and a 1.04 scale; the others are a muted dark pill with grey text and no outline. Dialogs and
 * short notices get a dark card with light text as well. Every colour is set here, none comes from the theme: the
 * DeviceDefault dialog buttons and the system text toast of our images drew a light (cyan) fill with white text,
 * unreadable (X50 t13 live). The colours are the tokens of {@link Tokens}.
 */
public final class TvFocus {
    public static final int ACCENT = Tokens.ACCENT, ACCENT_PRESSED = Tokens.ACCENT_PRESSED, IDLE = Tokens.IDLE,
            IDLE_TEXT = Tokens.IDLE_TEXT, OFF = Tokens.OFF, OFF_TEXT = Tokens.OFF_TEXT, PANEL = Tokens.PANEL,
            TITLE = Tokens.TITLE, BODY = Tokens.BODY;
    public static final float SCALE = 1.04f;

    private TvFocus() {}

    /** Styles one button: focused = accent + white outline + scale, unfocused = muted, disabled = darker still. */
    public static void button(Button b) {
        int r = dp(b, 24), w = dp(b, 2);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] { -android.R.attr.state_enabled }, pill(OFF, r, 0));
        bg.addState(new int[] { android.R.attr.state_pressed }, pill(ACCENT_PRESSED, r, w));
        bg.addState(new int[] { android.R.attr.state_focused }, pill(ACCENT, r, w));
        bg.addState(new int[0], pill(IDLE, r, 0));
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(
                new int[][] { { -android.R.attr.state_enabled }, { android.R.attr.state_focused }, { android.R.attr.state_pressed }, {} },
                new int[] { OFF_TEXT, Color.WHITE, Color.WHITE, IDLE_TEXT }));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, has) -> scale(v, has));
        scale(b, b.isFocused());
        unclip(b);
    }

    /**
     * A button on a full-screen safety cover or notice (X50EyeGuard): the {@link #button} look, focusable in touch mode
     * as well (the cover must hold the D-pad focus however its window starts), 24 dp side padding, 48 dp high.
     */
    public static void coverButton(Button b) {
        button(b);
        b.setFocusableInTouchMode(true);
        b.setPadding(dp(b, 24), 0, dp(b, 24), 0);
        b.setMinHeight(dp(b, 48));
        b.setMinimumHeight(dp(b, 48));
    }

    /**
     * The 1.04 scale draws a focused button past its own box, so its parent and grandparent must not clip it (t14 live:
     * the eye-protection cover button lost its left edge to the card's padding clip). Done once the button is in a
     * window, when both exist; a scroll container keeps its clip (its content must stay inside the viewport).
     */
    public static void unclip(View b) {
        Runnable r = () -> {
            android.view.ViewParent p = b.getParent();
            for (int i = 0; i < 2 && p instanceof android.view.ViewGroup; i++, p = p.getParent()) {
                android.view.ViewGroup g = (android.view.ViewGroup) p;
                if (g instanceof android.widget.ScrollView || g instanceof android.widget.HorizontalScrollView
                        || g instanceof android.widget.AbsListView) break;
                g.setClipToPadding(false);
                g.setClipChildren(false);
            }
        };
        if (b.isAttachedToWindow()) {
            r.run();
            return;
        }
        b.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                v.removeOnAttachStateChangeListener(this);
                r.run();
            }

            @Override public void onViewDetachedFromWindow(View v) {}
        });
    }

    /**
     * A shown dialog (call from its OnShowListener): dark card, light title and message, TvFocus buttons with a gap so a
     * scaled one never overlaps.
     */
    public static void dialog(AlertDialog d) {
        android.view.Window w = d.getWindow();
        View any = d.getButton(AlertDialog.BUTTON_POSITIVE);
        if (w != null && any != null) w.setBackgroundDrawable(card(PANEL, dp(any, 16), 0, 0));
        android.widget.TextView msg = d.findViewById(android.R.id.message);
        if (msg != null) msg.setTextColor(BODY);
        int titleId = d.getContext().getResources().getIdentifier("alertTitle", "id", "android");
        android.widget.TextView title = titleId == 0 ? null : d.findViewById(titleId);
        if (title != null) title.setTextColor(TITLE);
        for (int which : new int[] { AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE }) {
            Button b = d.getButton(which);
            if (b == null || b.getVisibility() != View.VISIBLE) continue;
            button(b);
            b.setPadding(dp(b, 20), 0, dp(b, 20), 0);
            b.setMinHeight(dp(b, 44));
            b.setMinimumHeight(dp(b, 44));
            ViewGroup.LayoutParams lp = b.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                m.setMargins(dp(b, 8), dp(b, 6), dp(b, 8), dp(b, 6));
                b.setLayoutParams(m);
            }
        }
    }

    /**
     * A short notice at the bottom, drawn by this app (a dark pill with white text) instead of the system text toast.
     * Main thread. A custom toast view is allowed from the background for this app because it runs as
     * android.uid.system (NotificationManagerService treats a system caller's toast as a system toast).
     */
    public static void toast(android.content.Context c, CharSequence text, boolean longer) {
        android.content.Context app = c.getApplicationContext();
        android.widget.TextView t = new android.widget.TextView(app);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int h = dp(t, 24), v = dp(t, 12);
        t.setPadding(h, v, h, v);
        t.setBackground(card(PANEL, dp(t, 24), dp(t, 2), IDLE));
        android.widget.Toast n = new android.widget.Toast(app);
        n.setView(t);
        n.setDuration(longer ? android.widget.Toast.LENGTH_LONG : android.widget.Toast.LENGTH_SHORT);
        n.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, dp(t, 48));
        n.show();
    }

    public static void toast(android.content.Context c, int text, boolean longer) {
        toast(c, c.getString(text), longer);
    }

    /** A focusable panel that is not a button (a scrollable log): muted card, a white outline while focused. */
    public static void panel(View v) {
        int r = dp(v, 10);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] { android.R.attr.state_focused }, card(PANEL, r, dp(v, 2), Color.WHITE));
        bg.addState(new int[0], card(PANEL, r, 0, 0));
        v.setBackground(bg);
    }

    public static void scale(View v, boolean has) {
        float s = has ? SCALE : 1f;
        v.animate().scaleX(s).scaleY(s).setDuration(90).start();
    }

    public static GradientDrawable pill(int color, int r, int stroke) { return card(color, r, stroke, Color.WHITE); }

    /** A rounded fill, with a white outline of stroke px when stroke > 0 (the same as {@link #pill}). */
    public static GradientDrawable card(int color, int r, int stroke) { return card(color, r, stroke, Color.WHITE); }

    public static GradientDrawable card(int color, int r, int stroke, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        if (stroke > 0) g.setStroke(stroke, strokeColor);
        return g;
    }

    public static int dp(View v, int x) { return Math.round(x * v.getResources().getDisplayMetrics().density); }
}
