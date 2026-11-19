package org.lumen.home;

import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.lumen.home.data.Card;
import org.lumen.home.data.HomeData;
import org.lumen.home.data.HomeLoader;
import org.lumen.home.data.TvpSource;
import org.lumen.home.img.ArtUrl;
import org.lumen.home.img.HeroArtPreload;
import org.lumen.home.img.ImageLoader;
import org.lumen.home.model.ArtRule;
import org.lumen.home.model.ContinueText;
import org.lumen.home.model.FocusMap;
import org.lumen.home.model.Greeting;
import org.lumen.home.model.HeroBind;
import org.lumen.home.model.HeroRotation;
import org.lumen.home.model.HomeSnapshot;
import org.lumen.home.model.Lanes;
import org.lumen.home.model.RowKind;
import org.lumen.home.model.Vitrina;
import org.lumen.home.proj.ProjectorBridge;
import org.lumen.home.search.SearchActivity;
import org.lumen.home.sky.DreamDefault;
import org.lumen.home.tmdb.Trending;
import org.lumen.home.ui.BackdropView;
import org.lumen.home.ui.CalmView;
import org.lumen.home.ui.ContextPanel;
import org.lumen.home.ui.DetailsView;
import org.lumen.home.watch.WatchLaunch;
import org.lumen.home.ui.ListPanel;
import org.lumen.home.ui.RowView;
import org.lumen.home.ui.ShelfView;
import org.lumen.home.ui.SourceMenu;
import org.lumen.home.ui.StatusView;
import org.lumen.home.ui.Theme;
import org.lumen.home.ui.TileView;
import org.lumen.home.weather.Weather;
import org.lumen.home.weather.Wmo;
import org.lumen.ui.Grid;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.Palette;
import org.lumen.ui.ThemeMode;
import org.lumen.ui.ThemeFade;
import org.lumen.ui.Tokens;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Lumen Home, variant A "Витрина" (owner approved 2026-11-18, mock home-v3 A_vitrina): the art of what to watch fills
 * the screen ({@link BackdropView}, {@link ArtRule}), the shelf bottom left ({@link ShelfView}: the title once, the
 * season line, the progress, Продолжить / Подробнее), one dock of app tiles, the input chips and the round buttons All
 * apps and Settings on the bottom line; everything on one screen. 1 or 2 Continue items are hero pages, 3 or more a
 * row of cards in the shelf. The All apps page shows every app in a grid over the veiled art. The D-pad is driven by
 * {@link FocusMap} (pure, host tested); views only show the state. Light theme by default, dark and Авто through the
 * shared switch of org.lumen.ui.
 */
public class HomeActivity extends Activity {
    private static final long LONG_PRESS_MS = 600;
    private static final int GRID_COLS = 6;
    /** The next hero page waits for its art this often (0.5 s each), then comes anyway. */
    private static final int HERO_WAITS = 6, HERO_WAIT_MS = 500;
    /** The calm screen's block (greeting, clock, day) stands where the shelf is. */
    private static final int CALM_TOP = 264;
    /** onCreate waits this long at most for the snapshot, so the first frame already shows the last screen. */
    private static final long SNAPSHOT_WAIT_MS = 120;

    private App mApp;
    private Handler mMain;
    private boolean mSafeMode, mStarted;
    private FrameLayout mRoot, mContent, mApps, mAppsGrid;
    private BackdropView mBackdrop;
    private ShelfView mShelf;
    private StatusView mStatus;
    private TextView mAppsTitle;
    private CalmView mCalm;
    private SourceMenu mSource;
    private DetailsView mDetails;
    private ContextPanel mPanel;
    private final FocusMap mMap = new FocusMap();
    private HomeData mData;
    /** One entry per plan row: the strip (null for the bottom line), its kind, its cards (bottom: chip cards). */
    private final ArrayList<RowView> mRows = new ArrayList<>();
    private final ArrayList<RowKind> mKinds = new ArrayList<>();
    private final ArrayList<List<Card>> mCards = new ArrayList<>();
    /** The bottom line: input chips (a null card is the "all sources" chip), then the round buttons. */
    private final ArrayList<TileView> mBottom = new ArrayList<>();
    private int mChips;
    private final ArrayList<TileView> mGrid = new ArrayList<>();
    private final ArrayList<ImageLoader.Request> mReqs = new ArrayList<>();
    private int mGridIndex, mHeroPage, mPage = FocusMap.H_HOME, mLoadSeq, mArtSeq;
    private boolean mOkDown, mOkLong, mNetworkPending, mHomePending;
    private int mMoving = -1;
    private long mLastMove;
    private List<Card> mPages = new ArrayList<>();
    private ThemeFade mTheme;
    private Weather.Now mWx;
    private String mHeroArtFor = "";
    private boolean mOnArt;
    private ContinueText.Words mWords;
    /** What the first frame shows: the last screen's data and weather reading, read on the data lane. */
    private FutureTask<Start> mStart;
    private boolean mShownOnce, mFreshOnce;

    private static final class Start {
        HomeData data;
        Weather.Now wx;
    }

