// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.remote;

import android.app.DreamManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Panel;
import org.lumen.projector.ui.Theme;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

/**
 * The full-screen "Reconnect your remote" prompt (RemotePairing decides when), in the Lumen 2.0 look shared with Home
 * and Setup (lumen-ui Palette and Tokens, the theme of LumenTheme): the XGIMI remote with Back and Home lit and pulsing,
 * "hold Back and Home for 3 seconds", a live status (looking / press any button (still paired) / found
 * &lt;name&gt; / connected) and one
 * quiet "Not now" button for the projector's own keys or a keyboard (BACK closes too).
 *
 * While it is shown RemotePairing scans as a fast NEW window; the status is polled once a second and
 * pushed on every bond / link change. Once the remote is usable (HID up) the prompt shows "Remote
 * connected" and closes itself {@value #CONNECTED_HOLD_MS} ms later. A key from an XGIMI remote is proof
 * too (and never acts on the button). Closing by the user (button, BACK, HOME, another window, the
 * {@value #AUTO_HIDE_MS} ms auto-hide) snoozes the next prompt; screen off / standby does not.
 * Never during the first-run setup, in standby, while dreaming or under another overlay. Main thread.
 */
public final class RemotePrompt extends Panel {
    private static final String TAG = "LumenRemote";

    /** maybeShow results. */
    static final int SHOWN = 0, LATER = 1, SKIP = 2;
    /** How the prompt closed (RemotePairing.onPromptClosed). */
    static final String CLOSED_CONNECTED = "connected", CLOSED_USER = "user", CLOSED_OFF = "off";

    private static final long CONNECTED_HOLD_MS = 1_200;
    private static final long AUTO_HIDE_MS = 120_000;
    private static final long POLL_MS = 1_000;
    /** OK is ignored this long after the prompt opened (a press meant for something else). */
    private static final long OK_GUARD_MS = 400;

    private static final String FONT = "fonts/InterVariable.ttf";
    /** Layout of the prompt in design px (1920x1080), the type and button sizes are lumen-ui Tokens. */
    private static final int ART_W = 250, ART_H = 840, COL_W = 820, COL_GAP = 140, DOT = 16, MARK = 40;

    private static RemotePrompt sShown;

