/*
 * Screen casting: the one casting page of every model (2026-11-21: casting is one shared feature set on EVERY
 * model). Home shows it as a tile (LEANBACK_LAUNCHER), the menu's All settings row opens it (action
 * org.lumen.cast.SCREEN, lumen-menu ModuleScreens) and so does the screen mirroring popup.
 *
 * Left: the name phones and computers list, then the rows, named and ordered as the cards on the right (one text
 * each, cast_airplay / cast_miracast): AirPlay, Miracast, the code on the screen, ask before connecting, the device
 * name, then the AirPlay and Miracast picture rows and the licences with the source offer. This page is the only
 * settings page of both receivers: the old AirPlay settings screen is gone (its addresses org.lumen.airplay.SETTINGS
 * and MAIN + INFO are this activity's), the Miracast one only redirects here (org.lumen.miracast SettingsActivity).
 * Right: one card per receiver with a short how-to and its state:
 * AirPlay (this app), screen mirroring (org.lumen.miracast, read and switched through its control provider, only where
 * the image has the sink and the unit has Wi-Fi Direct) and DLNA (Lumen Play is the renderer). The decisions are
 * cast/CastRules (host tested), the model data cast/CastModels; this class only draws and forwards.
 *
 * Copyright (C) 2026 Lumen OS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.lumen.airplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.Manifest;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;

import org.lumen.airplay.cast.CardState;
import org.lumen.airplay.cast.CastRules;
import org.lumen.airplay.cast.DlnaProbe;
import org.lumen.airplay.cast.Receiver;
import org.lumen.icons.Icons;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class CastActivity extends Activity implements AirPlayService.Listener {
    static final String TAG = "LumenCast";
    /** The Miracast sink's control provider (apps/LumenMiracast StateProvider), signature permission CONTROL. */
    static final String MIRACAST = "org.lumen.miracast";
    private static final Uri MIRACAST_URI = Uri.parse("content://" + MIRACAST);
    /** Lumen Play, the DLNA renderer of every model (kino-player, com.xgimiru.player). */
    static final String PLAYER = "com.xgimiru.player";
    /** The renderer answers its description at once on the unit itself; a silent port reads as off after this. */
    private static final int PROBE_MS = 700;
    /** The receivers change state on their own (a phone connects): the screen reads them again this often. */
    private static final long REREAD_MS = 3_000;
    /** The extra of the old Miracast settings address: "name" opens the name field. */
    static final String EDIT = "edit";

    private SharedPreferences prefs;
    private AirPlayDialogs dialogs;
    private Palette pal;
    private float scale;
    private final SparseArray<Typeface> faces = new SparseArray<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread workerThread;
    private Handler worker;
    private boolean visible;

    /** What the sink reported last (read on the worker). */
    private static final class Sink {
        boolean present, answered, enabled, ask, mirroring;
        String code, name, detail, quality;
    }

    private Sink sink = new Sink();

    /** What Lumen Play's renderer showed last (read on the worker). */
    private static final class Player {
        boolean installed, enabled, network, answered;
        String name;
    }

    private Player player = new Player();
    private LinearLayout cards, rows;
    private TextView nameView, headline;
    private View firstRow;
    /** The label of the focused row, so a rebuilt row set keeps the focus on the same row. */
    private String focusedLabel;
    /** A choice row steps forward (OK, RIGHT) or back (LEFT). */
    private interface Step { void go(int dir); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        dialogs = new AirPlayDialogs(this, e -> { e.commit(); AirPlayService.reload(this); refresh(); });
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        workerThread = new HandlerThread("cast-state");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        askNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
    }

    /** Once: the status notification (another way back here) needs POST_NOTIFICATIONS. */
    private void askNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                || prefs.getBoolean(Prefs.NOTIF_ASKED, false)) {
            return;
        }
        prefs.edit().putBoolean(Prefs.NOTIF_ASKED, true).apply();
        try {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
        } catch (Throwable ignored) {
            // no permission UI on this image: the receiver works without the notification
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        pal = LumenTheme.palette(this);
        if (prefs.getBoolean(Prefs.ENABLED, Prefs.DEF_ENABLED)) AirPlayService.reload(this);
        AirPlayService.addListener(this);
        build();
        readSink();
        // the old Miracast address asks for the name field (Home's rename): open it at once, once
        if ("name".equals(getIntent().getStringExtra(EDIT))) {
            getIntent().removeExtra(EDIT);
            editProjectorName();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        AirPlayService.removeListener(this);
        main.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        workerThread.quitSafely();
    }

    @Override
    public void onAirPlayStateChanged() {
        refresh();
    }

    // ------------------------------------------------------------------ state

    private CardState airplayState() {
        AirPlayService s = AirPlayService.instance();
        boolean on = prefs.getBoolean(Prefs.ENABLED, Prefs.DEF_ENABLED);
        String server = s == null ? null : s.serverState.name();
        return CastRules.airplay(on, server, s != null && s.sessionActive(), s != null && s.notVisible());
    }

    private CardState sinkState() {
        Sink k = sink;
        return CastRules.miracast(k.present, k.answered, k.enabled, k.code, k.mirroring);
    }

    private CardState playerState() {
        Player p = player;
        return CastRules.dlna(p.installed, p.enabled, p.network, p.answered);
    }

    /** Lumen Play (worker thread only): the app's state, the network and its renderer's answer on this unit. */
    private Player readPlayer() {
        Player p = new Player();
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(PLAYER, PackageManager.MATCH_DISABLED_COMPONENTS);
            p.installed = true;
            p.enabled = ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return p;
        }
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        p.network = nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        if (!p.enabled) return p;
        String answer = describe();
        p.answered = DlnaProbe.renderer(answer);
        p.name = DlnaProbe.name(answer);
        return p;
    }

    /** The renderer's device description from this unit (worker thread only); null when nothing answers. */
    private static String describe() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", DlnaProbe.PORT), PROBE_MS);
            s.setSoTimeout(PROBE_MS);
            s.getOutputStream().write(DlnaProbe.REQUEST.getBytes(StandardCharsets.US_ASCII));
            InputStream in = s.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            for (int n; out.size() < 16384 && (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** The sink's and Lumen Play's state on the worker, then the screen; again every REREAD_MS while shown. */
    private void readSink() {
        worker.post(() -> {
            Sink k = query("state", null);
            Player p = readPlayer();
            main.post(() -> {
                sink = k;
                player = p;
                if (visible) refresh();
            });
        });
        main.postDelayed(this::readSink, REREAD_MS);
    }

    /** A call to the sink's control provider (worker thread only); a sink that is not there reads as absent. */
    private Sink query(String method, String arg) {
        Sink k = new Sink();
        try {
            ProviderInfo pi = getPackageManager().resolveContentProvider(MIRACAST, 0);
            k.present = pi != null && MIRACAST.equals(pi.packageName) && pi.applicationInfo != null
                    && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!k.present) return k;
            Bundle r = getContentResolver().call(MIRACAST_URI, method, arg, null);
            if (r == null) return k;
            k.answered = true;
            k.enabled = r.getBoolean("enabled");
            k.ask = r.getBoolean("ask");
            k.mirroring = r.getBoolean("mirroring");
            k.code = r.getString("code");
            k.name = r.getString("name");
            k.detail = r.getString("detail");
            k.quality = r.getString("quality");
        } catch (RuntimeException e) {
            Log.w(TAG, "screen mirroring " + method + ": " + e);
        }
        return k;
    }

    private void setSink(String method, boolean on) {
        setSink(method, on ? "1" : "0");
    }

    private void setSink(String method, String arg) {
        worker.post(() -> {
            Sink k = query(method, arg);
            main.post(() -> { sink = k; refresh(); });
        });
    }

    // ------------------------------------------------------------------ views

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pal.bg);
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setPadding(px(Tokens.MARGIN), px(80), px(Tokens.MARGIN), px(64));
        root.addView(cols, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        cols.addView(left, new LinearLayout.LayoutParams(px(720), -1));
        left.addView(text(getString(R.string.cast_title), Tokens.TYPE_SETUP_TITLE, Tokens.W_SETUP_TITLE, pal.text1));
        TextView sub = text(getString(R.string.cast_subtitle), Tokens.TYPE_BODY_L, Tokens.W_BODY_L, pal.text2);
        sub.setPadding(0, px(12), 0, 0);
        left.addView(sub);
        TextView cap = text(getString(R.string.cast_name_caption), Tokens.TYPE_CAPTION, Tokens.W_CAPTION, pal.text3);
        cap.setPadding(0, px(40), 0, 0);
        left.addView(cap);
        nameView = text("", 48, 700, pal.text1);
        left.addView(nameView);
        headline = text("", Tokens.TYPE_BODY, 520, pal.text2);
        headline.setPadding(0, px(8), 0, px(28));
        left.addView(headline);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);           // the rows under the edge fade, never a cut line
        scroll.setFadingEdgeLength(px(56));
        scroll.setClipToPadding(false);
        scroll.addView(rows, new FrameLayout.LayoutParams(-1, -2));
        left.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
        cl.leftMargin = px(64);
        cols.addView(cards, cl);
        setContentView(root);
        refresh();
        if (firstRow != null) firstRow.requestFocus();
    }

    /** Re-reads every value into the views (rows and cards are rebuilt only when their set changes). */
    private void refresh() {
        if (rows == null) return;
        CardState air = airplayState(), mira = sinkState(), dlna = playerState();
        String name = Prefs.name(this);
        AirPlayService s = AirPlayService.instance();
        if (s != null && s.serverState == ServerState.RUNNING && !s.advertisedName.isEmpty()) name = s.advertisedName;
        nameView.setText(name);
        Receiver busy = CastRules.inUse(air, mira);
        headline.setText(busy == Receiver.AIRPLAY ? getString(R.string.cast_busy_airplay)
                : busy == Receiver.MIRACAST ? getString(R.string.cast_busy_mirroring)
                : CastRules.allOff(air, mira) ? getString(R.string.cast_none_ready) : getString(R.string.cast_pick_hint));
        buildCards(name, air, mira, dlna);
        buildRows(mira);
    }

    private void buildCards(String name, CardState air, CardState mira, CardState dlna) {
        cards.removeAllViews();
        for (Receiver r : CastRules.cards(mira, dlna)) {
            switch (r) {
                case AIRPLAY:
                    card(R.drawable.ic_airplay, R.string.cast_airplay, R.string.cast_airplay_title,
                            getString(R.string.cast_airplay_how, name), air);
                    break;
                case MIRACAST:
                    String sinkName = sink.name == null || sink.name.isEmpty() ? name : sink.name;
                    // the sink names why it is not ready (Wi-Fi Direct restarting, not answering, connections that
                    // cannot finish on this system) instead of starting forever
                    card(org.lumen.icons.R.drawable.lumen_ic_tiles, R.string.cast_miracast, R.string.cast_mirroring_title,
                            CastRules.mirroringBody(mira, sink.detail, getString(R.string.cast_mirroring_how, sinkName)), mira);
                    break;
                default:
                    String playName = player.name == null ? name : player.name;
                    card(org.lumen.icons.R.drawable.lumen_ic_play, R.string.cast_dlna, R.string.cast_dlna_title,
                            dlna == CardState.OFF ? getString(R.string.cast_dlna_off) : getString(R.string.cast_dlna_how, playName),
                            dlna);
            }
        }
    }

    /** One receiver: its name (the same text as its row on the left), the devices it serves, the how-to, the state. */
    private void card(int icon, int title, int devices, String how, CardState st) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(36), px(28), px(36), px(28));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.surface);
        bg.setCornerRadius(px(Tokens.R_CARD));
        if (pal.dark) bg.setStroke(Math.max(1, px(1)), pal.panelLine);
        c.setBackground(bg);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = Icons.view(this, icon, 0);
        iv.setImageDrawable(Icons.drawable(this, icon, pal.text1));
        top.addView(iv, new LinearLayout.LayoutParams(px(44), px(44)));
        TextView t = text(getString(title), Tokens.TYPE_TITLE, Tokens.W_TITLE, pal.text1);
        t.setPadding(px(20), 0, px(12), 0);
        top.addView(t);
        top.addView(text(getString(devices), Tokens.TYPE_CAPTION, Tokens.W_CAPTION, pal.text3),
                new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(chip(st));
        c.addView(top);
        TextView h = text(how, Tokens.TYPE_BODY, Tokens.W_BODY, pal.text2);
        h.setLineSpacing(0, 1.2f);
        h.setPadding(0, px(16), 0, 0);
        c.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        if (cards.getChildCount() > 0) lp.topMargin = px(24);
        cards.addView(c, lp);
    }

    private TextView chip(CardState st) {
        TextView v = text(getString(stateText(st)), Tokens.TYPE_CAPTION, 600,
                CastRules.good(st) ? pal.ok : st == CardState.ERROR ? pal.error : pal.text2);
        v.setPadding(px(18), px(6), px(18), px(6));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(Tokens.R_BUTTON));
        g.setStroke(Math.max(1, px(2)), CastRules.good(st) ? pal.ok : pal.panelLine);
        v.setBackground(g);
        return v;
    }

    private static int stateText(CardState s) {
        switch (s) {
            case OFF: return R.string.cast_state_off;
            case STARTING: return R.string.cast_state_starting;
            case READY: return R.string.cast_state_ready;
            case NOT_VISIBLE: return R.string.cast_state_not_visible;
            case CONNECTED: return R.string.cast_state_connected;
            case NO_WIFI: return R.string.cast_state_no_wifi;
            default: return R.string.cast_state_error;
        }
    }

    /**
     * The rows, in the order of the cards: AirPlay, Miracast (where the image has the sink), the code on the screen,
     * ask before connecting, the device name; then the AirPlay picture rows, the Miracast picture row and the licences.
     */
    private void buildRows(CardState mira) {
        View had = getCurrentFocus();
        if (had != null && had.getTag() instanceof String) focusedLabel = (String) had.getTag();
        rows.removeAllViews();
        boolean sinkRows = CastRules.mirroringRows(mira);
        boolean on = prefs.getBoolean(Prefs.ENABLED, Prefs.DEF_ENABLED);
        int pm = new Prefs.Config(this).pinMode;
        firstRow = row(getString(R.string.cast_airplay), null, on ? 1 : 2, v -> { toggleAirPlay(this); refresh(); });
        if (sinkRows) row(getString(R.string.cast_miracast), null, sink.enabled ? 1 : 2, v -> setSink("set_enabled", !sink.enabled));
        choice(getString(R.string.cast_row_code), getString(pm == NativeBridge.PIN_RANDOM ? R.string.access_pin
                : pm == NativeBridge.PIN_FIXED ? R.string.access_fixed : R.string.access_off), this::stepCode);
        if (pm == NativeBridge.PIN_FIXED) {
            String fp = prefs.getString(Prefs.FIXED_PIN, "");
            row(getString(R.string.access_fixed), Prefs.validPin(fp) ? fp : "", 0, v -> dialogs.editFixedPin(false));
        }
        if (sinkRows) row(getString(R.string.cast_row_ask), null, sink.ask ? 1 : 2, v -> setSink("set_ask", !sink.ask));
        row(getString(R.string.set_name), Prefs.name(this), 0, v -> editProjectorName());
        header(getString(R.string.cast_airplay));
        boolean k4 = prefs.getBoolean(Prefs.QUALITY_4K, Prefs.DEF_QUALITY_4K);
        if (ModelData.get(this).mirror4k) {                   // the model data: only where the chip decodes 4K HEVC
            choice(getString(R.string.set_quality), getString(k4 ? R.string.quality_4k : R.string.quality_1080),
                    d -> save(prefs.edit().putBoolean(Prefs.QUALITY_4K, !k4)));
        }
        int lat = latencyIndex();
        choice(getString(R.string.set_latency), getString(LATENCY_TEXT[lat]),
                d -> save(prefs.edit().putInt(Prefs.LATENCY_MS, Prefs.LATENCIES[Math.floorMod(lat + d, LATENCY_TEXT.length)])));
        flag(R.string.set_takeover, Prefs.NOHOLD, Prefs.DEF_NOHOLD);
        flag(R.string.set_video_links, Prefs.VIDEO_URL, Prefs.DEF_VIDEO_URL);
        flag(R.string.set_music_screen, Prefs.MUSIC_SCREEN, Prefs.DEF_MUSIC_SCREEN);
        if (pm != NativeBridge.PIN_OFF || !Prefs.trusted(prefs).isEmpty()) {   // trusted devices exist only with a code
            row(getString(R.string.set_forget), getString(R.string.trusted_count, Prefs.trusted(prefs).size()), 0, v -> {
                AirPlayService.forgetTrusted(this);
                Toast.makeText(this, R.string.forget_done, Toast.LENGTH_SHORT).show();
                refresh();
            });
        }
        if (sinkRows) {
            header(getString(R.string.cast_miracast));
            choice(getString(R.string.set_quality), sink.quality == null ? "" : sink.quality,
                    d -> setSink("set_quality", d > 0 ? "1" : "-1"));
        }
        row(getString(R.string.cast_row_licences), null, 3, v -> dialogs.showLicences());
        View f = focusedLabel == null ? null : rows.findViewWithTag(focusedLabel);
        if (f != null && f.isFocusable()) f.requestFocus();
    }

    private static final int[] LATENCY_TEXT = {R.string.latency_low, R.string.latency_normal, R.string.latency_smooth};

    /** Prefs.LATENCIES index of the saved delay (normal when it is none of them). */
    private int latencyIndex() {
        int lat = Prefs.latencyMs(prefs);
        for (int i = 0; i < Prefs.LATENCIES.length; i++) if (Prefs.LATENCIES[i] == lat) return i;
        return 1;
    }

    /** The code on the screen: off, a code on first connection, a fixed code (asked for when none is saved yet). */
    private void stepCode(int dir) {
        int next = Math.floorMod(new Prefs.Config(this).pinMode + dir, 3);
        if (next == NativeBridge.PIN_FIXED && !Prefs.validPin(prefs.getString(Prefs.FIXED_PIN, null))) {
            dialogs.editFixedPin(true);
        } else {
            save(prefs.edit().putInt(Prefs.PIN_MODE, next));
        }
    }

    private void flag(int label, String key, boolean def) {
        boolean v = prefs.getBoolean(key, def);
        row(getString(label), null, v ? 1 : 2, x -> save(prefs.edit().putBoolean(key, !v)));
    }

    /** Saved at once, then the receiver picks the change up. */
    private void save(SharedPreferences.Editor e) {
        e.commit();
        AirPlayService.reload(this);
        refresh();
    }

    /** A value row: OK and RIGHT take the next choice, LEFT the one before. */
    private void choice(String label, String value, Step step) {
        View r = row(label, value, 0, v -> step.go(1));
        r.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (e.getAction() == KeyEvent.ACTION_UP) step.go(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            return true;
        });
    }

    /** The caption over a group of rows, as the menu's section headers. */
    private void header(String s) {
        TextView h = text(s, Tokens.TYPE_CAPTION, Tokens.W_CAPTION, pal.text3);
        h.setPadding(px(24), px(28), px(24), px(10));
        rows.addView(h, new LinearLayout.LayoutParams(-1, -2));
    }

    /**
     * One focusable row: the label, then a value, a switch ({@code kind} 1 = on, 2 = off) or a chevron (3). The focused
     * row is filled with the menu's focus colour under white text, as the rows of the quick menu (Palette.focusFill).
     */
    private View row(String label, String value, int kind, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(24), 0, px(20), 0);
        r.setFocusable(true);
        r.setClickable(true);
        r.setOnClickListener(click);
        String tag = label;
        for (int n = 2; rows.findViewWithTag(tag) != null; n++) tag = label + "#" + n;   // two groups, one label
        r.setTag(tag);
        TextView l = text(label, Tokens.TYPE_LABEL + 2, Tokens.W_LABEL, pal.text1);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = value == null ? null : text(value, Tokens.TYPE_LABEL, 500, pal.text2);
        if (v != null) r.addView(v);
        ImageView end = null;
        if (kind > 0) {
            int icon = kind == 1 ? org.lumen.icons.R.drawable.lumen_ic_toggle_on : kind == 2
                    ? org.lumen.icons.R.drawable.lumen_ic_toggle_off : org.lumen.icons.R.drawable.lumen_ic_chevron_right;
            end = Icons.view(this, icon, 0);
            // a switch says its state (accessibility and the key walk read it): the shared on / off texts
            if (kind != 3) end.setContentDescription(getString(kind == 1 ? R.string.lumen_on : R.string.lumen_off));
            r.addView(end, new LinearLayout.LayoutParams(px(kind == 3 ? 32 : 48), px(kind == 3 ? 32 : 48)));
        }
        final ImageView endIcon = end;
        final int endKind = kind;
        View.OnFocusChangeListener paint = (x, has) -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(Tokens.R_ROW));
            g.setColor(has ? pal.focusFill : 0);
            r.setBackground(g);
            l.setTextColor(has ? Color.WHITE : pal.text1);
            if (v != null) v.setTextColor(has ? Color.WHITE : pal.text2);
            if (endIcon != null) {
                int col = has ? Color.WHITE : endKind == 1 ? pal.accentOnBg : pal.text2;
                endIcon.setImageDrawable(Icons.drawable(this, endKind == 1 ? org.lumen.icons.R.drawable.lumen_ic_toggle_on
                        : endKind == 2 ? org.lumen.icons.R.drawable.lumen_ic_toggle_off
                        : org.lumen.icons.R.drawable.lumen_ic_chevron_right, col));
            }
        };
        r.setOnFocusChangeListener(paint);
        paint.onFocusChange(r, false);
        rows.addView(r, new LinearLayout.LayoutParams(-1, px(72)));
        return r;
    }

    private TextView text(String s, int size, int weight, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale);
        t.setTypeface(face(weight));
        t.setIncludeFontPadding(false);
        // the layout's direction, not the text's: an AirPlay label in an Arabic row still starts at the right
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    /** Inter at a weight (assets/fonts, as Home, Setup and the menu), the system font without the asset. */
    private Typeface face(int weight) {
        Typeface t = faces.get(weight);
        if (t == null) {
            try {
                t = new Typeface.Builder(getAssets(), "fonts/InterVariable.ttf")
                        .setFontVariationSettings("'wght' " + weight).build();
            } catch (RuntimeException e) {
                t = null;
            }
            if (t == null) t = Typeface.create(Typeface.DEFAULT, weight, false);
            faces.put(weight, t);
        }
        return t;
    }

    private int px(float design) {
        return Math.round(design * scale);
    }

    // ------------------------------------------------------------------ the name

    /**
     * The projector's name (Settings.Global device_name, as Android Settings writes it): AirPlay and screen mirroring
     * both follow it, so one name shows on every phone and computer. The AirPlay-only name is dropped; if the system
     * refuses the write, the name stays an AirPlay-only one (the old behaviour of the AirPlay settings).
     */
    private void editProjectorName() {
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setText(Prefs.name(this));
        et.setSelectAllOnFocus(true);
        FrameLayout f = new FrameLayout(this);
        f.setPadding(px(48), px(16), px(48), 0);
        f.addView(et);
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_name)
                .setView(f)
                .setPositiveButton(android.R.string.ok, (d, w) -> saveName(Prefs.sanitizeName(et.getText().toString())))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveName(String n) {
        if (n.isEmpty()) return;
        SharedPreferences.Editor e = prefs.edit();
        try {
            Settings.Global.putString(getContentResolver(), Settings.Global.DEVICE_NAME, n);
            e.remove(Prefs.NAME);
            // one name everywhere: the Miracast receiver drops a name of its own and lists this one
            setSink("follow_name", (String) null);
        } catch (SecurityException se) {
            Log.w(TAG, "device name: " + se);
            e.putString(Prefs.NAME, n);
        }
        e.commit();
        AirPlayService.reload(this);
        refresh();
    }

    /** The AirPlay switch: saved at once, the receiver starts or stops. */
    static boolean toggleAirPlay(Context c) {
        SharedPreferences p = Prefs.get(c);
        boolean on = !p.getBoolean(Prefs.ENABLED, Prefs.DEF_ENABLED);
        p.edit().putBoolean(Prefs.ENABLED, on).commit();
        AirPlayService.reload(c);
        return on;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
