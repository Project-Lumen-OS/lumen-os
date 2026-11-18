package org.lumen.setup.steps;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.setup.Bridge;
import org.lumen.setup.L;
import org.lumen.setup.R;
import org.lumen.setup.RemoteSearch;
import org.lumen.setup.SetupActivity;
import org.lumen.setup.ui.Icon;
import org.lumen.setup.ui.Pill;
import org.lumen.setup.ui.RemoteArt;
import org.lumen.setup.ui.Ui;
import org.lumen.system.SettingsPage;
import org.lumen.system.SystemPages;

/**
 * Remote (Lumen OS 1.0: the FIRST step, always shown; 2.0 look the design notes). The XGIMI remote is
 * Bluetooth only, so before it is paired nothing else can be done with it. Pairing itself runs in Z9xProjector's
 * RemotePairing (LE scan open for the whole setup, auto-confirmed bond); this step shows the remote with Back and
 * Home lit, "hold Back and Home for 3 seconds" (in the image's first-start language, set before this screen by
 * FirstLocale) and the live state from the bridge (SETUP_STATE broadcast + 1 Hz remote_state poll): searching /
 * found &lt;name&gt; / press a button (a bonded remote asleep) / connected, and since 2.0 "not found" after 60 s of
 * searching with "Search again" (focused) and "Skip" ({@link RemoteSearch}). "Connected" is the bridge's: bonded AND
 * link up AND its HID input device up. A key from an XGIMI remote is proof too. Once connected the step asks for OK on
 * the remote (the OK key of the drawing lights up and pulses) and continues only on an OK / ENTER from an XGIMI
 * remote: the user wants the remote confirmed working before the setup goes on. Without a remote the only way on is
 * the projector's own keys or a keyboard: "Skip" (focused) is for them; the remote's OK is consumed by
 * {@link #onKey} and never presses it.
 */
public class RemoteStep extends Step {
    public static final String ID = "remote";
    private static final long POLL_MS = 1000;
    /** HID vendor ids of the XGIMI BLE remotes (gsi/overlay keylayouts: 000d_38xx, 1d5a_c081, 26e3_af02). */
    private static final int[] REMOTE_VENDOR_IDS = {0x000d, 0x1d5a, 0x26e3};

    private RemoteArt mArt;
    private TextView mStatus;
    private TextView mBody;
    private Icon mStatusIcon;
    private Pill mSkip;
    private Pill mOther;
    private Pill mRetry;
    /** Holds the focus without a mark while the step waits for OK on the remote (the design notes "found"). */
    private LinearLayout mCol;
    private boolean mShownOk;
    private boolean mVisible;
    private RemoteSearch mSearch = new RemoteSearch(0);
    /** Connected seen on this showing of the step (bridge or a remote key); never flips back. */
    private boolean mConnected;
    /** OK pressed on the remote while connected: its UP continues. */
    private boolean mOkDown;

    public RemoteStep(SetupActivity h) {
        super(h);
    }

    @Override
    public String id() { return ID; }

    /** Nothing to do here once a remote worked: BACK from the language step goes nowhere. */
    @Override
    public boolean revisitable() { return !host.remoteConnected && !host.state.remoteSeen(); }

    @Override
    public CharSequence title() { return s(R.string.remote_title); }

    @Override
    public CharSequence subtitle() { return s(R.string.remote_subtitle); }

    /** the design notes: the remote drawing at y 236, the status block beside it 136 lower (174 once found). */
    @Override
    public int rightTop() { return 236; }

    private static final int COL_TOP = 136, COL_TOP_FOUND = 174;

