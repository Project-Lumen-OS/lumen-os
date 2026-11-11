package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;
import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.source.SourceOverlay;
import org.lumen.projector.source.TifHdmiState;
import org.lumen.projector.ui.Notify;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * The Input tile's popup of layout D (prototype: a radio list of the inputs with what is connected): HDMI 1 (ARC),
 * HDMI 2, a USB stick while one is mounted (its name; OK opens the USB files as the USB tile does) and the Home
 * screen, each HDMI with its TIF state (Connected / No signal / Not connected, Unavailable = disabled), the dot on
 * the input shown now. Picking an input does what the Source overlay does: HDMI =
 * SourceOverlay.openPort (the same passthrough intent to org.lumen.tvinput's viewer), Home = the HOME intent. The
 * Source key and the Home screen shortcut still open the Source overlay itself.
 *
 * Which input is "now": the HDMI port org.lumen.tvinput reports as shown (TifHdmiState.readPorts), else IGmpf 696
 * when it says HDMI n and that input is CONNECTED (the overlay's rule), else the Home screen when both answered and
 * no HDMI is shown; unknown = no dot. Reads on the HAL thread (Hal.query), never on the main thread.
 */
final class QpdSource {
    private static final String TAG = "Z9xPanel";
    private static final String INPUT_ID_PREFIX = "org.lumen.tvinput/.HdmiInputService/HW";
    private static final int[] PORTS = {1, 2};

    interface Host {
        /** Closes the quick panel (the picked input or Home takes the screen). */
        void close();
        /** Lumen Home's USB files screen exists (the USB tile's own check). */
        boolean usbFilesAvailable();
        /** Opens the USB files screen (the USB tile's action); the panel is closed first. */
        void openUsbFiles();
    }

    /** Row key of the USB stick entry (only while one is mounted) and of the Home screen. */
    static final int USB = -2, HOME = 0;

    final Page page;
    private final Context app;
    private final Host host;
    private final List<InputRow> rows = new ArrayList<>();
    private TvInputManager tim;
    private TvInputManager.TvInputCallback callback;
    private int gen;
    /** Port shown now (0 = Home screen), -1 = unknown. */
    private int now = -1;
    /** The user moved in this show: a late read no longer moves the focus to the input shown now. */
    private boolean moved;

    QpdSource(Context app, CharSequence title, Host host) {
        this.app = app;
        this.host = host;
        page = new Page(title);
        for (int port : PORTS) rows.add(page.add(new InputRow(app, port)));
        rows.add(page.add(new InputRow(app, USB)));
        rows.add(page.add(new InputRow(app, HOME)));
        page.setOnShown(this::onShown);
        page.setOnHidden(this::onHidden);
    }

    // ------------------------------------------------------------------ shown / hidden
    private void onShown() {
        gen++;
        moved = false;
        try {
            tim = app.getSystemService(TvInputManager.class);
        } catch (Throwable t) {
            tim = null;
        }
        updateRows();
        register();
        final int g = gen;
        Hal.query((gm, g2) -> {
            int[] ports = TifHdmiState.readPorts(app);
            Integer src = null;
            try {
                src = gm.getCurrentInputSource();
            } catch (Throwable t) {
                Log.w(TAG, "source popup: 696 " + t);
            }
            return new Object[]{ports, src};
        }, v -> {
            if (g != gen || v == null) return;
            now = nowFrom((int[]) v[0], (Integer) v[1]);
            for (InputRow r : rows) r.invalidate();
            // the popup opens on the input shown now (as the Source overlay), unless the user moved already
            InputRow cur = now >= 0 ? row(now) : null;
            if (!moved && cur != null && cur.isRowEnabled() && cur.isAttachedToWindow() && !cur.isFocused()) {
                for (InputRow r : rows) {
                    if (r.isFocused()) { cur.requestFocus(); break; }
                }
            }
        });
    }

    private void onHidden() {
        gen++;
        if (tim != null && callback != null) {
            try {
                tim.unregisterCallback(callback);
            } catch (Throwable t) {
                Log.w(TAG, "source popup unregister: " + t);
            }
        }
        callback = null;
    }

    private int nowFrom(int[] tifPorts, Integer src) {
        if (tifPorts != null && tifPorts.length > 0) return tifPorts[0];
        if (src != null && (src == GmpfClient.SOURCE_HDMI1 || src == GmpfClient.SOURCE_HDMI2)) {
            int port = src == GmpfClient.SOURCE_HDMI1 ? 1 : 2;
            InputRow r = row(port);
            if (r != null && r.inputId != null && stateOf(r.inputId) == TvInputManager.INPUT_STATE_CONNECTED) return port;
            return -1;
        }
        return tifPorts != null && src != null ? 0 : -1;
    }

    private InputRow row(int port) {
        for (InputRow r : rows) if (r.port == port) return r;
        return null;
    }

    private void register() {
        if (tim == null || callback != null) return;
        callback = new TvInputManager.TvInputCallback() {
            @Override public void onInputStateChanged(String inputId, int state) { updateRows(); }
            @Override public void onInputAdded(String inputId) { updateRows(); }
            @Override public void onInputRemoved(String inputId) { updateRows(); }
            @Override public void onInputUpdated(String inputId) { updateRows(); }
        };
        try {
            tim.registerCallback(callback, Ui.main());
        } catch (Throwable t) {
            Log.w(TAG, "source popup register: " + t);
            callback = null;
        }
    }

    // ------------------------------------------------------------------ rows
    private void updateRows() {
        List<TvInputInfo> inputs = ourInputs();
        for (InputRow r : rows) {
            if (r.port == HOME) {
                r.setTitle(app.getString(R.string.source_home));
                continue;
            }
            if (r.port == USB) {
                // only a real, mounted stick, and only where the USB files screen exists (as the USB tile)
                CharSequence stick = host.usbFilesAvailable() ? usbStick() : null;
                r.state = stick;
                r.signal = stick != null;
                r.setVisibility(stick != null ? android.view.View.VISIBLE : android.view.View.GONE);
                r.setRowEnabled(stick != null);
                continue;
            }
            TvInputInfo info = null;
            for (TvInputInfo i : inputs) if (portOf(i.getId()) == r.port) { info = i; break; }
            r.inputId = info == null ? null : info.getId();
            r.setTitle(labelOf(info, r.port));
            if (r.inputId == null) {
                r.state = app.getString(R.string.source_state_unavailable);
                r.signal = false;
                r.setRowEnabled(false);
                continue;
            }
            r.setRowEnabled(true);
            int st = stateOf(r.inputId);
            r.signal = st == TvInputManager.INPUT_STATE_CONNECTED;
            r.state = app.getString(st == TvInputManager.INPUT_STATE_CONNECTED ? R.string.source_state_connected
                    : st == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? R.string.source_state_no_signal
                    : R.string.source_state_disconnected);
            r.invalidate();
        }
    }

    /** Name of the first mounted removable volume (a USB stick), or null. */
    private CharSequence usbStick() {
        try {
            android.os.storage.StorageManager sm = app.getSystemService(android.os.storage.StorageManager.class);
            if (sm == null) return null;
            for (android.os.storage.StorageVolume v : sm.getStorageVolumes()) {
                if (v == null || !v.isRemovable() || v.isPrimary()) continue;
                if (!android.os.Environment.MEDIA_MOUNTED.equals(v.getState())
                        && !android.os.Environment.MEDIA_MOUNTED_READ_ONLY.equals(v.getState())) continue;
                String d = v.getDescription(app);
                return TextUtils.isEmpty(d) ? app.getString(R.string.lumen_qpd_usb) : d;
            }
        } catch (Throwable t) {
            Log.w(TAG, "source popup usb: " + t);
        }
        return null;
    }

    /** org.lumen.tvinput's HDMI port inputs (passthrough, no CEC children), as the Source overlay lists them. */
    private List<TvInputInfo> ourInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        if (tim == null) return out;
        try {
            List<TvInputInfo> all = tim.getTvInputList();
            if (all == null) return out;
            for (TvInputInfo i : all) {
                if (i == null || i.getId() == null || !i.getId().startsWith(INPUT_ID_PREFIX)) continue;
                if (i.getType() != TvInputInfo.TYPE_HDMI || !i.isPassthroughInput() || i.getParentId() != null) continue;
                out.add(i);
            }
        } catch (Throwable t) {
            Log.w(TAG, "source popup inputs: " + t);
        }
        return out;
    }

    static int portOf(String inputId) {
        if (inputId == null || !inputId.startsWith(INPUT_ID_PREFIX)) return -1;
        try {
            return Integer.parseInt(inputId.substring(INPUT_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int stateOf(String inputId) {
        if (tim == null || inputId == null) return TvInputManager.INPUT_STATE_DISCONNECTED;
        try {
            return tim.getInputState(inputId);
        } catch (Throwable t) {
            return TvInputManager.INPUT_STATE_DISCONNECTED;
        }
    }

    /** The user's own label first, then org.lumen.tvinput's, then ours (port 1 is the ARC port). */
    private CharSequence labelOf(TvInputInfo info, int port) {
        if (info != null) {
            try {
                CharSequence l = info.loadCustomLabel(app);
                if (TextUtils.isEmpty(l)) l = info.loadLabel(app);
                if (!TextUtils.isEmpty(l)) return l;
            } catch (Throwable ignored) {
            }
        }
        return port == 1 ? app.getString(R.string.source_hdmi_arc, port) : app.getString(R.string.source_hdmi, port);
    }

    // ------------------------------------------------------------------ actions
    private void pick(InputRow r) {
        if (r.port == USB) {
            Log.i(TAG, "source popup: USB files");
            host.openUsbFiles();
            return;
        }
        if (r.port == HOME) {
            Log.i(TAG, "source popup: Home");
            host.close();
            try {
                Ui.wakeFromDream(app);
                app.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t) {
                Log.e(TAG, "source popup: go home", t);
            }
            return;
        }
        if (r.inputId == null) return;                       // disabled row; defensive
        Log.i(TAG, "source popup: HDMI " + r.port + " -> " + r.inputId);
        host.close();
        Ui.wakeFromDream(app);
        if (!SourceOverlay.openPort(app, r.port, "quick_panel")) {
            try {
                Notify.show(app, app.getString(R.string.source_open_failed, app.getString(R.string.source_hdmi, r.port)));
            } catch (Throwable ignored) {
            }
        }
    }

    /** One input: radio dot on the left, its state on the right (QpdRowFrame draws it). */
    final class InputRow extends Row implements QpdRowFrame.Radio {
        final int port;
        String inputId;
        CharSequence state;
        boolean signal;

        InputRow(Context c, int port) {
            super(c, port == HOME ? c.getString(R.string.source_home) : port == USB ? c.getString(R.string.lumen_qpd_usb)
                    : c.getString(R.string.source_hdmi, port));
            this.port = port;
            if (port == USB) setVisibility(GONE);
        }

        @Override public boolean isSelected() { return now >= 0 && now == port; }

        @Override public CharSequence radioValue() { return port == HOME ? null : state; }

        @Override public int radioDot() { return port == HOME || state == null ? -1 : signal ? 1 : 0; }

        @Override protected float rightWidth() { return 0f; }

        @Override protected void drawRight(Canvas c, float right, float cy, boolean focused) {}

        @Override
        protected void onFocusChanged(boolean gain, int direction, android.graphics.Rect prev) {
            super.onFocusChanged(gain, direction, prev);
            if (!gain && isAttachedToWindow()) moved = true;
        }

        @Override
        protected boolean onActivate() {
            pick(this);
            return true;
        }
    }
}
