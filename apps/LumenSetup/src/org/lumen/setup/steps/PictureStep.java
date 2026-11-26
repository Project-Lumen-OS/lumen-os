// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.steps;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.setup.Bridge;
import org.lumen.setup.L;
import org.lumen.setup.R;
import org.lumen.setup.SetupActivity;
import org.lumen.setup.ui.GridArt;
import org.lumen.setup.ui.Icon;
import org.lumen.setup.ui.Pill;
import org.lumen.setup.ui.Row;
import org.lumen.setup.ui.SwitchView;
import org.lumen.setup.ui.Ring;
import org.lumen.setup.ui.Tile;
import org.lumen.setup.ui.Ui;
import org.lumen.ui.Tokens;

/**
 * Picture: autofocus + keystone (2.0 look the design notes FULL: grid field, five tiles, two switches). Every action is
 * one named SetupBridge method (af_run, kst_auto, kst_fit, kst_manual, focus_manual, toggle_set of
 * POWER_ON_AF / MOVE_AK); Z9xProjector keeps the HAL whitelist and its busy gates, and refuses while
 * the vendor's own power-on AF/AK runs. Busy/ready from hal_state (STATE broadcast + 2 Hz poll).
 * Hidden when the bridge is missing.
 */
public class PictureStep extends Step {
    public static final String ID = "picture";
    private static final long POLL_MS = 500;
    private static final long READY_GIVE_UP_MS = 20_000;

    private boolean mVisible;
    private long mEnter;
    private boolean mReady;
    private boolean mBusy;
    private boolean mGaveUp;
    private boolean mFitSupported;

    private Icon mStIcon;
    private TextView mSt;
    private Tile mAf, mKst, mFit, mKstManual, mFocusManual;
    private Row mPowerOnAf, mMoveAk;
    private SwitchView mSwAf, mSwAk;
    private Pill mContinue;

    public PictureStep(SetupActivity h) {
        super(h);
    }

    @Override
    public String id() { return ID; }

    @Override
    public boolean available() { return !Boolean.FALSE.equals(host.bridgeOk); }

    @Override
    public int layout() { return FULL; }

    @Override
    public CharSequence title() { return s(R.string.pic_title); }

