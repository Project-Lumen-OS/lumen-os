package org.lumen.projector.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.lumen.audio.AutoOutput;
import org.lumen.audio.BtA2dp;

import java.util.ArrayList;
import java.util.List;

/**
 * Lumen OS 1.0.4 (proposal, NOT device-tested): the manual sound output of stock Sound > Output > Sound output
 * (voice_output: Auto, Internal speaker, ARC, Headphones, Bluetooth, USB; stock analysis).
 *
 * Stock did it in the XGIMI service (GimiAudioService.setAudioDeviceOn: AudioSystem.setForceUse(FOR_MEDIA, c) with
 * c = 1 speaker, 2 headphones, 4 BT A2DP and XGIMI-only 16 ARC, 17 SPDIF, 18 USB, which only XGIMI's patched
 * audioserver accepts; Lumen's AOSP AudioSystem.NUM_FORCE_CONFIG is 16). Lumen uses the AOSP 14 way instead:
 * AudioManager.setPreferredDeviceForStrategy(media strategy, device) (@SystemApi, MODIFY_AUDIO_ROUTING, held: the
 * platform signature grants it from /system/app). The engine gives a preferred device that is available priority over
 * every other rule (also over the speaker + Spdif media pair Lumen routes to today), so getDevicesForAttributes(MEDIA)
 * changes and AudioPathReporter sends the stock switch (59(100), 1 s, 59(P)): the HDMI input sound follows the choice
 * through the vendor path (lumen-audio PathSwitch), and that output's chip level follows its volume (StockVolume).
 *
 * Rows: Auto and Speaker always; ARC / eARC, Bluetooth A2DP, wired and USB only while such an output is connected.
 * HB fork (2026-11-10): the HB jack is never detected, so HbOutputOptions lists Headphones always (stock) and apply
 * keeps the wired output connected, out of the default route unless chosen (HbWiredJack, lumen-audio JackOutput).
 * Auto (2026-11-15): the stock last accessed output (lumen-audio AutoOutput) made the preferred device, as a choice.
 * Never Spdif (attached port in the Z9X policy, always "available", no jack) and never the XGIMI wireless speaker
 * ports (BUS, ECHO_CANCELLER). Bluetooth and USB are categories, like stock: the chosen address first, else the
 * first connected device of that kind.
 *
 * The choice is stored (device-protected prefs "audio_output"); the role lives only in AudioService memory, so it is
 * applied at process start, at BOOT_COMPLETED, on every device add / remove and on every media routing change
 * (idempotent: the current preferred device is read first). The chosen output missing: the role is removed (Auto
 * until it is back), the choice stays.
 *
 * Audio format (stock voice_arc_format_auto_sound_v5, Auto / PCM / Passthrough): XGIMI service MtkAudio sent
 * setParameters("digital_output_format=pcm|bypass|auto"). Sent at boot, when ARC appears and on a user choice.
 * Lumen 1.0.8 (parity sound item 19): never chosen = PCM, as stock (XgimiAudio.init wrote PCM when spdif_mode was
 * empty and sent digital_output_format=pcm, so a soundbar without DD / DTS still played AC3 / DTS films); 1.0.4 to
 * 1.0.7 sent nothing then and left the HAL in its own mode. Row shown only with ARC / eARC connected.
 *
 * Thread "z9x-audioout" for every AudioManager write. Log tag Z9xAudioOut.
 */
public final class AudioOutputChoice {
    private static final String TAG = "Z9xAudioOut";
    static final String PREFS = "audio_output";
    static final String KEY_KIND = "kind";
    static final String KEY_ADDR = "addr";
    static final String KEY_FORMAT = "digital_format";

    /** One row: a connected output (label = the device name for Bluetooth / USB). */
    public static final class Option {
        public final AudioOutputKind kind;
        public final String addr;
        public final CharSequence label;
        Option(AudioOutputKind k, String a, CharSequence l) { kind = k; addr = a == null ? "" : a; label = l; }
    }

