// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package lumen.fake;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.view.InputDevice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A fake Android Bluetooth world on virtual time for the remote pairing tests (host only, no device): the adapter,
 * remotes that advertise, bond, pair and bring their HID input device up, the screen, the setup flags, the runtime
 * permissions, broadcasts to registered receivers and one loop that runs every Handler post in time order. Every
 * decision of the code under test lands in {@link #events} ("createBond", "removeBond", "confirm", "hid-allow", "scan",
 * "notify", "prompt"), which the scenarios compare.
 */
public final class World {
    private World() {}

    public static long now;
    public static boolean btOn, screenOn, audio, selfGrantWorks, verbose, hidAutoConnect;
    public static String ownName;
    public static String bootReason;
    public static int pairVariant;
    public static int promptAnswer;
    public static final Map<String, String> props = new HashMap<>();
    public static final Map<String, Integer> secure = new HashMap<>();
    public static final Set<String> perms = new HashSet<>();
    public static final List<String> events = new ArrayList<>();
    public static final BluetoothAdapter adapter = new BluetoothAdapter();
    static final Map<String, BluetoothDevice> devices = new LinkedHashMap<>();
    static final Map<String, Remote> remotes = new LinkedHashMap<>();
    static final Map<String, Prefs> prefs = new HashMap<>();
    static final List<Object[]> receivers = new ArrayList<>();
    static final List<InputDevice> inputs = new ArrayList<>();
    static final TreeMap<Long, Task> loop = new TreeMap<>();
    static long seq;
    static ScanCallback scanCb;
    static int scanMode = -9;
    static final Context CTX = new Context();

    /** A remote in the room: its advertisement and how it behaves. */
    public static final class Remote {
        public final String addr, name;
        public final int vid, pid;
        public int flags = 0x05;                 // LE Limited Discoverable + BR/EDR not supported (pairing mode)
        public boolean hidUuid = true, connectable = true, pairing, sleeping, lostKeys;
        public int rssi = -50;
        /** manufacturer data of company 0x000D in the advertisement (the stock RcPair pairing mark), null = none */
        public byte[] xgimiData;
        Remote(String addr, String name, int vid, int pid) { this.addr = addr; this.name = name; this.vid = vid; this.pid = pid; }
    }

    static final class Task {
        final Handler h; final Runnable r;
        Task(Handler h, Runnable r) { this.h = h; this.r = r; }
    }

    public static void reset() {
        now = 1_000; btOn = true; screenOn = true; audio = false; selfGrantWorks = true; verbose = false;
        hidAutoConnect = true; ownName = "XGIMI Z9X"; bootReason = null; pairVariant = 3; promptAnswer = 0;
        props.clear(); secure.clear(); perms.clear(); events.clear(); devices.clear(); remotes.clear(); prefs.clear();
        receivers.clear(); inputs.clear(); loop.clear(); seq = 0; scanCb = null; scanMode = -9;
        perms.add("android.permission.BLUETOOTH_SCAN");
        perms.add("android.permission.BLUETOOTH_CONNECT");
    }

    public static Context context() { return CTX; }

    public static void event(String e) { events.add(String.format("%8.1f %s", (now - 1_000) / 1000.0, e)); }

    public static int androidLog(String lvl, String tag, String m) {
        if (verbose) System.out.println(lvl + " " + tag + ": " + m);
        return 0;
    }

    // ------------------------------------------------------------------ loop

    public static void post(Handler h, Runnable r, long delay) {
        long t = now + Math.max(0, delay);
        loop.put(t * 1_000_000 + (seq++ % 1_000_000), new Task(h, r));
    }

    public static void remove(Handler h, Runnable r) { loop.values().removeIf(t -> t.r == r); }

    public static void removeAll(Handler h) { loop.values().removeIf(t -> t.h == h); }

    /** Runs every task due within ms of virtual time. */
    public static void run(long ms) {
        long end = now + ms;
        while (!loop.isEmpty()) {
            long key = loop.firstKey();
            long t = key / 1_000_000;
            if (t > end) break;
            Task task = loop.remove(key);
            now = Math.max(now, t);
            task.r.run();
        }
        now = end;
    }

