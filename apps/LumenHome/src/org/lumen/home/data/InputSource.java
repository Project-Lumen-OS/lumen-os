// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;
import android.util.Log;

import org.lumen.hdmiports.HdmiPort;
import org.lumen.hdmiports.HdmiPorts;
import org.lumen.home.App;
import org.lumen.home.R;
import org.lumen.home.proj.MirrorReceiver;
import org.lumen.home.proj.ProjectorBridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HDMI inputs, cast receivers and projector shortcuts.
 *
 * C15: CEC child inputs (TvInputInfo.getParentId() != null) are grouped under their HDMI port, so a
 * console shows once as "PlayStation 5" with the subtitle "HDMI 1", never twice. Runs on io.
 */
public final class InputSource {
    private InputSource() {}

    public static final String TVINPUT_PKG = "org.lumen.tvinput";
    public static final String PKG_CHROMECAST = "com.google.android.apps.mediashell";
    public static final String PKG_AIRPLAY = "org.lumen.airplay";
    /** Screen mirroring receiver (Miracast sink, apps/LumenMiracast): tile "miracast" opens its settings. */
    public static final String PKG_MIRACAST = MirrorReceiver.PKG;
    private static final Pattern HW = Pattern.compile("/HW(\\d+)$");
    private static final Pattern CONSOLE = Pattern.compile("(?i)playstation|ps[345]|xbox|nintendo|switch|steam");

    public static List<Card> inputs(Context c, Map<String, String> customLabels) {
        ArrayList<Card> out = new ArrayList<>();
        TvInputManager tim = c.getSystemService(TvInputManager.class);
        if (tim == null) return out;
        List<TvInputInfo> all;
        try {
            all = tim.getTvInputList();
        } catch (Throwable t) {
            Log.w(App.TAG, "tv inputs: " + t);
            return out;
        }
        // the one HDMI port state (lumen-hdmiports, org.lumen.tvinput PortState): never decided here
        List<HdmiPort> read = HdmiPorts.read(c);
        Map<Integer, HdmiPort> ports = new HashMap<>();
        if (read != null) for (HdmiPort p : read) ports.put(p.port, p);
        int[] showingPorts = read == null ? new int[0] : HdmiPorts.open(c);
        HashMap<String, List<TvInputInfo>> children = new HashMap<>();
        ArrayList<TvInputInfo> tops = new ArrayList<>();
        for (TvInputInfo ii : all) {
            if (isHidden(c, ii)) continue;
            if (ii.getParentId() != null) {
                children.computeIfAbsent(ii.getParentId(), k -> new ArrayList<>()).add(ii);
            } else if (ii.getType() != TvInputInfo.TYPE_TUNER || ii.isPassthroughInput()) {
                tops.add(ii);
            }
        }
        // ours first (HW1, HW2), then other passthrough inputs
        tops.sort((a, b) -> {
            boolean oa = TVINPUT_PKG.equals(a.getServiceInfo().packageName);
            boolean ob = TVINPUT_PKG.equals(b.getServiceInfo().packageName);
            if (oa != ob) return oa ? -1 : 1;
            return Integer.compare(port(a), port(b)) != 0 ? Integer.compare(port(a), port(b)) : a.getId().compareTo(b.getId());
        });
        for (TvInputInfo ii : tops) {
            Card k = new Card(Card.INPUT, ii.getId(), "");
            int port = port(ii);
            String portLabel = label(c, ii);
            List<TvInputInfo> kids = children.get(ii.getId());
            HdmiDeviceInfo dev = kids != null && !kids.isEmpty() ? kids.get(0).getHdmiDeviceInfo() : null;
            k.icon = icon(dev);
            k.pkg = ii.getServiceInfo().packageName;
            k.showing = port > 0 && contains(showingPorts, port);
            k.color = 0;
            HdmiPort hp = TVINPUT_PKG.equals(k.pkg) ? ports.get(port) : null;
            String custom = customLabels.get(ii.getId());
            if (hp != null) {
                portCard(c, k, hp, custom, portLabel);
            } else {                                   // another TV input's input, or the HDMI app does not answer
                boolean named = custom != null && !custom.isEmpty();
                k.title = named ? custom : portLabel;
                k.desc = named ? portLabel : "";
                int st;
                try {
                    st = tim.getInputState(ii.getId());
                } catch (Throwable t) {
                    st = TvInputManager.INPUT_STATE_DISCONNECTED;
                }
                k.state = st;
                k.meta = stateText(c, st);
                k.intent = TvContract.buildChannelUriForPassthroughInput(ii.getId()).toString();
            }
            out.add(k);
        }
        return out;
    }

