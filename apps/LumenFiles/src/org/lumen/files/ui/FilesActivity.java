// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.lumen.files.R;
import org.lumen.files.data.DlnaFinder;
import org.lumen.files.data.DlnaFs;
import org.lumen.files.data.LocalFiles;
import org.lumen.files.data.ServersMove;
import org.lumen.files.data.SmbFinder;
import org.lumen.files.data.SmbFs;
import org.lumen.files.data.Volumes;
import org.lumen.files.model.Archive;
import org.lumen.files.model.ArchiveFormat;
import org.lumen.files.model.Category;
import org.lumen.files.model.Clip;
import org.lumen.files.model.Entry;
import org.lumen.files.model.Fit;
import org.lumen.files.model.Fs;
import org.lumen.files.model.Kind;
import org.lumen.files.model.LocKind;
import org.lumen.files.model.LocalFs;
import org.lumen.files.model.NameProblem;
import org.lumen.files.model.Names;
import org.lumen.files.model.Nav;
import org.lumen.files.model.Outcome;
import org.lumen.files.model.Place;
import org.lumen.files.model.PlaceKind;
import org.lumen.files.model.Search;
import org.lumen.files.model.Selection;
import org.lumen.files.model.Sizes;
import org.lumen.files.model.Sort;
import org.lumen.files.model.Sorter;
import org.lumen.files.model.Transfer;
import org.lumen.files.model.TransferOp;
import org.lumen.files.model.Upnp;
import org.lumen.files.model.Walk;
import org.lumen.files.net.NetFilesProvider;
import org.lumen.files.net.NetServers;
import org.lumen.files.net.Smb2;
import org.lumen.files.net.SmbException;
import org.lumen.files.open.ApkFiles;
import org.lumen.files.open.ApkInstall;
import org.lumen.files.open.LocalProvider;
import org.lumen.files.open.Opener;
import org.lumen.icons.Icons;
import org.lumen.ui.Palette;
import org.lumen.ui.ThemeFade;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Files (org.lumen.files): one screen, remote first. Top: the title and the Search, Sort and New folder pills. Then
 * the places strip (the projector's storage, every USB drive and SD card with its free space, the network, the kind
 * views). Under it the path and the rows of the place. OK opens, holding OK (or MENU) shows the row's actions on the
 * right, BACK goes up. Picking several rows and "paste here" use the bar at the bottom. Jobs (copy, move, delete,
 * unpack) run on their own thread with a progress card and Cancel.
 */
public class FilesActivity extends Activity {
    public static final String EXTRA_PATH = "org.lumen.files.extra.PATH";
    public static final String EXTRA_CATEGORY = "org.lumen.files.extra.CATEGORY";
    static final String TAG = "LumenFiles";
    static final int MAX_ROWS = 500, FOUND_LIMIT = 1000, DLNA_MS = 3000, PROGRESS_MS = 150;
    /** The widest a place card grows (design px) to fit its free space line. */
    static final int CARD_MAX = 560;
    /** The place strip's design height (design px, with its 12 px paddings): the least it takes. */
    static final int STRIP_H = 156;
    static final long ROOM_MARGIN = 64L << 20;
    static final long LONG_OK_MS = ViewConfiguration.getLongPressTimeout();

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ExecutorService mIo = Executors.newSingleThreadExecutor();
    private final ExecutorService mWork = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mStop = new AtomicBoolean();
    private final Nav mNav = new Nav();
    private final Selection mSel = new Selection();
    private final Collator mCol = Collator.getInstance();
    private Clip mClip;
    private Sort mSort = Sort.NAME;
    private List<Volumes.Volume> mVolumes = new ArrayList<>();
    private List<Entry> mRows = new ArrayList<>();
    private int mCut, mSeq;
    private String mNote;
    private boolean mLoading;
    private List<Upnp.Device> mDlna = new ArrayList<>();
    private boolean mDlnaBusy;
    private SmbFinder mFinder;
    private ThemeFade mFade;
    private long mOkAt;
    private boolean mOkLong;
    private long mLastTick;

    private FrameLayout mRoot;
    private LinearLayout mStrip, mPath, mList, mBar, mPills;
    private HorizontalScrollView mStripScroll;
    private ScrollView mListScroll;
    private TextView mTitle, mCount, mHint;
    /** A place was picked on the strip: its rows take the focus once they are read. */
    private boolean mFocusList;
    private Box mSortPill;
    private Sheet mSheet;
    private Prompt mPrompt;

