package org.lumen.setup;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.LocaleList;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.app.LocalePicker;

import org.lumen.setup.net.TzLookup;
import org.lumen.setup.net.NetMon;
import org.lumen.setup.net.TimeZones;
import org.lumen.setup.steps.AliceStep;
import org.lumen.setup.steps.DoneStep;
import org.lumen.setup.steps.GoogleStep;
import org.lumen.setup.steps.LauncherStep;
import org.lumen.setup.steps.NetworkStep;
import org.lumen.setup.steps.PictureStep;
import org.lumen.setup.steps.RemoteStep;
import org.lumen.setup.steps.Step;
import org.lumen.setup.steps.ThemeStep;
import org.lumen.setup.steps.WelcomeStep;
import org.lumen.setup.ui.AuroraView;
import org.lumen.setup.ui.Dots;
import org.lumen.setup.ui.Icon;
import org.lumen.setup.ui.Sheet;
import org.lumen.setup.ui.Ui;
import org.lumen.setup.ui.Wordmark;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.Palette;
import org.lumen.ui.ThemeFade;
import org.lumen.ui.Tokens;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lumen OS first-run setup: the single HOME activity (priority 10, priv-app) that hosts every step
 * as a View. Re-entry rules (4.10), crash guard (F1), the hidden
 * "hold BACK 8 s" skip (F2), language switching without a relaunch (4.1) and the finish (4.9).
 */
public class SetupActivity extends Activity implements NetMon.Listener {
    public static final String EXTRA_SAFE_FINISH = "safe_finish";
    private static final long CRASH_WINDOW_MS = 180_000;
    private static final int CRASH_LIMIT = 3;
    private static final long SKIP_HOLD_MS = 8_000;
    private static final long REMOTE_PREFETCH_MS = 3_000;

