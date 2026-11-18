package parity;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Everything the page can see or change, reset for every scenario; every visible change is an event. */
public final class World {
    public static HdmiControlManager hdmi;
    public static boolean hdmiThrows;
    public static Set<String> hdmiSetThrows = new HashSet<>();
    public static TvInputManager tim;
    public static List<HdmiDeviceInfo> devices = new ArrayList<>();
    public static List<TvInputInfo> inputs = new ArrayList<>();
    public static Map<String, String> props = new TreeMap<>();
    public static Set<String> providers = new HashSet<>();
    public static Map<String, Boolean> tvPrefs = new TreeMap<>();
    public static boolean tvSetFails;
    public static String deviceName;
    public static boolean deviceNameThrows;
    public static boolean paused;
    public static boolean setupDone = true;
    /** The vendor HAL of a driver that has one: name -> value, null = the call fails. */
    public static Map<String, Boolean> hal = new TreeMap<>();
    public static final ContentResolver resolver = new ContentResolver();
    public static final PackageManager pm = new PackageManager();
    private static final Map<String, Prefs> PREFS = new TreeMap<>();
    private static final Map<String, ArrayDeque<Runnable>> QUEUES = new LinkedHashMap<>();
    static Map<String, String> texts = new HashMap<>();
    static Map<Integer, String> names = new HashMap<>();
    static final List<String> EVENTS = new ArrayList<>();
    static String thread = "test";

    private World() { }

    static void reset() {
        hdmi = new HdmiControlManager();
        hdmiThrows = false;
        hdmiSetThrows = new HashSet<>();
        tim = new TvInputManager();
        devices = new ArrayList<>();
        inputs = new ArrayList<>();
        props = new TreeMap<>();
        providers = new HashSet<>();
        tvPrefs = new TreeMap<>();
        tvSetFails = false;
        deviceName = null;
        deviceNameThrows = false;
        paused = false;
        setupDone = true;
        hal = new TreeMap<>();
        PREFS.clear();
        QUEUES.clear();
        EVENTS.clear();
        thread = "test";
    }

    public static void event(String e) { EVENTS.add("[" + thread + "] " + e); }

    public static String text(int id) {
        String n = names.get(id);
        String t = n == null ? null : texts.get(n);
        return t == null ? "<missing " + n + ">" : t;
    }

    public static void post(String q, Runnable r, long ms) {
        QUEUES.computeIfAbsent(q, k -> new ArrayDeque<>()).add(r);
        if (ms > 0) event("post " + q + " after " + ms + " ms");
    }

    public static void remove(String q, Runnable r) {
        ArrayDeque<Runnable> d = QUEUES.get(q);
        if (d != null) d.remove(r);
    }

    /** Runs every queue until all are empty: background queues first, then main, one task at a time. */
    static void drain() {
        for (int guard = 0; guard < 10_000; guard++) {
            String pick = null;
            for (Map.Entry<String, ArrayDeque<Runnable>> e : QUEUES.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                if (!"main".equals(e.getKey())) { pick = e.getKey(); break; }
                if (pick == null) pick = e.getKey();
            }
            if (pick == null) { thread = "test"; return; }
            Runnable r = QUEUES.get(pick).poll();
            String was = thread;
            thread = pick;
            r.run();
            thread = was;
        }
        throw new IllegalStateException("queues never drain");
    }

    public static SharedPreferences prefs(String name) { return PREFS.computeIfAbsent(name, Prefs::new); }

    public static Bundle providerCall(Uri uri, String method, String arg, Bundle extras) {
        event("provider " + uri.getAuthority() + " " + method + (arg == null ? "" : " " + arg)
                + (extras == null ? "" : " " + extras));
        if (!providers.contains(uri.getAuthority())) throw new IllegalArgumentException("Unknown authority " + uri);
        if ("get_cec_prefs".equals(method)) {
            Bundle b = new Bundle();
            for (Map.Entry<String, Boolean> e : tvPrefs.entrySet()) b.putBoolean(e.getKey(), e.getValue());
            return b;
        }
        if ("set_cec_pref".equals(method)) {
            if (tvSetFails) return null;
            tvPrefs.put(arg, extras.getBoolean("value"));
            Bundle r = new Bundle();
            r.putBoolean("ok", true);
            return r;
        }
        return null;
    }

    /** A vendor HAL call of a driver (fake): logged, null = it fails. */
    public static Boolean halCall(String what, Boolean arg) {
        event("hal." + what + "(" + (arg == null ? "" : arg) + ")");
        if (arg != null) {
            if (hal.containsKey(what) && hal.get(what) == null) return null;
            return Boolean.TRUE;
        }
        return hal.get(what);
    }

    private static final class Prefs implements SharedPreferences {
        private final String name;
        private final Map<String, Object> m = new TreeMap<>();
        Prefs(String name) { this.name = name; }
        @Override public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v instanceof Boolean ? (Boolean) v : d; }
        @Override public int getInt(String k, int d) { Object v = m.get(k); return v instanceof Integer ? (Integer) v : d; }
        @Override public Editor edit() {
            Map<String, Object> pending = new TreeMap<>();
            return new Editor() {
                @Override public Editor putBoolean(String k, boolean v) { pending.put(k, v); return this; }
                @Override public Editor putInt(String k, int v) { pending.put(k, v); return this; }
                @Override public void apply() { commit(); }
                @Override public boolean commit() {
                    for (Map.Entry<String, Object> e : pending.entrySet()) event("prefs " + name + " " + e.getKey() + "=" + e.getValue());
                    m.putAll(pending);
                    return true;
                }
            };
        }
    }
}