    private final Runnable mLongPress = () -> {
        if (mOkDown && !mOkLong) {
            mOkLong = true;
            menuForFocus();
        }
    };
    private final Runnable mHeroTick = this::heroTick;
    private final Runnable mDwell = this::loadHeroArt;
    private final HeroBind<Bitmap> mHeroBind = new HeroBind<>(heroTarget());
    private final HeroRotation mRotation = new HeroRotation(Tokens.MS_HERO_PAGE);
    private final HeroRotation.State mRot = new HeroRotation.State();
    private HeroArtPreload mPreload;
    private int mHeroWaits;
    private final Runnable mReload = () -> load(mNetworkPending);
    private final Runnable mClockTick = this::tickClock;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mApp = App.get();
        mMain = new Handler(Looper.getMainLooper());
        if (mApp.isCrashLooping()) {
            mSafeMode = true;
            Log.w(App.TAG, "safe-mode");
            setContentView(new SafeModeView(this, () -> {
                mApp.clearCrashes();
                recreate();
            }));
            return;
        }
        readStart();
        Trace.beginSection("home:build");
        Theme.init(this);
        mWords = HomeLoader.words(getResources());
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.BG));
        build();
        Trace.endSection();
        showStart();
        mTheme = ThemeFade.activity(this, this::restyle);
        handleIntent(getIntent());
        mMain.postDelayed(mApp::clearCrashes, 60_000);
        final Context app = getApplicationContext();
        mApp.io().post(() -> DreamDefault.applyOnce(app));
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (!mSafeMode) recreate();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (!mSafeMode) handleIntent(i);
    }

    private void handleIntent(Intent i) {
        closeOverlays();
        endMove(false);
        if (i != null && Intent.ACTION_ALL_APPS.equals(i.getAction())) {
            setPage(FocusMap.H_APPS, false);
        } else {
            setPage(FocusMap.H_HOME, false);
            mMap.home();
            mHomePending = mData == null;
        }
        applyFocus(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mSafeMode) return;
        mStarted = true;
        mTheme.start();
        register();
        mApp.setHomeListener(() -> loadSoon(false));
        load(true);
        refreshWeather(false);
        tickClock();
        mRotation.input(SystemClock.uptimeMillis());
        scheduleHero();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSafeMode) return;
        WindowInsetsHide.apply(this);
        // back from another app or the system: every element repainted from the map, none keeps an old look
        applyFocus(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mSafeMode) return;
        mStarted = false;
        mTheme.stop();
        mApp.setHomeListener(null);
        unregister();
        mMain.removeCallbacks(mHeroTick);
        mMain.removeCallbacks(mClockTick);
        endMove(true);
    }

    // ------------------------------------------------------------------ build

    private void build() {
        mRoot = new FrameLayout(this);
        mRoot.setClipChildren(false);
        mBackdrop = new BackdropView(this);
        mRoot.addView(mBackdrop, match());
        mCalm = new CalmView(this);
        mCalm.setVisibility(View.GONE);
        mRoot.addView(mCalm, at(-2, -2, Grid.MARGIN, CALM_TOP));
        mShelf = new ShelfView(this, this::onClickView);
        FrameLayout.LayoutParams shp = new FrameLayout.LayoutParams(Theme.px(ShelfView.designWidth()), -2,
                android.view.Gravity.BOTTOM | android.view.Gravity.START);
        shp.leftMargin = Theme.px(Grid.MARGIN);
        shp.bottomMargin = Theme.px(Grid.H - Grid.SHELF_BOTTOM);
        mRoot.addView(mShelf, shp);
        mContent = new FrameLayout(this);
        mContent.setClipChildren(false);
        mContent.setClipToPadding(false);
        mRoot.addView(mContent, match());
        mApps = new FrameLayout(this);
        mApps.setClipChildren(false);
        mApps.setVisibility(View.GONE);
        mAppsGrid = new FrameLayout(this);
        mAppsGrid.setClipChildren(false);
        mAppsGrid.setClipToPadding(false);
        mApps.addView(mAppsGrid, match());
        mAppsTitle = new TextView(this);
        mAppsTitle.setText(R.string.tab_apps);
        mAppsGrid.addView(mAppsTitle, at(-2, -2, Grid.MARGIN, Grid.APPS_TITLE_Y));
        mRoot.addView(mApps, match());
        mStatus = new StatusView(this);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, Theme.px(Grid.STATUS_H), android.view.Gravity.END);
        sl.rightMargin = Theme.px(Grid.MARGIN);
        sl.topMargin = Theme.px(Grid.STATUS_Y);
        mRoot.addView(mStatus, sl);
        mSource = new SourceMenu(this);
        mRoot.addView(mSource, match());
        mDetails = new DetailsView(this);
        mRoot.addView(mDetails, match());
        mPanel = new ContextPanel(this);
        mRoot.addView(mPanel, match());
        styleAppsTitle();
        setContentView(mRoot);
        WindowInsetsHide.apply(this);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    /** Layout params at design x, y (sizes: real px or -1 / -2). */
    private static FrameLayout.LayoutParams at(int w, int h, int x, int y) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = Theme.px(x);
        lp.topMargin = Theme.px(y);
        return lp;
    }

    private void styleAppsTitle() {
        Theme.style(mAppsTitle, 44, 700, Theme.TEXT1, -1f);
        mAppsTitle.setLineHeight(Theme.px(52));
    }

    // ------------------------------------------------------------------ data

    /** Starts reading the snapshot and the cached weather on the data lane while the views are built. */
    private void readStart() {
        final Context c = getApplicationContext();
        mStart = new FutureTask<>(() -> {
            Trace.beginSection("home:snapshot");
            try {
                mApp.images(); // the image cache (its folders) is opened here, not on the main thread
                Start s = new Start();
                s.data = mApp.snapshot().read();
                s.wx = Weather.current(c);
                return s;
            } finally {
                Trace.endSection();
            }
        });
        mApp.lanes().post(Lanes.LOAD, mStart);
        View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().registerFrameCommitCallback(() -> mark("first frame", ""));
    }

    /** The first frame shows the last screen when the snapshot is ready in time, else it comes as soon as it is. */
    private void showStart() {
        Start s = null;
        try {
            s = mStart.get(SNAPSHOT_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
            // not ready yet (or unreadable): shown when it is, below
        }
        if (s != null) {
            applyStart(s, true);
            return;
        }
        mApp.lanes().post(Lanes.LOAD, () -> {
            Start late;
            try {
                late = mStart.get();
            } catch (Throwable t) {
                return;
            }
            mMain.post(() -> {
                if (!isDestroyed()) applyStart(late, false);
            });
        });
    }

    private void applyStart(Start s, boolean beforeFrame) {
        if (s.wx != null && mWx == null) mWx = s.wx;
        if (s.data != null && mData == null) {
            Trace.beginSection("home:bind snapshot");
            bind(s.data);
            Trace.endSection();
            shown();
            mark("snapshot shown", beforeFrame ? "before the first frame" : "after the first frame");
        } else {
            bindWeather();
        }
    }

    /** The screen shows real content for the first time in this process: the system's "fully drawn" mark. */
    private void shown() {
        if (mShownOnce) return;
        mShownOnce = true;
        try {
            reportFullyDrawn();
        } catch (Throwable ignored) {
        }
    }

    /** Startup marks in the log: ms since this process started (live_measure.sh reads them). */
    private static void mark(String what, String more) {
        long ms = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis();
        Log.i(App.TAG, "startup " + what + " +" + ms + "ms" + (more.isEmpty() ? "" : " " + more));
    }

    private void loadSoon(boolean network) {
        mNetworkPending |= network;
        mMain.removeCallbacks(mReload);
        mMain.postDelayed(mReload, 400);
    }

    /**
     * Fresh data from the data lane (local reads only); {@code network} also asks the network lane for a new showcase,
     * which reloads Home when it brings one. Data equal to what is shown changes nothing on screen.
     */
    private void load(boolean network) {
        mNetworkPending = false;
        final int seq = ++mLoadSeq;
        final Context c = getApplicationContext();
        final long t0 = SystemClock.uptimeMillis();
        mApp.lanes().post(Lanes.LOAD, () -> {
            HomeData d;
            Trace.beginSection("home:load");
            try {
                d = HomeLoader.load(c, mApp);
            } catch (Throwable t) {
                Log.e(App.TAG, "home load", t);
                return;
            } finally {
                Trace.endSection();
            }
            mMain.post(() -> {
                if (seq == mLoadSeq && !isDestroyed()) fresh(d, SystemClock.uptimeMillis() - t0);
            });
        });
        if (network) refreshShowcase();
    }

    private void fresh(HomeData d, long ms) {
        boolean same = HomeSnapshot.same(mData, d);
        if (!same) {
            Trace.beginSection("home:bind");
            bind(d);
            Trace.endSection();
            if (mData == d) mApp.lanes().post(Lanes.LOAD, () -> mApp.snapshot().write(d));
        }
        shown();
        if (!mFreshOnce) {
            mFreshOnce = true;
            mark("fresh data", "load=" + ms + "ms " + (same ? "same as shown" : "bound"));
        }
    }

    /** A new TMDB showcase when the cached one is old, on the network lane; Home reloads when one arrived. */
    private void refreshShowcase() {
        final Context c = getApplicationContext();
        mApp.lanes().once(Lanes.NET, "trending", () -> {
            if (HomeLoader.online(c) && Trending.refresh(c)) mApp.homeChanged();
        });
    }

    private void bind(HomeData d) {
        if (mMoving >= 0) return; // the order being moved stays until it is dropped
        String keepId = focusedId();
        RowKind keepKind = mMap.zone == FocusMap.ROWS && mMap.row < mKinds.size() ? mKinds.get(mMap.row) : null;
        int keepItem = mMap.item(mMap.row);
        mData = d;
        for (ImageLoader.Request r : mReqs) r.cancel();
        mReqs.clear();
        mContent.removeAllViews();
        mRows.clear();
        mKinds.clear();
        mCards.clear();
        mBottom.clear();
        ArrayList<FocusMap.Row> geo = new ArrayList<>();
        for (RowKind k : d.plan.rows) {
            switch (k) {
                case CONTINUE:
                    geo.add(strip(k, d.cont, Grid.UPNEXT_Y));
                    break;
                case APPS:
                    geo.add(strip(k, d.apps, Grid.DOCK_Y));
                    break;
                default:
                    geo.add(bottomLine(d));
            }
        }
        mMap.setLayout(d.plan.heroButtons() ? 2 : 0, geo.toArray(new FocusMap.Row[0]));
        if (mHomePending) {
            mHomePending = false;
            mMap.home();
        } else if (keepKind != null) restoreFocus(keepKind, keepId, keepItem);
        mPages = heroPages(d);
        if (mHeroPage >= Math.max(1, mPages.size())) mHeroPage = 0;
        if (!d.plan.calm && !d.plan.continueRow()) preload().pages(mPages, mHeroPage);
        FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) mShelf.getLayoutParams();
        sp.bottomMargin = Theme.px(Grid.H - Vitrina.shelfBottom(d.plan.continueRow()));
        mShelf.setLayoutParams(sp);
        mBackdrop.setStillY(Vitrina.stillY(d.plan.continueRow()));
        showHomeParts(mPage == FocusMap.H_HOME);
        buildGrid();
        mHeroArtFor = "";
        if (d.plan.calm) setArt(ArtRule.NONE, null, null, null, 0, 0, -1);
        applyFocus(false);
        tickClock();
        bindWeather();
    }

    /** A sliding row of the Витрина (Continue cards or the app dock) at design y. */
    private FocusMap.Row strip(RowKind kind, List<Card> cards, int y) {
        final int row = mRows.size();
        RowView rv = new RowView(this);
        rv.setTitle(null, null);
        rv.setHideBefore(true);
        ArrayList<TileView> tiles = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            TileView t = tile(kind, cards.get(i));
            final int idx = i;
            t.setOnClickListener(v -> onClickTile(row, idx));
            t.setOnLongClickListener(v -> {
                mMap.focusItem(row, idx);
                applyFocus(true);
                menuForFocus();
                return true;
            });
            tiles.add(t);
        }
        rv.setTiles(tiles);
        mContent.addView(rv, at(-1, -2, Grid.MARGIN, y));
        mRows.add(rv);
        mKinds.add(kind);
        mCards.add(cards);
        return rv.geometry();
    }

    /**
     * The bottom line: up to 3 input chips (with more inputs the last one opens the source menu), then the round
     * buttons All apps and Settings on the right margin.
     */
    private FocusMap.Row bottomLine(HomeData d) {
        final int row = mRows.size();
        int n = d.inputs.size();
        mChips = Vitrina.chips(n);
        ArrayList<Card> chipCards = new ArrayList<>();
        for (int i = 0; i < mChips; i++) {
            boolean more = Vitrina.moreChip(n) && i == mChips - 1;
            Card k = more ? null : d.inputs.get(i);
            TileView t = new TileView(this, TileView.CHIP);
            if (k == null) {
                t.setTitle(getString(R.string.source_title), null);
                t.setIcon(getDrawable(org.lumen.icons.R.drawable.lumen_ic_input).mutate());
            } else {
                boolean off = k.state == TvInputManager.INPUT_STATE_DISCONNECTED || k.state == Card.DEV_DISCONNECTED;
                t.setTitle(k.title, off ? getString(R.string.input_not_connected) : k.meta);
                int icon = Vitrina.console(k.meta) ? org.lumen.icons.R.drawable.lumen_ic_gamepad
                        : k.icon == 0 || k.icon == R.drawable.ic_hdmi ? org.lumen.icons.R.drawable.lumen_ic_hdmi : k.icon;
                t.setIcon(getDrawable(icon).mutate());
                t.setOff(off && k.kind == Card.INPUT);
            }
            chipCards.add(k);
            mContent.addView(t, at(Theme.px(Grid.CHIP_W), Theme.px(Grid.CHIP_H), Vitrina.chipX(i), Grid.CHIPS_Y));
            mBottom.add(t);
        }
        int[] icons = { org.lumen.icons.R.drawable.lumen_ic_apps, org.lumen.icons.R.drawable.lumen_ic_settings };
        int[] names = { R.string.tab_apps, R.string.settings_button };
        for (int i = 0; i < Vitrina.ROUNDS; i++) {
            TileView t = new TileView(this, TileView.ROUND);
            t.setTitle(getString(names[i]), null);
            t.setIcon(getDrawable(icons[i]).mutate());
            mContent.addView(t, at(Theme.px(Grid.ROUND), Theme.px(Grid.ROUND), Vitrina.roundX(i), Grid.CHIPS_Y));
            mBottom.add(t);
        }
        for (int i = 0; i < mBottom.size(); i++) {
            final int idx = i;
            mBottom.get(i).setOnClickListener(v -> onClickTile(row, idx));
        }
        mRows.add(null);
        mKinds.add(RowKind.INPUTS);
        mCards.add(chipCards);
        return new FocusMap.Row(Vitrina.bottomCenters(n));
    }

    private String focusedId() {
        if (mMap.zone != FocusMap.ROWS || mMap.row >= mCards.size()) return null;
        List<Card> l = mCards.get(mMap.row);
        int i = mMap.item(mMap.row);
        return i < l.size() && l.get(i) != null ? l.get(i).id : null;
    }

    private void restoreFocus(RowKind kind, String id, int item) {
        int r = mKinds.indexOf(kind);
        if (r < 0) return;
        List<Card> l = mCards.get(r);
        int idx = kind == RowKind.INPUTS ? item : 0;
        for (int i = 0; id != null && i < l.size(); i++) if (l.get(i) != null && id.equals(l.get(i).id)) idx = i;
        mMap.focusItem(r, idx);
    }

    private TileView tile(RowKind kind, Card k) {
        TileView t;
        if (kind == RowKind.CONTINUE) {
            t = new TileView(this, TileView.UPNEXT);
            t.setTitle(k.title, null);
            t.setPlaceholder(k.color);
            t.setProgress(k.progress);
            t.setTag(ContinueText.tag(k.wnType, mWords));
            image(t, k.image == null ? null : ArtUrl.card(k.image), ImageLoader.KIND_POSTER, k.pkg, k.title);
        } else {
            t = new TileView(this, TileView.DOCK);
            t.setTitle(k.title, null);
            image(t, k.image, ImageLoader.KIND_BANNER, k.pkg, k.title);
        }
        return t;
    }

    private void image(TileView t, String uri, int kind, String pkg, String label) {
        if (uri == null || uri.isEmpty()) return;
        // already in memory (a reload of the same art): set now, the tile never shows its placeholder in between
        Bitmap hit = mApp.images().peek(uri, Theme.px(t.designW()), Theme.px(t.designH()));
        if (hit != null) {
            t.setBitmap(hit);
            return;
        }
        ImageLoader.Request r = mApp.images().load(uri, Theme.px(t.designW()), Theme.px(t.designH()), kind, pkg, label,
                (b, col) -> {
                    if (b != null) t.setBitmap(b);
                });
        if (r != null) mReqs.add(r);
    }

    /** The All apps page: every visible app as a dock tile, 6 per line. */
    private void buildGrid() {
        for (TileView t : mGrid) mAppsGrid.removeView(t);
        mGrid.clear();
        if (mData == null) return;
        for (int i = 0; i < mData.apps.size(); i++) {
            Card k = mData.apps.get(i);
            TileView t = new TileView(this, TileView.DOCK);
            t.setTitle(k.title, null);
            image(t, k.image, ImageLoader.KIND_BANNER, k.pkg, k.title);
            final int idx = i;
            t.setOnClickListener(v -> {
                mGridIndex = idx;
                applyFocus(true);
                Launch.open(this, mData.apps.get(idx));
            });
            mAppsGrid.addView(t, at(Theme.px(Grid.DOCK_W), Theme.px(Grid.DOCK_H), Vitrina.gridX(i, GRID_COLS),
                    Vitrina.gridY(i, GRID_COLS)));
            mGrid.add(t);
        }
        mGridIndex = Math.max(0, Math.min(mGridIndex, mGrid.size() - 1));
    }

    // ------------------------------------------------------------------ focus

    /** Paints every focusable element from {@link FocusMap}: exactly one shows the focus, whatever came before. */
    private void applyFocus(boolean animate) {
        mMap.page = mPage;
        for (int i = 0; i < mGrid.size(); i++) mGrid.get(i).setFocusedState(mMap.gridFocused(i, mGridIndex), animate);
        mShelf.setButtonFocus(mMap.heroFocus(), animate);
        for (int r = 0; r < mKinds.size(); r++) {
            RowView rv = mRows.get(r);
            List<TileView> tiles = rv == null ? mBottom : rv.tiles();
            for (int i = 0; i < tiles.size(); i++) tiles.get(i).setFocusedState(mMap.itemFocused(r, i), animate);
            if (rv != null && mPage == FocusMap.H_HOME) rv.scrollTo(mMap.first(r), animate);
        }
        if (mPage == FocusMap.H_APPS) {
            float y = -Theme.pxf(Vitrina.gridOffset(mGridIndex, mGrid.size(), GRID_COLS));
            mAppsGrid.animate().translationY(y).setDuration(animate && Theme.animations() ? Theme.PAGE_SCROLL_MS : 0)
                    .setInterpolator(Theme.EMPHASIZED).start();
            scheduleHero();
            return;
        }
        heroContent(animate);
        scheduleHero();
    }

    /**
     * What the shelf shows: its pages (1 or 2 Continue items, then TMDB; without TMDB the first Continue item), or the
     * focused (else the last focused) card of the Continue row.
     */
    private void heroContent(boolean animate) {
        if (mData == null || mData.plan.calm) return;
        if (mData.plan.continueRow()) {
            Card k = mData.cont.get(Math.min(mMap.item(0), mData.cont.size() - 1));
            mShelf.setPages(0, 1);
            if (mHeroBind.item(k, false, 0, 1) || mHeroArtFor.isEmpty()) {
                mMain.removeCallbacks(mDwell);
                mMain.postDelayed(mDwell, animate ? Theme.BACKDROP_DWELL_MS : 0);
            }
            return;
        }
        if (mPages.isEmpty()) return;
        mShelf.setPages(mHeroPage % mPages.size(), mPages.size());
        if (!mShelf.swapping()) bindHeroPage();
        mMain.removeCallbacks(mDwell);
        loadHeroArt();
    }

    /** The shelf's texts for the hero page current now ({@link HeroBind} skips the same card). */
    private void bindHeroPage() {
        int n = mPages.size();
        if (n == 0) return;
        int page = mHeroPage % n;
        Card k = mPages.get(page);
        if (k.kind == Card.FEATURE) mHeroBind.feature(k, page, n);
        else mHeroBind.item(k, true, page, n);
    }

    /** The shelf behind {@link HeroBind}: texts per card, the badge slot, icons loaded once per app. */
    private HeroBind.Target<Bitmap> heroTarget() {
        return new HeroBind.Target<Bitmap>() {
            @Override
            public void item(Card k, boolean buttons, int page, int pages) {
                mShelf.showItem(k.appLabel, getString(R.string.row_continue), k.title, k.meta, k.left, k.progress,
                        buttons, k.progress >= 0);
            }

            @Override
            public void feature(Card k, int page, int pages) {
                mShelf.showFeature(getString(R.string.now_popular), k.title, k.meta, k.watchLabel);
            }

            @Override
            public void badge(Bitmap b, boolean slot) {
                mShelf.setBadge(b, slot);
            }

            @Override
            public void loadBadge(String pkg) {
                mApp.images().load("icon:" + pkg, Theme.px(32), Theme.px(32), ImageLoader.KIND_ICON, pkg, null,
                        (b, c) -> mHeroBind.badgeLoaded(pkg, b));
            }
        };
    }

    /** The card the shelf stands for right now (null: none). */
    private Card heroCard() {
        if (mData == null || mData.plan.calm) return null;
        if (mData.plan.continueRow()) return mData.cont.get(Math.min(mMap.item(0), mData.cont.size() - 1));
        if (mPages.isEmpty()) return null;
        return mPages.get(mHeroPage % mPages.size());
    }

    /** The hero's pages: the Continue items the plan gives it, then TMDB; without both the first Continue item. */
    private static List<Card> heroPages(HomeData d) {
        ArrayList<Card> p = new ArrayList<>();
        for (int i = 0; i < d.plan.heroContinue && i < d.cont.size(); i++) p.add(d.cont.get(i));
        p.addAll(d.hero);
        if (p.isEmpty() && !d.cont.isEmpty()) p.add(d.cont.get(0));
        return p;
    }

    /**
     * The art for the shelf's card by {@link ArtRule}: the real sizes of its art (published art first, then its other
     * art) decide between the full screen (sharp, 1280 wide or more) and the frame (blurred field, sharp frame at or
     * below its own size); no art leaves the plain field.
     */
    private void loadHeroArt() {
        Card k = heroCard();
        if (k == null) return;
        String[] uris = HeroArtPreload.uris(k, Theme.px(Grid.W));
        String key = uris[0] + "|" + (uris.length > 1 ? uris[1] : "");
        if (key.equals(mHeroArtFor)) {
            mBackdrop.setProgress(k.progress);
            return;
        }
        mHeroArtFor = key;
        final int seq = ++mArtSeq;
        HeroArtPreload.Art ready = preload().now(k);
        if (ready != null) {
            // preloaded: the page changes in this frame, together with its text
            setArt(ready.mode, ready.uri, ready.sharp, ready.blur, ready.w, ready.h, ready.mode == ArtRule.NONE ? -1 : k.progress);
            return;
        }
        final int[] sizes = new int[uris.length * 2];
        final int[] left = { 0 };
        for (String u : uris) if (u != null && !u.isEmpty()) left[0]++;
        if (left[0] == 0) {
            setArt(ArtRule.NONE, null, null, null, 0, 0, -1);
            return;
        }
        if (!mOnArt) ink(true);
        for (int i = 0; i < uris.length; i++) {
            if (uris[i] == null || uris[i].isEmpty()) continue;
            final int at = i;
            mApp.images().probe(uris[i], k.pkg, (w, h) -> {
                sizes[2 * at] = w;
                sizes[2 * at + 1] = h;
                if (--left[0] == 0) showArt(seq, k, uris, sizes);
            });
        }
    }

    private void showArt(int seq, Card k, String[] uris, int[] sizes) {
        if (seq != mArtSeq) return;
        int boxW = Theme.px(Grid.span(Grid.STILL_COLS)), boxH = Math.round(boxW * 9f / 16f);
        ArtRule.Pick p = ArtRule.pick(sizes, boxW, boxH);
        Log.i(App.TAG, "hero art " + p + " " + k.id);
        if (p.mode == ArtRule.NONE) {
            setArt(ArtRule.NONE, null, null, null, 0, 0, -1);
            return;
        }
        String uri = uris[p.index];
        boolean full = p.mode == ArtRule.FULL;
        int w = full ? Theme.px(Grid.W) : p.w, h = full ? Theme.px(Grid.H) : p.h;
        mApp.images().loadHero(uri, w, h, k.pkg, (b, c) -> {
            if (seq == mArtSeq) setArt(p.mode, uri, b, mApp.images().peekBlur(uri), p.w, p.h, k.progress);
        });
        mApp.images().loadBlur(uri, k.pkg, (b, c) -> {
            if (seq == mArtSeq) mBackdrop.setBlur(mShownSharp, b);
        });
    }

    private Bitmap mShownSharp;

    private void setArt(int mode, String uri, Bitmap sharp, Bitmap blur, int w, int h, int progress) {
        boolean animate = mStarted && mBackdrop.isLaidOut();
        mShownSharp = sharp;
        mShelf.setTitleColumns(mode == ArtRule.FRAME ? Grid.STILL_COL - 2 : Grid.SHELF_COLS);
        mBackdrop.set(mode, sharp, blur, w, h, progress, animate);
        ink(mode != ArtRule.NONE);
    }

    /** White ink over art, the theme's on the bare field (the Apps page is always bare). */
    private void ink(boolean onArt) {
        mOnArt = onArt;
        mShelf.setInk(onArt);
        mStatus.setInk(onArt && mPage == FocusMap.H_HOME);
    }

    /** The next or previous hero page: dots at once, the text swaps on its own, the art changes ({@link BackdropView}). */
    private void pageHero(int delta) {
        if (mData == null || mPages.size() < 2) return;
        int n = mPages.size();
        mHeroPage = (mHeroPage + delta + n) % n;
        mShelf.setPages(mHeroPage, n);
        mShelf.swapText(this::bindHeroPage);
        loadHeroArt();
        preload().moved(mHeroPage);
        mRotation.shown(SystemClock.uptimeMillis() + Tokens.MS_HERO_FADE);
        scheduleHero();
    }

    /** The hero turns to its next page by itself ({@link HeroRotation}: 8 s, paused on the buttons and after keys). */
    private void heroTick() {
        rotationState();
        long d = mRotation.delay(SystemClock.uptimeMillis(), mRot);
        if (d < 0) return; // stopped: a key, the focus or Home coming back plans again
        if (d > 0) {
            mMain.postDelayed(mHeroTick, d);
            return;
        }
        int next = (mHeroPage + 1) % mPages.size();
        if (!preload().ready(next) && mHeroWaits++ < HERO_WAITS) {
            preload().moved(mHeroPage);
            mMain.postDelayed(mHeroTick, HERO_WAIT_MS);
            return;
        }
        mHeroWaits = 0;
        pageHero(1);
    }

    /** Made on first use, after the data lane opened the image cache (never on the main thread at start). */
    private HeroArtPreload preload() {
        if (mPreload == null) {
            int boxW = Theme.px(Grid.span(Grid.STILL_COLS));
            mPreload = new HeroArtPreload(mApp.images(), Theme.px(Grid.W), Theme.px(Grid.H), boxW,
                    Math.round(boxW * 9f / 16f));
        }
        return mPreload;
    }

    private void scheduleHero() {
        mMain.removeCallbacks(mHeroTick);
        rotationState();
        long d = mRotation.delay(SystemClock.uptimeMillis(), mRot);
        if (d >= 0) mMain.postDelayed(mHeroTick, d);
    }

    private void rotationState() {
        mRot.started = mStarted;
        mRot.home = mPage == FocusMap.H_HOME;
        mRot.panel = overlayOpen();
        mRot.followsRow = mData == null || mData.plan.calm || mData.plan.continueRow();
        mRot.heroFocus = mMap.zone == FocusMap.HERO;
        mRot.animations = Theme.animations();
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        mRot.touchExploration = am != null && am.isTouchExplorationEnabled();
        mRot.pages = mPages.size();
    }

    // ------------------------------------------------------------------ pages

    private void showHomeParts(boolean home) {
        boolean calm = mData != null && mData.plan.calm;
        mContent.setVisibility(home ? View.VISIBLE : View.GONE);
        mShelf.setVisibility(home && !calm ? View.VISIBLE : View.GONE);
        mCalm.setVisibility(home && calm ? View.VISIBLE : View.GONE);
        mStatus.setVisibility(home && calm ? View.GONE : View.VISIBLE);
    }

    private void setPage(int page, boolean animate) {
        mPage = page;
        mMap.page = page;
        boolean apps = page == FocusMap.H_APPS;
        mApps.setVisibility(apps ? View.VISIBLE : View.GONE);
        showHomeParts(!apps);
        mBackdrop.setVeil(apps ? 1f : 0f, animate);
        mStatus.setInk(!apps && mOnArt);
        if (apps) {
            mGridIndex = 0;
            if (animate && Theme.animations()) {
                mApps.setAlpha(0f);
                mApps.animate().alpha(1f).setDuration(Theme.PAGE_SCROLL_MS).start();
            }
        }
    }

    // ------------------------------------------------------------------ keys

    private static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER
                || k == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static int dir(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return FocusMap.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return FocusMap.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return FocusMap.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return FocusMap.RIGHT;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                return FocusMap.BACK;
            default:
                return -1;
        }
    }

    private boolean overlayOpen() {
        return mPanel.isOpen() || mDetails.isOpen() || mSource.isOpen();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (mSafeMode) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down) {
            mRotation.input(SystemClock.uptimeMillis());
            scheduleHero();
        }
        if (mPanel.isOpen()) {
            mPanel.onKey(code, e);
            return true;
        }
        int d = dir(code);
        if (mDetails.isOpen()) {
            if (down && (d == FocusMap.LEFT || d == FocusMap.RIGHT)) mDetails.key(d == FocusMap.RIGHT);
            else if (!down && isOk(code)) mDetails.act();
            else if (!down && d == FocusMap.BACK) mDetails.close();
            return true;
        }
        if (mSource.isOpen()) {
            if (down && (d == FocusMap.UP || d == FocusMap.DOWN)) mSource.key(d == FocusMap.UP ? 0 : 1);
            else if (!down && isOk(code)) mSource.pick();
            else if (!down && (d == FocusMap.BACK || d == FocusMap.LEFT)) mSource.close();
            return true;
        }
        if (code == KeyEvent.KEYCODE_TV_INPUT) {
            if (!down) openSource();
            return true;
        }
        if (isOk(code)) {
            if (down && e.getRepeatCount() == 0) {
                mOkDown = true;
                mOkLong = false;
                mMain.postDelayed(mLongPress, LONG_PRESS_MS);
            } else if (!down) {
                mMain.removeCallbacks(mLongPress);
                boolean wasLong = mOkLong;
                mOkDown = false;
                mOkLong = false;
                if (!wasLong) activate();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (!down) menuForFocus();
            return true;
        }
        if (d < 0) return super.dispatchKeyEvent(e);
        if (d == FocusMap.BACK ? down : !down) return true; // BACK acts on release, arrows on press
        long now = SystemClock.uptimeMillis();
        if (e.getRepeatCount() > 0 && now - mLastMove < Tokens.MS_KEY_REPEAT) return true;
        mLastMove = now;
        if (mMoving >= 0) {
            moveKey(d);
            return true;
        }
        if (mPage == FocusMap.H_APPS) {
            appsKey(d);
            return true;
        }
        int r = mMap.move(d);
        if (r == FocusMap.HERO_PREV) pageHero(-1);
        else if (r == FocusMap.HERO_NEXT) pageHero(1);
        else if (r == FocusMap.MOVED) applyFocus(true);
        return true;
    }

    private void appsKey(int d) {
        if (d == FocusMap.BACK) {
            backFromApps();
            return;
        }
        int n = FocusMap.grid(mGridIndex, mGrid.size(), GRID_COLS, d);
        if (n < 0 || n == mGridIndex) return;
        mGridIndex = n;
        applyFocus(true);
    }

    /** BACK on the Apps page: Home, the focus back on the All apps button. */
    private void backFromApps() {
        setPage(FocusMap.H_HOME, true);
        int r = mKinds.indexOf(RowKind.INPUTS);
        if (r >= 0) mMap.focusItem(r, mChips + Vitrina.ROUND_APPS);
        applyFocus(true);
    }

    private void onClickView(View v) {
        Object tag = v.getTag();
        if (!(tag instanceof Integer)) return;
        mMap.zone = FocusMap.HERO;
        mMap.heroBtn = (Integer) tag - 10;
        applyFocus(true);
        activate();
    }

    private void onClickTile(int row, int idx) {
        mMap.focusItem(row, idx);
        applyFocus(true);
        activate();
    }

    private void activate() {
        if (mMoving >= 0) {
            endMove(true);
            return;
        }
        if (mPage == FocusMap.H_APPS) {
            if (mData != null && mGridIndex < mData.apps.size()) Launch.open(this, mData.apps.get(mGridIndex));
            return;
        }
        if (mMap.zone == FocusMap.HERO) {
            Card k = heroCard();
            if (k == null) return;
            if (mMap.heroBtn == 0) watch(k);
            else details(k);
            return;
        }
        openRowItem(mMap.row, mMap.item(mMap.row));
    }

    private void openRowItem(int row, int i) {
        if (row >= mKinds.size()) return;
        if (mKinds.get(row) != RowKind.INPUTS) {
            if (i < mCards.get(row).size()) Launch.open(this, mCards.get(row).get(i));
            return;
        }
        if (i < mChips) {
            Card k = mCards.get(row).get(i);
            if (k == null) openSource();
            else openInput(k);
        } else if (i - mChips == Vitrina.ROUND_APPS) {
            setPage(FocusMap.H_APPS, true);
            applyFocus(false);
        } else {
            ProjectorBridge.showPanel(this, null);
        }
    }

    private void watch(Card k) {
        if (k.kind == Card.FEATURE) WatchLaunch.open(this, k);
        else Launch.open(this, k);
    }

    private void details(Card k) {
        String body = k.desc == null || k.desc.isEmpty() ? k.meta : k.desc;
        if (k.kind != Card.FEATURE) {
            mDetails.open(k.title, k.meta + (k.left.isEmpty() ? "" : "  " + k.left), body, "", "", null, () -> watch(k));
            return;
        }
        String rating = k.rating.isEmpty() ? "" : getString(R.string.details_rating, k.rating);
        String where = k.where.isEmpty() ? "" : getString(R.string.details_where, k.where);
        mDetails.open(k.title, k.meta, body, rating, where, k.watchLabel, () -> watch(k));
    }

    private void openInput(Card k) {
        if (k.kind == Card.TILE) ProjectorBridge.showPanel(this, k.intent);
        else Launch.open(this, k);
    }

    // ------------------------------------------------------------------ source menu

    private void openSource() {
        if (mData == null) return;
        ArrayList<SourceMenu.Entry> l = new ArrayList<>();
        boolean any = false;
        for (Card k : mData.inputs) any |= k.showing;
        l.add(new SourceMenu.Entry(R.drawable.ic_input, "Lumen OS", getString(R.string.source_home_sub), !any, false, null));
        int focus = 0;
        for (Card k : mData.inputs) {
            if (k.showing) focus = l.size();
            l.add(new SourceMenu.Entry(k.icon != 0 ? k.icon : R.drawable.ic_hdmi, k.title, k.meta, k.showing, false,
                    () -> openInput(k)));
        }
        if (focus == 0 && l.size() > 1) focus = 1;
        l.add(new SourceMenu.Entry(R.drawable.ic_settings, getString(R.string.tile_projector_settings), null, false, true,
                () -> ProjectorBridge.showPanel(this, "section:general")));
        mSource.open(l, focus);
    }

    // ------------------------------------------------------------------ menus

    private void menuForFocus() {
        if (mData == null) return;
        if (mPage == FocusMap.H_APPS) {
            if (mGridIndex < mData.apps.size()) appMenu(mData.apps.get(mGridIndex), mGridIndex, true);
            return;
        }
        if (mMap.zone == FocusMap.ROWS && mMap.row < mKinds.size()) {
            RowKind k = mKinds.get(mMap.row);
            int i = mMap.item(mMap.row);
            if (k == RowKind.APPS && i < mData.apps.size()) {
                appMenu(mData.apps.get(i), i, false);
                return;
            }
            if (k == RowKind.CONTINUE && i < mData.cont.size()) {
                continueMenu(mData.cont.get(i));
                return;
            }
            if (k == RowKind.INPUTS && i < mChips) {
                openSource();
                return;
            }
        }
        homeMenu();
    }

    private ListPanel.Item item(int icon, int text, Runnable r) {
        return ListPanel.Item.action(icon, getString(text), r);
    }

    private void appMenu(Card k, int index, boolean grid) {
        ArrayList<ListPanel.Item> it = new ArrayList<>();
        it.add(item(R.drawable.ic_open, R.string.menu_open, () -> {
            mPanel.close();
            Launch.open(this, k);
        }));
        it.add(item(R.drawable.ic_move, R.string.menu_move, () -> {
            mPanel.close();
            startMove(index, grid);
        }));
        it.add(item(R.drawable.ic_eye_off, R.string.menu_hide_app, () -> {
            mPanel.close();
            mApp.prefs().toggleInSet(Prefs.K_HIDDEN_APPS, k.intent, true);
            loadSoon(false);
        }));
        it.add(item(R.drawable.ic_info, R.string.menu_app_info, () -> {
            mPanel.close();
            Launch.start(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + k.pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "app info");
        }));
        if (!k.system) {
            it.add(item(R.drawable.ic_trash, R.string.menu_uninstall, () -> {
                mPanel.close();
                Launch.start(this, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + k.pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "uninstall");
            }));
        }
        mPanel.open(k.title, null, it);
    }

    private void continueMenu(Card k) {
        ArrayList<ListPanel.Item> it = new ArrayList<>();
        it.add(item(R.drawable.ic_play, R.string.menu_open, () -> {
            mPanel.close();
            Launch.open(this, k);
        }));
        it.add(item(R.drawable.ic_close, R.string.menu_remove_continue, () -> {
            mPanel.close();
            final Context c = getApplicationContext();
            mApp.io().post(() -> TvpSource.hideProgram(c, k));
            loadSoon(false);
        }));
        it.add(item(R.drawable.ic_info, R.string.menu_app_info, () -> {
            mPanel.close();
            Launch.start(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + k.pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "app info");
        }));
        mPanel.open(k.title, k.appLabel, it);
    }

    /** MENU on Home: the theme (Светлая, Тёмная, Авто), "Continue watching", weather, hidden apps. */
    private void homeMenu() {
        ArrayList<ListPanel.Item> it = new ArrayList<>();
        ListPanel.Item head = new ListPanel.Item();
        head.kind = ListPanel.HEADER;
        head.text = getString(R.string.theme_title);
        it.add(head);
        ThemeMode cur = LumenTheme.mode(this);
        ThemeMode[] modes = { ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO };
        int[] names = { R.string.theme_light, R.string.theme_dark, R.string.theme_auto };
        int[] icons = { R.drawable.wx_clear_day, R.drawable.wx_clear_night, R.drawable.ic_timer };
        for (int i = 0; i < modes.length; i++) {
            final ThemeMode m = modes[i];
            ListPanel.Item x = ListPanel.Item.action(icons[i], getString(names[i]), () -> {
                mPanel.close();
                if (!LumenTheme.setMode(this, m)) Log.w(App.TAG, "theme not stored");
                mTheme.apply(LumenTheme.palette(this));
            }).kind(ListPanel.CHECK).on(m == cur);
            if (m == ThemeMode.AUTO) x.value(getString(R.string.theme_auto_sub));
            it.add(x);
        }
        Prefs p = mApp.prefs();
        it.add(ListPanel.Item.action(R.drawable.ic_history, getString(R.string.cz_continue), () -> {
            p.putBool(Prefs.K_CONTINUE_HOME, !p.continueOnHome());
            mPanel.close();
            loadSoon(false);
        }).kind(ListPanel.TOGGLE).on(p.continueOnHome()));
        it.add(ListPanel.Item.action(R.drawable.ic_cloud, getString(R.string.weather_show), () -> {
            p.putBool(Prefs.K_WEATHER_ON, !p.weatherOn());
            mPanel.close();
            refreshWeather(false);
        }).kind(ListPanel.TOGGLE).on(p.weatherOn()));
        int hidden = mData == null ? 0 : mData.hidden.size();
        it.add(ListPanel.Item.action(R.drawable.ic_eye_off, getString(R.string.cz_hidden_apps), this::hiddenApps)
                .value(Integer.toString(hidden)));
        mPanel.open(getString(R.string.customize_home), null, it);
    }

    private void hiddenApps() {
        ArrayList<ListPanel.Item> it = new ArrayList<>();
        if (mData == null || mData.hidden.isEmpty()) {
            it.add(ListPanel.Item.info(R.drawable.ic_eye, getString(R.string.hidden_none)));
        } else {
            for (Card k : mData.hidden) {
                it.add(ListPanel.Item.action(R.drawable.ic_eye, k.title, () -> {
                    Prefs p = mApp.prefs();
                    p.toggleInSet(Prefs.K_HIDDEN_APPS, k.intent, false);
                    p.toggleInSet(Prefs.K_HIDDEN_APPS, k.pkg, false);
                    p.toggleInSet(HomeLoader.K_UNHIDDEN_APPS, k.pkg, true);
                    mPanel.close();
                    loadSoon(false);
                }).value(getString(R.string.menu_show_app)));
            }
        }
        mPanel.push(getString(R.string.cz_hidden_apps), null, it);
    }

    // ------------------------------------------------------------------ moving an app

    private void startMove(int index, boolean grid) {
        mMoving = index;
        TileView t = grid ? mGrid.get(index) : appsTile(index);
        if (t != null) t.setMoving(true);
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_SHORT).show();
    }

    private RowView appsRow() {
        int r = mKinds.indexOf(RowKind.APPS);
        return r < 0 ? null : mRows.get(r);
    }

    private TileView appsTile(int i) {
        RowView rv = appsRow();
        return rv == null ? null : rv.tile(i);
    }

    private void moveKey(int d) {
        boolean grid = mPage == FocusMap.H_APPS;
        int n = mData.apps.size();
        int to;
        if (d == FocusMap.BACK) {
            endMove(true);
            return;
        }
        if (grid) to = FocusMap.grid(mMoving, n, GRID_COLS, d);
        else to = d == FocusMap.LEFT ? mMoving - 1 : d == FocusMap.RIGHT ? mMoving + 1 : mMoving;
        if (to < 0 || to >= n || to == mMoving) return;
        Card c = mData.apps.remove(mMoving);
        mData.apps.add(to, c);
        mMoving = to;
        saveOrder();
        int keep = mMoving;
        boolean wasGrid = grid;
        mMoving = -1;
        bind(mData);
        mMoving = keep;
        if (wasGrid) {
            mGridIndex = keep;
            mGrid.get(keep).setMoving(true);
        } else {
            mMap.focusItem(mKinds.indexOf(RowKind.APPS), keep);
            TileView t = appsTile(keep);
            if (t != null) t.setMoving(true);
        }
        applyFocus(true);
    }

    private void saveOrder() {
        ArrayList<String> order = new ArrayList<>();
        for (Card k : mData.apps) order.add(k.intent);
        mApp.prefs().putList(Prefs.K_FAVORITES, order);
        mApp.prefs().putBool(Prefs.K_FAVORITES_SET, true);
    }

    private void endMove(boolean reload) {
        if (mMoving < 0) return;
        mMoving = -1;
        for (TileView t : mGrid) t.setMoving(false);
        RowView rv = appsRow();
        if (rv != null) for (TileView t : rv.tiles()) t.setMoving(false);
        if (reload) loadSoon(false);
    }

    private void closeOverlays() {
        if (mPanel.isOpen()) mPanel.close();
        if (mDetails.isOpen()) mDetails.close();
        if (mSource.isOpen()) mSource.close();
    }

    // ------------------------------------------------------------------ theme

    /** The theme changed (lumen-ui ThemeFade: under the cross fade while shown, at once while hidden): re-style in place. */
    private void restyle(Palette p) {
        if (p == Theme.P || mRoot == null) return;
        Theme.apply(p);
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.BG));
        mBackdrop.restyle();
        mShelf.restyle();
        mStatus.restyle();
        mCalm.restyle();
        styleAppsTitle();
        for (RowView r : mRows) if (r != null) r.restyle();
        for (TileView t : mBottom) t.restyle();
        for (TileView t : mGrid) t.restyle();
        applyFocus(false);
    }

    // ------------------------------------------------------------------ clock and weather

    private void tickClock() {
        mMain.removeCallbacks(mClockTick);
        Date now = new Date();
        String clock = DateFormat.format(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm", now).toString();
        mStatus.setClock(clock);
        Calendar cal = Calendar.getInstance();
        int g = Greeting.of(cal.get(Calendar.HOUR_OF_DAY));
        int gr = g == Greeting.MORNING ? R.string.greet_morning : g == Greeting.DAY ? R.string.greet_day
                : g == Greeting.EVENING ? R.string.greet_evening : R.string.greet_night;
        Locale loc = Locale.getDefault();
        String day = DateFormat.format(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), now).toString();
        if (!day.isEmpty()) day = day.substring(0, 1).toUpperCase(loc) + day.substring(1);
        mCalm.set(getString(gr), clock, day);
        long ms = 60_000 - System.currentTimeMillis() % 60_000 + 50;
        if (mStarted) mMain.postDelayed(mClockTick, ms);
    }

    private void refreshWeather(boolean force) {
        final Context c = getApplicationContext();
        if (!mApp.prefs().weatherOn()) {
            mWx = null;
            bindWeather();
            return;
        }
        // the stored reading at once (data lane), then a new one from the network lane when it is due; a request still
        // waiting for a blocked server is not stacked again
        mApp.lanes().post(Lanes.LOAD, () -> postWeather(Weather.current(c)));
        mApp.lanes().once(Lanes.NET, "weather", () -> {
            try {
                if (!Weather.refresh(c, false, force)) return;
            } catch (Throwable t) {
                Log.w(App.TAG, "weather: " + t);
                return;
            }
            postWeather(Weather.current(c));
        });
    }

    /** Shows a reading unless a newer one is already shown (the two lanes may answer in either order). */
    private void postWeather(Weather.Now n) {
        mMain.post(() -> {
            if (n != null && mWx != null && n.time < mWx.time) return;
            mWx = n;
            bindWeather();
        });
    }

    private void bindWeather() {
        Weather.Now n = mApp.prefs().weatherOn() ? mWx : null;
        if (n == null || n.temp == null) {
            mStatus.setWeather(0, null);
            mCalm.setWeather(0, null);
            return;
        }
        int icon = Wmo.icon(n.code, n.day);
        mStatus.setWeather(icon, n.temp);
        String cond = getString(Wmo.label(n.code));
        Locale loc = Locale.getDefault();
        if (!cond.isEmpty()) cond = cond.substring(0, 1).toUpperCase(loc) + cond.substring(1);
        mCalm.setWeather(icon, cond + ", " + n.temp);
    }

    // ------------------------------------------------------------------ system events

    private BroadcastReceiver mTimeRx, mPkgRx, mMediaRx;
    private ContentObserver mTvpObs;
    private ConnectivityManager.NetworkCallback mNet;

    private void register() {
        mTimeRx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                tickClock();
            }
        };
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(mTimeRx, tf, Context.RECEIVER_EXPORTED);
        mPkgRx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                loadSoon(false);
            }
        };
        IntentFilter pf = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(mPkgRx, pf, Context.RECEIVER_EXPORTED);
        mMediaRx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                loadSoon(false);
            }
        };
        IntentFilter mf = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        mf.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mf.addAction(Intent.ACTION_MEDIA_REMOVED);
        mf.addDataScheme("file");
        registerReceiver(mMediaRx, mf, Context.RECEIVER_EXPORTED);
        mTvpObs = new ContentObserver(mMain) {
            @Override
            public void onChange(boolean self) {
                loadSoon(false);
            }
        };
        try {
            getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, mTvpObs);
            getContentResolver().registerContentObserver(TvContract.PreviewPrograms.CONTENT_URI, true, mTvpObs);
        } catch (Throwable t) {
            Log.w(App.TAG, "tvp observer: " + t);
        }
        mNet = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network n) {
                mMain.post(() -> {
                    loadSoon(true);
                    refreshWeather(false);
                });
            }
        };
        try {
            getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(mNet);
        } catch (Throwable t) {
            mNet = null;
        }
    }

    private void unregister() {
        try {
            if (mTimeRx != null) unregisterReceiver(mTimeRx);
            if (mPkgRx != null) unregisterReceiver(mPkgRx);
            if (mMediaRx != null) unregisterReceiver(mMediaRx);
            if (mTvpObs != null) getContentResolver().unregisterContentObserver(mTvpObs);
            if (mNet != null) getSystemService(ConnectivityManager.class).unregisterNetworkCallback(mNet);
        } catch (Throwable ignored) {
        }
        mTimeRx = mPkgRx = mMediaRx = null;
        mTvpObs = null;
        mNet = null;
    }

    /** Full screen: no system bars over Home. */
    private static final class WindowInsetsHide {
        static void apply(Activity a) {
            try {
                a.getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
            } catch (Throwable ignored) {
            }
        }
    }
}
