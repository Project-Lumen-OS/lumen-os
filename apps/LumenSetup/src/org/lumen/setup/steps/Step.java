package org.lumen.setup.steps;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.lumen.setup.SetupActivity;
import org.lumen.setup.ui.Ui;

/**
 * One wizard step: a View tree swapped inside the single SetupActivity window. Content is always
 * built fresh by {@link #createContent()} (also after a language switch), so steps keep their
 * durable state in fields or SetupState, never only in Views.
 */
public abstract class Step {
    /** Title left, content right (most steps). */
    public static final int SPLIT = 0;
    /** Title on top, content across the full width (launcher cards). */
    public static final int WIDE = 1;
    /** Full screen, no wordmark/title column (picture grid). */
    public static final int FULL = 2;

    protected final SetupActivity host;
    private FrameLayout mPages;
    private View mPage;

    protected Step(SetupActivity host) {
        this.host = host;
    }

    public abstract String id();

    /** Shown at all (cheap, synchronous; may use the host's cached async state). */
    public boolean available() { return true; }

    /** Already done (for example the remote is connected): skipped silently, the dot shows done. */
    public boolean autoSkip() { return false; }

    /** BACK from a later step may land here (false: nothing left to do here, BACK goes further back). */
    public boolean revisitable() { return true; }

    public int layout() { return SPLIT; }

    public abstract CharSequence title();

    public CharSequence subtitle() { return null; }

    /** Optional view under the subtitle in the left column (SPLIT only): a note (Ui.note) or the remote's hint. */
    public View leftExtra() { return null; }

    /** Optional view above the title in the left column (the big logo of the language step). */
    public View leftHead() { return null; }

    /** Design y of the left column's first line (the design notes: 300). */
    public int leftTop() { return 300; }

    /**
     * SPLIT: design y where the right column's content starts (the design notes draws every step's list or block at a
     * fixed height, the Wi-Fi list at 236, Google at 340), or -1 to centre it between the top bar and the bottom margin.
     * A step with sub-pages returns 0 and places each page itself ({@link #pageLp}).
     */
    public int rightTop() { return -1; }

    /** Most lines of the step title (a long Wi-Fi name in the password title gets 2). */
    public int titleLines() { return 3; }

    /** False: no logo in the top bar on this step (the language step shows its big one). */
    public boolean showsLogo() { return true; }

    /** Optional line at the bottom left (the version on the language step). */
    public CharSequence footer() { return null; }

    public abstract View createContent();

    /** View to focus after the transition; null = first focusable of the content. */
    public View initialFocus() { return null; }

    public void onEnter() {}

    public void onExit() {}

    /** BACK inside the step (sub-page, busy state). True = handled. */
    public boolean onBack() { return false; }

    public void onResume() {}

    public void onPause() {}

    /** Default network or validation changed (host NetMon). */
    public void onNetChanged() {}

    /** SETUP_STATE event from Z9xProjector (lossy; steps also poll). */
    public void onBridgeEvent(Bundle extras) {}

    /** Every key before the views (and before the BACK handling) while no sheet is shown. True = consumed. */
    public boolean onKey(KeyEvent e) { return false; }

    // ------------------------------------------------------------------ helpers

    protected Context ctx() { return host; }

    protected String s(int id) { return host.getString(id); }

    protected String s(int id, Object... args) { return host.getString(id, args); }

    /** A page container for steps with sub-pages (list -> password -> status); focus rings may draw past it. */
    protected FrameLayout pages() {
        mPages = new FrameLayout(host);
        mPages.setClipChildren(false);
        mPages.setClipToPadding(false);
        mPage = null;
        return mPages;
    }

    /** A sub-page placed at a fixed design y of the screen (the step's {@link #rightTop} is 0). */
    protected static FrameLayout.LayoutParams pageLp(float topDesign) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP);
        lp.topMargin = Ui.px(topDesign);
        return lp;
    }

    /** Swap the visible sub-page with a short cross-fade and move focus into it. */
    protected void showPage(View page, View focus) {
        if (mPages == null) return;
        View old = mPage;
        mPage = page;
        if (old != null && old != page) {
            old.animate().cancel();
            if (old instanceof ViewGroup) ((ViewGroup) old).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            old.animate().alpha(0f).setDuration(120).withEndAction(() -> mPages.removeView(old)).start();
        }
        if (page.getParent() == null) {
            ViewGroup.LayoutParams lp = page.getLayoutParams();
            if (lp == null) {
                lp = rightTop() == 0 ? pageLp(300) : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER_VERTICAL);
            }
            mPages.addView(page, lp);
        }
        page.setAlpha(0f);
        page.setTranslationY(Ui.pxf(16));
        page.animate().alpha(1f).translationY(0f).setDuration(200).setStartDelay(old == null ? 0 : 60)
                .setInterpolator(Ui.decel()).start();
        View f = focus != null ? focus : page;
        f.post(() -> {
            if (f.isAttachedToWindow()) f.requestFocus();
        });
    }

    protected View currentPage() { return mPage; }
}