    private final BroadcastReceiver mMedia = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Log.i(TAG, "storage " + i.getAction());
            volumes(FilesActivity.this::afterDrivesChanged);
        }
    };

    // ------------------------------------------------------------------ life

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        mCol.setStrength(Collator.SECONDARY);
        build();
        mFade = ThemeFade.activity(this, p -> {
            Ui.apply(p);
            restyle();
        });
        final Context app = getApplicationContext();
        mIo.execute(() -> ServersMove.once(app));
        Intent in = getIntent();
        volumes(() -> start(in));
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        volumes(() -> start(i));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mMedia, Volumes.changes(), Context.RECEIVER_EXPORTED);
        mFade.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(mMedia);
        } catch (IllegalArgumentException ignored) {
        }
        mFade.stop();
        if (mFinder != null) mFinder.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mStop.set(true);
        mMain.removeCallbacksAndMessages(null);
        mIo.shutdownNow();
        mWork.shutdownNow();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == ApkInstall.REQUEST) Log.i(TAG, "installer result " + res);
    }

    private void start(Intent in) {
        String cat = in == null ? null : in.getStringExtra(EXTRA_CATEGORY);
        String path = in == null ? null : in.getStringExtra(EXTRA_PATH);
        if (cat != null) {
            try {
                openCategory(Category.valueOf(cat));
                return;
            } catch (IllegalArgumentException ignored) {
            }
        }
        Volumes.Volume v = path == null ? null : volumeOf(path);
        if (v != null) openVolume(v, new File(path).getAbsolutePath());
        else if (!mVolumes.isEmpty()) openVolume(mVolumes.get(0), mVolumes.get(0).place.root);
    }

    private void volumes(Runnable then) {
        final Context app = getApplicationContext();
        mIo.execute(() -> {
            List<Volumes.Volume> v = Volumes.list(app);
            mMain.post(() -> {
                if (isDestroyed()) return;
                mVolumes = v;
                buildStrip();
                if (then != null) then.run();
            });
        });
    }

    private void afterDrivesChanged() {
        Nav.Loc t = mNav.top();
        if (t == null) return;
        if (mClip != null && mClip.from instanceof LocalFs && volumeOf(((LocalFs) mClip.from).root().getAbsolutePath()) == null) mClip = null;
        if (t.fs instanceof LocalFs && volumeOf(((LocalFs) t.fs).root().getAbsolutePath()) == null) {
            toast(getString(R.string.files_drive_removed));
            if (!mVolumes.isEmpty()) openVolume(mVolumes.get(0), mVolumes.get(0).place.root);
            return;
        }
        if (t.kind == LocKind.FOLDER || t.kind == LocKind.CATEGORY) load(focusedName());
    }

    private Volumes.Volume volumeOf(String path) {
        Volumes.Volume best = null;
        for (Volumes.Volume v : mVolumes) {
            if (v.fs.holds(path) && (best == null || v.place.root.length() > best.place.root.length())) best = v;
        }
        return best;
    }

    // ------------------------------------------------------------------ views

    private void build() {
        Palette p = Ui.P;
        mRoot = new FrameLayout(this);
        mRoot.setClipChildren(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.px(Ui.MARGIN), Ui.px(44), Ui.px(Ui.MARGIN), Ui.px(28));
        mRoot.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        mTitle = Ui.line(this, getString(R.string.files_title), 40, Ui.DISPLAY, p.text1);
        head.addView(mTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        mPills = new LinearLayout(this);
        mPills.setClipChildren(false);
        head.addView(mPills);
        pill(R.drawable.lumen_ic_search, getString(R.string.files_search), this::askSearch);
        mSortPill = pill(R.drawable.lumen_ic_sort, sortLabel(), this::nextSort);
        pill(R.drawable.lumen_ic_new_folder, getString(R.string.files_new_folder), this::askNewFolder);
        col.addView(head, new LinearLayout.LayoutParams(-1, Ui.px(64)));

        mStripScroll = new HorizontalScrollView(this);
        mStripScroll.setHorizontalScrollBarEnabled(false);
        mStripScroll.setClipChildren(false);
        mStripScroll.setClipToPadding(false);
        mStripScroll.setPadding(Ui.px(8), Ui.px(12), Ui.px(8), Ui.px(12));
        mStrip = new LinearLayout(this);
        mStrip.setClipChildren(false);
        mStripScroll.addView(mStrip);
        // the design height; buildStrip makes it as tall as its tallest card needs (fitStripHeight)
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Ui.px(STRIP_H));
        sl.topMargin = Ui.px(20);
        sl.leftMargin = -Ui.px(8);
        sl.rightMargin = -Ui.px(8);
        col.addView(mStripScroll, sl);

        LinearLayout pathRow = new LinearLayout(this);
        pathRow.setGravity(Gravity.CENTER_VERTICAL);
        mPath = new LinearLayout(this);
        mPath.setGravity(Gravity.CENTER_VERTICAL);
        pathRow.addView(mPath, new LinearLayout.LayoutParams(0, -2, 1f));
        mCount = Ui.line(this, "", 22, Ui.REGULAR, p.text3);
        pathRow.addView(mCount);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, Ui.px(48));
        pl.topMargin = Ui.px(16);
        col.addView(pathRow, pl);

        mListScroll = new ScrollView(this);
        mListScroll.setVerticalScrollBarEnabled(false);
        mListScroll.setClipChildren(false);
        mListScroll.setClipToPadding(false);
        mListScroll.setDefaultFocusHighlightEnabled(false);
        mListScroll.setPadding(Ui.px(8), Ui.px(8), Ui.px(8), Ui.px(8));
        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setClipChildren(false);
        mListScroll.addView(mList);
        // the list scrolls inside its own box: rows below it never run under the hint bar (the ring keeps its 8 px)
        FrameLayout listBox = new FrameLayout(this);
        listBox.setClipChildren(true);
        listBox.addView(mListScroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 0, 1f);
        ll.leftMargin = -Ui.px(8);
        ll.rightMargin = -Ui.px(8);
        col.addView(listBox, ll);

        mBar = new LinearLayout(this);
        mBar.setGravity(Gravity.CENTER_VERTICAL);
        mBar.setClipChildren(false);
        mHint = Ui.line(this, "", 22, Ui.REGULAR, p.text2);
        mBar.addView(mHint, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.px(64));
        bl.topMargin = Ui.px(12);
        col.addView(mBar, bl);

        mSheet = new Sheet(this);
        mSheet.lost = this::lost;
        mRoot.addView(mSheet, new FrameLayout.LayoutParams(-1, -1));
        mPrompt = new Prompt(this);
        mPrompt.lost = this::lost;
        mRoot.addView(mPrompt, new FrameLayout.LayoutParams(-1, -1));
        mRoot.setBackgroundColor(p.bg);
        setContentView(mRoot);
    }

    private Box pill(int icon, CharSequence label, Runnable r) {
        Palette p = Ui.P;
        Box b = new Box(this);
        b.radius = 28;
        b.lift = 1.05f;
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.px(22), 0, Ui.px(26), 0);
        ImageView i = Icons.view(this, icon, 24);
        b.addView(b.inked(i), new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
        TextView t = Ui.line(this, label, 22, Ui.SEMIBOLD, p.text1);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(Ui.px(12));
        b.addView(b.inked(t), tl);
        b.look(p.surface, p.focus, p.text1, p.onFocus);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, Ui.px(56));
        l.setMarginStart(Ui.px(16));
        mPills.addView(b, l);
        return b;
    }

    /** The theme changed while Files is on screen: same place, same focus, new colours. */
    private void restyle() {
        String focus = focusedName();
        mRoot.removeAllViews();
        build();
        buildStrip();
        show(focus);
    }

    private void buildStrip() {
        View f = mStrip.findFocus();
        int had = f == null ? -1 : mStrip.indexOfChild(f);
        if (f != null) hold();
        mStrip.removeAllViews();
        Nav.Loc t = mNav.top();
        for (Volumes.Volume v : mVolumes) {
            Place pl = v.place;
            String sub = pl.free >= 0 && pl.total > 0 ? getString(R.string.files_free_of, size(pl.free), size(pl.total)) : "";
            int icon = pl.kind == PlaceKind.INTERNAL ? R.drawable.lumen_ic_storage : pl.kind == PlaceKind.SD ? R.drawable.lumen_ic_sd_card : R.drawable.lumen_ic_usb;
            boolean on = t != null && t.kind == LocKind.FOLDER && t.fs != null && t.fs.same(v.fs) && rootOfNav() == v;
            card(icon, pl.label, sub, pl.total > 0 ? pl.used() : -1f, 300, on, () -> openVolume(v, pl.root));
        }
        boolean net = t != null && (t.kind == LocKind.NETWORK || (t.fs != null && !(t.fs instanceof LocalFs)));
        card(R.drawable.lumen_ic_server, getString(R.string.files_network), getString(R.string.files_network_sub), -1f, 240, net, this::openNetwork);
        gap();
        for (Category c : Category.values()) {
            boolean on = t != null && t.kind == LocKind.CATEGORY && t.category == c;
            card(categoryIcon(c), categoryLabel(c), "", -1f, 184, on, () -> openCategory(c));
        }
        // drives came or went while the strip had the focus: it stays on the strip (a picked place sends it to the list)
        if (had >= 0 && !mFocusList && mStrip.getChildCount() > 0) {
            View c = mStrip.getChildAt(Math.min(had, mStrip.getChildCount() - 1));
            if (!c.isFocusable()) c = activeCard();
            if (c != null) c.requestFocus();
            mListScroll.setFocusable(false);
        }
        fitStripHeight();
    }

    /**
     * The strip is as tall as its tallest card needs (a drive: icon, name, free space line, meter), never less than its
     * design height: a fixed height cut the free space line at the bottom. Cards fill the strip's height.
     */
    private void fitStripHeight() {
        int h = 0;
        for (int i = 0; i < mStrip.getChildCount(); i++) {
            View c = mStrip.getChildAt(i);
            if (!(c instanceof Box)) continue;
            c.measure(View.MeasureSpec.makeMeasureSpec(c.getLayoutParams().width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            h = Math.max(h, c.getMeasuredHeight());
        }
        int want = Math.max(Ui.px(STRIP_H), h + mStripScroll.getPaddingTop() + mStripScroll.getPaddingBottom());
        ViewGroup.LayoutParams sl = mStripScroll.getLayoutParams();
        if (sl != null && sl.height != want) {
            sl.height = want;
            mStripScroll.setLayoutParams(sl);
        }
    }

    private Volumes.Volume rootOfNav() {
        List<String> c = mNav.crumbs();
        Nav.Loc t = mNav.top();
        return t == null || t.fs == null || !(t.fs instanceof LocalFs) || c.isEmpty() ? null : volumeOf(((LocalFs) t.fs).root().getAbsolutePath());
    }

    private void gap() {
        View g = new View(this);
        mStrip.addView(g, new LinearLayout.LayoutParams(Ui.px(24), 1));
    }

    private void card(int icon, CharSequence label, CharSequence sub, float used, int w, boolean on, Runnable r) {
        Palette p = Ui.P;
        Box b = new Box(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.radius = 20;
        b.lift = 1.04f;
        b.setPadding(Ui.px(22), Ui.px(14), Ui.px(22), Ui.px(12));
        ImageView i = Icons.view(this, icon, 24);
        i.setImageTintList(android.content.res.ColorStateList.valueOf(on ? p.accentOnBg : p.text2));
        b.addView(i, new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34)));
        TextView t = Ui.line(this, label, 22, Ui.SEMIBOLD, p.text1);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = Ui.px(10);
        b.addView(t, tl);
        float text = t.getPaint().measureText(label.toString());
        if (sub.length() > 0) {
            TextView s = Ui.line(this, sub, 20, Ui.REGULAR, p.text3);
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(-1, -2);
            l2.topMargin = Ui.px(6);
            b.addView(s, l2);
            text = Math.max(text, s.getPaint().measureText(sub.toString()));
        }
        if (used >= 0f) b.addView(new Meter(this, used), new LinearLayout.LayoutParams(-1, Ui.px(14)));
        b.look(on ? p.surface2 : p.surface, p.surface2, p.text1, p.text1);
        b.setOnClickListener(v -> r.run());
        b.setTag(R.id.files_card, Boolean.valueOf(on));
        // as wide as the longest line needs (the free space line of a big drive), the strip scrolls
        int width = Fit.width(Ui.px(w), text, b.getPaddingLeft() + b.getPaddingRight() + Ui.px(2), Ui.px(CARD_MAX));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(width, -1);
        l.setMarginEnd(Ui.px(16));
        mStrip.addView(b, l);
    }

    /** The used share of a drive under its card. */
    static final class Meter extends View {
        final float used;

        Meter(Context c, float used) {
            super(c);
            this.used = used;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            float h = Ui.pxf(4), y = getHeight() - h;
            pt.setColor(Ui.P.track);
            c.drawRoundRect(0, y, getWidth(), y + h, h, h, pt);
            pt.setColor(Ui.P.progress);
            c.drawRoundRect(0, y, Math.max(h, getWidth() * used), y + h, h, h, pt);
        }
    }

    // ------------------------------------------------------------------ places

    private void openVolume(Volumes.Volume v, String path) {
        mFocusList = true;
        mNav.root(new Nav.Loc(LocKind.FOLDER, v.fs, v.place.root, v.place.label));
        String rel = path.length() > v.place.root.length() ? path.substring(v.place.root.length()) : "";
        String cur = v.place.root;
        for (String seg : rel.split("/")) {
            if (seg.isEmpty()) continue;
            String next = v.fs.child(cur, seg);
            mNav.open(new Nav.Loc(LocKind.FOLDER, v.fs, next, seg), seg);
            cur = next;
        }
        buildStrip();
        load(null);
    }

    private void openCategory(Category c) {
        mFocusList = true;
        Nav.Loc l = new Nav.Loc(LocKind.CATEGORY, null, "", categoryLabel(c));
        l.category = c;
        mNav.root(l);
        buildStrip();
        load(null);
    }

    private void openNetwork() {
        mFocusList = true;
        mNav.root(new Nav.Loc(LocKind.NETWORK, null, "", getString(R.string.files_network)));
        buildStrip();
        load(null);
        findServers();
    }

    private void openFolder(Fs fs, String path, String label) {
        mNav.open(new Nav.Loc(LocKind.FOLDER, fs, path, label), focusedName());
        load(null);
    }

    private void up() {
        Nav.Loc l = mNav.up();
        if (l == null) return;
        buildStrip();
        load(l.focus);
    }

    // ------------------------------------------------------------------ loading

    private void load(String focus) {
        final int seq = ++mSeq;
        final Nav.Loc t = mNav.top();
        if (t == null) return;
        mLoading = true;
        mNote = null;
        if (t.kind == LocKind.NETWORK) {
            mRows = new ArrayList<>();
            mLoading = false;
            show(focus);
            return;
        }
        show(focus);
        final List<Volumes.Volume> vols = new ArrayList<>(mVolumes);
        final Sort sort = mSort;
        mIo.execute(() -> {
            List<Entry> rows = new ArrayList<>();
            String note = null;
            int cut = 0;
            try {
                if (t.kind == LocKind.FOLDER) {
                    rows = t.fs.list(t.path);
                    Sorter.sort(rows, sort, mCol::compare);
                } else {
                    Walk.Found f = new Walk.Found();
                    String q = Search.norm(t.query);
                    for (Volumes.Volume v : vols) {
                        boolean scoped = t.kind == LocKind.SEARCH && t.fs instanceof LocalFs;
                        if (scoped && !t.fs.same(v.fs)) continue;
                        String root = scoped ? t.path : v.place.root;
                        String label = scoped ? t.label : v.place.label;
                        Walk.into(v.fs, root, label, t.kind == LocKind.SEARCH ? e -> Search.matches(e.name, q) : e -> e.kind == t.category.kind,
                                FOUND_LIMIT, () -> seq != mSeq, f);
                    }
                    rows = f.entries;
                    Sorter.sort(rows, t.kind == LocKind.CATEGORY && sort == Sort.NAME ? Sort.DATE : sort, mCol::compare);
                    if (f.more) note = getString(R.string.files_more_found);
                }
                cut = Sorter.cap(rows, MAX_ROWS);
            } catch (IOException e) {
                note = message(e);
            } catch (RuntimeException e) {
                Log.w(TAG, "list " + t.path, e);
                note = getString(R.string.files_failed, String.valueOf(e.getMessage()));
            }
            final List<Entry> fr = rows;
            final String fn = note;
            final int fc = cut;
            mMain.post(() -> {
                if (seq != mSeq || isDestroyed()) return;
                mRows = fr;
                mCut = fc;
                mNote = fn;
                mLoading = false;
                mSel.keepOnly(fr);
                show(focus);
            });
        });
    }

    String message(Throwable e) {
        if (e instanceof SmbException) {
            switch (((SmbException) e).kind) {
                case SmbException.LOGON: return getString(R.string.net_err_logon);
                case SmbException.DENIED: return getString(R.string.net_err_denied);
                case SmbException.NO_SHARE: return getString(R.string.net_err_share);
                case SmbException.UNREACHABLE: return getString(R.string.net_err_unreachable);
                case SmbException.ENCRYPTION: return getString(R.string.net_err_encrypt);
                default:
            }
        }
        String m = e.getMessage();
        return getString(R.string.net_err_other, m == null ? e.getClass().getSimpleName() : m);
    }

    // ------------------------------------------------------------------ the list

    private void show(String focus) {
        View cur = getCurrentFocus();
        boolean listHad = mFocusList || mList.findFocus() != null || cur == null || cur == mListScroll;
        if (listHad && cur != mListScroll) hold();
        mList.removeAllViews();
        Nav.Loc t = mNav.top();
        crumbs();
        if (t == null) return;
        Box want = null, first = null;
        if (t.kind == LocKind.NETWORK) {
            for (Box b : networkRows()) {
                mList.addView(b, rowParams());
                if (first == null) first = b;
                if (focus != null && focus.equals(b.getTag(R.id.files_name))) want = b;
            }
        } else if (mLoading) {
            info(R.drawable.lumen_ic_refresh, getString(R.string.files_loading));
        } else {
            for (Entry e : mRows) {
                Box b = row(e, t.kind != LocKind.FOLDER);
                mList.addView(b, rowParams());
                if (first == null) first = b;
                if (focus != null && focus.equals(e.name)) want = b;
            }
            if (mNote != null) info(R.drawable.lumen_ic_info, mNote);
            else if (mRows.isEmpty()) info(R.drawable.lumen_ic_folder, getString(t.kind == LocKind.FOLDER ? R.string.files_empty : R.string.files_search_none));
            if (mCut > 0) info(R.drawable.lumen_ic_info, getString(R.string.files_more, mCut));
        }
        mCount.setText(t.kind == LocKind.NETWORK || mLoading ? "" : getString(R.string.files_items, mRows.size() + mCut));
        bar();
        Box target = want != null ? want : first;
        if (target != null && (want != null || listHad) && !mSheet.shown() && !mPrompt.shown()) {
            final Box ft = target;
            ft.requestFocus();
            mListScroll.setFocusable(false);
            if (!mLoading) mFocusList = false;
            mListScroll.post(() -> scrollTo(ft));
        } else if (first == null && !mLoading && (getCurrentFocus() == null || getCurrentFocus() == mListScroll)) {
            View c = activeCard();
            if (c != null) c.requestFocus();
            mListScroll.setFocusable(false);
            mFocusList = false;
        }
    }

    /**
     * While a window or the actions panel is up the arrows stay inside it: the next view is looked for in the panel
     * only, nothing there means the key does nothing (Android would move on to the rows behind it). Left and right in
     * a text field move its cursor.
     */
    private boolean trap(KeyEvent e, ViewGroup panel) {
        int dir;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = View.FOCUS_RIGHT; break;
            default: return false;
        }
        View cur = getCurrentFocus();
        if (cur instanceof android.widget.EditText && (dir == View.FOCUS_LEFT || dir == View.FOCUS_RIGHT)) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (cur == null || !inside(cur, panel)) {
            View first = android.view.FocusFinder.getInstance().findNextFocus(panel, null, View.FOCUS_DOWN);
            if (first != null) first.requestFocus();
            return true;
        }
        View next = android.view.FocusFinder.getInstance().findNextFocus(panel, cur, dir);
        if (next != null) next.requestFocus(dir);
        return true;
    }

    /** A panel or window closed and its row is gone (deleted, moved): the list keeps the focus for its next rows. */
    private void lost() {
        hold();
        mListScroll.post(() -> {
            if (getCurrentFocus() != mListScroll || mLoading || mSheet.shown() || mPrompt.shown()) return;
            View r = firstFocusable(mList);
            if (r == null) r = activeCard();
            if (r != null) r.requestFocus();
            mListScroll.setFocusable(false);
        });
    }

    /**
     * Rebuilding the rows would drop the focus to the first view of the screen (the Search pill). The list box keeps
     * it meanwhile (no ring of its own), and the new rows take it back.
     */
    private void hold() {
        mListScroll.setFocusable(true);
        mListScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mListScroll.requestFocus();
        mListScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private void scrollTo(View v) {
        int y = v.getTop() - (mListScroll.getHeight() - v.getHeight()) / 2;
        mListScroll.scrollTo(0, Math.max(0, y));
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, Ui.px(76));
        l.bottomMargin = Ui.px(6);
        return l;
    }

    private void info(int icon, CharSequence text) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        ImageView i = Icons.view(this, icon, 24);
        i.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.P.text3));
        r.addView(i, new LinearLayout.LayoutParams(Ui.px(32), Ui.px(32)));
        TextView t = Ui.line(this, text, 24, Ui.REGULAR, Ui.P.text2);
        t.setSingleLine(false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(Ui.px(20));
        r.addView(t, tl);
        mList.addView(r, rowParams());
    }

    private Box baseRow(int icon, CharSequence name, CharSequence meta, CharSequence size, CharSequence date) {
        Palette p = Ui.P;
        Box b = new Box(this);
        b.radius = 16;
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.px(24), 0, Ui.px(28), 0);
        ImageView i = Icons.view(this, icon, 24);
        b.addView(b.inked(i), new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34)));
        TextView n = Ui.line(this, name, 25, Ui.MEDIUM, p.text1);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.setMarginStart(Ui.px(24));
        b.addView(b.inked(n), nl);
        column(b, meta, 420, Gravity.START);
        column(b, size, 170, Gravity.END);
        column(b, date, 220, Gravity.END);
        b.look(0, p.surface2, p.text1, p.text1);
        return b;
    }

    private void column(Box b, CharSequence s, int w, int gravity) {
        if (s == null) return;
        TextView t = Ui.line(this, s, 21, Ui.REGULAR, Ui.P.text3);
        t.setGravity(gravity);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.px(w), -2);
        l.setMarginStart(Ui.px(24));
        b.addView(t, l);
    }

    private Box row(Entry e, boolean showWhere) {
        boolean picked = mSel.has(e);
        String size = e.dir() ? "" : e.size > 0 ? size(e.size) : "";
        String date = e.modified > 0 ? DateFormat.getMediumDateFormat(this).format(new Date(e.modified)) : "";
        Box b = baseRow(picked ? R.drawable.lumen_ic_checkbox_on : kindIcon(e.kind), e.name, showWhere ? e.where : kindLabel(e.kind), size, date);
        b.setPicked(picked, Palette.alpha(Ui.P.accentOnBg, 0.16f));
        b.setTag(e);
        b.setTag(R.id.files_name, e.name);
        b.setOnClickListener(v -> {
            if (!mSel.empty()) togglePick(e);
            else open(e);
        });
        return b;
    }

    private void togglePick(Entry e) {
        mSel.toggle(e);
        show(e.name);
    }

    private void crumbs() {
        mPath.removeAllViews();
        List<String> c = mNav.crumbs();
        Nav.Loc t = mNav.top();
        if (t != null && t.kind == LocKind.SEARCH) c.set(c.size() - 1, getString(R.string.files_search_title, t.query));
        int from = Math.max(0, c.size() - 4);
        for (int i = from; i < c.size(); i++) {
            if (i > from) {
                ImageView ch = Icons.view(this, R.drawable.lumen_ic_chevron_right, 24);
                ch.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.P.text3));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26));
                cl.setMarginStart(Ui.px(8));
                cl.setMarginEnd(Ui.px(8));
                mPath.addView(ch, cl);
            }
            boolean last = i == c.size() - 1;
            TextView tv = Ui.line(this, c.get(i), 24, last ? Ui.SEMIBOLD : Ui.REGULAR, last ? Ui.P.text1 : Ui.P.text2);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (!last) l.width = -2;
            mPath.addView(tv, last ? new LinearLayout.LayoutParams(0, -2, 1f) : l);
        }
    }

    /** The bottom line: a hint, or the picked rows and their actions, or "paste here". */
    private void bar() {
        while (mBar.getChildCount() > 1) mBar.removeViewAt(1);
        Nav.Loc t = mNav.top();
        if (mClip != null) {
            mHint.setText(getString(mClip.op == TransferOp.MOVE ? R.string.files_clip_move : R.string.files_clip_copy, mClip.items.size()));
            if (t != null && t.kind == LocKind.FOLDER && mClip.fits(t.fs, t.path)) barPill(R.drawable.lumen_ic_copy, getString(R.string.files_paste_here), this::paste);
            barPill(R.drawable.lumen_ic_close, getString(R.string.files_cancel), () -> {
                mClip = null;
                bar();
            });
        } else if (!mSel.empty()) {
            long bytes = mSel.bytes();
            mHint.setText(bytes > 0 ? getString(R.string.files_selected_size, mSel.count(), size(bytes)) : getString(R.string.files_selected, mSel.count()));
            Fs fs = pickedFs();
            barPill(R.drawable.lumen_ic_copy, getString(R.string.files_copy), () -> clip(TransferOp.COPY, fs, mSel.items()));
            if (fs != null && fs.writable()) {
                barPill(R.drawable.lumen_ic_move_to, getString(R.string.files_move), () -> clip(TransferOp.MOVE, fs, mSel.items()));
                barPill(R.drawable.lumen_ic_delete, getString(R.string.files_delete), () -> askDelete(fs, mSel.items()));
            }
            barPill(R.drawable.lumen_ic_select_all, getString(R.string.files_select_all), () -> {
                mSel.all(mRows);
                show(focusedName());
            });
            barPill(R.drawable.lumen_ic_close, getString(R.string.files_unselect), () -> {
                mSel.clear();
                show(focusedName());
            });
        } else {
            mHint.setText(t != null && t.kind == LocKind.NETWORK ? getString(R.string.net_hint) : getString(R.string.files_hint));
        }
    }

    private void barPill(int icon, CharSequence label, Runnable r) {
        Palette p = Ui.P;
        Box b = new Box(this);
        b.radius = 26;
        b.lift = 1.05f;
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.px(20), 0, Ui.px(24), 0);
        ImageView i = Icons.view(this, icon, 24);
        b.addView(b.inked(i), new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26)));
        TextView t = Ui.line(this, label, 21, Ui.SEMIBOLD, p.text1);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(Ui.px(10));
        b.addView(b.inked(t), tl);
        b.look(p.surface, p.focus, p.text1, p.onFocus);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, Ui.px(52));
        l.setMarginStart(Ui.px(14));
        mBar.addView(b, l);
    }

    private String focusedName() {
        View f = getCurrentFocus();
        Object n = f == null ? null : f.getTag(R.id.files_name);
        return n instanceof String ? (String) n : null;
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (mPrompt.shown()) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (down && e.getRepeatCount() == 0) {
                    if (mPrompt.busy()) mPrompt.cancel();
                    else mPrompt.hide();
                    refocus();
                }
                return true;
            }
            return trap(e, mPrompt) || super.dispatchKeyEvent(e);
        }
        if (mSheet.shown()) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (down && e.getRepeatCount() == 0) {
                    mSheet.hide();
                    refocus();
                }
                return true;
            }
            return trap(e, mSheet) || super.dispatchKeyEvent(e);
        }
        View f = getCurrentFocus();
        Object tag = f == null ? null : f.getTag();
        if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) && tag != null) {
            if (down) {
                if (e.getRepeatCount() == 0) {
                    mOkAt = SystemClock.uptimeMillis();
                    mOkLong = false;
                } else if (!mOkLong && SystemClock.uptimeMillis() - mOkAt >= LONG_OK_MS) {
                    mOkLong = true;
                    actions(tag);
                }
                return true;
            }
            if (!mOkLong) f.performClick();
            mOkLong = false;
            return true;
        }
        if (k == KeyEvent.KEYCODE_MENU && down && e.getRepeatCount() == 0) {
            if (tag != null) actions(tag);
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (down && e.getRepeatCount() == 0) back();
            return true;
        }
        if (down && k == KeyEvent.KEYCODE_DPAD_DOWN && f != null && inside(f, mStripScroll)) {
            View r = mList.getChildCount() > 0 ? firstFocusable(mList) : null;
            if (r != null) {
                r.requestFocus();
                return true;
            }
        }
        if (down && k == KeyEvent.KEYCODE_DPAD_UP && f != null && inside(f, mList) && mList.indexOfChild(f) == firstIndex()) {
            View c = activeCard();
            if (c != null) {
                c.requestFocus();
                return true;
            }
        }
        if (down && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) && f != null && inside(f, mList)) return true;
        return super.dispatchKeyEvent(e);
    }

    private int firstIndex() {
        for (int i = 0; i < mList.getChildCount(); i++) if (mList.getChildAt(i).isFocusable()) return i;
        return -1;
    }

    private static View firstFocusable(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i).isFocusable()) return g.getChildAt(i);
        return null;
    }

    private View activeCard() {
        for (int i = 0; i < mStrip.getChildCount(); i++) {
            View c = mStrip.getChildAt(i);
            if (Boolean.TRUE.equals(c.getTag(R.id.files_card))) return c;
        }
        return firstFocusable(mStrip);
    }

    private static boolean inside(View v, View group) {
        for (View x = v; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null) if (x == group) return true;
        return false;
    }

    private void refocus() {
        View f = getCurrentFocus();
        if (f != null && f.isShown() && !mPrompt.owns(f) && !mSheet.owns(f)) return;
        View r = firstFocusable(mList);
        if (r == null) r = activeCard();
        if (r != null) r.requestFocus();
    }

    private void back() {
        if (mClip != null) {
            mClip = null;
            bar();
            return;
        }
        if (!mSel.empty()) {
            mSel.clear();
            show(focusedName());
            return;
        }
        if (mNav.canUp()) {
            up();
            return;
        }
        finish();
    }

    // ------------------------------------------------------------------ opening

    private Fs fsOf(Entry e) {
        Nav.Loc t = mNav.top();
        if (t != null && t.fs != null) return t.fs;
        Volumes.Volume v = volumeOf(e.path);
        return v == null ? null : v.fs;
    }

    private Fs pickedFs() {
        List<Entry> items = mSel.items();
        return items.isEmpty() ? null : fsOf(items.get(0));
    }

    private Uri uriOf(Fs fs, Entry e) {
        if (fs instanceof LocalFs) return LocalProvider.uri(new File(e.path));
        if (fs instanceof SmbFs) {
            String[] sp = e.path.substring(1).split("/", 2);
            return NetFilesProvider.uri(((SmbFs) fs).server().id, sp[0], sp.length > 1 ? sp[1] : "", e.size);
        }
        return e.url.isEmpty() ? null : Uri.parse(e.url);
    }

    private String mimeOf(Entry e) {
        return !e.mime.isEmpty() && !e.mime.equals("*") ? e.mime : LocalFiles.mime(e.name);
    }

    private void open(Entry e) {
        Fs fs = fsOf(e);
        if (fs == null) return;
        if (e.dir()) {
            openFolder(fs, e.path, e.name);
            return;
        }
        File local = fs instanceof LocalFs ? new File(e.path) : null;
        Uri u = uriOf(fs, e);
        boolean ok = true;
        switch (e.kind) {
            case VIDEO:
            case MUSIC:
                ok = u != null && Opener.play(this, u, mimeOf(e), local);
                break;
            case PHOTO:
                ArrayList<Uri> all = new ArrayList<>();
                if (local == null) for (Entry r : mRows) if (r.kind == Kind.PHOTO) all.add(uriOf(fs, r));
                ok = u != null && Opener.photo(this, local, u, all);
                break;
            case APK:
                if (local != null) ok = ApkInstall.start(this, local);
                else fetchApk(fs, e);
                break;
            case ARCHIVE:
                if (ArchiveFormat.of(e.name) != null && fs.writable()) askUnpack(fs, e);
                else ok = u != null && Opener.with(this, u, mimeOf(e));
                break;
            default:
                ok = u != null && Opener.with(this, u, mimeOf(e));
        }
        if (!ok) toast(getString(R.string.files_open_failed, e.name));
    }

    // ------------------------------------------------------------------ actions

    private void actions(Object tag) {
        if (tag instanceof Entry) entryActions((Entry) tag);
        else if (tag instanceof NetServers.Server) serverActions((NetServers.Server) tag);
    }

    private void entryActions(Entry e) {
        Fs fs = fsOf(e);
        if (fs == null) return;
        List<Sheet.Action> a = new ArrayList<>();
        a.add(new Sheet.Action(e.kind == Kind.APK ? R.drawable.lumen_ic_install : kindIcon(e.kind),
                getString(e.kind == Kind.APK ? R.string.files_install : R.string.files_menu_open), () -> open(e)));
        if (!e.dir() && e.kind != Kind.APK) {
            a.add(new Sheet.Action(R.drawable.lumen_ic_open_with, getString(R.string.files_open_with), () -> {
                Uri u = uriOf(fs, e);
                if (u == null || !Opener.with(this, u, mimeOf(e))) toast(getString(R.string.files_open_failed, e.name));
            }));
        }
        if (ArchiveFormat.of(e.name) != null && fs.writable()) {
            a.add(new Sheet.Action(R.drawable.lumen_ic_unarchive, getString(R.string.files_unpack), () -> askUnpack(fs, e)));
        }
        a.add(new Sheet.Action(R.drawable.lumen_ic_checkbox_on, getString(R.string.files_select), () -> togglePick(e)));
        a.add(new Sheet.Action(R.drawable.lumen_ic_copy, getString(R.string.files_copy), () -> clip(TransferOp.COPY, fs, single(e))));
        if (fs.writable()) {
            a.add(new Sheet.Action(R.drawable.lumen_ic_move_to, getString(R.string.files_move), () -> clip(TransferOp.MOVE, fs, single(e))));
            a.add(new Sheet.Action(R.drawable.lumen_ic_edit, getString(R.string.files_rename), () -> askRename(fs, e)));
            a.add(new Sheet.Action(R.drawable.lumen_ic_delete, getString(R.string.files_delete), () -> askDelete(fs, single(e))));
        }
        a.add(new Sheet.Action(R.drawable.lumen_ic_info, getString(R.string.files_details), () -> details(fs, e)));
        mSheet.show(e.name, e.dir() ? kindLabel(e.kind) : kindLabel(e.kind) + (e.size > 0 ? ", " + size(e.size) : ""), a);
    }

    private static List<Entry> single(Entry e) {
        List<Entry> l = new ArrayList<>();
        l.add(e);
        return l;
    }

    private void clip(TransferOp op, Fs fs, List<Entry> items) {
        if (fs == null || items.isEmpty()) return;
        mClip = new Clip(op, fs, items);
        mSel.clear();
        show(focusedName());
    }

    private void paste() {
        Nav.Loc t = mNav.top();
        Clip c = mClip;
        if (c == null || t == null || !c.fits(t.fs, t.path)) return;
        mClip = null;
        final Fs dst = t.fs;
        final String into = t.path;
        job(getString(c.op == TransferOp.MOVE ? R.string.files_moving : R.string.files_copying_title), stop -> {
            Transfer tr = new Transfer(c.from, dst, c.op, c.items, into, stop, this::tick);
            Outcome o = tr.run();
            String first = tr.made.isEmpty() ? null : new File(tr.made.get(0)).getName();
            return new Object[]{o, first, c.op == TransferOp.MOVE ? R.string.files_done_move : R.string.files_done_copy, c.items.size()};
        });
    }

    private void askDelete(Fs fs, List<Entry> items) {
        if (items.isEmpty()) return;
        String q = items.size() == 1 ? getString(R.string.files_delete_confirm, items.get(0).name) : getString(R.string.files_delete_many, items.size());
        mPrompt.confirmLoss(q, getString(R.string.files_cannot_undo), getString(R.string.files_delete), getString(R.string.files_cancel), () ->
                job(getString(R.string.files_deleting), stop -> new Object[]{Transfer.delete(fs, items, stop), null, R.string.files_done_delete, items.size()}));
    }

    private void askRename(Fs fs, Entry e) {
        String parent = fs.parent(e.path);
        if (parent == null) return;
        mPrompt.form(getString(R.string.files_rename), null, new Prompt.Field[]{new Prompt.Field(getString(R.string.net_name), e.name, "", false)},
                getString(R.string.save), getString(R.string.files_cancel), v -> {
                    String n = v[0].trim();
                    if (n.equals(e.name)) {
                        mPrompt.hide();
                        return null;
                    }
                    NameProblem p = Names.check(n, x -> !x.equalsIgnoreCase(e.name) && taken(x));
                    if (p != null) return problem(p);
                    mPrompt.hide();
                    mIo.execute(() -> {
                        boolean ok = fs.rename(e.path, fs.child(parent, n));
                        mMain.post(() -> {
                            if (!ok) toast(getString(R.string.files_failed, e.name));
                            load(ok ? n : e.name);
                        });
                    });
                    return null;
                });
        int dot = e.dir() ? e.name.length() : Names.stem(e.name).length();
        mPrompt.selectStem(dot);
    }

    private boolean taken(String name) {
        for (Entry r : mRows) if (r.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    private String problem(NameProblem p) {
        switch (p) {
            case EMPTY: return getString(R.string.files_name_empty);
            case TOO_LONG: return getString(R.string.files_name_long);
            case TAKEN: return getString(R.string.files_name_taken);
            default: return getString(R.string.files_name_bad);
        }
    }

    private void askNewFolder() {
        Nav.Loc t = mNav.top();
        if (t == null || t.kind != LocKind.FOLDER || !t.fs.writable()) {
            toast(getString(R.string.files_read_only));
            return;
        }
        String name = Names.free(getString(R.string.files_new_folder), this::taken);
        mPrompt.form(getString(R.string.files_new_folder), null, new Prompt.Field[]{new Prompt.Field(getString(R.string.net_name), name, "", false)},
                getString(R.string.files_create), getString(R.string.files_cancel), v -> {
                    String n = v[0].trim();
                    NameProblem p = Names.check(n, this::taken);
                    if (p != null) return problem(p);
                    mPrompt.hide();
                    mIo.execute(() -> {
                        boolean ok = true;
                        try {
                            t.fs.mkdir(t.fs.child(t.path, n));
                        } catch (IOException x) {
                            ok = false;
                        }
                        final boolean fok = ok;
                        mMain.post(() -> {
                            if (!fok) toast(getString(R.string.files_failed, n));
                            load(n);
                        });
                    });
                    return null;
                });
        mPrompt.selectStem(name.length());
    }

    private void askUnpack(Fs fs, Entry e) {
        ArchiveFormat f = ArchiveFormat.of(e.name);
        String parent = fs.parent(e.path);
        if (f == null || parent == null) return;
        String folder = Names.stem(e.name);
        mPrompt.confirm(getString(R.string.files_unpack), getString(R.string.files_unpack_confirm, folder), getString(R.string.files_unpack),
                getString(R.string.files_cancel), () -> job(getString(R.string.files_unpacking), stop -> {
                    String name = Names.free(folder, n -> exists(fs, fs.child(parent, n)));
                    String into = fs.child(parent, name);
                    fs.mkdir(into);
                    long free = fs.free(parent);
                    long room = free < 0 ? Long.MAX_VALUE : Math.max(0, free - ROOM_MARGIN);
                    Outcome o;
                    try (InputStream in = fs.read(e.path)) {
                        o = Archive.extract(in, f, fs, into, e.size, room, stop, this::tick);
                    }
                    if (o != Outcome.DONE) Transfer.removeAll(fs, into);
                    return new Object[]{o, name, R.string.files_done_unpack, 1};
                }));
    }

    static boolean exists(Fs fs, String path) {
        try {
            return fs.stat(path) != null;
        } catch (IOException e) {
            return true;
        }
    }

    /** An APK of a network folder: copied to Files' cache first, then the installer reads it there. */
    private void fetchApk(Fs fs, Entry e) {
        final File out = new File(ApkInstall.cache(this), Names.hidden(e.name) ? "app.apk" : e.name);
        job(getString(R.string.files_preparing), stop -> {
            long done = 0;
            byte[] buf = new byte[1 << 16];
            try (InputStream in = fs.read(e.path); OutputStream o = new java.io.FileOutputStream(out)) {
                for (int n; (n = in.read(buf)) > 0; ) {
                    o.write(buf, 0, n);
                    done += n;
                    tick(done, e.size, e.name);
                    if (stop.getAsBoolean()) break;
                }
            }
            if (stop.getAsBoolean()) {
                out.delete();
                return new Object[]{Outcome.CANCELLED, null, 0, 0};
            }
            mMain.post(() -> {
                if (!ApkInstall.start(this, out)) toast(getString(R.string.files_open_failed, e.name));
            });
            return new Object[]{Outcome.DONE, null, 0, 0};
        });
    }

    private void details(Fs fs, Entry e) {
        List<CharSequence[]> rows = new ArrayList<>();
        rows.add(new CharSequence[]{getString(R.string.files_d_name), e.name});
        rows.add(new CharSequence[]{getString(R.string.files_d_type), kindLabel(e.kind) + (e.dir() || e.kind == Kind.APK ? "" : ", " + mimeOf(e))});
        int sizeRow = rows.size();
        rows.add(new CharSequence[]{getString(R.string.files_d_size), e.dir() ? getString(R.string.files_d_counting) : size(Math.max(0, e.size))});
        if (e.modified > 0) {
            rows.add(new CharSequence[]{getString(R.string.files_d_date), DateFormat.getMediumDateFormat(this).format(new Date(e.modified)) + " "
                    + DateFormat.getTimeFormat(this).format(new Date(e.modified))});
        }
        String parent = fs.parent(e.path);
        rows.add(new CharSequence[]{getString(R.string.files_d_where), e.where.isEmpty() ? String.join(" / ", mNav.crumbs()) : e.where});
        if (e.kind == Kind.APK && fs instanceof LocalFs) {
            ApkFiles.Entry a = ApkFiles.read(getPackageManager(), new File(e.path), new File(parent == null ? "/" : parent));
            if (a != null) {
                rows.add(new CharSequence[]{getString(R.string.files_d_app), a.label});
                rows.add(new CharSequence[]{getString(R.string.files_d_package), a.pkg});
                rows.add(new CharSequence[]{getString(R.string.files_d_version), a.versionName});
                rows.add(new CharSequence[]{getString(R.string.files_d_installed), getString(a.installed ? R.string.usb_update : R.string.usb_new)});
            }
        }
        mPrompt.info(e.name, rows.toArray(new CharSequence[0][]), getString(R.string.files_close));
        if (e.dir()) {
            final int seq = mSeq;
            mIo.execute(() -> {
                String v;
                try {
                    v = size(Transfer.bytes(fs, e));
                } catch (IOException x) {
                    v = "";
                }
                final String fv = v;
                mMain.post(() -> {
                    if (seq == mSeq && mPrompt.shown()) mPrompt.value(sizeRow, fv);
                });
            });
        }
    }

    // ------------------------------------------------------------------ jobs

    /** A job's body: returns {Outcome, name to focus, done text, count}; runs on the job thread. */
    interface Body {
        Object[] run(java.util.function.BooleanSupplier stop) throws IOException;
    }

    private void job(CharSequence title, Body body) {
        mStop.set(false);
        mLastTick = 0;
        mPrompt.progress(title, getString(R.string.files_cancel), () -> mStop.set(true));
        mWork.execute(() -> {
            Object[] r;
            try {
                r = body.run(mStop::get);
            } catch (IOException | RuntimeException x) {
                Log.w(TAG, "job", x);
                r = new Object[]{Outcome.FAILED, null, 0, 0};
            }
            final Object[] fr = r;
            mMain.post(() -> done(fr));
        });
    }

    private void tick(long done, long total, String name) {
        long now = SystemClock.uptimeMillis();
        if (now - mLastTick < PROGRESS_MS && done < total) return;
        mLastTick = now;
        final int pct = total > 0 ? Sizes.percent(done, total) : -1;
        final String line = total > 0 ? name + "\n" + getString(R.string.files_of, size(done), size(total)) : name;
        mMain.post(() -> {
            if (mPrompt.busy()) mPrompt.update(pct, line);
        });
    }

    private void done(Object[] r) {
        if (isDestroyed()) return;
        mPrompt.hide();
        Outcome o = (Outcome) r[0];
        String focus = (String) r[1];
        int text = (Integer) r[2];
        int n = (Integer) r[3];
        switch (o) {
            case DONE:
                if (text != 0) toast(text == R.string.files_done_unpack ? getString(text, focus) : getString(text, n));
                break;
            case CANCELLED: toast(getString(R.string.files_cancelled)); break;
            case NO_SPACE: toast(getString(R.string.files_no_space)); break;
            case INTO_ITSELF: toast(getString(R.string.files_into_itself)); break;
            case READ_ONLY: toast(getString(R.string.files_read_only)); break;
            case UNSAFE: toast(getString(R.string.files_unsafe)); break;
            default: toast(getString(R.string.files_failed, "")); break;
        }
        mSel.clear();
        volumes(null);
        Nav.Loc t = mNav.top();
        if (t != null && t.kind != LocKind.NETWORK) load(focus);
        else bar();
    }

    // ------------------------------------------------------------------ network

    private void findServers() {
        if (mFinder == null) mFinder = new SmbFinder(this, f -> {
            Nav.Loc t = mNav.top();
            if (t != null && t.kind == LocKind.NETWORK) show(focusedName());
        });
        mFinder.start();
        if (mDlnaBusy) return;
        mDlnaBusy = true;
        final Context app = getApplicationContext();
        mIo.execute(() -> {
            List<Upnp.Device> d = DlnaFinder.find(app, DLNA_MS);
            mMain.post(() -> {
                mDlnaBusy = false;
                mDlna = d;
                Nav.Loc t = mNav.top();
                if (t != null && t.kind == LocKind.NETWORK) show(focusedName());
            });
        });
    }

    private List<Box> networkRows() {
        List<Box> out = new ArrayList<>();
        List<NetServers.Server> saved = NetServers.all(this);
        for (NetServers.Server s : saved) {
            Box b = baseRow(R.drawable.lumen_ic_server, s.label(), getString(R.string.files_smb_sub), null, s.host);
            b.setTag(s);
            b.setTag(R.id.files_name, "s:" + s.id);
            b.setOnClickListener(v -> openServer(s));
            out.add(b);
        }
        if (mFinder != null) {
            for (SmbFinder.Found f : mFinder.found()) {
                boolean known = false;
                for (NetServers.Server s : saved) known |= s.host.equalsIgnoreCase(f.host);
                if (known) continue;
                Box b = baseRow(R.drawable.lumen_ic_lan, f.name, getString(R.string.net_found), null, f.host);
                b.setTag(R.id.files_name, "f:" + f.host);
                b.setOnClickListener(v -> {
                    NetServers.Server s = NetServers.create();
                    s.host = f.host;
                    s.name = f.name;
                    s.port = f.port > 0 ? f.port : Smb2.DEFAULT_PORT;
                    serverForm(s, true);
                });
                out.add(b);
            }
        }
        for (Upnp.Device d : mDlna) {
            Box b = baseRow(R.drawable.lumen_ic_movie, d.name, getString(R.string.files_dlna_sub), null, "");
            b.setTag(R.id.files_name, "d:" + d.udn);
            b.setOnClickListener(v -> openFolder(new DlnaFs(d), Upnp.ROOT, d.name));
            out.add(b);
        }
        Box add = baseRow(R.drawable.lumen_ic_add, getString(R.string.net_add), getString(R.string.net_sub), null, "");
        add.setTag(R.id.files_name, "add");
        add.setOnClickListener(v -> serverForm(NetServers.create(), true));
        out.add(add);
        if (mDlnaBusy) {
            Box b = baseRow(R.drawable.lumen_ic_refresh, getString(R.string.files_net_searching), "", null, "");
            b.setFocusable(false);
            out.add(b);
        }
        return out;
    }

    private void openServer(NetServers.Server s) {
        if (s.password == null) {
            serverForm(s, false);
            return;
        }
        SmbFs fs = new SmbFs(s);
        mNav.open(new Nav.Loc(LocKind.FOLDER, fs, fs.start(), s.label()), "s:" + s.id);
        load(null);
    }

    private void serverActions(NetServers.Server s) {
        List<Sheet.Action> a = new ArrayList<>();
        a.add(new Sheet.Action(R.drawable.lumen_ic_server, getString(R.string.files_menu_open), () -> openServer(s)));
        a.add(new Sheet.Action(R.drawable.lumen_ic_edit, getString(R.string.net_edit), () -> serverForm(s, false)));
        a.add(new Sheet.Action(R.drawable.lumen_ic_delete, getString(R.string.files_delete), () ->
                mPrompt.confirmLoss(getString(R.string.files_delete_confirm, s.label()), null, getString(R.string.files_delete),
                        getString(R.string.files_cancel), () -> {
                            NetServers.delete(this, s.id);
                            show(null);
                        })));
        mSheet.show(s.label(), s.host, a);
    }

    private void serverForm(NetServers.Server s, boolean isNew) {
        String addr = s.port == Smb2.DEFAULT_PORT ? s.host : s.host + ":" + s.port;
        Prompt.Field[] f = {
                new Prompt.Field(getString(R.string.net_address), addr, getString(R.string.net_address_hint), false),
                new Prompt.Field(getString(R.string.net_login), s.login, getString(R.string.net_login_hint), false),
                new Prompt.Field(getString(R.string.net_password), s.password == null ? "" : s.password, "", true),
                new Prompt.Field(getString(R.string.net_share), s.share, getString(R.string.net_all_shares), false)};
        mPrompt.form(getString(isNew ? R.string.net_add : R.string.net_edit), getString(R.string.net_sub), f, getString(R.string.net_connect),
                getString(R.string.files_cancel), v -> {
                    String host = v[0].trim();
                    if (host.isEmpty()) return getString(R.string.net_err_address);
                    NetServers.Server n = s.copy();
                    int c = host.lastIndexOf(':');
                    n.port = Smb2.DEFAULT_PORT;
                    if (c > 0 && host.indexOf(':') == c) {
                        try {
                            n.port = Integer.parseInt(host.substring(c + 1));
                            host = host.substring(0, c);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    n.host = host;
                    if (n.name == null || n.name.isEmpty() || isNew) n.name = n.name == null || n.name.isEmpty() ? host : n.name;
                    n.login = v[1].trim();
                    n.password = v[2];
                    n.share = v[3].trim().replace("/", "").replace("\\", "");
                    mPrompt.error(getString(R.string.net_connecting));
                    mIo.execute(() -> {
                        String err = null;
                        try (Smb2 conn = Smb2.connect(n.host, n.port, n.login, n.password)) {
                            if (!n.share.isEmpty()) conn.treeConnect(n.share);
                        } catch (IOException x) {
                            err = message(x);
                        }
                        final String fe = err;
                        mMain.post(() -> {
                            if (fe != null) {
                                mPrompt.error(fe);
                                return;
                            }
                            NetServers.save(this, n);
                            mPrompt.hide();
                            openServer(n);
                        });
                    });
                    return getString(R.string.net_connecting);
                });
    }

    // ------------------------------------------------------------------ search and sort

    private void askSearch() {
        Nav.Loc t = mNav.top();
        mPrompt.form(getString(R.string.files_search), null, new Prompt.Field[]{new Prompt.Field(getString(R.string.files_search_hint), "", "", false)},
                getString(R.string.files_search), getString(R.string.files_cancel), v -> {
                    String q = v[0].trim();
                    if (Search.norm(q).isEmpty()) return getString(R.string.files_name_empty);
                    mPrompt.hide();
                    boolean local = t != null && t.kind == LocKind.FOLDER && t.fs instanceof LocalFs;
                    Nav.Loc s = new Nav.Loc(LocKind.SEARCH, local ? t.fs : null, local ? t.path : "", t == null ? q : t.label);
                    s.query = q;
                    if (t == null) mNav.root(s);
                    else mNav.open(s, focusedName());
                    mFocusList = true;
                    load(null);
                    return null;
                });
    }

    private void nextSort() {
        mSort = Sorter.next(mSort);
        TextView t = (TextView) mSortPill.getChildAt(1);
        t.setText(sortLabel());
        Nav.Loc l = mNav.top();
        if (l != null && l.kind != LocKind.NETWORK) load(focusedName());
    }

    private String sortLabel() {
        return getString(mSort == Sort.DATE ? R.string.files_sort_date : mSort == Sort.SIZE ? R.string.files_sort_size : R.string.files_sort_name);
    }

    // ------------------------------------------------------------------ words and icons

    String size(long b) {
        return Sizes.format(b, getString(R.string.files_units).split("\\|"), Locale.getDefault());
    }

    private void toast(CharSequence s) {
        org.lumen.ui.TvFocus.toast(this, s, true);
    }

    static int kindIcon(Kind k) {
        switch (k) {
            case FOLDER: return R.drawable.lumen_ic_folder;
            case VIDEO: return R.drawable.lumen_ic_movie;
            case MUSIC: return R.drawable.lumen_ic_music;
            case PHOTO: return R.drawable.lumen_ic_photo;
            case APK: return R.drawable.lumen_ic_apk;
            case ARCHIVE: return R.drawable.lumen_ic_archive;
            case DOCUMENT: return R.drawable.lumen_ic_document;
            default: return R.drawable.lumen_ic_file;
        }
    }

    String kindLabel(Kind k) {
        switch (k) {
            case FOLDER: return getString(R.string.kind_folder);
            case VIDEO: return getString(R.string.kind_video);
            case MUSIC: return getString(R.string.kind_music);
            case PHOTO: return getString(R.string.kind_photo);
            case APK: return getString(R.string.kind_apk);
            case ARCHIVE: return getString(R.string.kind_archive);
            case DOCUMENT: return getString(R.string.kind_document);
            default: return getString(R.string.kind_other);
        }
    }

    static int categoryIcon(Category c) {
        return kindIcon(c.kind);
    }

    String categoryLabel(Category c) {
        switch (c) {
            case VIDEO: return getString(R.string.cat_video);
            case MUSIC: return getString(R.string.cat_music);
            case PHOTO: return getString(R.string.cat_photo);
            case APK: return getString(R.string.cat_apk);
            default: return getString(R.string.cat_archive);
        }
    }
}