    public SetupState state;
    public NetMon net;
    public final Handler main = new Handler(Looper.getMainLooper());
    public final ExecutorService bg = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "z9x-setup-bg");
        t.setDaemon(true);
        return t;
    });

    // cached async state (main thread)
    /** null = not known yet, then the SetupBridge answered ping or not. */
    public Boolean bridgeOk;
    /** The successful ping reply (null until then, or when the bridge is missing): optional fields such as "names". */
    public Bundle bridgeInfo;
    public boolean remoteConnected;
    public Bundle aliceInfo;
    private boolean mGeoStarted;

    // views
    /** The window's content: holds the skeleton, two of them for a moment while a new theme fades in. */
    private FrameLayout mFrame;
    private FrameLayout mRoot;
    private AuroraView mAurora;
    private Wordmark mWordmark;
    private Dots mDots;
    private LinearLayout mSplit;
    private LinearLayout mLeft;
    private TextView mTitle;
    private TextView mSub;
    private FrameLayout mLeftExtra;
    private FrameLayout mLeftHead;
    private LinearLayout mTop;
    private TextView mFoot;
    private FrameLayout mRight;
    private FrameLayout mFull;
    private View mContent;
    private View mWorking;

    private final List<Step> mSteps = new ArrayList<>();
    private int mCur = -1;
    private long mStepEnter;
    private final Deque<Integer> mHistory = new ArrayDeque<>();
    private boolean mLauncherOnly;
    private boolean mFinishing;
    /** Between onResume and onPause: the steps built late (first-start locale switch) start what onResume would. */
    private boolean mResumed;
    /** Follows the shared theme live (lumen-ui ThemeFade): Home, the quick menu and the wizard switch together. */
    private ThemeFade mTheme;
    private boolean mReduced;
    private boolean mUiBuilt;
    private boolean mStarted;
    private Sheet mSheet;
    private View mFocusBeforeSheet;
    private boolean mBackFired;
    private final Runnable mBackHold = () -> {
        mBackFired = true;
        showSkipDialog();
    };
    private LocaleList mLocales;
    private Runnable mLocaleThen;
    private long mInputBlockedUntil;
    private boolean mReceiverOn;

    private final Runnable mLocaleTimeout = () -> {
        Runnable r = mLocaleThen;
        mLocaleThen = null;
        if (r != null) r.run();
    };

    private final Runnable mRemotePrefetch = new Runnable() {
        @Override
        public void run() {
            Step s = current();
            if (s == null || mFinishing || !Boolean.TRUE.equals(bridgeOk)) return;
            String id = s.id();
            if (!WelcomeStep.ID.equals(id) && !ThemeStep.ID.equals(id) && !NetworkStep.ID.equals(id)) return;
            Bridge.remoteState(SetupActivity.this, r -> {
                if (Bridge.ok(r)) setRemoteConnected(r.getBoolean("connected", false));
            });
            main.postDelayed(this, REMOTE_PREFETCH_MS);
        }
    };

    private final Runnable mKill = () -> {
        L.i("finish step=8 kill");
        Process.killProcess(Process.myPid());
    };

    private final BroadcastReceiver mBridgeRcv = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Bundle ex = i.getExtras();
            if (ex == null) return;
            if ("remote".equals(ex.getString("what")) && ex.containsKey("connected")) {
                setRemoteConnected(ex.getBoolean("connected"));
            }
            Step s = current();
            if (s != null) s.onBridgeEvent(ex);
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(null);
        Ui.init(this);
        mReduced = Ui.reduced(this);
        mTheme = ThemeFade.activity(this, this::restyle);
        state = new SetupState(this);
        mLocales = getResources().getConfiguration().getLocales();
        L.i("create t=" + L.sinceStart() + " state=" + state.state() + " step=" + state.step());

        Intent in = getIntent();
        if (in != null && in.getBooleanExtra(EXTRA_SAFE_FINISH, false)) {
            int uid = getLaunchedFromUid();
            if (uid == Process.ROOT_UID || uid == Process.SHELL_UID) {
                safeFinish("adb");
                return;
            }
            L.w("safe_finish ignored from uid " + uid);
        }

        String st = state.state();
        boolean flags = Sys.setupFlagsComplete(this);
        if (SetupState.STATE_FINISHING.equals(st)) {
            L.i("re-entry: finish was interrupted, running it again");
            runFinish(Finisher.FULL, "resume");
            return;
        }
        if (flags) {
            if (state.launcherApplied() || SetupState.STATE_DONE.equals(st)) {
                L.i("re-entry: flags set, launcher applied -> tail");
                runFinish(Finisher.TAIL, "tail");
                return;
            }
            // upgrade over existing data (or a finish interrupted between flags and launcher):
            // a one-time launcher choice, no full setup
            if (Sys.hasLumenHome(this) && Sys.hasClassic(this)) {
                L.i("re-entry: flags set, launcher not applied -> launcher step only");
                mLauncherOnly = true;
                mSteps.add(new LauncherStep(this));
                buildUi();
                next(null);
                return;
            }
            runFinish(Finisher.LAUNCHER_ONLY, "single-launcher");
            return;
        }
        if (SetupState.STATE_DONE.equals(st)) state.setState(SetupState.STATE_UI); // flags were reset

        final int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, 0);
        installCrashRecorder(boot);
        int crashes = state.recentCrashes(boot, SystemClock.elapsedRealtime(), CRASH_WINDOW_MS);
        if (crashes >= CRASH_LIMIT) {
            L.w("!!! safe-finish crashloop crashes=" + crashes);
            safeFinish("crashloop");
            return;
        }
        if (Sys.debuggable() && "1".equals(Sys.prop("debug.z9x.setup.crash", "0"))) {
            throw new RuntimeException("debug.z9x.setup.crash=1 (test T13)");
        }

        // The first screen in the image's first-start language (devices/_image/first_start_prop.txt
        // 2026-11-13): a run that has shown no step yet does not start in a language /data carries from
        // before. The steps are built only after the switch (or its 1.5 s timeout), so no screen shows the old one.
        String first = FirstLocale.toApply(state.step(), mLocales.isEmpty() ? "" : mLocales.get(0).toLanguageTag(),
                Sys.prop("ro.product.locale", ""));
        if (first != null) {
            L.i("first start: locale " + mLocales.toLanguageTags() + " -> " + first + " before the first screen");
            setLocale(Locale.forLanguageTag(first), this::startSteps);
            return;
        }
        startSteps();
    }

    /** The full setup: every step in order, the UI, the stored step (once per instance). */
    private void startSteps() {
        if (mUiBuilt || mFinishing || isFinishing() || isDestroyed()) return;
        // Lumen OS 1.0: the remote comes first (2026-10-08): the XGIMI remote is Bluetooth only,
        // so nothing else can be done with it before it is paired. Always shown, never skipped silently.
        // The stock XGIMI wizard has the same order (vendor bootwizard_release_848 of the HB and the Z9X:
        // the welcome animation, then pair_remote_page in every config with a remote, no language page in
        // any) and the remote step is drawn in the first-start language (FirstLocale above).
        mSteps.add(new RemoteStep(this));
        mSteps.add(new WelcomeStep(this));
        // the theme right after the language (2026-11-19): Светлая by default, Тёмная, Авто, with a live preview
        mSteps.add(new ThemeStep(this));
        mSteps.add(new NetworkStep(this));
        mSteps.add(new GoogleStep(this));
        mSteps.add(new PictureStep(this));
        mSteps.add(new LauncherStep(this));
        mSteps.add(new AliceStep(this));
        mSteps.add(new DoneStep(this));
        buildUi();
        resume(state.step());
        if (mResumed) {
            startAfterFirstFrame();
            Step s = current();
            if (s != null) s.onResume();
        }
    }

    private static boolean sCrashRecorder;

    /** Records every uncaught exception of this process (crash guard F1), then lets it crash. */
    private void installCrashRecorder(int boot) {
        if (sCrashRecorder) return;
        sCrashRecorder = true;
        final SetupState st = state;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                st.recordCrash(boot, SystemClock.elapsedRealtime());
                L.e("crash recorded", e);
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_SAFE_FINISH, false)) {
            // the launching uid of a running singleTask instance is the original one: use am start -S
            L.w("safe_finish on a running instance ignored; use: am start -S -n org.lumen.setup/.SetupActivity --ez safe_finish true");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mTheme.start();
    }

    @Override
    protected void onStop() {
        mTheme.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mFinishing) return;
        mResumed = true;
        if (!mReceiverOn) {
            try {
                registerReceiver(mBridgeRcv, new IntentFilter(Bridge.ACTION_STATE), Bridge.PERMISSION, main,
                        Context.RECEIVER_EXPORTED);
                mReceiverOn = true;
            } catch (RuntimeException e) {
                L.w("bridge receiver", e);
            }
        }
        startAfterFirstFrame();
        if (mAurora != null) mAurora.poke();
        Step s = current();
        if (s != null) s.onResume();
    }

    /** Background work once the UI is built and shown (onResume, or startSteps after a first-start locale switch). */
    private void startAfterFirstFrame() {
        if (!mStarted && mUiBuilt) {
            mStarted = true;
            // nothing heavy before the first frame: start background work after it
            Choreographer.getInstance().postFrameCallback(t -> main.post(this::startBackgroundWork));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
        if (mReceiverOn) {
            try {
                unregisterReceiver(mBridgeRcv);
            } catch (RuntimeException ignored) {
            }
            mReceiverOn = false;
        }
        Step s = current();
        if (s != null && !mFinishing) s.onPause();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(mRemotePrefetch);
        if (net != null) net.stop();
        Step s = current();
        if (s != null && !mFinishing) s.onExit();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (mFinishing && !hasFocus) {
            // HOME is drawing over us: leave no cached process behind (SPEC 4.9 step 8)
            main.removeCallbacks(mKill);
            main.postDelayed(mKill, 1500);
        }
    }

    private void startBackgroundWork() {
        L.i("first frame t=" + L.sinceStart());
        net = new NetMon(this, this);
        net.start();
        bg.execute(() -> {
            try {
                WifiManager wm = getSystemService(WifiManager.class);
                if (wm != null) {
                    if (!wm.isWifiEnabled()) {
                        L.i("wifi off -> on");
                        wm.setWifiEnabled(true);
                    } else {
                        wm.startScan();
                    }
                }
            } catch (RuntimeException e) {
                L.w("wifi kick", e);
            }
        });
        Bridge.ping(this, r -> {
            bridgeOk = Bridge.ok(r) && r.getInt("version", 0) >= Bridge.API;
            bridgeInfo = bridgeOk ? r : null;
            L.i("bridge ping ok=" + bridgeOk + " projector=" + (r == null ? "?" : r.get("projectorVersion")));
            refreshDots();
            if (!bridgeOk) return;
            Bridge.remoteState(this, rs -> {
                if (Bridge.ok(rs)) setRemoteConnected(rs.getBoolean("connected", false));
            });
            Bridge.feature(this, "alice", fr -> {
                aliceInfo = Bridge.ok(fr) && fr.getBoolean("enabled", false) ? fr : null;
                refreshDots();
            });
            main.postDelayed(mRemotePrefetch, REMOTE_PREFETCH_MS);
        });
    }

    /** Bridge / remote step: the XGIMI remote is connected (its keys work) or not. Main thread. */
    public void setRemoteConnected(boolean c) {
        if (c && !remoteConnected) L.i("remote connected (bridge)");
        remoteConnected = c;
        if (c) state.setRemoteSeen(true);
    }

    /** Availability of later steps changed (bridge, network): recount the progress dots. */
    private void refreshDots() {
        if (mUiBuilt && mCur >= 0 && !mFinishing) updateDots();
    }

    @Override
    public void onNetChanged() {
        if (net != null && net.online() && !mGeoStarted) startGeo();
        refreshDots();
        Step s = current();
        if (s != null) s.onNetChanged();
    }

    /** IP time zone, once per process, in the background; never blocks the UI. */
    private void startGeo() {
        mGeoStarted = true;
        if (state.tzManual()) return;
        Thread t = new Thread(() -> {
            TzLookup.Result r = TzLookup.lookup(getApplicationContext());
            main.post(() -> {
                if (r != null) state.setGeo(r.json, r.country, System.currentTimeMillis());
                if (state.tzManual()) return;
                if (r == null || r.tz == null) {
                    L.i("tz source=none id=" + java.util.TimeZone.getDefault().getID());
                    return;
                }
                if (TimeZones.apply(this, r.tz)) {
                    state.setTzSource(r.src);
                    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(r.tz));
                    L.i("tz source=" + r.src + " id=" + r.tz);
                    Step s = current();
                    if (s != null) s.onNetChanged(); // lets the summary re-read the zone
                }
            });
        }, "z9x-setup-geo");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ UI skeleton

    /**
     * The 2.0 skeleton: the field, a top bar at y 56 (logo 52 / 30 at x 96, "Шаг N из M" at the right
     * margin), SPLIT = left column x 96 width 660 (title from y 300, top aligned) and right column x 880 width 944
     * (content centred between the bar and the bottom margin), WIDE and FULL fill the screen themselves.
     */
    private void buildUi() {
        mUiBuilt = true;
        mRoot = new FrameLayout(this);
        mAurora = new AuroraView(this);
        mRoot.addView(mAurora, match());

        mRoot.setClipChildren(false);
        mSplit = new LinearLayout(this);
        mSplit.setOrientation(LinearLayout.HORIZONTAL);
        mSplit.setClipChildren(false);   // focused rows scale and cast their shadow past the column edge
        mSplit.setClipToPadding(false);
        mSplit.setPaddingRelative(Ui.px(Tokens.MARGIN), 0, Ui.px(Tokens.MARGIN), 0);
        mLeft = Ui.vbox(this);
        mLeft.setGravity(Gravity.TOP);
        mLeft.setPadding(0, Ui.px(300), 0, Ui.px(110));
        mLeftHead = new FrameLayout(this);
        mTitle = Ui.title(this);
        mSub = Ui.subtitle(this);
        mLeftExtra = new FrameLayout(this);
        mLeft.addView(mLeftHead, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mLeft.addView(mTitle, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mLeft.addView(mSub, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 22));
        mLeft.addView(mLeftExtra, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 28));
        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(Ui.px(660), ViewGroup.LayoutParams.MATCH_PARENT);
        lpLeft.setMarginEnd(Ui.px(Tokens.SETUP_SPLIT_X - Tokens.MARGIN - 660));
        mSplit.addView(mLeft, lpLeft);
        mRight = new FrameLayout(this);
        mRight.setClipChildren(false);
        mRight.setClipToPadding(false);
        mSplit.addView(mRight, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        mRoot.addView(mSplit, match());

        mFull = new FrameLayout(this);
        mFull.setClipChildren(false);
        mFull.setVisibility(View.GONE);
        mRoot.addView(mFull, match());

        mTop = Ui.hbox(this);
        mTop.setPaddingRelative(Ui.px(Tokens.MARGIN), 0, Ui.px(Tokens.MARGIN), 0);
        mWordmark = new Wordmark(this);
        mTop.addView(mWordmark);
        mTop.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        mDots = new Dots(this);
        mTop.addView(mDots);
        FrameLayout.LayoutParams lt = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(56),
                Gravity.TOP);
        lt.topMargin = Ui.px(56);
        mRoot.addView(mTop, lt);
        mFoot = Ui.text(this, 20, Ui.TEXT3, Ui.W_CAPTION);
        FrameLayout.LayoutParams lf = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lf.setMarginStart(Ui.px(Tokens.MARGIN));
        lf.bottomMargin = Ui.px(56);
        mRoot.addView(mFoot, lf);
        if (mFrame == null) {
            mFrame = new FrameLayout(this);
            setContentView(mFrame);
        }
        mFrame.addView(mRoot, match());
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Spinner + "Getting things ready…" over everything (finish, headless re-runs). */
    private void showWorking() {
        if (mRoot == null) {
            mRoot = new FrameLayout(this);
            mAurora = new AuroraView(this);
            mRoot.addView(mAurora, match());
            setContentView(mRoot);
        }
        if (mWorking != null) return;
        LinearLayout w = Ui.vbox(this);
        w.setGravity(Gravity.CENTER);
        w.setBackgroundColor(org.lumen.ui.Palette.alpha(Ui.BG, 0.92f));
        w.setClickable(true);
        Icon sp = new Icon(this, Icon.SPINNER).color(Ui.TEXT);
        w.addView(sp, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        TextView t = Ui.text(this, 26, Ui.TEXT_DIM, Ui.W_BODY_L);
        t.setText(R.string.working);
        w.addView(t, Ui.lpTop(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 28));
        mWorking = w;
        w.setAlpha(0f);
        mRoot.addView(w, match());
        w.animate().alpha(1f).setDuration(200).start();
        w.requestFocus();
    }

    // ------------------------------------------------------------------ navigation

    public Step current() {
        return mCur >= 0 && mCur < mSteps.size() ? mSteps.get(mCur) : null;
    }

    public Step stepById(String id) {
        for (Step s : mSteps) if (s.id().equals(id)) return s;
        return null;
    }

    private void resume(String id) {
        // Power cut mid-setup (F9): resume at the stored step, or at the nearest earlier step that is
        // available right now (the Google step needs the network, which is not known before NetMon).
        int target = -1;
        if (id != null) {
            for (int i = 0; i < mSteps.size(); i++) {
                if (mSteps.get(i).id().equals(id)) target = i;
            }
            while (target > 0 && !mSteps.get(target).available()) target--;
        }
        if (target <= 0) {
            next(null);
            return;
        }
        for (int i = 0; i < target; i++) {
            if (mSteps.get(i).available() && !mSteps.get(i).autoSkip()) mHistory.push(i);
        }
        L.i("resume at step=" + id);
        show(target, true);
    }

    /** Leave the current step forward. result = next | skip | auto (null for the first show). */
    public void next(String result) {
        if (mFinishing) return;
        Step cur = current();
        if (cur != null) {
            logExit(cur, result == null ? "next" : result);
            state.clearCrashes();
        }
        int i = mCur + 1;
        while (i < mSteps.size()) {
            Step s = mSteps.get(i);
            if (!s.available()) {
                i++;
                continue;
            }
            if (s.autoSkip()) {
                L.i("step=" + s.id() + " exit result=auto dur=0");
                i++;
                continue;
            }
            break;
        }
        if (i >= mSteps.size()) {
            runFinish(mLauncherOnly ? Finisher.LAUNCHER_ONLY : Finisher.FULL, null);
            return;
        }
        if (mCur >= 0) mHistory.push(mCur);
        show(i, true);
    }

    /** Previous visible step (none on the first one); steps with nothing left to do are passed over. */
    public void back() {
        while (!mHistory.isEmpty()) {
            int p = mHistory.pop();
            if (mSteps.get(p).available() && mSteps.get(p).revisitable()) {
                logExit(current(), "back");
                show(p, false);
                return;
            }
        }
    }

    /** Jump to a step from the summary (e.g. the home-screen row on Done); BACK returns. */
    public void goTo(String id) {
        for (int i = 0; i < mSteps.size(); i++) {
            if (mSteps.get(i).id().equals(id) && mSteps.get(i).available()) {
                logExit(current(), "goto");
                mHistory.push(mCur);
                show(i, true);
                return;
            }
        }
    }

    private void logExit(Step s, String result) {
        if (s == null) return;
        L.i("step=" + s.id() + " exit result=" + result + " dur=" + (SystemClock.elapsedRealtime() - mStepEnter));
    }

    private void show(int index, boolean forward) {
        Step old = current();
        if (old != null) old.onExit();
        dismissSheet();
        Step s = mSteps.get(index);
        mCur = index;
        mStepEnter = SystemClock.elapsedRealtime();
        state.setStep(s.id());
        L.i("step=" + s.id() + " enter t=" + L.sinceStart());
        mInputBlockedUntil = SystemClock.uptimeMillis() + 220;

        View content = s.createContent();
        int mode = s.layout();
        applyMode(mode);
        setTitles(s, old != null);
        ViewGroup target = mode == Step.FULL ? mFull : mRight;
        swapContent(content, target, mode == Step.FULL ? -1 : s.rightTop(), forward, old != null);
        updateDots();
        s.onEnter();
        focusStep(s, content);
        if (old == null && index == 0) playIntro();
    }

    private void applyMode(int mode) {
        boolean full = mode == Step.FULL;
        mFull.setVisibility(full ? View.VISIBLE : View.GONE);
        mSplit.setVisibility(full ? View.INVISIBLE : View.VISIBLE);
        mWordmark.setVisibility(current().showsLogo() ? View.VISIBLE : View.INVISIBLE);
        mDots.setVisibility(View.VISIBLE);
        mAurora.setAuroraEnabled(!full);
        mLeft.setVisibility(mode == Step.SPLIT ? View.VISIBLE : View.GONE);
    }

    /** The current step's left column again (a step whose page names something there, the Wi-Fi password). */
    public void refreshTitles() {
        Step s = current();
        if (s != null && mUiBuilt && !mFinishing) setTitles(s, true);
    }

    private void setTitles(Step s, boolean animate) {
        CharSequence t = s.title(), sub = s.subtitle(), foot = s.footer();
        View extra = s.leftExtra(), head = s.leftHead();
        Runnable apply = () -> {
            mTitle.setMaxLines(s.titleLines());
            mTitle.setText(t);
            mSub.setText(sub);
            mSub.setVisibility(sub == null ? View.GONE : View.VISIBLE);
            mLeft.setPadding(0, Ui.px(s.leftTop()), 0, Ui.px(110));
            mLeftHead.removeAllViews();
            if (head != null) mLeftHead.addView(head);
            mLeftExtra.removeAllViews();
            if (extra != null) mLeftExtra.addView(extra);
            mFoot.setText(foot);
            mFoot.setVisibility(foot == null ? View.GONE : View.VISIBLE);
        };
        if (!animate || s.layout() != Step.SPLIT) {
            apply.run();
            mLeft.setAlpha(1f);
            return;
        }
        mLeft.animate().cancel();
        mLeft.animate().alpha(0f).setDuration(110).withEndAction(() -> {
            apply.run();
            mLeft.animate().alpha(1f).setDuration(200).setInterpolator(Ui.decel()).start();
        }).start();
    }

    /** The right column of SPLIT: centred content sits between these design y (top bar, bottom margin). */
    private static final int RIGHT_TOP = 150, RIGHT_BOTTOM = 90;

    /**
     * Puts a step's content in place: FULL and WIDE fill the screen, SPLIT content starts at the step's {@code top} (a
     * design y) or, with -1, fills the column between the top bar and the bottom margin.
     */
    private void swapContent(View content, ViewGroup target, int top, boolean forward, boolean animate) {
        View old = mContent;
        mContent = content;
        float dir = forward ? 1f : -1f;
        if (Ui.isRtl(mRoot)) dir = -dir;
        if (old != null) {
            ViewGroup op = (ViewGroup) old.getParent();
            if (old instanceof ViewGroup) ((ViewGroup) old).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            old.animate().cancel();
            old.animate().alpha(0f).translationX(mReduced ? 0f : -Ui.pxf(Tokens.STEP_SLIDE) * dir)
                    .setDuration(mReduced ? Tokens.MS_REDUCED : Tokens.MS_SETUP_STEP / 2).withLayer()
                    .setInterpolator(Ui.standard())
                    .withEndAction(() -> {
                        if (op != null) op.removeView(old);
                    }).start();
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        if (target == mRight) {
            lp.topMargin = Ui.px(top >= 0 ? top : RIGHT_TOP);
            lp.bottomMargin = Ui.px(RIGHT_BOTTOM);
        }
        target.addView(content, lp);
        if (animate) {
            content.setAlpha(0f);
            content.setTranslationX(mReduced ? 0f : Ui.pxf(Tokens.STEP_SLIDE) * dir);
            content.animate().alpha(1f).translationX(0f).setDuration(mReduced ? Tokens.MS_REDUCED : Tokens.MS_SETUP_STEP)
                    .setStartDelay(60).withLayer()
                    .setInterpolator(Ui.decel()).start();
        }
    }

    private void focusStep(Step s, View content) {
        content.post(() -> {
            if (mContent != content) return;
            View f = s.initialFocus();
            if (f != null && f.isAttachedToWindow()) {
                f.requestFocus();
            } else {
                content.requestFocus();
            }
        });
    }

    /** "Шаг N из M": steps this unit lacks are not counted, a step that skipped itself counts as passed (Progress). */
    private void updateDots() {
        if (mLauncherOnly) {
            mDots.setVisibility(View.INVISIBLE);
            return;
        }
        boolean[] av = new boolean[mSteps.size()];
        for (int i = 0; i < av.length; i++) av[i] = mSteps.get(i).available();
        Progress p = Progress.of(av, mCur);
        mDots.set(p.count(), p.current());
    }

    /** The wordmark grows from the centre (last boot-animation frame) to its corner. */
    private void playIntro() {
        mSplit.setAlpha(0f);
        mDots.setAlpha(0f);
        mRoot.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRoot.getViewTreeObserver().removeOnPreDrawListener(this);
                float sc = 2.6f;
                int[] loc = new int[2];
                mWordmark.getLocationInWindow(loc);
                float w = mWordmark.getWidth() * sc, h = mWordmark.getHeight() * sc;
                mWordmark.setPivotX(0);
                mWordmark.setPivotY(0);
                mWordmark.setScaleX(sc);
                mWordmark.setScaleY(sc);
                mWordmark.setTranslationX((mRoot.getWidth() - w) / 2f - loc[0]);
                mWordmark.setTranslationY((mRoot.getHeight() - h) / 2f - loc[1]);
                mWordmark.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                        .setStartDelay(400).setDuration(500).setInterpolator(Ui.standard()).start();
                mSplit.animate().alpha(1f).setStartDelay(700).setDuration(420).setInterpolator(Ui.decel()).start();
                mDots.animate().alpha(1f).setStartDelay(800).setDuration(300).start();
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ sheets

    public void showSheet(Sheet s, View focus) {
        dismissSheet();
        mSheet = s;
        mFocusBeforeSheet = getCurrentFocus();
        mSplit.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mFull.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        s.setOnDismiss(() -> {
            if (mSheet == s) mSheet = null;
            mSplit.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mFull.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            View f = mFocusBeforeSheet;
            mFocusBeforeSheet = null;
            if (f != null && f.isAttachedToWindow()) f.requestFocus();
            else if (mContent != null) mContent.requestFocus();
        });
        s.show(mRoot, focus);
    }

    public void dismissSheet() {
        if (mSheet != null) mSheet.dismiss();
        mSheet = null;
    }

    private void showSkipDialog() {
        if (mFinishing || mLauncherOnly) return;
        L.i("skip dialog (BACK held " + SKIP_HOLD_MS / 1000 + " s)");
        Sheet sh = new Sheet(this, getString(R.string.skip_setup_title), getString(R.string.skip_setup_text));
        android.widget.TextView cancel = sh.addButton(getString(R.string.action_cancel), true, v -> sh.dismiss());
        sh.addButton(getString(R.string.skip_setup_confirm), false, v -> {
            sh.dismiss();
            safeFinish("user");
        });
        showSheet(sh, cancel);
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (mAurora != null) mAurora.poke();
        if (mFinishing) return true;
        Step cur = mSheet == null ? current() : null;
        if (cur != null && cur.onKey(e)) return true;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            // hidden skip: BACK held 8 s. A timer from the first DOWN works for IR repeats,
            // HID repeats and remotes that send no repeats at all.
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    mBackFired = false;
                    main.removeCallbacks(mBackHold);
                    main.postDelayed(mBackHold, SKIP_HOLD_MS);
                }
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_UP) {
                main.removeCallbacks(mBackHold);
                if (mBackFired) {
                    mBackFired = false;
                    return true;
                }
                if (!e.isCanceled()) handleBack();
                return true;
            }
            return true;
        }
        if (SystemClock.uptimeMillis() < mInputBlockedUntil
                && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void handleBack() {
        if (mSheet != null) {
            dismissSheet();
            return;
        }
        if (SystemClock.uptimeMillis() < mInputBlockedUntil) return;
        Step s = current();
        if (s != null && s.onBack()) return;
        back();
    }

    // ------------------------------------------------------------------ language

    /** LocalePicker.updateLocales off the main thread; then the config change re-binds the strings. */
    public void setLocale(Locale l, Runnable then) {
        L.i("locale -> " + l.toLanguageTag());
        mLocaleThen = then;
        main.removeCallbacks(mLocaleTimeout);
        bg.execute(() -> {
            try {
                LocalePicker.updateLocales(new LocaleList(l));
            } catch (Throwable t) {
                L.w("updateLocales", t);
            }
            main.postDelayed(mLocaleTimeout, 1500);
        });
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (Ui.displayChanged(this)) {
            // Lumen OS 1.0.1: the UI resolution (1080p / 2K / 4K) changed while setup is shown. configChanges
            // keeps this activity, but the skeleton and every step are sized in px of the old scale: build
            // everything again at the stored step, the same path as after a power cut (F9).
            android.util.DisplayMetrics m = getResources().getDisplayMetrics();
            L.i("display changed -> " + m.widthPixels + "x" + m.heightPixels + " " + m.densityDpi + " dpi");
            Ui.init(this);
            if (mUiBuilt && !mFinishing) {
                mLocaleThen = null;                      // the new instance starts at the stored step
                main.removeCallbacksAndMessages(null);   // nothing of this instance runs after it
                recreate();
                return;
            }
        }
        LocaleList ll = c.getLocales();
        if (ll.equals(mLocales)) return;
        mLocales = ll;
        L.i("locale changed -> " + ll.toLanguageTags() + " (re-bind, no relaunch)");
        Ui.init(this);
        rebindCurrent();
        if (mLocaleThen != null) {
            main.removeCallbacks(mLocaleTimeout);
            Runnable r = mLocaleThen;
            mLocaleThen = null;
            main.postDelayed(r, 280);
        }
    }

    /**
     * The theme step stored a choice: the wizard takes it at once through the shared cross fade, then {@code then}
     * runs (the next step comes in the new colours). Home and the quick menu follow the same setting.
     */
    public void retheme(Runnable then) {
        mTheme.apply(LumenTheme.palette(this));
        then.run();
    }

    /**
     * The theme changed (lumen-ui ThemeFade, under the frozen old picture while shown): the palette is read again and
     * the skeleton and the current step are built anew in it, without their entry animations. The focus goes back to
     * the view at the same place, else to the step's first one.
     */
    private void restyle(Palette p) {
        Ui.init(this);
        if (!mUiBuilt || mFinishing || mRoot == null) return;
        Step s = current();
        if (s == null) return;
        int[] focus = ThemeFade.focusPath(mRoot);
        dismissSheet();
        s.onExit();
        mContent = null;
        FrameLayout old = mRoot;
        buildUi();
        if (mFrame != null) mFrame.removeView(old);
        L.i("theme on screen -> " + (Ui.P.dark ? "dark" : "light"));
        View content = s.createContent();
        int mode = s.layout();
        applyMode(mode);
        setTitles(s, false);
        swapContent(content, mode == Step.FULL ? mFull : mRight, mode == Step.FULL ? -1 : s.rightTop(), true, false);
        updateDots();
        s.onEnter();
        content.post(() -> {
            if (mContent != content || ThemeFade.refocus(mRoot, focus)) return;
            View f = s.initialFocus();
            if (f != null && f.isAttachedToWindow()) f.requestFocus();
            else content.requestFocus();
        });
    }

    /** Rebuild the current step's views in the new language with a 200 ms cross-fade. */
    private void rebindCurrent() {
        Step s = current();
        if (s == null || mContent == null) return;
        dismissSheet();
        s.onExit();
        View old = mContent;
        ViewGroup parent = (ViewGroup) old.getParent();
        View content = s.createContent();
        mContent = content;
        setTitles(s, false);
        if (parent != null) {
            parent.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(200).start();
            if (old instanceof ViewGroup) ((ViewGroup) old).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            old.animate().alpha(0f).setDuration(200).withEndAction(() -> parent.removeView(old)).start();
        }
        mLeft.setAlpha(0f);
        mLeft.animate().alpha(1f).setDuration(200).start();
        s.onEnter();
        focusStep(s, content);
    }

    // ------------------------------------------------------------------ finish

    /** [Start] on the Done screen. */
    public void finishSetup() {
        runFinish(Finisher.FULL, null);
    }

    /**
     * Safe finish (crash loop, hidden BACK combo, adb rescue): the same end state with defaults:
     * the current locale, no Google, the launcher chosen so far, else Lumen Home, else classic.
     */
    public void safeFinish(String reason) {
        L.w("safe-finish reason=" + reason);
        if (state.launcher() == null) {
            String d = Sys.defaultLauncher(this);
            if (d != null) state.setLauncher(d);
        }
        runFinish(Finisher.FULL, "safe-" + reason);
    }

    private void runFinish(int mode, String reason) {
        if (mFinishing) return;
        mFinishing = true;
        Step s = current();
        if (s != null) s.onExit();
        dismissSheet();
        main.removeCallbacks(mRemotePrefetch);
        showWorking();
        final Context app = getApplicationContext();
        bg.execute(() -> {
            try {
                Finisher.runBackground(app, state, mode, reason);
            } catch (Throwable t) {
                L.e("finish failed", t);
            }
            main.post(this::finishTail);
        });
    }

    /** Steps 7-8 on the main thread. */
    private void finishTail() {
        Finisher.disableSelf(this);
        try {
            startActivity(Finisher.homeIntent());
        } catch (RuntimeException e) {
            L.w("start HOME", e);
        }
        finishAndRemoveTask();
        overridePendingTransition(0, android.R.anim.fade_out);
        main.postDelayed(mKill, 5000);
    }
}