    private Context app;
    private RemoteHost host;
    private RemoteArt art;
    private TextView status;
    private ImageView statusMark;
    private View statusDot;
    private TextView button;
    private long openedAt;
    private boolean connected;
    private String closeHow;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!isShowing()) return;
            RemotePairing.requestPromptState();
            Ui.main().postDelayed(this, POLL_MS);
        }
    };
    private final Runnable closeConnected = () -> close(CLOSED_CONNECTED);

    // =================================================================== entry points

    /**
     * Main thread (RemotePairing.lostCheck). bondedCase = a remote is still bonded (the 60 s rule):
     * not after an HDMI-CEC wake. Returns SHOWN, LATER (blocked for now: dream, another overlay) or SKIP
     * (not this wake).
     */
    static int maybeShow(Context ctx, String reason, boolean bondedCase, RemoteHost host) {
        Context app = ctx.getApplicationContext();
        try {
            if (sShown != null && sShown.isShowing()) return SKIP;
            if (!RemotePairing.setupDone(app)) return SKIP;               // the setup has its own step
            PowerManager pm = app.getSystemService(PowerManager.class);
            if (pm != null && !pm.isInteractive()) return SKIP;
            // the app's own blockers (standby, an HDMI-CEC wake when bondedCase, its full-screen overlays)
            int gate = host == null ? RemoteHost.FREE : host.promptGate(app, bondedCase);
            if (gate == RemoteHost.SKIP) {
                Log.i(TAG, "remote lost (" + reason + "): not this wake (app gate)");
                return SKIP;
            }
            DreamManager dm = app.getSystemService(DreamManager.class);
            if (dm != null && dm.isDreaming()) return LATER;
            if (gate == RemoteHost.LATER || OverlayHost.get(app).current() != null) return LATER;
            RemotePrompt p = new RemotePrompt();
            p.app = app;
            p.host = host;
            sShown = p;
            RemotePairing.onPromptShown();
            if (!OverlayHost.get(app).show(p)) {
                sShown = null;
                RemotePairing.onPromptClosed(CLOSED_OFF);
                return LATER;
            }
            return SHOWN;
        } catch (Throwable t) {
            Log.w(TAG, "remote prompt: " + t);
            return SKIP;
        }
    }

    /** Main thread: the latest remote state (RemotePairing.postPromptState). */
    static void apply(RemoteState st) {
        RemotePrompt p = sShown;
        if (p != null && p.isShowing() && st != null) p.show(st);
    }

    // =================================================================== Panel

    @Override
    protected View onCreateView(Context ctx) {
        openedAt = SystemClock.uptimeMillis();
        Palette p = LumenTheme.palette(ctx);
        FrameLayout root = new FrameLayout(ctx);
        GradientDrawable field = new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[] {p.bgGlow, p.bg, p.bgLow});
        root.setBackground(field);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        art = new RemoteArt(ctx, p.accentOnBg, p.ok);
        row.addView(art, new LinearLayout.LayoutParams(px(ctx, ART_W), px(ctx, ART_H)));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(px(ctx, COL_W), ViewGroup.LayoutParams.WRAP_CONTENT);
        lc.setMarginStart(px(ctx, COL_GAP));
        row.addView(col, lc);

        TextView title = text(ctx, Tokens.TYPE_SETUP_TITLE, p.text1, font(ctx, Tokens.W_SETUP_TITLE));
        title.setText(R.string.lumen_remote_lost_title);
        title.setMaxLines(2);
        title.setLetterSpacing(-0.01f);
        col.addView(title);

        TextView how = text(ctx, Tokens.TYPE_BODY_L, p.text2, font(ctx, Tokens.W_BODY_L));
        how.setText(R.string.lumen_remote_lost_text);
        how.setLineSpacing(0f, 1.45f);
        col.addView(how, top(ctx, 28));

        LinearLayout st = new LinearLayout(ctx);
        st.setOrientation(LinearLayout.HORIZONTAL);
        st.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout mark = new FrameLayout(ctx);
        statusDot = new View(ctx);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(p.accentOnBg);
        statusDot.setBackground(dot);
        mark.addView(statusDot, new FrameLayout.LayoutParams(px(ctx, DOT), px(ctx, DOT), Gravity.CENTER));
        statusMark = new ImageView(ctx);
        statusMark.setImageResource(R.drawable.lumen_remote_check);
        statusMark.setImageTintList(ColorStateList.valueOf(p.ok));
        statusMark.setVisibility(View.GONE);
        mark.addView(statusMark, new FrameLayout.LayoutParams(px(ctx, MARK), px(ctx, MARK), Gravity.CENTER));
        st.addView(mark, new LinearLayout.LayoutParams(px(ctx, MARK), px(ctx, MARK)));
        status = text(ctx, Tokens.TYPE_LABEL + 4, p.text1, font(ctx, Tokens.W_LABEL));
        status.setText(R.string.lumen_remote_pair_searching);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ls.setMarginStart(px(ctx, 16));
        st.addView(status, ls);
        col.addView(st, top(ctx, 48));

        TextView battery = text(ctx, Tokens.TYPE_CAPTION + 2, p.text3, font(ctx, Tokens.W_BODY));
        battery.setText(R.string.lumen_remote_lost_battery);
        col.addView(battery, top(ctx, 20));

        button = text(ctx, Tokens.TYPE_LABEL, p.text1, font(ctx, Tokens.W_LABEL));
        button.setText(R.string.lumen_remote_lost_dismiss);
        button.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {p.onFocus, p.text1}));
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(px(ctx, 220));
        button.setMinHeight(px(ctx, Tokens.BUTTON_H));
        button.setPadding(px(ctx, 40), 0, px(ctx, 40), 0);
        button.setBackground(buttonBg(ctx, p));
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnClickListener(v -> close(CLOSED_USER));
        button.setOnFocusChangeListener((v, has) -> {
            float s = has ? Tokens.SCALE_BUTTON : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(has ? Tokens.MS_FOCUS_IN : Tokens.MS_FOCUS_OUT)
                    .setInterpolator(Theme.panelInterpolator()).start();
        });
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lb.topMargin = px(ctx, 56);
        col.addView(button, lb);
        col.setClipChildren(false);
        row.setClipChildren(false);
        return root;
    }

    @Override
    protected WindowManager.LayoutParams onCreateLayoutParams(Context ctx) {
        return OverlayHost.fullscreenParams(true);
    }

    @Override
    protected long autoHideMs() { return AUTO_HIDE_MS; }

    @Override
    protected void onShown(View root) {
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(260).setInterpolator(Theme.panelInterpolator()).withLayer().start();
        if (button != null) button.requestFocus();
        if (statusDot != null) pulseDot(true);
        Ui.main().removeCallbacks(poll);
        Ui.main().post(poll);
        Log.i(TAG, "remote-lost prompt shown");
    }

    @Override
    protected void animateOut(View root, Runnable end) {
        root.animate().cancel();
        root.animate().alpha(0f).setDuration(200).withLayer().withEndAction(end).start();
    }

    @Override
    protected boolean onBack() {
        if (closeHow == null) closeHow = CLOSED_USER;
        return false;                                   // OverlayHost closes the panel
    }

    @Override
    protected boolean onKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        if (fromRemote(ev)) {
            // the remote works: show it as connected and close; its keys never press "Not now"
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0 && !connected) {
                Log.i(TAG, "key from the XGIMI remote on the prompt: connected");
                RemoteState st = new RemoteState();
                st.connected = true;
                show(st);
            }
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        return ok && SystemClock.uptimeMillis() - openedAt < OK_GUARD_MS;
    }

    @Override
    protected void onDismissed() {
        Ui.main().removeCallbacks(poll);
        Ui.main().removeCallbacks(closeConnected);
        if (statusDot != null) statusDot.animate().cancel();
        if (sShown == this) sShown = null;
        String how = closeHow;
        if (how == null) {
            // closed by OverlayHost: screen off / standby, or the user (HOME, another window, auto-hide)
            PowerManager pm = app == null ? null : app.getSystemService(PowerManager.class);
            boolean awake = pm == null || pm.isInteractive();
            how = !awake || (host != null && host.inStandby()) ? CLOSED_OFF : connected ? CLOSED_CONNECTED : CLOSED_USER;
        }
        Log.i(TAG, "remote-lost prompt closed: " + how);
        RemotePairing.onPromptClosed(how);
    }

    // =================================================================== state

    private void show(RemoteState st) {
        if (status == null || connected) return;
        if (st.connected) {
            connected = true;
            status.setText(R.string.lumen_remote_connected);
            statusDot.animate().cancel();
            statusDot.setVisibility(View.GONE);
            statusMark.setVisibility(View.VISIBLE);
            art.setConnected(true);
            Ui.main().removeCallbacks(closeConnected);
            Ui.main().postDelayed(closeConnected, CONNECTED_HOLD_MS);
            return;
        }
        if (st.pairing) {
            String name = !TextUtils.isEmpty(st.found) ? st.found : st.name;
            if (!TextUtils.isEmpty(name)) {
                status.setText(app.getString(R.string.lumen_remote_lost_found, name));
                return;
            }
        }
        // still paired, only not connected (the 60 s rule): any key wakes it, as on the setup's step;
        // Back + Home (the text above) is for a remote that lost its pairing
        status.setText(st.bonded > 0 && !st.pairing ? R.string.lumen_remote_lost_wake : R.string.lumen_remote_pair_searching);
    }

    private void close(String how) {
        if (!isShowing()) return;
        if (closeHow == null) closeHow = connected ? CLOSED_CONNECTED : how;
        dismiss();
    }

    /** The status dot breathes with the illustration's glow (alpha only, stopped when closed). */
    private void pulseDot(boolean dim) {
        if (statusDot == null || connected || !isShowing()) return;
        statusDot.animate().alpha(dim ? 0.25f : 1f).setDuration(1_100)
                .withEndAction(() -> pulseDot(!dim)).start();
    }

    /** A key from an XGIMI BLE remote (not the keypad, the IR receiver or a keyboard). */
    private static boolean fromRemote(KeyEvent ev) {
        InputDevice d = ev.getDevice();
        return d != null && d.isExternal() && RemoteMatch.isRemoteVendor(d.getVendorId());
    }

    // =================================================================== views

    private static int px(Context c, float design) { return Theme.px(c, design); }

    private static TextView text(Context c, float size, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Theme.pxf(c, size));
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    /** Inter at a weight (the menus' font, assets of lumen-menu); the system font when the app has none. */
    private static Typeface font(Context c, int weight) {
        try {
            Typeface t = new Typeface.Builder(c.getAssets(), FONT).setFontVariationSettings("'wght' " + weight).build();
            if (t != null) return t;
        } catch (Throwable ignored) {
            // no Inter in this app: the system font below
        }
        return Typeface.create("sans-serif", weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
    }

    private static LinearLayout.LayoutParams top(Context c, float margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(c, margin);
        return lp;
    }

    /** The 2.0 button: the palette's focus fill when focused (its text colour onFocus), a quiet tile otherwise. */
    private static Drawable buttonBg(Context c, Palette p) {
        float r = Theme.pxf(c, Tokens.R_BUTTON);
        GradientDrawable on = new GradientDrawable();
        on.setColor(p.focus);
        on.setCornerRadius(r);
        GradientDrawable off = new GradientDrawable();
        off.setColor(p.surface);
        off.setCornerRadius(r);
        off.setStroke(Math.max(1, Theme.px(c, 2)), p.panelLine);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, on);
        s.addState(new int[] {}, off);
        s.setEnterFadeDuration(Tokens.MS_FOCUS_IN);
        s.setExitFadeDuration(Tokens.MS_FOCUS_OUT);
        return s;
    }
}