    @Override
    public View createContent() {
        LinearLayout v = Ui.hbox(ctx());
        v.setGravity(Gravity.TOP);
        mArt = new RemoteArt(ctx());
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(Ui.px(175), Ui.px(600));
        al.setMarginStart(Ui.px(60));
        v.addView(mArt, al);

        LinearLayout col = Ui.vbox(ctx());
        col.setFocusable(true);
        col.setDefaultFocusHighlightEnabled(false);
        mCol = col;
        mShownOk = false;
        LinearLayout st = Ui.hbox(ctx());
        mStatusIcon = new Icon(ctx(), Icon.SPINNER).color(Ui.TEXT);
        st.addView(mStatusIcon, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        mStatus = Ui.text(ctx(), 30, Ui.TEXT, Ui.W_STATUS);
        mStatus.setLetterSpacing(-0.3f / 30f);
        mStatus.setMaxLines(2);
        mStatus.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sl.setMarginStart(Ui.px(16));
        st.addView(mStatus, sl);
        col.addView(st, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mBody = Ui.body(ctx(), "");
        col.addView(mBody, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 18));

        LinearLayout btns = Ui.hbox(ctx());
        mRetry = new Pill(ctx(), s(R.string.action_search_again), false).icon(Icon.REFRESH);
        mRetry.setOnClickListener(x -> retry());
        mSkip = new Pill(ctx(), s(R.string.action_skip), false);
        mSkip.setOnClickListener(x -> {
            if (host.current() == this) host.next(mConnected ? "next" : "skip");
        });
        Pill other = new Pill(ctx(), s(R.string.remote_other), false).icon(Icon.BLUETOOTH);
        other.setOnClickListener(x -> {
            if (!SystemPages.open(host, SettingsPage.BLUETOOTH)) L.w("bt settings: no way opens Remotes & Accessories");
        });
        mOther = other;
        btns.addView(mRetry, gap(false));
        btns.addView(mSkip, gap(false));
        btns.addView(other, gap(true));
        col.addView(btns, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 36));

        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.px(624), ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.setMarginStart(Ui.px(85));
        cl.gravity = Gravity.TOP;
        cl.topMargin = Ui.px(COL_TOP);
        v.addView(col, cl);
        show(mSearch.state());
        return v;
    }

