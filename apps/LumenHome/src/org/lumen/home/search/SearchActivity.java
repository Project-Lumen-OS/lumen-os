// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.search;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.lumen.home.App;
import org.lumen.home.FilesLink;
import org.lumen.home.Launch;
import org.lumen.home.Prefs;
import org.lumen.home.R;
import org.lumen.home.Safe;
import org.lumen.home.data.AppSource;
import org.lumen.home.data.Card;
import org.lumen.home.data.HomeData;
import org.lumen.home.data.HomeLoader;
import org.lumen.home.img.ArtUrl;
import org.lumen.home.img.ImageLoader;
import org.lumen.home.ui.FieldView;
import org.lumen.home.ui.RowView;
import org.lumen.home.ui.Theme;
import org.lumen.home.ui.TileView;
import org.lumen.home.watch.WatchApps;
import org.lumen.home.watch.WatchLaunch;
import org.lumen.home.watch.WatchPick;
import org.lumen.ui.FocusRing;
import org.lumen.ui.RingPaint;
import org.lumen.ui.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * Lumen search (the mic key of the remote, web search intents, "Смотреть" on a showcase title), in the Home 2.0 look:
 * the field with the mic on top, then rows of matching apps, results the apps publish, "Search in" an app, recent
 * searches. Voice is push to talk: org.lumen.projector's HomeSearch starts VoiceSearchAlias on mic DOWN (with the
 * key's down time) and MicUpReceiver ends listening on UP; a short tap means keyboard search. Languages without an
 * IME layout get our own on-screen keyboard ({@link TvKeyboard}).
 */
public class SearchActivity extends Activity implements VoiceInput.Listener, TvKeyboard.Listener {
    static volatile SearchActivity sActive;
    public static final String EXTRA_HELD = "held";
    public static final String EXTRA_DOWN_TIME = "down_uptime";
    /** org.lumen.projector HomeSearch.EXTRA_TAP: tap-to-talk (classic launcher voice orb), listen at once. */
    public static final String EXTRA_TAP = "tap_voice";
    private static final String ALIAS = "org.lumen.home.search.VoiceSearchAlias";
    private static final long DEBOUNCE_MS = 250;
    static final long TAP_MS = 400;
    private static final String KEYBOARD_APP = "com.google.android.inputmethod.latin";
    private static final String YT = AppSource.PKG_YOUTUBE;
    /** Zones: the mic, the field, then the visible result rows. */
    private static final int Z_MIC = -1, Z_FIELD = 0;
    /** The field's text and the mic sit above the field box, whose own elevation (up to 10) once hid the query. */
    static final int FIELD_Z = 12;
    /** Gap between result rows, and the room under a row of results for the focused tile's two caption lines. */
    static final int ROW_GAP = 76, CAPTION_H = 64;