    // ------------------------------------------------------------------ services, permissions, prefs

    @SuppressWarnings("unchecked")
    public static <T> T service(Class<T> c) {
        if (c == BluetoothManager.class) return (T) new BluetoothManager();
        if (c == PowerManager.class) return (T) new PowerManager();
        if (c == InputManager.class) return (T) new InputManager();
        return null;
    }

    public static boolean granted(String p) { return perms.contains(p); }

    public static void selfGrant(String p) {
        if (!selfGrantWorks) throw new SecurityException("no GRANT_RUNTIME_PERMISSIONS");
        if (perms.add(p)) event("self-grant " + p.substring(p.lastIndexOf('.') + 1));
    }

    public static String string(int id) { return "@" + id; }

    public static SharedPreferences prefs(String n) { return prefs.computeIfAbsent(n, k -> new Prefs()); }

    // ------------------------------------------------------------------ receivers

    public static void register(BroadcastReceiver r, IntentFilter f) { receivers.add(new Object[] {r, f}); }

    public static void unregister(BroadcastReceiver r) { receivers.removeIf(o -> o[0] == r); }

    /** Delivers to every receiver registered for the action (on the loop, as Android does); ordered: aborts stop it. */
    public static void send(Intent i, boolean ordered) {
        for (Object[] o : new ArrayList<>(receivers)) {
            if (!((IntentFilter) o[1]).hasAction(i.getAction())) continue;
            boolean aborted = ((BroadcastReceiver) o[0]).deliver(CTX, i, ordered);
            if (aborted && ordered) {
                event("aborted " + i.getAction().substring(i.getAction().lastIndexOf('.') + 1));
                return;
            }
        }
        if (ordered && BluetoothDevice.ACTION_PAIRING_REQUEST.equals(i.getAction())) event("system-pairing-dialog");
    }

    // ------------------------------------------------------------------ the radio

    public static Remote remote(String addr, String name, int vid, int pid) {
        Remote r = new Remote(addr, name, vid, pid);
        remotes.put(addr, r);
        devices.put(addr, new BluetoothDevice(addr, name));
        return r;
    }

    public static BluetoothDevice device(String a) {
        return devices.computeIfAbsent(a, k -> new BluetoothDevice(k, null));
    }

    public static Set<BluetoothDevice> bonded() {
        if (!btOn) return null;
        if (!perms.contains("android.permission.BLUETOOTH_CONNECT")) throw new SecurityException("BLUETOOTH_CONNECT");
        Set<BluetoothDevice> s = new java.util.LinkedHashSet<>();
        for (BluetoothDevice d : devices.values()) if (d.bond == BluetoothDevice.BOND_BONDED) s.add(d);
        return s;
    }

    public static void scanStart(ScanCallback cb, int mode) {
        if (!perms.contains("android.permission.BLUETOOTH_SCAN")) throw new SecurityException("BLUETOOTH_SCAN");
        scanCb = cb;
        scanMode = mode;
        event("scan " + modeName(mode));
    }

    public static void scanStop(ScanCallback cb) {
        if (scanCb != cb) return;
        scanCb = null;
        scanMode = -9;
        event("scan stop");
    }

    public static String scanState() { return scanCb == null ? "off" : modeName(scanMode); }

    static String modeName(int m) {
        switch (m) {
            case 0: return "low_power";
            case 1: return "balanced";
            case 2: return "low_latency";
            default: return "m" + m;
        }
    }

    /** One advertisement of r, delivered to the running scan (the binder thread pre-filter of the code runs here). */
    public static void advertise(Remote r) {
        if (scanCb == null || !btOn) return;
        List<ParcelUuid> u = new ArrayList<>();
        if (r.hidUuid) u.add(ParcelUuid.fromString("00001812-0000-1000-8000-00805f9b34fb"));
        android.util.SparseArray<byte[]> mfr = null;
        if (r.xgimiData != null) {
            mfr = new android.util.SparseArray<>();
            mfr.put(0x000d, r.xgimiData);
        }
        ScanRecord rec = new ScanRecord(r.name, r.flags, u, mfr);
        scanCb.onScanResult(1, new ScanResult(devices.get(r.addr), rec, r.rssi, r.connectable));
    }

