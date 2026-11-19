// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The signal panel of every Lumen projector (README.md). The projector app installs its {@link SignalReader} at start;
 * the remote action and the menu's round button call {@link #press}. HAL reads run only while the panel is on screen,
 * at most SignalBudget.MAX_PER_SECOND per second. Main thread, except {@link #onTvEvent}.
 */
public final class SignalPanel {
    static final String TAG = "LumenSignal";
    /** The intent of the remote action (lumen-remote key_actions.tsv \"signal\") and of the menu's round button. */
    public static final String ACTION = "org.lumen.projector.action.SIGNAL_PANEL";
    private static final String PREFS = "lumen_signal", POPUP = "popup", PIN = "pin", REFRESH = "refresh";
    private static final long TICK_MS = 500L, PIN_MS = 1_000L;
    /** stock TvHandler: event 0 "status:3" = the HDMI signal is stable (a new input, a new format) */
    private static final int EVENT_SIGNAL = 0;
    private static final String STABLE = "status:3";
    /** Settings.Global.ENCODED_SURROUND_OUTPUT: the sound format of the HDMI and ARC outputs (0 auto .. 3 manual). */
    private static final String ENCODED_SURROUND = "encoded_surround_output";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final PanelToggle TOGGLE = new PanelToggle();
    private static Context sApp;
    private static SignalReader sReader;
    private static Set<SignalItem> sItems = EnumSet.noneOf(SignalItem.class);
    private static SignalBudget sBudget;
    private static SignalWindow sWindow;
    private static SignalValues sValues = new SignalValues();
    private static String sDrawn;
    private static long sAndroidAt;
    private static boolean sReading;
    /** the HDMI receiver report (HDMI_FORMAT): when to ask, what may show; the model's HDMI input to MStar port map */
    private static final FormatWatch FORMAT = new FormatWatch();
    private static int[] sPorts = SignalItems.ports(null);
    /** Another developer overlay that closes when the panel opens (lumen-sensors: the two never show together). */
    private static Runnable sOnOpen;
    /** the small card shows the hold hint this time (CardHold: the first few cards only) */
    private static boolean sHint;
    /** the card coming up now was asked for by the remote key the user gave it */
    private static boolean sByKey;
    /** the menu's words for the rows shared with it (picture mode, motion smoothing), null = those rows stay hidden */
    private static MenuWords sMenu;

    /** Holding OK on the card (CardHold): open, fold back, the panel's own OK. */
    private static final CardHold.Card CARD = new CardHold.Card() {
        @Override public PanelMode mode() { return TOGGLE.mode(); }

        @Override public void expand() {
            sHint = false;
            apply(TOGGLE.expand());
        }

        @Override public void collapse() { apply(TOGGLE.collapse()); }

        @Override public void sheetOk() { switchPopup(); }
    };

    /** A card shown by a signal change goes after POPUP_MS (again after the full panel folded back into it). */
    private static final Runnable POPUP_OVER = () -> { if (TOGGLE.mode() == PanelMode.POPUP) show(TOGGLE.popupOver()); };

    private SignalPanel() {}

    /**
     * App start, once: the model's reader (null = no panel on this model) and the menu's words for the rows the panel
     * shares with the menu (lumen-menu MenuSignalText).
     */
    public static void install(Context c, SignalReader reader, MenuWords menu) {
        sApp = c.getApplicationContext();
        sReader = reader;
        sMenu = menu;
        CardHold.start(sApp);
        String tsv = asset(sApp, SignalItems.ASSET);
        Set<SignalItem> model = SignalItems.parse(tsv);
        sItems = reader == null ? EnumSet.noneOf(SignalItem.class) : SignalItems.shown(model, reader.items());
        sPorts = SignalItems.ports(tsv);
        Set<SignalItem> hal = EnumSet.noneOf(SignalItem.class);
        for (SignalItem i : sItems) if (i.hal) hal.add(i);
        sBudget = new SignalBudget(hal);
        Log.i(TAG, "items " + sItems + (reader == null ? " (no reader)" : ""));
        try {
            sApp.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context ctx, Intent i) { hide(); }
            }, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
        } catch (Throwable t) {
            Log.w(TAG, "screen off: " + t);
        }
        if (available(sApp) && pinned(sApp)) pinChanged(true);
    }

    /** Registers the overlay that closes when this panel opens (one, the sensor card). */
    public static void onOpen(Runnable r) { sOnOpen = r; }

    /** True where the model reads the HDMI receiver itself (colour format, depth, colorimetry): the refresh choice. */
    public static boolean receiverInfo(Context c) {
        return available(c) && sItems.contains(SignalItem.HDMI_FORMAT);
    }

    /** The option "keep the signal card on screen" over HDMI (All settings, For developers), off by default. */
    public static boolean pinned(Context c) { return prefs(c).getBoolean(PIN, false); }

    public static void setPinned(Context c, boolean on) {
        prefs(c).edit().putBoolean(PIN, on).apply();
        Log.i(TAG, "keep the card on screen: " + on);
        MAIN.post(() -> pinChanged(on));
    }

    /** The refresh choice of the receiver report (SignalRefresh token), Normal by default. */
    public static String refresh(Context c) { return SignalRefresh.of(prefs(c).getString(REFRESH, null)).token; }

    public static void setRefresh(Context c, String token) {
        SignalRefresh r = SignalRefresh.of(token);
        prefs(c).edit().putString(REFRESH, r.token).apply();
        Log.i(TAG, "receiver refresh: " + r.token);
        MAIN.post(() -> FORMAT.refresh(r));
    }

    private static void pinChanged(boolean on) {
        if (sApp == null || sReader == null) return;
        MAIN.removeCallbacks(PIN_WATCH);
        PanelMode before = TOGGLE.mode();
        PanelMode now = TOGGLE.pin(on, sReader.hdmiPort() > 0);
        if (now != before) show(now);
        if (on) MAIN.postDelayed(PIN_WATCH, PIN_MS);
    }

    /** About once a second while the pin option is on: a field read, no vendor call. */
    private static final Runnable PIN_WATCH = new Runnable() {
        @Override public void run() {
            if (sReader == null || !TOGGLE.pinned()) return;
            PanelMode before = TOGGLE.mode();
            PanelMode now = TOGGLE.pinTick(sReader.hdmiPort() > 0, SystemClock.uptimeMillis());
            if (now != before) show(now);
            MAIN.postDelayed(this, PIN_MS);
        }
    };

    /** True where the model reads at least the input timing (the menu shows its round button). */
    public static boolean available(Context c) {
        return sReader != null && sItems.contains(SignalItem.HDMI_INFO);
    }

    /** The one button: card, full panel, hidden. False when this model has no panel. */
    public static boolean press(Context c) { return press(c, false); }

    /** {@code byKey}: the remote key the user gave the signal action (the card then shows the hold hint, OkHold.hint). */
    public static boolean press(Context c, boolean byKey) {
        if (!available(c)) return false;
        sByKey = byKey;
        show(TOGGLE.press());
        sByKey = false;
        return true;
    }

    /** Closes the panel (standby, light off, the screen goes off). */
    public static void hide() {
        TOGGLE.hide();
        show(PanelMode.HIDDEN);
    }

    /** A GmTvManager event (any thread): a stable new signal shows the card for 5 s when the user wants it. */
    public static void onTvEvent(int type, String msg) {
        if (type != EVENT_SIGNAL || msg == null || !msg.trim().startsWith(STABLE)) return;
        MAIN.post(() -> {
            Context c = sApp;
            if (c == null || !available(c) || TOGGLE.mode() != PanelMode.HIDDEN) return;
            if (TOGGLE.signalChanged(popupOn(c), sReader.hdmiPort() > 0) != PanelMode.POPUP) return;
            apply(PanelMode.POPUP);
        });
    }

    /** Shows {@code mode}; a popup card gets its time again. */
    private static void apply(PanelMode mode) {
        show(mode);
        MAIN.removeCallbacks(POPUP_OVER);
        if (mode == PanelMode.POPUP) MAIN.postDelayed(POPUP_OVER, PanelToggle.POPUP_MS);
    }

    private static void switchPopup() {
        boolean on = !popupOn(sApp);
        prefs(sApp).edit().putBoolean(POPUP, on).apply();
        Log.i(TAG, "show on signal change: " + on);
        draw(true);
    }

    /** A text of the panel's language (lumen-sensors shows the same hold hint). */
    public static String text(Context c, String key) { return words(c).get(key); }

    static boolean popupOn(Context c) {
        return prefs(c).getBoolean(POPUP, false);
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static void show(PanelMode mode) {
        MAIN.removeCallbacks(TICK);
        if (sApp != null) CardHold.shown(sApp, CARD, mode);
        if (!mode.visible()) {
            if (sWindow != null) sWindow.hide();
            sDrawn = null;
            FORMAT.reset();
            return;
        }
        if (sWindow == null) sWindow = new SignalWindow(sApp, new SignalWindow.Keys() {
            @Override public void back() { apply(TOGGLE.back()); }

            @Override public void switchPopup() { SignalPanel.switchPopup(); }

            @Override public void collapse() { CARD.collapse(); }
        });
        if (!sWindow.shown()) {
            sHint = mode.card() && CardHold.hint(sApp, sByKey);
            if (sHint) CardHold.hintShown(sApp);
            if (sOnOpen != null) sOnOpen.run();
            sValues = new SignalValues();
            sValues.port = sReader == null ? 0 : sReader.hdmiPort();
            sBudget.reset();
            FORMAT.reset();
            FORMAT.refresh(SignalRefresh.of(refresh(sApp)));
            sAndroidAt = SystemClock.uptimeMillis();
            readAndroid(sAndroidAt);
        }
        draw(true);
        TICK.run();
    }

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            PanelMode mode = TOGGLE.mode();
            if (!mode.visible() || sReader == null) return;
            long now = SystemClock.uptimeMillis();
            int port = sReader.hdmiPort();
            if (port != sValues.port) {
                sValues.port = port;
                sBudget.reset();
            }
            if (now - sAndroidAt >= SignalBudget.FAST_MS) {
                sAndroidAt = now;
                readAndroid(now);
            }
            if (sItems.contains(SignalItem.HDMI_FORMAT)) receiver(now, port);
            List<SignalItem> due = sReading ? new ArrayList<>() : sBudget.due(now, mode, port > 0);
            if (!due.isEmpty()) {
                sReading = true;
                final List<SignalItem> asked = due;
                sReader.read(asked, port, fresh -> {
                    sReading = false;
                    if (fresh != null && TOGGLE.mode().visible()) {
                        long at = SystemClock.uptimeMillis();
                        sValues.merge(fresh, asked, at);
                        if (sValues.ratePending()) sBudget.soon(SignalItem.HDMI_INFO, at, SteadyRate.CONFIRM_MS);
                        draw(false);
                    }
                });
            }
            draw(false);
            MAIN.postDelayed(this, TICK_MS);
        }
    };

    /**
     * The HDMI receiver report (FormatWatch): the signal now drops an old answer at once; a due request goes to the
     * bridge on its own thread; the answer shows only for the signal and the port on screen.
     */
    private static void receiver(long now, int port) {
        FORMAT.signal(FormatWatch.key(sValues), now);
        sValues.rx = FORMAT.shown();
        long id = FORMAT.due(TOGGLE.mode().visible(), now);
        if (id == 0) return;
        final int want = SignalItems.port(sPorts, port);
        HdmiRxBridge.fetch(MAIN, info -> {
            if (FORMAT.answer(id, info, want, sValues.width, sValues.height) && TOGGLE.mode().visible()) {
                sValues.rx = FORMAT.shown();
                draw(false);
            }
            if (info != null && info.port != want) Log.i(TAG, "hdmirx: port " + info.port + " is not HDMI " + port);
        });
    }

    private static void draw(boolean force) {
        PanelMode mode = TOGGLE.mode();
        SignalSheet s = SignalSheet.build(sValues, sItems, words(sApp));
        boolean hint = sHint && mode.card(), held = TOGGLE.held();
        String key = mode + "|" + popupOn(sApp) + "|" + hint + held + "|" + s.port + s.big + s.chips + s.verdict + s.sound
                + s.rows;
        if (!force && key.equals(sDrawn)) return;
        sDrawn = key;
        sWindow.show(mode, s, popupOn(sApp), hint, held);
    }

    /** The Android items: display mode and the media output, no vendor call. */
    private static void readAndroid(long now) {
        SignalValues v = new SignalValues();
        List<SignalItem> got = new ArrayList<>();
        try {
            Display d = sApp.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            Display.Mode m = d == null ? null : d.getMode();
            if (m != null) {
                v.screenWidth = m.getPhysicalWidth();
                v.screenHeight = m.getPhysicalHeight();
                v.screenRateCenti = Math.round(m.getRefreshRate() * 100f);
                got.add(SignalItem.SCREEN);
            }
        } catch (Throwable t) {
            Log.d(TAG, "display: " + t);
        }
        try {
            AudioManager am = sApp.getSystemService(AudioManager.class);
            List<AudioDeviceInfo> out = am.getAudioDevicesForAttributes(
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
            if (out != null && !out.isEmpty()) {
                v.audioOut = outToken(out.get(0).getType());
                got.add(SignalItem.AUDIO_OUT);
            }
        } catch (Throwable t) {
            Log.d(TAG, "audio: " + t);
        }
        try {
            v.soundFormat = SignalFormat.soundFormatToken(Settings.Global.getInt(sApp.getContentResolver(),
                    ENCODED_SURROUND, 0));
            got.add(SignalItem.SOUND_FORMAT);
        } catch (Throwable t) {
            Log.d(TAG, "sound format: " + t);
        }
        sValues.merge(v, got, now);
    }

    static String outToken(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return SignalFormat.OUT_SPEAKER;
            case AudioDeviceInfo.TYPE_HDMI_ARC: return SignalFormat.OUT_ARC;
            case AudioDeviceInfo.TYPE_HDMI_EARC: return SignalFormat.OUT_EARC;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_BLE_BROADCAST: return SignalFormat.OUT_BT;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: return SignalFormat.OUT_USB;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
            case AudioDeviceInfo.TYPE_AUX_LINE: return SignalFormat.OUT_WIRED;
            case AudioDeviceInfo.TYPE_HDMI: return SignalFormat.OUT_HDMI;
            default: return SignalFormat.OUT_OTHER;
        }
    }

    /**
     * The texts of the current language (string resources lumen_signal_*). The menu's words come in the panel's own
     * language: the system one when the panel is translated into it, else English (one language on the panel).
     */
    static SignalText.Words words(Context c) {
        return new SignalText.Words() {
            private Context menuCtx;

            @Override public String get(String key, Object... args) {
                int id = c.getResources().getIdentifier("lumen_signal_" + key, "string", c.getPackageName());
                if (id == 0) return key;
                return args.length == 0 ? c.getString(id) : c.getString(id, args);
            }

            @Override public String menuTitle(String feature) {
                return sMenu == null ? null : sMenu.title(menuContext(), feature);
            }

            @Override public String menuOption(String feature, String token) {
                return sMenu == null ? null : sMenu.option(menuContext(), feature, token);
            }

            private Context menuContext() {
                if (menuCtx == null) menuCtx = panelLanguage(c);
                return menuCtx;
            }

            @Override public char decimal() {
                Locale l = c.getResources().getConfiguration().getLocales().get(0);
                return new java.text.DecimalFormatSymbols(l).getDecimalSeparator();
            }
        };
    }

    /** {@code c} when the panel's own texts are in its language, else {@code c} in English (the panel's fallback). */
    private static Context panelLanguage(Context c) {
        Locale l = c.getResources().getConfiguration().getLocales().get(0);
        if (l == null || "en".equals(l.getLanguage())) return c;
        android.content.res.Configuration en = new android.content.res.Configuration(c.getResources().getConfiguration());
        en.setLocale(Locale.ENGLISH);
        Context english = c.createConfigurationContext(en);
        int id = c.getResources().getIdentifier("lumen_signal_" + SignalText.TITLE, "string", c.getPackageName());
        if (id == 0) return english;
        return c.getString(id).equals(english.getString(id)) ? english : c;
    }

    private static String asset(Context c, String name) {
        try (InputStream in = c.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Throwable t) {
            Log.i(TAG, "no " + name + ": " + t);
            return null;
        }
    }
}