    private LinearLayout.LayoutParams gap(boolean last) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!last) lp.setMarginEnd(Ui.px(16));
        return lp;
    }

    @Override
    public View initialFocus() {
        RemoteSearch.State st = mSearch.state();
        return st == RemoteSearch.State.NOT_FOUND ? mRetry : waitsForOk(st) ? mCol : mSkip;
    }

    /** Found or connected: the remote's OK finishes, so no button shows focus (OK must not look like Skip). */
    private static boolean waitsForOk(RemoteSearch.State st) {
        return st == RemoteSearch.State.FOUND || st == RemoteSearch.State.CONNECTED;
    }

    @Override
    public void onEnter() {
        mVisible = true;
        mOkDown = false;
        mConnected = false;
        mSearch = new RemoteSearch(SystemClock.elapsedRealtime());
        show(mSearch.state());
        host.main.post(mPoll);
    }

    @Override
    public void onExit() {
        mVisible = false;
        host.main.removeCallbacks(mPoll);
    }

    @Override
    public void onResume() {
        if (mVisible) {
            host.main.removeCallbacks(mPoll);
            host.main.post(mPoll);
        }
    }

    private void retry() {
        L.i("remote: search again");
        mSearch.retry(SystemClock.elapsedRealtime());
        show(mSearch.state());
        mSkip.requestFocus();
    }

    private final Runnable mPoll = new Runnable() {
        @Override
        public void run() {
            if (!mVisible) return;
            if (!Boolean.FALSE.equals(host.bridgeOk)) {
                Bridge.remoteState(host, r -> {
                    if (Bridge.ok(r)) apply(r);
                    else tick();
                });
            } else {
                tick();
            }
            host.main.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onBridgeEvent(Bundle ex) {
        if ("remote".equals(ex.getString("what"))) apply(ex);
    }

    /**
     * Keys of the XGIMI remote prove it works: "connected" at once. Its OK (a full press that started
     * on this step) is the confirmation the step waits for and continues (the DOWN and UP are consumed
     * so they never press Skip); other keys (BACK of the pairing combo too) go on as usual.
     */
    @Override
    public boolean onKey(KeyEvent e) {
        if (!mVisible) return false;
        if (!fromRemote(e)) {
            // projector keys or a keyboard while the focus waits unmarked: the first key shows Skip focused
            if (mCol != null && mCol.isFocused() && e.getAction() == KeyEvent.ACTION_DOWN && navKey(e.getKeyCode())) {
                mSkip.requestFocus();
                return true;
            }
            return false;
        }
        if (!mConnected) {
            L.i("key " + e.getKeyCode() + " from the XGIMI remote on the remote step -> connected");
            onConnected();
        }
        int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER
                && code != KeyEvent.KEYCODE_NUMPAD_ENTER) {
            return false;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) mOkDown = true;
        } else if (e.getAction() == KeyEvent.ACTION_UP && mOkDown && !e.isCanceled()) {
            mOkDown = false;
            L.i("OK on the XGIMI remote -> remote confirmed, continue");
            if (host.current() == this) host.next("next");
        }
        return true;
    }

    /** A poll without a bridge: only the 60 s timer can change what is shown. */
    private void tick() {
        if (!mVisible || mConnected) return;
        if (mSearch.report(SystemClock.elapsedRealtime(), false, false, null, 0)) show(mSearch.state());
    }

    private void apply(Bundle r) {
        if (!mVisible || mStatus == null || mConnected) return;
        if (r.getBoolean("connected", false)) {
            onConnected();
            return;
        }
        String name = r.getString("found", "");
        if (TextUtils.isEmpty(name)) name = r.getString("name", "");
        if (mSearch.report(SystemClock.elapsedRealtime(), false, r.getBoolean("pairing", false), name,
                r.getInt("bonded", 0))) {
            show(mSearch.state());
        }
    }

    private void onConnected() {
        mConnected = true;
        mSearch.connected();
        host.setRemoteConnected(true);
        show(RemoteSearch.State.CONNECTED);
        L.i("remote connected on the step -> waiting for OK on the remote");
    }

    /** One state on screen: status line, its icon and colour, the text under it, the buttons, the drawing. */
    private void show(RemoteSearch.State st) {
        if (mStatus == null) return;
        boolean lost = st == RemoteSearch.State.NOT_FOUND, ok = waitsForOk(st);
        View focused = host.getCurrentFocus();
        mArt.setLost(lost);
        mArt.setConnected(ok);
        mArt.setAwaitOk(ok);
        switch (st) {
            case CONNECTED:
                status(Icon.DONE, Ui.OK, s(R.string.remote_connected), s(R.string.remote_press_ok));
                break;
            case NOT_FOUND:
                status(Icon.ALERT, Ui.ERROR, s(R.string.remote_not_found), s(R.string.remote_not_found_hint));
                L.i("remote: not found in " + RemoteSearch.TIMEOUT_MS / 1000 + " s");
                break;
            case FOUND:
                // the design notes "found": the check, "Found remote XGIMI RC" in the success colour, the lit OK key
                status(Icon.DONE, Ui.OK, s(R.string.remote_found, mSearch.name()), s(R.string.remote_press_ok));
                break;
            case WAKE:
                status(Icon.SPINNER, Ui.TEXT, s(R.string.remote_wake), s(R.string.remote_later_hint));
                break;
            default:
                status(Icon.SPINNER, Ui.TEXT, s(R.string.remote_searching), s(R.string.remote_later_hint));
        }
        mRetry.setVisibility(lost ? View.VISIBLE : View.GONE);
        mOther.setVisibility(lost || ok ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams cl = (LinearLayout.LayoutParams) mCol.getLayoutParams();
        int top = Ui.px(ok ? COL_TOP_FOUND : COL_TOP);
        if (cl != null && cl.topMargin != top) {
            cl.topMargin = top;
            mCol.setLayoutParams(cl);
        }
        boolean entered = ok != mShownOk;
        mShownOk = ok;
        boolean mine = focused == null || focused == mSkip || focused == mOther || focused == mRetry || focused == mCol;
        if (!mine) return;
        if (lost) mRetry.requestFocus();
        else if (ok) {
            if (entered || focused != mSkip) mCol.requestFocus();
        } else if (focused != mSkip) mSkip.requestFocus();
    }

    private void status(int icon, int color, CharSequence text, CharSequence body) {
        mStatusIcon.type(icon).color(color);
        mStatus.setTextColor(color);
        mStatus.setText(text);
        mBody.setText(body);
    }

    private static boolean navKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /** A key from an XGIMI BLE remote (not the projector's keypad, the IR receiver or a keyboard). */
    private static boolean fromRemote(KeyEvent e) {
        InputDevice d = e.getDevice();
        if (d == null || !d.isExternal()) return false;
        int vid = d.getVendorId();
        for (int v : REMOTE_VENDOR_IDS) if (v == vid) return true;
        return false;
    }
}
