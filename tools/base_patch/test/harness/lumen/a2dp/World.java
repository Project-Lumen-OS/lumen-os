package lumen.a2dp;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Bluetooth side of the TvSettings A2DP connector on a virtual clock: the profile proxy arriving late, the
 * UUID broadcast, the A2DP state broadcasts of the speaker and what the connector reports.
 */
public final class World {
    private static final class Task {
        final long at;
        final long seq;
        final Handler handler;
        final int what;
        final Runnable run;

        Task(long at, long seq, Handler handler, int what, Runnable run) {
            this.at = at;
            this.seq = seq;
            this.handler = handler;
            this.what = what;
            this.run = run;
        }
    }

    private static final List<Task> QUEUE = new ArrayList<>();
    private static final List<BroadcastReceiver> RECEIVERS = new ArrayList<>();
    private static final List<IntentFilter> FILTERS = new ArrayList<>();
    private static final Set<BluetoothDevice> CONNECTED = new LinkedHashSet<>();
    private static final List<String> EVENTS = new ArrayList<>();
    private static final BluetoothA2dp PROXY = new BluetoothA2dp();
    private static final Context CONTEXT = new Context();
    private static long sNow;
    private static long sSeq;

    /** ms from openConnection to onServiceConnected of the A2DP proxy */
    public static long proxyDelay;
    /** ms from fetchUuidsWithSdp to the UUID broadcast */
    public static long uuidDelay;
    public static boolean proxyOk;
    /** what the stack does when the connector allows the A2DP policy (null: nothing) */
    public static Consumer<BluetoothDevice> onPolicy;

    private World() {
    }

    public static void reset() {
        QUEUE.clear();
        RECEIVERS.clear();
        FILTERS.clear();
        CONNECTED.clear();
        EVENTS.clear();
        sNow = 0;
        sSeq = 0;
        proxyDelay = 100;
        uuidDelay = 300;
        proxyOk = true;
        onPolicy = null;
    }

    public static long now() {
        return sNow;
    }

    public static void at(long t, Runnable r) {
        QUEUE.add(new Task(t, sSeq++, null, 0, r));
    }

    public static void post(Handler h, int what, long delay) {
        QUEUE.add(new Task(sNow + delay, sSeq++, h, what, null));
    }

    public static boolean has(Handler h, int what) {
        for (Task t : QUEUE) if (t.handler == h && t.what == what) return true;
        return false;
    }

    public static void removeAll(Handler h) {
        QUEUE.removeIf(t -> t.handler == h);
    }

    public static void register(BroadcastReceiver r, IntentFilter f) {
        RECEIVERS.add(r);
        FILTERS.add(f);
    }

    public static void unregister(BroadcastReceiver r) {
        int i = RECEIVERS.indexOf(r);
        if (i < 0) throw new IllegalArgumentException("Receiver not registered");
        RECEIVERS.remove(i);
        FILTERS.remove(i);
    }

    public static boolean profileProxy(BluetoothProfile.ServiceListener l, int profile) {
        if (!proxyOk) return false;
        at(sNow + proxyDelay, () -> l.onServiceConnected(profile, PROXY));
        return true;
    }

    public static boolean fetchUuids(BluetoothDevice d) {
        at(sNow + uuidDelay, () -> broadcast(new Intent(BluetoothDevice.ACTION_UUID)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, d)));
        return true;
    }

    public static boolean setPolicy(BluetoothDevice d, int policy) {
        event(policy == BluetoothProfile.CONNECTION_POLICY_ALLOWED ? "policy" : "policy" + policy);
        if (onPolicy != null) onPolicy.accept(d);
        return true;
    }

    public static List<BluetoothDevice> connectedDevices() {
        return new ArrayList<>(CONNECTED);
    }

    /** The A2DP state machine of d moves prev -> next: the connected list and the broadcast. */
    public static Runnable a2dp(BluetoothDevice d, int prev, int next) {
        return () -> {
            if (next == BluetoothProfile.STATE_CONNECTED) CONNECTED.add(d);
            else if (next == BluetoothProfile.STATE_DISCONNECTED) CONNECTED.remove(d);
            broadcast(new Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                    .putExtra(BluetoothDevice.EXTRA_DEVICE, d)
                    .putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prev)
                    .putExtra(BluetoothProfile.EXTRA_STATE, next));
        };
    }

    private static void broadcast(Intent i) {
        // a receiver registered or removed during the delivery does not change who gets this one
        List<BroadcastReceiver> rs = new ArrayList<>(RECEIVERS);
        List<IntentFilter> fs = new ArrayList<>(FILTERS);
        for (int k = 0; k < rs.size(); k++) {
            if (RECEIVERS.contains(rs.get(k)) && fs.get(k).hasAction(i.getAction())) rs.get(k).onReceive(CONTEXT, i);
        }
    }

    public static void event(String name) {
        EVENTS.add(name + "@" + String.format(Locale.ROOT, "%.1f", sNow / 1000.0));
    }

    public static void run(long until) {
        while (true) {
            Task next = null;
            for (Task t : QUEUE) {
                if (t.at <= until && (next == null || t.at < next.at || (t.at == next.at && t.seq < next.seq))) next = t;
            }
            if (next == null) return;
            QUEUE.remove(next);
            sNow = next.at;
            if (next.handler != null) {
                Message m = new Message();
                m.what = next.what;
                next.handler.handleMessage(m);
            } else {
                next.run.run();
            }
        }
    }

    public static String trace() {
        return EVENTS.isEmpty() ? "-" : String.join(" ", EVENTS);
    }
}