    private FrameLayout mRoot, mResults;
    private EditText mField;
    private FieldBox mFieldBg;
    private Mic mMic;
    private TextView mStatus;
    private TvKeyboard mKb;
    private VoiceInput mVoice;
    private long mVoiceDown;
    private boolean mVoiceOk = true;
    private int mZone = Z_FIELD, mSeq;
    private String mQuery = "";
    private final ArrayList<RowView> mRows = new ArrayList<>();
    private final ArrayList<List<Runnable>> mActs = new ArrayList<>();
    private final ArrayList<Integer> mItem = new ArrayList<>();
    private volatile List<Card> mApps;
    private List<SearchIn> mSearchIn;
    private final Runnable mRun = () -> runSearch(mField.getText().toString());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        sActive = this;
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.BG));
        build();
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sActive = this;
        mVoiceOk = VoiceInput.available(this);
        mMic.setVisibility(mVoiceOk ? View.VISIBLE : View.GONE);
        if (!mVoiceOk && mZone == Z_MIC) setZone(Z_FIELD);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVoice != null) mVoice.cancel();
        mMic.setListening(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sActive == this) sActive = null;
        if (mVoice != null) mVoice.destroy();
        mRoot.removeCallbacks(mRun);
    }

    private void handle(Intent i) {
        mVoiceOk = VoiceInput.available(this);
        boolean voice = i.getComponent() != null && ALIAS.equals(i.getComponent().getClassName());
        boolean held = voice && i.getBooleanExtra(EXTRA_HELD, false);
        String q = i.getStringExtra(SearchManager.QUERY);
        Log.i(App.TAG, "search open voice=" + voice + " held=" + held + " action=" + i.getAction());
        if (q != null && !q.isEmpty()) {
            mField.setText(q);
            mField.setSelection(q.length());
            mRoot.removeCallbacks(mRun);
            runSearch(q);
        }
        setZone(Z_FIELD);
        if (voice) {
            if (!mVoiceOk) {
                showStatus(getString(R.string.voice_none));
                showIme();
                return;
            }
            if (VoiceInput.recentlyFailed()) {
                showStatus(getString(R.string.voice_unavailable_hint));
                showIme();
                return;
            }
            if (!held) {
                if (i.getBooleanExtra(EXTRA_TAP, false)) startVoice(0);
                return;
            }
            long down = i.getLongExtra(EXTRA_DOWN_TIME, 0);
            if (down > 0 && MicUpReceiver.lastUp() >= down) {
                showStatus(getString(R.string.voice_hold));
                showIme();
                return;
            }
            startVoice(down);
        } else if (mKb != null && (q == null || q.isEmpty())) {
            showIme();
        }
    }

    // ------------------------------------------------------------------ UI

    private void build() {
        mRoot = new FrameLayout(this);
        mRoot.setClipChildren(false);
        mRoot.addView(new FieldView(this), new FrameLayout.LayoutParams(-1, -1));
        mFieldBg = new FieldBox(this);
        FrameLayout.LayoutParams fb = new FrameLayout.LayoutParams(Theme.px(1728), Theme.px(88));
        fb.leftMargin = Theme.px(Tokens.MARGIN);
        fb.topMargin = Theme.px(72);
        mRoot.addView(mFieldBg, fb);
        mMic = new Mic(this);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(Theme.px(64), Theme.px(64));
        ml.leftMargin = Theme.px(Tokens.MARGIN + 12);
        ml.topMargin = Theme.px(84);
        mMic.setOnClickListener(v -> {
            setZone(Z_MIC);
            startVoice(0);
        });
        mMic.setElevation(Theme.pxf(FIELD_Z));
        mRoot.addView(mMic, ml);
        mField = new EditText(this);
        mField.setBackground(null);
        Theme.style(mField, 34, 450, Theme.TEXT1, 0);
        mField.setHintTextColor(Theme.TEXT3);
        mField.setHint(R.string.search_hint);
        mField.setSingleLine(true);
        mField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        mField.setGravity(Gravity.CENTER_VERTICAL);
        mField.setPadding(0, 0, 0, 0);
        mField.setTextCursorDrawable(R.drawable.cursor);
        mField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mRoot.removeCallbacks(mRun);
                mRoot.postDelayed(mRun, DEBOUNCE_MS);
            }
        });
        mField.setOnEditorActionListener((v, id, ev) -> {
            submitQuery();
            return true;
        });
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Theme.px(1560), Theme.px(88));
        fl.leftMargin = Theme.px(Tokens.MARGIN + 100);
        fl.topMargin = Theme.px(72);
        mField.setElevation(Theme.pxf(FIELD_Z));
        mRoot.addView(mField, fl);
        mStatus = new TextView(this);
        Theme.style(mStatus, Tokens.TYPE_BODY, 450, Theme.TEXT2, 0);
        mStatus.setSingleLine(true);
        mStatus.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Theme.px(1728), -2);
        sl.leftMargin = Theme.px(Tokens.MARGIN + 8);
        sl.topMargin = Theme.px(180);
        mRoot.addView(mStatus, sl);
        mResults = new FrameLayout(this);
        mResults.setClipChildren(false);
        mRoot.addView(mResults, new FrameLayout.LayoutParams(-1, -1));
        String kbLang = TvKeyboard.nativeLayout(this);
        if (kbLang != null) {
            mField.setShowSoftInputOnFocus(false);
            mKb = new TvKeyboard(this, kbLang, this);
            mKb.setVisibility(View.GONE);
            mRoot.addView(mKb);
        }
        setContentView(mRoot);
        showRecent();
    }

    private void showStatus(String s) {
        mStatus.setText(s == null ? "" : s);
    }

    private void setZone(int z) {
        mZone = z;
        mFieldBg.setRing(z == Z_FIELD);
        mFieldBg.setElevation(z == Z_FIELD ? Theme.pxf(10) : Theme.pxf(2));
        mMic.setFocusedState(z == Z_MIC);
        if (z == Z_FIELD) mField.requestFocus();
        else mField.clearFocus();
        for (int r = 0; r < mRows.size(); r++) {
            RowView rv = mRows.get(r);
            int it = mItem.get(r);
            for (int i = 0; i < rv.tiles().size(); i++) rv.tiles().get(i).setFocusedState(z == r + 1 && i == it, true);
            rv.scrollTo(Math.max(0, Math.min(it, rv.tiles().size() - rv.geometry().visible())), true);
        }
        layoutRows();
    }

    /** A row's height plus the gap; a row of results also keeps room for the focused tile's caption (title, app). */
    private float pitch(int r) {
        RowView rv = mRows.get(r);
        boolean caption = !rv.tiles().isEmpty() && rv.tiles().get(0).kind == TileView.REC;
        return rv.designHeight() + ROW_GAP + (caption ? CAPTION_H : 0);
    }

    private void layoutRows() {
        float y = 240;
        int focus = mZone - 1;
        for (int r = 0; r < mRows.size(); r++) {
            RowView rv = mRows.get(r);
            float pitch = pitch(r);
            rv.animate().translationY(Theme.pxf(y)).setDuration(Theme.PAGE_SCROLL_MS).setInterpolator(Theme.EMPHASIZED).start();
            y += pitch;
        }
        float shift = 0;
        if (focus >= 0) {
            float top = 240;
            for (int r = 0; r < focus; r++) top += pitch(r);
            float bottom = top + mRows.get(focus).designHeight();
            if (bottom > 1000) shift = 1000 - bottom;
        }
        mResults.animate().translationY(Theme.pxf(shift)).setDuration(Theme.PAGE_SCROLL_MS).setInterpolator(Theme.EMPHASIZED).start();
    }

    private void showIme() {
        if (mZone != Z_FIELD) setZone(Z_FIELD);
        mField.requestFocus();
        if (mKb != null) {
            if (mKb.getVisibility() != View.VISIBLE) {
                mKb.reset();
                mKb.setVisibility(View.VISIBLE);
                mKb.setAlpha(0f);
                mKb.setTranslationY(Theme.pxf(40));
                mKb.animate().translationY(0f).alpha(1f).setDuration(Theme.PANEL_MS).setInterpolator(Theme.EMPHASIZED).start();
            }
            return;
        }
        InputMethodManager im = getSystemService(InputMethodManager.class);
        mField.post(() -> im.showSoftInput(mField, 0));
    }

    private void hideIme() {
        if (mKb != null) {
            mKb.animate().cancel();
            mKb.setVisibility(View.GONE);
            return;
        }
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(mField.getWindowToken(), 0);
    }

    private boolean kbShown() {
        return mKb != null && mKb.getVisibility() == View.VISIBLE;
    }

    // ------------------------------------------------------------------ keyboard

    @Override
    public void onKbText(String t) {
        Editable ed = mField.getText();
        int a = Math.max(0, Math.min(mField.getSelectionStart(), mField.getSelectionEnd()));
        int b = Math.max(0, Math.max(mField.getSelectionStart(), mField.getSelectionEnd()));
        if (ed.length() + t.length() > 200) return;
        ed.replace(a, b, t);
        mField.setSelection(Math.min(ed.length(), a + t.length()));
    }

    @Override
    public void onKbBackspace() {
        Editable ed = mField.getText();
        int a = mField.getSelectionStart(), b = mField.getSelectionEnd();
        if (a < 0) a = ed.length();
        if (b < 0) b = a;
        if (a != b) ed.delete(Math.min(a, b), Math.max(a, b));
        else if (a > 0) ed.delete(Character.offsetByCodePoints(ed, a, -1), a);
    }

    @Override
    public void onKbAction() {
        submitQuery();
    }

    private void submitQuery() {
        String q = mField.getText().toString().trim();
        if (!q.isEmpty()) remember(q);
        mRoot.removeCallbacks(mRun);
        runSearch(q);
        hideIme();
        mRoot.postDelayed(() -> {
            if (!mRows.isEmpty()) setZone(1);
        }, 300);
    }

    // ------------------------------------------------------------------ search

    private void runSearch(String q) {
        q = q.trim();
        final int seq = ++mSeq;
        mQuery = q;
        if (q.isEmpty()) {
            showRecent();
            return;
        }
        final String fq = q;
        final Context c = getApplicationContext();
        App.get().io().post(() -> Safe.run("search", () -> {
            if (mApps == null) {
                HomeData d = HomeLoader.load(c, App.get());
                mApps = d.apps;
            }
            List<Card> apps = AppMatcher.match(mApps, fq, null, 12);
            List<SearchIn> in = searchIn(c);
            runOnUiThread(() -> {
                if (seq == mSeq) bindResults(fq, apps, new ArrayList<>(), in, false);
            });
            List<Card> sugg = SuggestProviders.query(c, fq);
            runOnUiThread(() -> {
                if (seq == mSeq) bindResults(fq, apps, sugg, in, true);
            });
        }));
    }

    private void bindResults(String q, List<Card> apps, List<Card> content, List<SearchIn> in, boolean done) {
        clearRows();
        if (!apps.isEmpty()) {
            ArrayList<TileView> t = new ArrayList<>();
            ArrayList<Runnable> a = new ArrayList<>();
            for (Card k : apps) {
                TileView v = new TileView(this, TileView.BANNER);
                v.setTitle(k.title, null);
                load(v, k.image, ImageLoader.KIND_BANNER, k.pkg, k.title);
                t.add(v);
                a.add(() -> open(k));
            }
            addRow(getString(R.string.search_apps), null, t, a);
        }
        content = Relevance.rank(content, k -> k.title, q);
        if (!content.isEmpty()) {
            ArrayList<String> labels = new ArrayList<>();
            for (Card k : content) if (!k.appLabel.isEmpty() && !labels.contains(k.appLabel)) labels.add(k.appLabel);
            ArrayList<TileView> t = new ArrayList<>();
            ArrayList<Runnable> a = new ArrayList<>();
            for (Card k : content) {
                TileView v = new TileView(this, TileView.REC);
                v.setTitle(k.title, k.appLabel);
                v.setPlaceholder(k.color != 0 ? k.color : 0xFF2A2F38);
                String u = k.image != null && ArtUrl.isKeyArt(k.image) ? ArtUrl.landscapeCard(k.image) : k.image;
                load(v, u, ImageLoader.KIND_POSTER, k.pkg, k.title);
                badge(v, k.pkg);
                t.add(v);
                a.add(() -> open(k));
            }
            addRow(getString(R.string.search_from_apps), TextUtils.join(", ", labels.subList(0, Math.min(3, labels.size()))), t, a);
        }
        if (!in.isEmpty()) {
            ArrayList<TileView> t = new ArrayList<>();
            ArrayList<Runnable> a = new ArrayList<>();
            for (SearchIn o : in) {
                TileView v = new TileView(this, TileView.INPUT);
                v.setTitle(o.label, o.searches ? q : getString(R.string.search_open_app));
                if (o.icon != null) v.setAppIcon(o.icon);
                t.add(v);
                a.add(() -> searchInApp(o, q));
            }
            addRow(getString(R.string.search_in), null, t, a);
        }
        if (done && apps.isEmpty() && content.isEmpty()) showStatus(getString(R.string.search_no_results));
        else if (mVoice == null || !mVoice.active()) showStatus("");
        if (mZone > mRows.size()) mZone = mRows.isEmpty() ? Z_FIELD : 1;
        setZone(mZone);
    }

    private void showRecent() {
        clearRows();
        List<String> rec = App.get().prefs().list(Prefs.K_RECENT_SEARCH);
        if (!rec.isEmpty()) {
            ArrayList<TileView> t = new ArrayList<>();
            ArrayList<Runnable> a = new ArrayList<>();
            for (String s : rec) {
                TileView v = new TileView(this, TileView.INPUT);
                v.setTitle(s, null);
                v.setIcon(getDrawable(R.drawable.ic_history).mutate());
                t.add(v);
                a.add(() -> {
                    mField.setText(s);
                    mField.setSelection(s.length());
                    mRoot.removeCallbacks(mRun);
                    runSearch(s);
                });
            }
            TileView clear = new TileView(this, TileView.INPUT);
            clear.setTitle(getString(R.string.search_clear), null);
            clear.setIcon(getDrawable(R.drawable.ic_trash).mutate());
            t.add(clear);
            a.add(() -> {
                App.get().prefs().putList(Prefs.K_RECENT_SEARCH, new ArrayList<>());
                showRecent();
                setZone(Z_FIELD);
            });
            addRow(getString(R.string.search_recent), null, t, a);
        }
        if (TvKeyboard.needsKeyboardApp(this)) {
            TileView v = new TileView(this, TileView.INPUT);
            v.setTitle(getString(R.string.kb_get_app, java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.getDefault())), null);
            v.setIcon(getDrawable(R.drawable.ic_keyboard).mutate());
            ArrayList<TileView> t = new ArrayList<>();
            t.add(v);
            ArrayList<Runnable> a = new ArrayList<>();
            a.add(this::openKeyboardApp);
            addRow(getString(R.string.kb_title), null, t, a);
        }
        if (mZone > mRows.size()) mZone = Z_FIELD;
        setZone(mZone);
    }

    private void clearRows() {
        mResults.removeAllViews();
        mRows.clear();
        mActs.clear();
        mItem.clear();
    }

    private void addRow(String title, String sub, List<TileView> tiles, List<Runnable> acts) {
        RowView rv = new RowView(this);
        rv.setTitle(title, sub);
        final int row = mRows.size();
        for (int i = 0; i < tiles.size(); i++) {
            final int idx = i;
            tiles.get(i).setOnClickListener(v -> {
                mItem.set(row, idx);
                setZone(row + 1);
                acts.get(idx).run();
            });
        }
        rv.setTiles(tiles);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.leftMargin = Theme.px(Tokens.MARGIN);
        mResults.addView(rv, lp);
        mRows.add(rv);
        mActs.add(acts);
        mItem.add(0);
    }

    private void load(TileView t, String uri, int kind, String pkg, String label) {
        if (uri == null || uri.isEmpty()) return;
        App.get().images().load(uri, Theme.px(t.designW()), Theme.px(t.designH()), kind, pkg, label, (b, c) -> {
            if (b != null) t.setBitmap(b);
        });
    }

    private void open(Card k) {
        if (!mQuery.isEmpty()) remember(mQuery);
        Launch.open(this, k);
    }

    private void remember(String q) {
        Prefs p = App.get().prefs();
        List<String> l = p.list(Prefs.K_RECENT_SEARCH);
        l.remove(q);
        l.add(0, q.length() > 80 ? q.substring(0, 80) : q);
        while (l.size() > 8) l.remove(l.size() - 1);
        p.putList(Prefs.K_RECENT_SEARCH, l);
    }

    /** One "Искать в" tile: the app, its real icon (never tinted), and whether the query goes along. */
    static final class SearchIn {
        final String pkg, label;
        final Drawable icon;
        final boolean searches;

        SearchIn(String pkg, String label, Drawable icon, boolean searches) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
            this.searches = searches;
        }
    }

    /**
     * "Искать в": the installed apps of the watch table (catalogue apps, Lampa first, then official services, then video
     * platforms, {@link WatchPick#searchOrder}), then the other TV apps that declare search (Play Store, Spotify). An app
     * without a search intent still gets a tile: it opens, the query stays here.
     */
    private List<SearchIn> searchIn(Context c) {
        if (mSearchIn != null) return mSearchIn;
        PackageManager pm = c.getPackageManager();
        ArrayList<String> others = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_SEARCH), 0)) {
            if (ri.activityInfo == null || !ri.activityInfo.exported) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || others.contains(pkg)) continue;
            if (pm.getLeanbackLaunchIntentForPackage(pkg) == null) continue;
            others.add(pkg);
        }
        WatchApps table = WatchLaunch.table(c);
        ArrayList<SearchIn> out = new ArrayList<>();
        for (String pkg : WatchPick.searchOrder(table, WatchLaunch.device(c), others)) {
            String label = pkg;
            Drawable icon = null;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                icon = pm.getApplicationIcon(pkg);
            } catch (Throwable ignored) {
            }
            WatchApps.Row r = table.of(pkg);
            if (r != null) label = r.name;
            boolean launchable = pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
            boolean searches = WatchLaunch.searchIn(c, pkg, "x") != null;
            if (searches || launchable) out.add(new SearchIn(pkg, label, icon, searches));
        }
        mSearchIn = out;
        return out;
    }

    private void searchInApp(SearchIn o, String q) {
        if (q.isEmpty()) return;
        remember(q);
        Intent i = WatchLaunch.searchIn(this, o.pkg, q);
        if (i != null) Launch.start(this, i, "search-in " + o.pkg);
        else Launch.openApp(this, o.pkg);
    }

    /** The icon of the app a result comes from, on the result's tile. */
    private void badge(TileView v, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        App.get().images().load("icon:" + pkg, Theme.px(40), Theme.px(40), ImageLoader.KIND_ICON, pkg, null, (b, c) -> {
            if (b != null) v.setBadge(b);
        });
    }

    private void openKeyboardApp() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + KEYBOARD_APP))
                .setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (getPackageManager().resolveActivity(i, 0) == null) {
            String hint = getString(R.string.kb_no_store);
            showStatus(hint);
            Launch.start(this, FilesLink.apps(), "files apps (keyboard)");
            return;
        }
        Launch.start(this, i, "keyboard app");
    }

    // ------------------------------------------------------------------ voice

    private void startVoice(long downUptime) {
        if (mVoice == null) mVoice = new VoiceInput(this, this);
        mVoiceDown = downUptime;
        hideIme();
        mVoice.start(downUptime > 0 ? downUptime : SystemClock.uptimeMillis());
    }

    /** MIC_UP (main thread): end of push-to-talk; a short tap (under TAP_MS) means keyboard search. */
    void onMicUp(long upUptime) {
        if (mVoice == null || !mVoice.active() || mVoiceDown <= 0 || upUptime < mVoiceDown) return;
        if (upUptime - mVoiceDown < TAP_MS) {
            mVoice.cancel();
            mMic.setListening(false);
            mField.setTextColor(Theme.TEXT1);
            showStatus(getString(R.string.voice_hold));
            showIme();
            return;
        }
        mVoice.stop();
    }

    @Override
    public void onListening() {
        mMic.setListening(true);
        showStatus(getString(R.string.voice_listening));
    }

    @Override
    public void onRms(float rmsDb) {
        mMic.setLevel(rmsDb);
    }

    @Override
    public void onPartial(String text) {
        mField.setTextColor(Theme.TEXT2);
        mField.setText(text);
        mField.setSelection(text.length());
    }

    @Override
    public void onFinal(String text) {
        mMic.setListening(false);
        mField.setTextColor(Theme.TEXT1);
        mField.setText(text);
        mField.setSelection(text.length());
        remember(text);
        showStatus("");
        mRoot.removeCallbacks(mRun);
        runSearch(text);
    }

    @Override
    public void onFailed(boolean remembered) {
        mMic.setListening(false);
        mField.setTextColor(Theme.TEXT1);
        showStatus(getString(R.string.voice_unavailable));
        showIme();
    }

    @Override
    public void onEnd() {
        mMic.setListening(false);
        mField.setTextColor(Theme.TEXT1);
        if (mField.length() == 0) showStatus(getString(R.string.voice_nothing_heard));
    }

    // ------------------------------------------------------------------ keys

    private static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER
                || k == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (kbShown() && mZone == Z_FIELD) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (!down) hideIme();
                return true;
            }
            if (mKb.onKey(k, e)) return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down && mZone > Z_FIELD) setZone(Z_FIELD);
            else if (!down) finish();
            return true;
        }
        if (mZone == Z_FIELD && !isOk(k) && k != KeyEvent.KEYCODE_DPAD_UP && k != KeyEvent.KEYCODE_DPAD_DOWN
                && !(k == KeyEvent.KEYCODE_DPAD_LEFT && mField.getSelectionStart() == 0 && mVoiceOk)) {
            return super.dispatchKeyEvent(e);
        }
        if (!down) {
            if (isOk(k)) ok();
            return isOk(k) || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                    || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT || super.dispatchKeyEvent(e);
        }
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mZone > Z_FIELD) setZone(mZone - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mZone == Z_MIC) setZone(Z_FIELD);
                else if (mZone < mRows.size()) {
                    hideIme();
                    setZone(mZone + 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                boolean right = k == KeyEvent.KEYCODE_DPAD_RIGHT;
                if (mZone == Z_FIELD && !right) setZone(Z_MIC);
                else if (mZone == Z_MIC && right) setZone(Z_FIELD);
                else if (mZone >= 1) {
                    int r = mZone - 1, n = mRows.get(r).tiles().size(), i = mItem.get(r) + (right ? 1 : -1);
                    if (i >= 0 && i < n) {
                        mItem.set(r, i);
                        setZone(mZone);
                    }
                }
                return true;
            }
            default:
                return isOk(k) || super.dispatchKeyEvent(e);
        }
    }

    private void ok() {
        if (mZone == Z_MIC) {
            startVoice(0);
        } else if (mZone == Z_FIELD) {
            if (kbShown()) return;
            showIme();
        } else {
            int r = mZone - 1;
            mActs.get(r).get(mItem.get(r)).run();
        }
    }

    /** The search field's box: the surface, and the Lumen focus ring (accent, gap) like every Home element. */
    private static final class FieldBox extends View {
        private final RingPaint mRing = new RingPaint();
        private boolean mOn;

        FieldBox(Context c) {
            super(c);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Theme.pxf(44));
            g.setColor(Theme.SURFACE);
            setBackground(g);
        }

        void setRing(boolean on) {
            mOn = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            mRing.draw(c, Theme.P.focusRing, 0, 0, getWidth(), getHeight(), Theme.pxf(44), Theme.pxf(1f), getScaleX(),
                    FocusRing.alpha(mOn, 1f));
        }
    }

    /** The round mic button: listening grows a soft ring with the voice level. */
    private static final class Mic extends View {
        private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable mIcon;
        private boolean mFocused, mListening;
        private float mLevel;

        Mic(Context c) {
            super(c);
            mIcon = c.getDrawable(R.drawable.ic_mic).mutate();
            setClickable(true);
        }

        void setFocusedState(boolean f) {
            mFocused = f;
            invalidate();
        }

        void setListening(boolean l) {
            mListening = l;
            mLevel = 0;
            invalidate();
        }

        void setLevel(float rmsDb) {
            mLevel = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 12f));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), r = w / 2f;
            if (mListening) {
                mP.setColor(Theme.alpha(Theme.ACCENT, 0.25f));
                c.drawCircle(r, r, r * (0.9f + 0.25f * mLevel), mP);
            }
            mP.setColor(mFocused || mListening ? Theme.FOCUS : Theme.alpha(Theme.TEXT1, 0.08f));
            c.drawCircle(r, r, r * 0.86f, mP);
            int s = Theme.px(34), o = (int) (r - s / 2f);
            mIcon.setTint(mFocused || mListening ? Theme.ON_FOCUS : Theme.TEXT1);
            mIcon.setBounds(o, o, o + s, o + s);
            mIcon.draw(c);
        }
    }
}