    public static String stateText(Context c, int st) {
        switch (st) {
            case TvInputManager.INPUT_STATE_CONNECTED:
                return c.getString(R.string.input_connected);
            case TvInputManager.INPUT_STATE_CONNECTED_STANDBY:
                return c.getString(R.string.input_standby);
            default:
                return c.getString(R.string.input_not_connected);
        }
    }

    private static boolean isHidden(Context c, TvInputInfo ii) {
        try {
            return ii.isHidden(c);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String label(Context c, TvInputInfo ii) {
        try {
            CharSequence l = ii.loadCustomLabel(c);
            if (l == null || l.length() == 0) l = ii.loadLabel(c);
            if (l != null && l.length() > 0) return l.toString();
        } catch (Throwable ignored) {
        }
        int p = port(ii);
        return p > 0 ? "HDMI " + p : ii.getId();
    }

    /** cec_names: the label the TV input gave a CEC device's input (it may carry the OSD name), or null. */
    private static String childLabel(Context c, TvInputInfo kid) {
        try {
            CharSequence l = kid.loadCustomLabel(c);
            if (l == null || l.length() == 0) l = kid.loadLabel(c);
            return l != null ? l.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static int port(TvInputInfo ii) {
        Matcher m = HW.matcher(ii.getId());
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        HdmiDeviceInfo d = ii.getHdmiDeviceInfo();
        return d != null ? d.getPortId() : 0;
    }

    private static int icon(HdmiDeviceInfo dev) {
        if (dev == null) return R.drawable.ic_hdmi;
        switch (dev.getDeviceType()) {
            case HdmiDeviceInfo.DEVICE_AUDIO_SYSTEM:
                return R.drawable.ic_soundbar;
            case HdmiDeviceInfo.DEVICE_PLAYBACK:
                String n = dev.getDisplayName();
                return n != null && CONSOLE.matcher(n).find() ? R.drawable.ic_gamepad : R.drawable.ic_player;
            case HdmiDeviceInfo.DEVICE_RECORDER:
            case HdmiDeviceInfo.DEVICE_TUNER:
                return R.drawable.ic_player;
            default:
                return R.drawable.ic_hdmi;
        }
    }

    /**
     * The card of one of our HDMI ports, as the HDMI app describes it (lumen-hdmiports HdmiPort): its state (on,
     * asleep, no signal, not connected), the device's name over the port's label (a name the user gave the input
     * still wins), the glyph of its kind in a softened brand accent, and the input to open (the device's own input
     * routes to it, and on an asleep device the HDMI app's viewer offers OK to wake). {@code c} null: no texts (tests).
     */
    static void portCard(Context c, Card k, HdmiPort p, String custom, String portLabel) {
        String label = nonEmpty(p.label, portLabel);
        if (custom != null && !custom.isEmpty()) {
            k.title = custom;
            k.desc = nonEmpty(p.title, label);
        } else if (p.title != null) {
            k.title = p.title;
            k.desc = label;
        } else {
            k.title = label;
            k.desc = "";
        }
        k.state = PortChip.state(p.state);
        k.meta = c == null ? null : devText(c, k.state);
        if ("console".equals(p.kind)) k.icon = R.drawable.ic_gamepad;
        else if ("player".equals(p.kind)) k.icon = R.drawable.ic_player;
        k.color = p.accent == 0 || p.accent == NEUTRAL_ACCENT ? 0 : CecNames.softAccent(p.accent);
        String target = p.target();
        if (target != null) k.intent = TvContract.buildChannelUriForPassthroughInput(target).toString();
    }

    /** The TV input app's colour for a device without a brand accent. */
    private static final int NEUTRAL_ACCENT = 0xFF3A3F47;

    public static String devText(Context c, int st) {
        switch (st) {
            case Card.DEV_ON: return c.getString(R.string.input_on);
            case Card.DEV_ASLEEP: return c.getString(R.string.input_asleep);
            case Card.DEV_NO_SIGNAL: return c.getString(R.string.input_no_signal);
            default: return c.getString(R.string.input_not_connected);
        }
    }

    private static String nonEmpty(String a, String b) {
        return a != null && !a.trim().isEmpty() ? a.trim() : b;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    // ------------------------------------------------------------------ cast + projector tiles

    public static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static String deviceName(Context c) {
        String n = Settings.Global.getString(c.getContentResolver(), Settings.Global.DEVICE_NAME);
        return n == null || n.isEmpty() ? "XGIMI Z9X" : n;
    }

    /**
     * Cast cards (Chromecast built-in, AirPlay). Not called since 1.0.1: their only place, the Inputs tab,
     * is gone and the user wants no duplicate of it; kept with HomeActivity's cast how-to panel for an
     * entry point elsewhere, if one is ever wanted.
     */
    public static List<Card> casts(Context c) {
        ArrayList<Card> out = new ArrayList<>();
        String name = deviceName(c);
        if (installed(c, PKG_CHROMECAST)) {
            Card k = new Card(Card.CAST, "cast:chromecast", c.getString(R.string.cast_chromecast));
            k.icon = R.drawable.ic_cast;
            k.meta = c.getString(R.string.cast_ready, name);
            k.pkg = PKG_CHROMECAST;
            out.add(k);
        }
        if (installed(c, PKG_AIRPLAY)) {
            Card k = new Card(Card.CAST, "cast:airplay", c.getString(R.string.cast_airplay));
            k.icon = R.drawable.ic_airplay;
            k.meta = c.getString(R.string.cast_ready, name);
            k.pkg = PKG_AIRPLAY;
            out.add(k);
        }
        return out;
    }

    /**
     * Projector shortcuts: intent = "section:x", "action:x" or "settings". A projector app with the
     * device-state provider may list tiles its projector does not support (ProjectorBridge.unsupportedTiles,
     * by intent or Card id): those are left out. Without the provider (the Z9X) every tile, as before.
     */
    public static List<Card> tiles(Context c) {
        ArrayList<Card> out = new ArrayList<>();
        tile(out, c, "action:autofocus", R.string.tile_autofocus, R.drawable.ic_autofocus);
        tile(out, c, "section:keystone", R.string.tile_keystone, R.drawable.ic_keystone);
        tile(out, c, "section:picture", R.string.tile_picture, R.drawable.ic_picture);
        tile(out, c, "section:sound", R.string.tile_sound, R.drawable.ic_sound);
        tile(out, c, "section:eye", R.string.tile_eye, R.drawable.ic_eye);
        // v1 power rules: the projector turns the lamp off and enters real sleep (STR); action id kept
        tile(out, c, "action:lamp_standby", R.string.tile_sleep, R.drawable.ic_power);
        tile(out, c, "action:sleep_timer", R.string.tile_sleep_timer, R.drawable.ic_timer);
        tile(out, c, "section:general", R.string.tile_projector_settings, R.drawable.ic_tune);
        tile(out, c, "settings", R.string.tile_android_settings, R.drawable.ic_settings);
        if (MirrorReceiver.offered(c)) tile(out, c, "miracast", R.string.tile_miracast, R.drawable.ic_cast);
        Set<String> off = ProjectorBridge.unsupportedTiles(c);
        if (!off.isEmpty()) out.removeIf(k -> off.contains(k.intent) || off.contains(k.id));
        return out;
    }

    private static void tile(List<Card> out, Context c, String intent, int label, int icon) {
        Card k = new Card(Card.TILE, "tile:" + intent, c.getString(label));
        k.intent = intent;
        k.icon = icon;
        out.add(k);
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
