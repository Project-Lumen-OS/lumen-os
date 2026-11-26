// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import org.lumen.projector.R;
import org.lumen.projector.ui.HeaderRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The menu's Bluetooth page on every model (stock Bluetooth and accessories, X50 live audit 2026-11-24: the tile opened
 * Android's Remotes and accessories and BACK did not come back to the menu): the section's own rows (Add more
 * devices), then the paired devices under a My devices title, each with its state. OK on a device opens its page:
 * Connect or Disconnect, Forget. Add more devices opens the search page: what a scan finds, OK pairs it (Android's own
 * pairing prompt asks for a code where the device needs one) and goes back to the page, where it now stands.
 */
final class BtPage {
    private static final String TAG = "LumenBtPage";

    /** The menu's part: pages, repaint, back one page. */
    interface Host {
        void push(Page p);

        void refreshPage();

        void back();
    }

    private final Context app;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<View> added = new ArrayList<>();
    private List<String> shown = Collections.emptyList();

    BtPage(Context c, Host host) {
        this.app = c.getApplicationContext();
        this.host = host;
    }

    private String s(int res) { return app.getString(res); }

    static BluetoothAdapter adapter(Context c) {
        try {
            BluetoothManager m = c.getSystemService(BluetoothManager.class);
            return m == null ? null : m.getAdapter();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** True where the projector has Bluetooth (the Add more devices row). */
    static boolean available(Context c) { return adapter(c) != null; }

    private List<BluetoothDevice> paired() {
        BluetoothAdapter a = adapter(app);
        List<BluetoothDevice> out = new ArrayList<>();
        try {
            Set<BluetoothDevice> b = a == null ? null : a.getBondedDevices();
            if (b != null) out.addAll(b);
        } catch (RuntimeException e) {                // no permission yet, the stack restarting
            Log.w(TAG, "bonded devices: " + e);
        }
        out.sort((x, y) -> name(x).compareToIgnoreCase(name(y)));
        return out;
    }

    private static String name(BluetoothDevice d) {
        try {
            String n = d.getAlias();
            if (n == null || n.isEmpty()) n = d.getName();
            return n == null || n.isEmpty() ? d.getAddress() : n;
        } catch (RuntimeException e) {
            return d.getAddress();
        }
    }

    private static boolean connected(BluetoothDevice d) {
        try {
            return d.isConnected();                        // system API (the projector apps are system apps)
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Connect, Disconnect or Forget (system APIs of BluetoothDevice); a failure is logged, the list reads again. */
    private static void act(BluetoothDevice d, String what) {
        try {
            if ("connect".equals(what)) d.connect();
            else if ("disconnect".equals(what)) d.disconnect();
            else d.removeBond();
        } catch (RuntimeException e) {
            Log.w(TAG, what + " " + d.getAddress() + ": " + e);
        }
    }

    private String state(BluetoothDevice d) {
        return s(connected(d) ? R.string.lumen_m_out_connected : R.string.lumen_m_out_offline);
    }

    /**
     * The paired devices under the page's own rows, as they are now: called whenever the page shows; repaints once
     * when the list or a state changed (the rows are already on screen then).
     */
    void fill(Page p) {
        List<BluetoothDevice> list = paired();
        List<String> now = new ArrayList<>();
        for (BluetoothDevice d : list) now.add(d.getAddress() + " " + name(d) + " " + connected(d));
        if (now.equals(shown) && p.rows().containsAll(added)) return;
        shown = now;
        for (View v : added) p.remove(v);
        added.clear();
        if (!list.isEmpty()) added.add(p.add(new HeaderRow(app, s(R.string.lumen_m_bt_mine))));
        for (BluetoothDevice d : list) {
            NavRow r = new NavRow(app, name(d));
            r.setOnClick(() -> openDevice(d));
            r.setValue(state(d));
            added.add(p.add(r));
        }
        main.post(host::refreshPage);
    }

    /** One device: Connect or Disconnect by its state, Forget; each goes back to the list after. */
    private void openDevice(BluetoothDevice d) {
        Page p = new Page(name(d));
        boolean on = connected(d);
        p.add(new NavRow(app, s(on ? R.string.lumen_m_bt_disconnect : R.string.lumen_m_out_connect), () -> {
            act(d, on ? "disconnect" : "connect");
            done();
        }).setChevron(false));
        p.add(new NavRow(app, s(R.string.lumen_m_bt_forget), () -> {
            act(d, "removeBond");
            done();
        }).setChevron(false));
        host.push(p);
    }

    /** Back to the list, which reads the devices again a moment later (the stack answers after the call). */
    private void done() {
        host.back();
        main.postDelayed(host::refreshPage, 1500);
    }

    /** Add more devices: the search page, a scan while it shows. */
    void openSearch() {
        BluetoothAdapter a = adapter(app);
        if (a == null) return;
        Page p = new Page(s(R.string.lumen_m_bt_add));
        TextRow status = p.add(new TextRow(app, s(R.string.lumen_m_bt_searching)));
        Map<String, NavRow> found = new HashMap<>();
        BroadcastReceiver rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                String act = i.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(act) && d != null && !found.containsKey(d.getAddress())) {
                    if (d.getBondState() == BluetoothDevice.BOND_BONDED || d.getName() == null) return;
                    NavRow r = p.add(new NavRow(app, name(d)));
                    r.setOnClick(() -> {
                        r.setValue(s(R.string.lumen_m_bt_pairing));
                        try {
                            a.cancelDiscovery();
                            if (!d.createBond()) r.setValue("");
                        } catch (RuntimeException e) {
                            Log.w(TAG, "pair " + d.getAddress() + ": " + e);
                            r.setValue("");
                        }
                    });
                    found.put(d.getAddress(), r);
                    host.refreshPage();
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(act)) {
                    status.setText(found.isEmpty() ? s(R.string.lumen_m_bt_none) : "");
                    status.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                    host.refreshPage();
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(act) && d != null
                        && d.getBondState() == BluetoothDevice.BOND_BONDED && found.containsKey(d.getAddress())) {
                    host.back();                                 // it stands under My devices now
                }
            }
        };
        p.setOnShown(() -> {
            IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            try {
                app.registerReceiver(rx, f, Context.RECEIVER_EXPORTED);     // the Bluetooth stack is another uid
                if (!a.isDiscovering() && !a.startDiscovery()) status.setText(s(R.string.lumen_m_bt_none));
            } catch (RuntimeException e) {
                Log.w(TAG, "search: " + e);
                status.setText(s(R.string.lumen_m_bt_none));
            }
        });
        p.setOnHidden(() -> {
            try {
                a.cancelDiscovery();
                app.unregisterReceiver(rx);
            } catch (RuntimeException ignored) {
                // not registered (the page never showed)
            }
        });
        host.push(p);
    }
}