    /** r keeps advertising pairing mode every second until it is bonded (or sleeps). */
    public static void pairingMode(Remote r, boolean on) {
        r.pairing = on;
        if (on) {
            Handler h = new Handler();
            Runnable[] tick = new Runnable[1];
            tick[0] = () -> {
                BluetoothDevice d = devices.get(r.addr);
                if (!r.pairing) return;
                if (d.bond == BluetoothDevice.BOND_BONDED && d.connected) { r.pairing = false; return; }
                advertise(r);
                post(h, tick[0], 1_000);
            };
            post(h, tick[0], 200);
        }
    }

    public static boolean createBond(BluetoothDevice d, int transport) {
        event("createBond " + d.addr + " " + (transport == 2 ? "LE" : transport == 0 ? "auto" : "t" + transport));
        if (d.bond != BluetoothDevice.BOND_NONE) return false;
        d.bond = BluetoothDevice.BOND_BONDING;
        bondBroadcast(d, BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_NONE);
        Remote r = remotes.get(d.addr);
        Handler h = new Handler();
        post(h, () -> {
            if (r == null || !r.pairing) {                 // nobody there in pairing mode: the attempt fails
                d.bond = BluetoothDevice.BOND_NONE;
                bondBroadcast(d, BluetoothDevice.BOND_NONE, BluetoothDevice.BOND_BONDING);
                return;
            }
            Intent i = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
            i.putExtra(BluetoothDevice.EXTRA_DEVICE, (Object) d);
            i.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, pairVariant);
            int before = events.size();
            send(i, true);
            boolean confirmed = false;
            for (int k = before; k < events.size(); k++) confirmed |= events.get(k).endsWith("confirm " + d.addr + " true");
            if (!confirmed) return;                          // waits for a user in the system dialog: stays BONDING
            post(h, () -> {
                d.bond = BluetoothDevice.BOND_BONDED;
                r.lostKeys = false;
                bondBroadcast(d, BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_BONDING);
                if (hidAutoConnect) post(h, () -> linkUp(d), 800);
            }, 400);
        }, 500);
        return true;
    }

    public static boolean removeBond(BluetoothDevice d) {
        event("removeBond " + d.addr);
        if (d.bond != BluetoothDevice.BOND_BONDED) return false;
        linkDown(d);
        d.bond = BluetoothDevice.BOND_NONE;
        post(new Handler(), () -> bondBroadcast(d, BluetoothDevice.BOND_NONE, BluetoothDevice.BOND_BONDED), 300);
        return true;
    }

    static void bondBroadcast(BluetoothDevice d, int s, int prev) {
        Intent i = new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, (Object) d);
        i.putExtra(BluetoothDevice.EXTRA_BOND_STATE, s);
        i.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, prev);
        send(i, false);
    }

    /** The remote's link and its HID input device come up (it woke, or the host connected it). */
    public static void linkUp(BluetoothDevice d) {
        Remote r = remotes.get(d.addr);
        if (d.connected || d.bond != BluetoothDevice.BOND_BONDED || r == null || r.sleeping || r.lostKeys) return;
        d.connected = true;
        r.pairing = false;
        Intent i = new Intent(BluetoothDevice.ACTION_ACL_CONNECTED);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, (Object) d);
        send(i, false);
        post(new Handler(), () -> {
            if (d.connected) inputs.add(new InputDevice(r.name == null || r.name.startsWith("XGIMI RC") ? "XGIMI RC Keyboard" : r.name, d.addr, r.vid, r.pid, true));
        }, 500);
    }

    public static void linkDown(BluetoothDevice d) {
        if (!d.connected) return;
        d.connected = false;
        inputs.removeIf(x -> d.addr.equals(x.address));
        Intent i = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, (Object) d);
        send(i, false);
    }

    /** A bonded remote that already exists at boot (from an earlier run). */
    public static Remote bondedRemote(String addr, String name, int vid, int pid, boolean awake) {
        Remote r = remote(addr, name, vid, pid);
        BluetoothDevice d = devices.get(addr);
        d.bond = BluetoothDevice.BOND_BONDED;
        r.sleeping = !awake;
        if (awake) {
            d.connected = true;
            inputs.add(new InputDevice(name.startsWith("XGIMI RC") ? "XGIMI RC Keyboard" : name, addr, vid, pid, true));
        }
        return r;
    }

    /** A sleeping bonded remote wakes (a key press): the host's background connection brings its link up. */
    public static void wake(Remote r) {
        r.sleeping = false;
        BluetoothDevice d = devices.get(r.addr);
        if (d.bond == BluetoothDevice.BOND_BONDED) post(new Handler(), () -> linkUp(d), 1_000);
    }

    /** An app broadcast (the setup events of the X50 code): counted, the last one kept, not a decision. */
    public static void broadcast(Intent i) {
        Hooks.setupEvents++;
        Hooks.lastState = String.valueOf(i.getExtras());
    }

    public static boolean hidAllow(BluetoothDevice d, int policy) {
        event("hid-allow " + d.addr);
        post(new Handler(), () -> linkUp(d), 300);
        return true;
    }

    public static int hidState(BluetoothDevice d) { return d.connected ? BluetoothProfile.STATE_CONNECTED : 0; }

    public static boolean proxy(BluetoothProfile.ServiceListener l, int p) {
        if (p != BluetoothProfile.HID_HOST) return false;
        post(new Handler(), () -> l.onServiceConnected(p, new BluetoothHidHost()), 10);
        return true;
    }

    public static int[] inputIds() {
        int[] ids = new int[inputs.size()];
        for (int k = 0; k < ids.length; k++) ids[k] = k + 1;
        return ids;
    }

    public static InputDevice input(int id) { return id >= 1 && id <= inputs.size() ? inputs.get(id - 1) : null; }

    public static void screen(boolean on) {
        screenOn = on;
        send(new Intent(on ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF), false);
    }

    public static void bt(boolean on) {
        btOn = on;
        if (!on && scanCb != null) {                       // the stack ends every scan when the adapter goes off
            scanCb = null;
            scanMode = -9;
            event("scan stop (adapter off)");
        }
        Intent i = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
        i.putExtra(BluetoothAdapter.EXTRA_STATE, on ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF);
        send(i, false);
    }

    public static void setupDone(boolean done) {
        secure.put("user_setup_complete", done ? 1 : 0);
        secure.put("tv_user_setup_complete", done ? 1 : 0);
    }

    /** In-memory SharedPreferences. */
    static final class Prefs implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String k, Set<String> d) { Object o = m.get(k); return o == null ? d : new HashSet<>((Set<String>) o); }
        public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o == null ? d : (Boolean) o; }
        public int getInt(String k, int d) { Object o = m.get(k); return o == null ? d : (Integer) o; }
        public long getLong(String k, long d) { Object o = m.get(k); return o == null ? d : (Long) o; }
        public String getString(String k, String d) { Object o = m.get(k); return o == null ? d : (String) o; }
        public boolean contains(String k) { return m.containsKey(k); }
        public Editor edit() {
            Map<String, Object> put = new HashMap<>();
            Set<String> rm = new HashSet<>();
            return new Editor() {
                public Editor putStringSet(String k, Set<String> v) { put.put(k, new HashSet<>(v)); return this; }
                public Editor putBoolean(String k, boolean v) { put.put(k, v); return this; }
                public Editor putInt(String k, int v) { put.put(k, v); return this; }
                public Editor putLong(String k, long v) { put.put(k, v); return this; }
                public Editor putString(String k, String v) { put.put(k, v); return this; }
                public Editor remove(String k) { rm.add(k); return this; }
                public void apply() { commit(); }
                public boolean commit() { for (String k : rm) m.remove(k); m.putAll(put); return true; }
            };
        }
    }
}