    /** the design notes FULL: grid field, title from y 196, five tiles at y 520, two switches at y 800, Continue. */
    @Override
    public View createContent() {
        FrameLayout root = new FrameLayout(ctx());
        root.setClipChildren(false);
        root.addView(new GridArt(ctx()), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout head = Ui.vbox(ctx());
        TextView t = Ui.title(ctx());
        t.setText(s(R.string.pic_title));
        head.addView(t);
        TextView sub = Ui.subtitle(ctx());
        sub.setText(s(R.string.pic_subtitle));
        head.addView(sub, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 22));
        LinearLayout st = Ui.hbox(ctx());
        mStIcon = new Icon(ctx(), Icon.SPINNER).color(Ui.TEXT_DIM);
        st.addView(mStIcon, new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
        mSt = Ui.text(ctx(), 22, Ui.TEXT_DIM, Ui.W_LABEL);
        mSt.setMaxLines(2);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sl.setMarginStart(Ui.px(12));
        st.addView(mSt, sl);
        head.addView(st, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 20));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(Ui.px(660), ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.setMarginStart(Ui.px(Tokens.MARGIN));
        hl.topMargin = Ui.px(196);
        root.addView(head, hl);

        LinearLayout tiles = Ui.hbox(ctx());
        mAf = action(tiles, Icon.FOCUS, R.string.pic_af, () -> Bridge.afRun(host, this::onActionReply));
        mKst = action(tiles, Icon.KEYSTONE, R.string.pic_kst, () -> Bridge.kstAuto(host, this::onActionReply));
        mFit = action(tiles, Icon.FIT, R.string.pic_fit, () -> Bridge.kstFit(host, r -> {
            if ("unsupported".equals(Bridge.err(r))) {
                mFitSupported = false;
                mFit.setVisibility(View.GONE);
            }
            onActionReply(r);
        }));
        mFit.setVisibility(mFitSupported ? View.VISIBLE : View.GONE);
        mKstManual = action(tiles, Icon.TRANSFORM, R.string.pic_kst_manual, () -> Bridge.kstManual(host, this::onActionReply));
        mFocusManual = action(tiles, Icon.FOCUS_WEAK, R.string.pic_focus_manual, () -> Bridge.focusManual(host, this::onActionReply));
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.setMarginStart(Ui.px(Tokens.MARGIN - Ring.INSET));
        tl.topMargin = Ui.px(520 - Ring.INSET);
        root.addView(tiles, tl);

        LinearLayout sw = Ui.vbox(ctx());
        mSwAf = new SwitchView(ctx());
        mPowerOnAf = toggle(sw, R.string.pic_poweron_af, mSwAf, Bridge.T_POWER_ON_AF);
        mSwAk = new SwitchView(ctx());
        mMoveAk = toggle(sw, R.string.pic_move_ak, mSwAk, Bridge.T_MOVE_AK);
        mPowerOnAf.setVisibility(View.GONE);
        mMoveAk.setVisibility(View.GONE);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(Ui.px(760), ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.setMarginStart(Ui.px(Tokens.MARGIN));
        wl.topMargin = Ui.px(800);
        root.addView(sw, wl);

        mContinue = new Pill(ctx(), s(R.string.action_continue), false);
        mContinue.setOnClickListener(x -> host.next("next"));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        cl.setMarginEnd(Ui.px(Tokens.MARGIN));
        cl.topMargin = Ui.px(860);
        root.addView(mContinue, cl);
        bindState();
        return root;
    }

    private Tile action(LinearLayout tiles, int icon, int label, Runnable r) {
        Tile t = new Tile(ctx(), icon, s(label));
        t.setOnClickListener(x -> {
            if (!mReady || mBusy) {
                bindState();
                return;
            }
            r.run();
        });
        LinearLayout.LayoutParams lp = Tile.lp();
        if (tiles.getChildCount() > 0) lp.setMarginStart(Ui.px(28 - 2 * Ring.INSET));
        tiles.addView(t, lp);
        return t;
    }

    private Row toggle(LinearLayout card, int label, SwitchView sw, String name) {
        Row row = new Row(ctx(), s(label));
        row.end(sw, Ui.px(48), Ui.px(48));
        row.setOnClickListener(x -> {
            boolean on = !sw.isOn();
            sw.setOn(on, true);
            Bridge.toggleSet(host, name, on, r -> {
                if (Bridge.ok(r) && r.containsKey(name)) sw.setOn(r.getBoolean(name), true);
                else if (!Bridge.ok(r)) sw.setOn(!on, true);
            });
        });
        card.addView(row, rowLp());
        return row;
    }

    private static LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(6);
        return lp;
    }

    @Override
    public View initialFocus() { return mAf; }

    @Override
    public void onEnter() {
        mVisible = true;
        mEnter = SystemClock.elapsedRealtime();
        mGaveUp = false;
        host.main.post(mPoll);
        Bridge.togglesGet(host, r -> {
            if (!Bridge.ok(r) || mSwAf == null) return;
            if (r.containsKey(Bridge.T_POWER_ON_AF)) {
                mSwAf.setOn(r.getBoolean(Bridge.T_POWER_ON_AF), false);
                mPowerOnAf.setVisibility(View.VISIBLE);
            }
            if (r.containsKey(Bridge.T_MOVE_AK)) {
                mSwAk.setOn(r.getBoolean(Bridge.T_MOVE_AK), false);
                mMoveAk.setVisibility(View.VISIBLE);
            }
        });
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

    private final Runnable mPoll = new Runnable() {
        @Override
        public void run() {
            if (!mVisible) return;
            Bridge.halState(host, r -> {
                if (Bridge.ok(r)) applyHal(r);
                else if (!mReady) bindState();
            });
            host.main.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onBridgeEvent(Bundle ex) {
        String w = ex.getString("what");
        if ("hal".equals(w) || "af".equals(w) || "kst".equals(w)) {
            if (ex.containsKey("ready")) applyHal(ex);
        }
    }

    private void applyHal(Bundle r) {
        boolean ready = r.getBoolean("ready", false);
        boolean busy = r.getBoolean("kstRunning", false) || r.getBoolean("afBusy", false) || r.getBoolean("akOverlay", false);
        if (r.containsKey("curtainFit")) {
            boolean fit = r.getBoolean("curtainFit", false);
            if (fit != mFitSupported) {
                mFitSupported = fit;
                if (mFit != null) mFit.setVisibility(fit ? View.VISIBLE : View.GONE);
            }
        }
        if (ready != mReady || busy != mBusy) {
            mReady = ready;
            mBusy = busy;
            bindState();
        } else if (!ready) {
            bindState();
        }
    }

    private void onActionReply(Bundle r) {
        if (Bridge.ok(r)) {
            mBusy = true; // until hal_state says otherwise
            bindState();
            return;
        }
        String e = Bridge.err(r);
        if ("busy".equals(e)) mBusy = true;
        else if ("not_ready".equals(e)) mReady = false;
        L.i("picture action refused err=" + e);
        bindState();
    }

    private void bindState() {
        if (mSt == null) return;
        if (!mReady && !mGaveUp && SystemClock.elapsedRealtime() - mEnter > READY_GIVE_UP_MS && mEnter > 0) {
            mGaveUp = true;
            L.i("picture: HAL not ready after 20 s");
        }
        boolean disabled = !mReady || mBusy;
        for (Tile row : new Tile[]{mAf, mKst, mFit, mKstManual, mFocusManual}) {
            if (row != null) row.setBusy(disabled);
        }
        if (mReady && !mBusy) {
            mStIcon.type(Icon.DONE).color(Ui.OK);
            mSt.setText(s(R.string.pic_ready));
        } else if (mReady) {
            mStIcon.type(Icon.SPINNER).color(Ui.TEXT_DIM);
            mSt.setText(s(R.string.pic_busy));
        } else if (mGaveUp) {
            mStIcon.type(Icon.ALERT).color(Ui.TEXT_DIM);
            mSt.setText(s(R.string.pic_unavailable));
            if (mAf != null && mAf.isFocused()) mContinue.requestFocus();
        } else {
            mStIcon.type(Icon.SPINNER).color(Ui.TEXT_DIM);
            mSt.setText(s(R.string.pic_starting));
        }
    }
}