    private static final AudioAttributes MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).build();

    private static Handler sH;
    private static Context sApp;
    private static boolean sInstalled;
    private static boolean sArcSeen;

    private AudioOutputChoice() {}

    /** Application.onCreate. Idempotent, never throws. */
    public static synchronized void install(Context ctx) {
        if (sInstalled) return;
        try {
            sInstalled = true;
            sApp = ctx.getApplicationContext();
            HandlerThread t = new HandlerThread("z9x-audioout");
            t.start();
            sH = new Handler(t.getLooper());
            AudioManager am = sApp.getSystemService(AudioManager.class);
            if (am != null) {
                am.registerAudioDeviceCallback(new AudioDeviceCallback() {
                    @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d) {
                        AutoOutput.added(d, kind(sApp) == AudioOutputKind.AUTO, HbWiredJack.SENSED);
                        apply("devices added");
                        arcFormat();
                    }
                    @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d) {
                        AutoOutput.removed(d);
                        apply("devices removed");
                        arcFormat();
                    }
                }, sH);
                try {
                    am.addOnDevicesForAttributesChangedListener(MEDIA, sH::post,
                            (attrs, devices) -> apply("media routing " + devices));
                } catch (Throwable e) {
                    Log.w(TAG, "routing listener not available: " + e);
                }
            }
            // a connected A2DP device that is not active is listed and made active on a Bluetooth choice (lumen-audio)
            BtA2dp.install(sApp, () -> { Handler h = sH; if (h != null) h.post(() -> apply("bluetooth")); });
            AutoOutput.chose(kind(sApp).name());                          // a stored manual choice, as stock's force use
            sH.post(() -> apply("process start"));
            Log.i(TAG, "installed, choice " + kind(sApp));
        } catch (Throwable t) {
            Log.e(TAG, "install", t);
        }
    }

    /** BOOT_COMPLETED receiver. */
    public static void onBootCompleted(Context ctx) {
        install(ctx);
        Handler h = sH;
        if (h == null) return;
        h.post(() -> apply("boot completed"));
        h.post(() -> sendFormat("boot completed"));
    }

    // ------------------------------------------------------------------ UI (main thread)
    /** Store the user's choice and apply it. */
    public static void choose(Context ctx, AudioOutputKind k, String addr) {
        prefs(ctx).edit().putString(KEY_KIND, k.name()).putString(KEY_ADDR, addr == null ? "" : addr).apply();
        Log.i(TAG, "user chose " + k + (addr == null || addr.isEmpty() ? "" : " " + addr));
        AutoOutput.chose(k.name());
        install(ctx);
        Handler h = sH;
        if (h != null) h.post(() -> apply("user"));
    }

    public static void chooseFormat(Context ctx, DigitalOutputFormat f) {
        prefs(ctx).edit().putString(KEY_FORMAT, f.name()).apply();
        install(ctx);
        Handler h = sH;
        if (h != null) h.post(() -> sendFormat("user"));
    }

    /** The stored choice (AUTO when none or unreadable). */
    public static AudioOutputKind kind(Context ctx) {
        try {
            return AudioOutputKind.valueOf(prefs(ctx).getString(KEY_KIND, AudioOutputKind.AUTO.name()));
        } catch (Throwable t) {
            return AudioOutputKind.AUTO;
        }
    }

    /** UI index of the stored format; PCM when never chosen (the stock default, 1.0.8). */
    public static int formatIndex(Context ctx) {
        return effectiveFormat(ctx).ordinal();
    }

    /** The stored format, or PCM when never chosen (stock XgimiAudio.init default). */
    private static DigitalOutputFormat effectiveFormat(Context ctx) {
        DigitalOutputFormat f = format(ctx);
        return f == null ? DigitalOutputFormat.PCM : f;
    }

    /** Rows to show now: Auto, Speaker, then each connected kind once (rule: hide, never disable). */
    public static List<Option> options(Context ctx) {
        List<Option> out = new ArrayList<>();
        out.add(new Option(AudioOutputKind.AUTO, "", ctx.getString(org.lumen.projector.R.string.sound_output_auto)));
        out.add(new Option(AudioOutputKind.SPEAKER, "", ctx.getString(org.lumen.projector.R.string.sound_output_speaker)));
        AudioManager am = ctx.getSystemService(AudioManager.class);
        if (am == null) return out;
        String addr = prefs(ctx).getString(KEY_ADDR, "");
        for (AudioOutputKind k : new AudioOutputKind[] {AudioOutputKind.ARC, AudioOutputKind.BT, AudioOutputKind.WIRED, AudioOutputKind.USB}) {
            if (k == AudioOutputKind.BT) {
                Option bt = btOption(ctx, am, k == kind(ctx) ? addr : "");
                if (bt != null) out.add(bt);
                continue;
            }
            AudioDeviceInfo d = find(am, k, k == kind(ctx) ? addr : "");
            if (d == null) continue;
            CharSequence label;
            switch (k) {
                // eARC: an HDMI eARC output, or the HDMI ARC output while the vendor reports the eARC link
                // (cec/EarcLink; stock routes eARC through the plain ARC output and labels it "eARC")
                case ARC: label = ctx.getString(d.getType() == AudioDeviceInfo.TYPE_HDMI_EARC
                        || org.lumen.projector.cec.EarcLink.linked()
                        ? org.lumen.projector.R.string.sound_output_earc : org.lumen.projector.R.string.sound_output_arc); break;
                case WIRED: label = ctx.getString(org.lumen.projector.R.string.sound_output_wired); break;
                default: {
                    CharSequence n = d.getProductName();
                    label = n != null && n.length() > 0 ? n : ctx.getString(k == AudioOutputKind.BT
                            ? org.lumen.projector.R.string.sound_output_bt : org.lumen.projector.R.string.sound_output_usb);
                }
            }
            out.add(new Option(k, d.getAddress(), label));
        }
        return out;
    }

    /** Selected row: the stored kind while it is listed, else Auto (the choice stays stored). */
    public static int selectedIndex(Context ctx, List<Option> opts) {
        AudioOutputKind k = kind(ctx);
        for (int i = 0; i < opts.size(); i++) if (opts.get(i).kind == k) return i;
        return 0;
    }

    /** An HDMI ARC / eARC output is connected now (the Audio format row is shown only then). */
    public static boolean arcConnected(Context ctx) {
        AudioManager am = ctx.getSystemService(AudioManager.class);
        return am != null && find(am, AudioOutputKind.ARC, "") != null;
    }

    // ------------------------------------------------------------------ z9x-audioout thread
    private static void apply(String why) {
        try {
            Context app = sApp;
            AudioManager am = app == null ? null : app.getSystemService(AudioManager.class);
            if (am == null) return;
            AudioProductStrategy media = mediaStrategy();
            if (media == null) {
                Log.w(TAG, why + ": no product strategy for USAGE_MEDIA");
                return;
            }
            AudioOutputKind k = kind(app);
            // the 3.5 mm jack: connected for good, out of the default route unless chosen (HbWiredJack, lumen-audio JackOutput)
            if (HbWiredJack.keep(am, media, k, why)) return;      // "devices added" evaluates again with the output listed
            if (route(am, media, app, k, why)) HbWiredJack.settle(am, media, k, why);
        } catch (Throwable t) {
            Log.w(TAG, why + ": " + t);
        }
    }

    /** The preferred media device for the choice; false while the Bluetooth stack is asked to activate a device first. */
    private static boolean route(AudioManager am, AudioProductStrategy media, Context app, AudioOutputKind k, String why) {
        String addr = prefs(app).getString(KEY_ADDR, "");
        if (k == AudioOutputKind.BT) {
            String pick = BtA2dp.pick(addr);                      // a stale stored address: the connected device
            if (pick != null) {
                if (BtA2dp.activateIfNeeded(pick, why)) return false;   // the stack adds its output, "devices added" applies again
                addr = pick;
            }
        }
        // Auto: the stock last accessed output (lumen-audio AutoOutput), preferred like a chosen one
        AudioDeviceInfo d = k == AudioOutputKind.AUTO ? AutoOutput.device(am, HbWiredJack.SENSED) : find(am, k, addr);
        List<AudioDeviceAttributes> cur = am.getPreferredDevicesForStrategy(media);
        if (d == null) {
            if (cur != null && !cur.isEmpty() && ours(cur)) {
                boolean ok = am.removePreferredDeviceForStrategy(media);
                Log.i(TAG, why + ": " + (k == AudioOutputKind.AUTO ? "auto, no output" : k + " not connected, auto until it is back")
                        + ", preferred " + cur + " removed" + (ok ? "" : " (REFUSED)"));
            }
            return true;
        }
        AudioDeviceAttributes want = new AudioDeviceAttributes(d);
        if (cur != null && cur.size() == 1 && same(cur.get(0), want)) return true;             // already set
        boolean ok = am.setPreferredDeviceForStrategy(media, want);
        Log.i(TAG, why + ": media -> " + k + " (type " + want.getType() + " '" + want.getAddress() + "')" + (ok ? "" : " REFUSED"));
        return true;
    }

    /** ARC appeared: send the stored format again (the HAL may have reset it). */
    private static void arcFormat() {
        Context app = sApp;
        if (app == null) return;
        boolean arc = arcConnected(app);
        if (arc && !sArcSeen) sendFormat("ARC connected");
        sArcSeen = arc;
    }

    /**
     * The stored format again: after a media path switch (AudioPathReporter, stock libxgimiaudiopolicy sends it with
     * every switch start) and an eARC link change (EarcLink). Any thread.
     */
    public static void resendFormat(String why) {
        Handler h = sH;
        if (h != null) h.post(() -> sendFormat(why));
    }

    private static void sendFormat(String why) {
        Context app = sApp;
        if (app == null) return;
        DigitalOutputFormat f = effectiveFormat(app);                         // never chosen: PCM, as stock
        try {
            AudioManager am = app.getSystemService(AudioManager.class);
            if (am == null) return;
            am.setParameters("digital_output_format=" + f.wire);
            Log.i(TAG, "digital_output_format=" + f.wire + (format(app) == null ? " (default)" : "") + " (" + why + ")");
        } catch (Throwable t) {
            Log.w(TAG, "digital_output_format: " + t);
        }
    }

    // ------------------------------------------------------------------ helpers
    private static AudioProductStrategy mediaStrategy() {
        for (AudioProductStrategy s : AudioManager.getAudioProductStrategies()) {
            if (s.supportsAudioAttributes(MEDIA)) return s;
        }
        return null;
    }

    /**
     * The Bluetooth row: the stored device while it is connected, else the first connected one (lumen-audio
     * A2dpPick), also while the stack keeps it inactive (a wired output dropped it); AudioManager's A2DP output
     * while the stack is not known yet.
     */
    private static Option btOption(Context ctx, AudioManager am, String stored) {
        String pick = BtA2dp.pick(stored);
        AudioDeviceInfo d = find(am, AudioOutputKind.BT, pick != null ? pick : stored);
        if (pick == null && d == null) return null;
        String addr = pick != null ? pick : d.getAddress();
        CharSequence label = d != null && addr.equalsIgnoreCase(d.getAddress()) ? d.getProductName() : BtA2dp.name(addr);
        if (label == null || label.length() == 0) label = ctx.getString(org.lumen.projector.R.string.sound_output_bt);
        return new Option(AudioOutputKind.BT, addr, label);
    }

    /** A connected output of this kind: the given address first, else the first one. */
    private static AudioDeviceInfo find(AudioManager am, AudioOutputKind k, String addr) {
        AudioDeviceInfo first = null;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (!is(k, d.getType())) continue;
            if (k == AudioOutputKind.ARC && d.getType() == AudioDeviceInfo.TYPE_HDMI_EARC) return d;   // eARC before ARC
            if (addr != null && !addr.isEmpty() && addr.equals(d.getAddress())) return d;
            if (first == null) first = d;
        }
        return first;
    }

    private static boolean is(AudioOutputKind k, int type) {
        switch (k) {
            case SPEAKER: return type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
            case ARC: return type == AudioDeviceInfo.TYPE_HDMI_ARC || type == AudioDeviceInfo.TYPE_HDMI_EARC;
            case BT: return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
            case WIRED: return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET;
            case USB: return type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET;
            default: return false;
        }
    }

    /** Only remove a role of a kind this class sets (never an internal role of another output). */
    private static boolean ours(List<AudioDeviceAttributes> cur) {
        for (AudioDeviceAttributes a : cur) {
            for (AudioOutputKind k : AudioOutputKind.values()) if (k != AudioOutputKind.AUTO && is(k, a.getType())) return true;
        }
        return false;
    }

    private static boolean same(AudioDeviceAttributes a, AudioDeviceAttributes b) {
        return a.getType() == b.getType() && String.valueOf(a.getAddress()).equals(String.valueOf(b.getAddress()));
    }

    private static DigitalOutputFormat format(Context ctx) {
        try {
            String v = prefs(ctx).getString(KEY_FORMAT, null);
            return v == null ? null : DigitalOutputFormat.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        Context c = ctx.getApplicationContext();
        try {
            return c.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable t) {
            return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }
}
