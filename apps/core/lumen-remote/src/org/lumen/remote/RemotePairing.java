// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.remote;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;

import org.lumen.projector.R;
import org.lumen.projector.SafeHandler;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.Notify;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * lumen-remote (README.md): the ONE remote pairing of every Lumen projector, moved from the Z9X RemoteAutoPair
 * (apps/common/projector, also HB and Play 6) and joined with the X50 RemotePair (HID host connect, the X50 match
 * rules). Which advertiser is a remote comes from the remote table (RemoteMatch over RemoteCatalog); the app gives a
 * RemoteHost. Pinned by test/parity (the traces of both old implementations, recorded before the move).
 *
 * Auto-pairs the XGIMI BLE remote with an LE scan. The remote advertises LE
 * *limited* discoverable while in pairing mode (Back + Home held), so the stock Android TV
 * scanners (classic discovery) never list it. Port of gsi/apps/Z9xBtPair (org.lumen.btpair, proven
 * on the device: unfiltered LE scan, name contains "XGIMI", connectable, createBond(TRANSPORT_LE),
 * confirm the pairing request) into the persistent app, so it also works during the first-run setup.
 *
 * <b>Scanning</b> (v6.3.2): ONE long-running LE scan, SCAN_MODE_LOW_LATENCY, while a pairing
 * window is open, instead of the v6.3.1 short bursts (15 s every 60 s), so a remote put into
 * pairing mode is seen within about a second and bonded within ~3 s. The scan is started once
 * per window and only restarted when its mode changes, after a bond attempt, after a failure or
 * every 25 min (Android moves a regular scan that runs longer than 30 min to opportunistic);
 * starts are at least 7 s apart (Android throttles more than 5 starts per 30 s). The scan has no
 * ScanFilter: ScanFilter cannot match a name prefix and the remote's advertisement is not
 * verified to carry the HID UUID 0x1812 (Z9xBtPair, proven, is unfiltered); instead the binder
 * callback posts only what RemoteMatch.worthPosting keeps (an XGIMI or table name, the XGIMI company data,
 * the HID service), and each address gets one "advert" log line with the match's verdict per scan.
 *
 * <b>Pairing windows</b> (Bluetooth on, screen on, no bond in progress):
 *  - MANUAL: {@link #startPairing(Context)} ("Pair remote" in the Projector settings; Lumen OS 1.0:
 *    no longer in the quick panel), 120 s, regardless of the state below; shows the "Hold Back and
 *    Home" card. Broad match: a name containing "XGIMI" plus "RC"/"REMOTE", the HID service
 *    0x1812 or the LE Limited flag; a bonded but not connected XGIMI device advertising the
 *    Limited flag is unbonded and paired again; a new / second remote can be paired;
 *  - NEW: no XGIMI remote is bonded at all (also during setup). After setup, each "budget"
 *    (started by boot, screen on, Bluetooth on, a bond removed or the user's 'Pair remote') scans
 *    LOW_LATENCY for its first 2 min, then SCAN_MODE_BALANCED (25 % duty, a pairing remote is
 *    still found within ~4 s), and stops after 60 min (until the next budget start), so a
 *    projector used without an XGIMI remote does not keep the 2.4 GHz radio busy for hours;
 *  - RECOVER: an XGIMI remote is bonded but none is connected, and either the setup is not
 *    complete, or it is the boot window: 120 s starting 10 s after boot or after the screen
 *    turns on (the grace lets a sleeping remote reconnect first; a bonded remote that is merely
 *    asleep is normal, so outside these windows nothing scans for it).
 *  - otherwise (an XGIMI remote is connected, or bonded and asleep after the boot window): no scan.
 * Radio coexistence: in the automatic windows after setup (NEW, RECOVER) the scan is capped at
 * SCAN_MODE_LOW_POWER (10 % duty) while a Bluetooth audio device (A2DP, LE audio, hearing aid) is
 * connected; its connection-state broadcasts re-evaluate the mode. MANUAL and setup keep LOW_LATENCY.
 * In the automatic windows (NEW, RECOVER) a candidate must be a connectable advertiser, not our
 * own adapter name, named "XGIMI RC..." AND advertising the LE Limited Discoverable flag (= a
 * remote in pairing mode), so another XGIMI projector or a sleeping device is never bonded
 * unattended. RECOVER never bonds anything new (as in 6.3.1): only an address that is bonded
 * or that we bonded earlier (KEY_BONDED); a new / second remote is left to NEW and MANUAL.
 * <b>Re-pair</b> (a bonded XGIMI remote that lost its keys: reset, battery swap): a bonded, not
 * connected address advertising the Limited flag is only a candidate. A sleeping remote that wakes
 * on a key press advertises to reconnect, and LOW_LATENCY sees that advert before the host's
 * background connection completes, so the bond is removed only if, after REPAIR_CONFIRM_MS (3 s;
 * 11 s with a BALANCED / LOW_POWER scan), the address is still advertising Limited, is still
 * bonded and not connected, and (automatic windows) no ACL_CONNECTED arrived for it meanwhile.
 * A reset remote stays in pairing mode for tens of seconds, so the delay costs little.
 * UNVERIFIED on the device: that a reset XGIMI remote advertises with its bonded identity address
 * (if not, it is not re-paired automatically: 'Pair remote' pairs it as a new device).
 * Automatic mode never removes any other bond.
 *
 * <b>Pairing request</b>: for the device we are bonding, variant CONSENT (3) or
 * PASSKEY_CONFIRMATION (2) -> setPairingConfirmation(true) (needs BLUETOOTH_CONNECT +
 * BLUETOOTH_PRIVILEGED, VERIFIED AdapterService.java:3666-3680; BLUETOOTH_PRIVILEGED is
 * signature|privileged, granted by the platform key). The broadcast is ordered
 * (BondStateMachine.java:494 sendOrderedBroadcast, permission BLUETOOTH_CONNECT), so after a
 * successful confirm it is aborted and no TvSettings dialog shows over the setup.
 *
 * <b>Permissions</b>: BLUETOOTH_SCAN (neverForLocation) and BLUETOOTH_CONNECT are dangerous;
 * pre-granted by the image's default-permissions XML, else self-granted with
 * PackageManager.grantRuntimePermission (GRANT_RUNTIME_PERMISSIONS = signature|installer|verifier;
 * PermissionManagerServiceImpl.grantRuntimePermissionInternal only enforces that permission).
 *
 * Threading: everything runs on "lumen-remote" (receivers are registered with that handler, scan
 * callbacks are pre-filtered on the binder thread and hop onto it). Nothing here touches the
 * gmpf HAL.
 *
 * <b>Lumen OS 1.0 (remote pairing UX)</b>:
 *  - "Connected" for the first-run setup and the prompt ({@link #state}) means a bonded XGIMI remote
 *    that is ACL connected AND whose HID input device is up (hidUp: the input device with its
 *    Bluetooth address, or an external one with an XGIMI remote vendor id), i.e. its keys reach
 *    Android. The scan policy above keeps the plain ACL test.
 *  - During the setup a remote in pairing mode is bonded in the RECOVER window too (the user is on
 *    the remote step); after the setup RECOVER stays "known addresses only".
 *  - Remote lost ({@link RemotePrompt}): after the setup, with the screen on, not in standby, and
 *    only if this projector had an XGIMI remote before, a full-screen prompt asks to hold Back + Home
 *    when no XGIMI remote is bonded any more ({@value #UNBOND_GRACE_MS} ms after the bond went, or at
 *    a wake), or when none connected within {@value #LOST_AFTER_MS} ms of boot / SCREEN_ON (not after
 *    an HDMI-CEC wake, and 1.0.1: not after a boot that followed an unattended restart, see
 *    {@link #unattendedBootReason}). At most once per wake; a dismissal by the user snoozes it (30 min, 4 h, 24 h),
 *    and after {@value #LOST_MAX_DISMISSALS} dismissals it stays quiet until a remote connects again.
 *    While it is shown the scan runs as a NEW window at LOW_LATENCY (strict match, new remotes
 *    allowed) and the prompt closes itself once the remote is connected.
 */
public final class RemotePairing {
    private static final String TAG = "LumenRemote";

    private static final ParcelUuid HID = ParcelUuid.fromString(RemoteMatch.HID_UUID);

    private static final long FIRST_CHECK_MS = 3_000;
    private static final long TICK_MS = 30_000;            // re-check while a window is open
    private static final long MANUAL_MS = 120_000;
    private static final long BOOT_GRACE_MS = 10_000;      // let a bonded remote reconnect first
    private static final long BOOT_WINDOW_MS = 120_000;
    private static final long BOOT_MAX_UPTIME_MS = 10 * 60_000;   // process start counts as boot
    private static final long NEW_FAST_MS = 2 * 60_000;    // NEW budget after setup: LOW_LATENCY, then BALANCED
    private static final long NEW_MAX_MS = 60 * 60_000;    // NEW budget after setup: then stop
    /** Re-pair debounce (LOW_LATENCY scan): advert must persist this long, last one at most FRESH old. */
    private static final long REPAIR_CONFIRM_MS = 3_000, REPAIR_FRESH_MS = 1_500;
    /** Same with a BALANCED (1 s every 4 s) / LOW_POWER (0.5 s every 5 s) scan. */
    private static final long REPAIR_CONFIRM_SLOW_MS = 11_000, REPAIR_FRESH_SLOW_MS = 5_500;
    private static final int PROFILE_HEARING_AID = 21, PROFILE_LE_AUDIO = 22;   // BluetoothProfile
    private static final String ACTION_A2DP_CONN = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED";
    private static final String ACTION_LE_AUDIO_CONN = "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED";
    private static final String ACTION_HEARING_AID_CONN = "android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED";
    private static final long SCAN_REFRESH_MS = 25 * 60_000;
    private static final long SCAN_RETRY_MS = 30_000;
    private static final long BOND_TIMEOUT_MS = 45_000;
    private static final long MIN_START_GAP_MS = 7_000;    // Android throttles >5 scan starts / 30 s
    private static final long FAIL_COOLDOWN_MS = 10 * 60_000;
    private static final int FAILS_BEFORE_COOLDOWN = 3;
    private static final long PERM_RETRY_MS = 5 * 60_000;
    private static final long JUST_BONDED_MS = 60_000;

    private static final int MODE_IDLE = 0, MODE_NEW = 1, MODE_RECOVER = 2, MODE_MANUAL = 3;
    private static final String[] MODE_NAMES = {"idle", "new", "recover", "manual"};

    private static final String PREFS = "z9x_remote";
    private static final String KEY_BONDED = "bonded_addrs";

    /** a bonded remote that is not connected is asked to connect again this often in a recover window */
    private static final long CONNECT_RETRY_MS = 10_000;
    private static final String ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    private static final String EXTRA_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL";

    // ---- remote lost prompt (Lumen OS 1.0)
    private static final long LOST_AFTER_MS = 60_000;      // no remote connected this long after a wake
    private static final long UNBOND_GRACE_MS = 5_000;     // a removed bond: let a re-pair finish first
    private static final long LOST_RETRY_MS = 30_000;      // a blocker (dream, other panel) went away?
    private static final long[] LOST_SNOOZE_MS = {30 * 60_000L, 4 * 60 * 60_000L, 24 * 60 * 60_000L};
    private static final int LOST_MAX_DISMISSALS = 3;
    /** boolean: an XGIMI remote was connected on this projector at least once (prompt precondition). */
    private static final String KEY_HAD_REMOTE = "had_remote";
    /** long, wall clock: no prompt before this (the user dismissed the last one). */
    private static final String KEY_LOST_SNOOZE = "lost_snooze_until";
    /** int: prompts dismissed by the user since a remote was last connected. */
    private static final String KEY_LOST_DISMISSALS = "lost_dismissals";

    private static Context sApp;
    private static SafeHandler sH;
    private static RemoteHost sHost;
    /** the HID host profile proxy (connects a bonded remote), null until the stack hands it over */
    private static volatile BluetoothHidHost sHid;
    private static long sConnectAskedAt;
    private static final Map<String, Integer> sBattery = new HashMap<>();

    // ---- state, lumen-remote thread only (except the volatile ones, read by the scan callback)
    private static volatile boolean sScanning;
    private static int sScanMode = -1;
    private static long sScanStartedAt;
    private static long sLastStart;
    private static volatile String sBondingAddr;
    private static volatile String sRepairAddr;
    private static long sManualUntil;
    private static long sBootFrom, sBootUntil;
    /** Current pairing window (MODE_*); MODE_MANUAL also widens the binder-thread pre-filter. */
    private static volatile int sMode = MODE_IDLE;
    /** Start of the current NEW budget (boot, screen on, Bluetooth on, bond removed, 'Pair remote'). */
    private static long sNewSince;
    /** Re-pair candidate: bonded, not connected, advertising Limited; confirmed by REPAIR_CHECK. */
    private static String sRepairCand;
    private static long sRepairCandFirst, sRepairCandLast, sRepairCandConfirm, sRepairCandFresh;
    private static boolean sRepairCandAcl;
    private static Boolean sLastAudio;
    private static String sLastIdle;
    private static final Set<String> sRecoverSkipped = new HashSet<>();
    private static final Set<String> sSeen = new HashSet<>();
    private static final Map<String, int[]> sFails = new HashMap<>();      // addr -> {count}
    private static final Map<String, Long> sCooldownUntil = new HashMap<>();
    /** Advertised name of the remote being bonded (State.found); read by binder threads. */
    private static volatile String sBondingName;
    /** The remote-lost prompt is on screen: scan as a NEW window at LOW_LATENCY. */
    private static volatile boolean sPromptOpen;
    /** Remote-lost bookkeeping of the current wake (boot / SCREEN_ON), lumen-remote thread. */
    private static long sWakeAt;
    private static boolean sConnSinceWake, sLostDoneThisWake;
    /** lostCheck re-checks (5 s apart) of a remote whose link is up but no input device of it is seen. */
    private static int sLinkOnlyChecks;
    private static final int LINK_ONLY_CHECKS = 6;

    private static final Runnable EVALUATE = RemotePairing::evaluate;
    private static final Runnable BOND_TIMEOUT = RemotePairing::bondTimeout;
    private static final Runnable REPAIR_CHECK = RemotePairing::repairCheck;
    private static final Runnable LOST_CHECK = RemotePairing::lostCheck;

    private static final ScanCallback CB = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult r) {
            if (sH != null && r != null && worthPosting(r)) sH.post(() -> handle(r));
        }

        @Override public void onBatchScanResults(List<ScanResult> rs) {
            if (sH == null || rs == null) return;
            for (ScanResult r : rs) if (r != null && worthPosting(r)) sH.post(() -> handle(r));
        }

        @Override public void onScanFailed(int err) {
            Log.w(TAG, "scan failed " + err);
            if (sH == null) return;
            sH.post(() -> {
                if (err == SCAN_FAILED_ALREADY_STARTED) return;   // our callback is still scanning
                sScanning = false;
                sScanMode = -1;
                sH.removeCallbacks(EVALUATE);
                sH.postDelayed(EVALUATE, SCAN_RETRY_MS);
            });
        }
    };

    /**
     * Binder thread, for every advertisement of every device nearby: keep only what can be an XGIMI
     * remote, without IPC (no BluetoothDevice.getName()) and without posting the rest.
     */
    private static boolean worthPosting(ScanResult r) {
        if (!sScanning || !r.isConnectable()) return false;
        ScanRecord rec = r.getScanRecord();
        if (rec == null) return false;
        return RemoteMatch.worthPosting(advert(r, rec.getDeviceName()), sMode == MODE_MANUAL);
    }

    /** The advertisement as RemoteMatch reads it; name = the advertised one or the cached device name. */
    private static RemoteAdvert advert(ScanResult r, String name) {
        ScanRecord rec = r.getScanRecord();
        int flags = rec != null ? rec.getAdvertiseFlags() : -1;
        List<ParcelUuid> uuids = rec != null ? rec.getServiceUuids() : null;
        byte[] xgimi = rec != null ? rec.getManufacturerSpecificData(RemoteMatch.ADV_COMPANY) : null;
        return new RemoteAdvert(name, r.isConnectable(), flags, uuids != null && uuids.contains(HID), xgimi, r.getRssi());
    }

    private RemotePairing() {}

    // =================================================================== entry points

    /** App.onCreate, main thread, once per process; host = the app's RemoteHost. */
    public static void install(Context ctx, RemoteHost host) {
        if (sApp != null) return;
        sHost = host;
        sApp = ctx.getApplicationContext();
        sH = SafeHandler.newThread("lumen-remote");
        sH.post(() -> {
            ensurePermissions();
            registerReceivers();
            bindHid();
            resetNewBudget("process start");
            // The app is persistent: a process start soon after boot is the boot. A later restart
            // (crash) does not open the boot window again, and does not start the remote-lost 60 s
            // rule either: a restart mid-film with the remote asleep is not a wake.
            boolean boot = SystemClock.elapsedRealtime() < BOOT_MAX_UPTIME_MS;
            if (boot) openBootWindow("boot");
            if (boot && interactive()) {
                onWake("boot");
                // Lumen OS 1.0.1: after a restart nobody started at the remote (update, its rollback /
                // stuck gate, the interface resolution, rescue / fastboot) the remote is only asleep:
                // no remote-lost prompt this boot (a removed bond or the next SCREEN_ON re-arm it)
                String why = unattendedBootReason();
                if (why != null) {
                    sLostDoneThisWake = true;
                    sH.removeCallbacks(LOST_CHECK);
                    Log.i(TAG, "boot after an unattended restart (" + why + "): the remote is asleep,"
                            + " no remote-lost prompt this boot");
                }
            }
        });
        sH.postDelayed(EVALUATE, FIRST_CHECK_MS);
    }

    /**
     * User-requested pairing (Projector settings item; v1.0: no longer in the quick panel, pairing is
     * automatic): 120 s fast scan, shows the "Hold Back and Home" card. Any thread.
     */
    public static void startPairing(Context ctx) {
        if (sApp == null) return;
        sH.post(() -> {
            ensurePermissions();
            BluetoothAdapter a = adapter();
            if (a == null || !a.isEnabled()) {
                notify(R.string.lumen_remote_bt_off, 0);
                return;
            }
            Log.i(TAG, "pairing window opened by the user (" + MANUAL_MS / 1000 + " s)");
            sManualUntil = SystemClock.elapsedRealtime() + MANUAL_MS;
            sCooldownUntil.clear();
            sFails.clear();
            resetNewBudget("Pair remote");
            notify(R.string.lumen_remote_pair_hint, R.string.lumen_remote_pair_window);
            evaluate();   // keeps a running LOW_LATENCY scan, no restart (scan start throttling)
        });
    }

    // =================================================================== policy

    private static void openBootWindow(String why) {
        long now = SystemClock.elapsedRealtime();
        sBootFrom = now + BOOT_GRACE_MS;
        sBootUntil = sBootFrom + BOOT_WINDOW_MS;
        Log.i(TAG, "recover window (" + why + "): " + BOOT_WINDOW_MS / 1000 + " s from +" + BOOT_GRACE_MS / 1000
                + " s if the bonded remote does not connect");
    }

    /** A new NEW budget: LOW_LATENCY for NEW_FAST_MS, BALANCED until NEW_MAX_MS, then stop. */
    private static void resetNewBudget(String why) {
        sNewSince = SystemClock.elapsedRealtime();
        sRecoverSkipped.clear();
        Log.i(TAG, "new-remote scan budget restarted (" + why + ")");
    }

    private static void evaluate() {
        sH.removeCallbacks(EVALUATE);
        long now = SystemClock.elapsedRealtime();
        if (sManualUntil != 0 && now >= sManualUntil && sBondingAddr == null && sRepairAddr == null) {
            // the user-requested window ended without a bond
            sManualUntil = 0;
            Log.i(TAG, "pairing window closed without a new remote");
            notify(R.string.lumen_remote_pair_not_found, R.string.lumen_remote_pair_hint);
        }
        boolean manual = sManualUntil != 0 && now < sManualUntil;
        if (!permissionsOk()) {
            ensurePermissions();
            if (!permissionsOk()) {
                Log.w(TAG, "Bluetooth permissions missing, retry later");
                setIdle("no permissions");
                sH.postDelayed(EVALUATE, PERM_RETRY_MS);
                return;
            }
        }
        BluetoothAdapter a = adapter();
        if (a == null || !a.isEnabled()) {
            setIdle("Bluetooth off: waiting for STATE_ON");
            return;
        }
        if (sBondingAddr != null || sRepairAddr != null) return;  // bond receiver / timeout re-evaluates
        if (!interactive()) {
            setIdle("screen off: waiting for SCREEN_ON");
            return;
        }
        int mode;
        long next = TICK_MS;
        boolean prompt = sPromptOpen;
        if (manual) {
            mode = MODE_MANUAL;
            next = Math.min(next, sManualUntil - now);
        } else {
            boolean[] st = xgimiState(a);             // {bonded, connected}
            boolean setupDone = setupComplete();
            if (st[1]) {
                // it connected: the boot / screen-on recover window is not needed any more (a later
                // disconnect = the remote going to sleep, not a lost bond)
                sBootFrom = sBootUntil = 0;
                checkConnectedSoon(0);                // usable (HID up)? then the remote-lost prompt calms down
                setIdle("idle: an XGIMI remote is connected");
                return;
            }
            if (prompt) {
                // the remote-lost prompt asks the user to hold Back + Home right now: a NEW window
                // (strict match, a new or reset remote may be bonded), no budget, LOW_LATENCY below
                mode = MODE_NEW;
            } else if (!st[0]) {
                if (setupDone && now - sNewSince >= NEW_MAX_MS) {
                    setIdle("idle: no XGIMI remote found in " + NEW_MAX_MS / 60_000 + " min of scanning; next scan"
                            + " after screen on / Bluetooth on / 'Pair remote'");
                    return;
                }
                mode = MODE_NEW;
                if (setupDone) next = Math.min(next, sNewSince + NEW_MAX_MS - now);
            } else if (!setupDone) {
                mode = MODE_RECOVER;
            } else if (now < sBootFrom) {
                setIdle("bonded remote not connected: recover window opens in " + (sBootFrom - now) / 1000 + " s");
                sH.postDelayed(EVALUATE, sBootFrom - now);
                return;
            } else if (now < sBootUntil) {
                mode = MODE_RECOVER;
                next = Math.min(next, sBootUntil - now);
            } else {
                setIdle("idle: XGIMI remote bonded, not connected (asleep); no window open");
                return;
            }
        }
        if (mode == MODE_RECOVER) {
            // a bonded remote that does not come back by itself is asked to connect every CONNECT_RETRY_MS (X50 rule)
            if (now - sConnectAskedAt >= CONNECT_RETRY_MS) connectBonded("recover window");
            next = Math.min(next, CONNECT_RETRY_MS);
        }
        if (sMode != mode) Log.i(TAG, "pairing window " + MODE_NAMES[sMode] + " -> " + MODE_NAMES[mode]);
        sMode = mode;
        sLastIdle = null;

        // MANUAL, setup and the remote-lost prompt: LOW_LATENCY. Automatic windows after setup: NEW
        // drops to BALANCED after NEW_FAST_MS; both are capped at LOW_POWER while a Bluetooth audio
        // device is connected.
        int want = ScanSettings.SCAN_MODE_LOW_LATENCY;
        boolean fastNew = false;
        if (mode != MODE_MANUAL && !prompt && setupComplete()) {
            if (mode == MODE_NEW) {
                fastNew = now - sNewSince < NEW_FAST_MS;
                if (!fastNew) want = ScanSettings.SCAN_MODE_BALANCED;
            }
            boolean audio = btAudioConnected(a);
            if (sLastAudio == null || sLastAudio != audio) {
                Log.i(TAG, audio ? "Bluetooth audio connected: automatic scan capped at LOW_POWER"
                        : "no Bluetooth audio connected");
                sLastAudio = audio;
            }
            if (audio) want = ScanSettings.SCAN_MODE_LOW_POWER;
        }
        if (sScanning && (sScanMode != want || now - sScanStartedAt >= SCAN_REFRESH_MS)) {
            Log.i(TAG, sScanMode != want ? "scan mode " + sScanMode + " -> " + want : "refreshing the long-running scan");
            stopScan();
        }
        if (!sScanning) {
            long gap = now - sLastStart;
            if (sLastStart != 0 && gap < MIN_START_GAP_MS) {
                sH.postDelayed(EVALUATE, MIN_START_GAP_MS - gap);
                return;
            }
            startScan(a, want);
            if (!sScanning) {
                sH.postDelayed(EVALUATE, SCAN_RETRY_MS);
                return;
            }
        }
        if (fastNew) next = Math.min(next, sNewSince + NEW_FAST_MS - now);
        sH.postDelayed(EVALUATE, Math.max(1_000, next));
    }

    private static void setIdle(String why) {
        stopScan();
        sMode = MODE_IDLE;
        if (!why.equals(sLastIdle)) Log.i(TAG, why);
        sLastIdle = why;
    }

    // =================================================================== scanning

    private static void startScan(BluetoothAdapter a, int mode) {
        BluetoothLeScanner s = a.getBluetoothLeScanner();
        if (s == null) {
            Log.w(TAG, "no LE scanner");
            return;
        }
        try {
            ScanSettings st = new ScanSettings.Builder()
                    .setScanMode(mode)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setReportDelay(0)
                    .build();
            sSeen.clear();
            sScanning = true;      // before startScan: the binder pre-filter checks it
            s.startScan(null, st, CB);    // unfiltered (see the class comment), name pre-filter in CB
            sScanMode = mode;
            sLastStart = sScanStartedAt = SystemClock.elapsedRealtime();
            Log.i(TAG, "continuous scan started mode=" + mode + " window=" + MODE_NAMES[sMode]);
        } catch (Throwable t) {
            Log.w(TAG, "startScan: " + t);
            sScanning = false;
            sScanMode = -1;
        }
    }

    private static void stopScan() {
        // a re-pair candidate can only be confirmed by the scan that saw it
        dropRepairCandidate(null);
        if (!sScanning) return;
        sScanning = false;
        sScanMode = -1;
        try {
            BluetoothAdapter a = adapter();
            BluetoothLeScanner s = a == null ? null : a.getBluetoothLeScanner();
            if (s != null) s.stopScan(CB);
            Log.i(TAG, "scan stopped after " + (SystemClock.elapsedRealtime() - sScanStartedAt) / 1000 + " s");
        } catch (Throwable t) {
            Log.w(TAG, "stopScan: " + t);
        }
    }

    private static void handle(ScanResult r) {
        if (!sScanning || sMode == MODE_IDLE || sBondingAddr != null || sRepairAddr != null) return;
        BluetoothDevice d = r.getDevice();
        if (d == null) return;
        String addr = d.getAddress();
        ScanRecord rec = r.getScanRecord();
        String name = rec != null ? rec.getDeviceName() : null;
        if (name == null) {
            try { name = d.getName(); } catch (Throwable ignored) { }
        }
        RemoteAdvert a = advert(r, name);
        boolean candidate = RemoteMatch.candidate(a, sMode == MODE_MANUAL);
        boolean manualNow = sMode == MODE_MANUAL && SystemClock.elapsedRealtime() < sManualUntil;
        if (sSeen.add(addr)) {
            // once per address and scan: what the first live boot of a new remote needs to tell which rule refused it
            Log.i(TAG, "advert " + addr + " " + a + " bond=" + d.getBondState() + " -> " + (!candidate ? "not a remote"
                    : looksLikeRemote(a, manualNow) ? "a remote in pairing mode" : "a remote, not in pairing mode"));
        }
        if (!candidate || !r.isConnectable()) return;
        if (name != null && name.equalsIgnoreCase(ownName())) return;
        boolean pairing = a.limited() || a.stockPairingMark();
        if (!pairing && addr.equals(sRepairCand)) {
            dropRepairCandidate("advertises without the Limited flag or the pairing mark (reconnecting, not pairing)");
            return;
        }
        if (!looksLikeRemote(a, manualNow)) return;
        Long cool = sCooldownUntil.get(addr);
        if (cool != null && SystemClock.elapsedRealtime() < cool) return;

        int bond = d.getBondState();
        if (bond == BluetoothDevice.BOND_BONDING) return;
        long now = SystemClock.elapsedRealtime();
        if (bond == BluetoothDevice.BOND_BONDED) {
            // Maybe our own bonded remote, reset into pairing mode (lost our keys): re-pair, but only
            // after REPAIR_CHECK confirms it (a sleeping remote waking up advertises too). Automatic
            // mode already requires a table remote in pairing mode above; the address must be an XGIMI remote.
            if (!pairing || connected(d) || !(manualNow || isXgimiRemote(d))) {
                if (addr.equals(sRepairCand)) dropRepairCandidate("connected / not a re-pair candidate");
                return;
            }
            if (addr.equals(sRepairCand)) {
                sRepairCandLast = now;
                return;
            }
            if (sRepairCand != null) return;   // one candidate at a time
            boolean fast = sScanMode == ScanSettings.SCAN_MODE_LOW_LATENCY;
            sRepairCand = addr;
            sRepairCandFirst = sRepairCandLast = now;
            sRepairCandConfirm = fast ? REPAIR_CONFIRM_MS : REPAIR_CONFIRM_SLOW_MS;
            sRepairCandFresh = fast ? REPAIR_FRESH_MS : REPAIR_FRESH_SLOW_MS;
            sRepairCandAcl = false;
            Log.i(TAG, "bonded " + addr + " advertises pairing mode while not connected (" + MODE_NAMES[sMode]
                    + "): re-pair if it still does in " + sRepairCandConfirm / 1000 + " s");
            sH.removeCallbacks(REPAIR_CHECK);
            sH.postDelayed(REPAIR_CHECK, sRepairCandConfirm);
            return;
        }
        if (sMode == MODE_RECOVER && !manualNow && setupComplete() && !knownRemote(addr)) {
            // 6.3.1 policy: the recover window (a remote is bonded) never bonds anything new. Lumen OS
            // 1.0: except during the first-run setup, whose first step asks for exactly this.
            if (sRecoverSkipped.add(addr)) {
                Log.i(TAG, "recover window: " + addr + " (" + name + ") is in pairing mode but is not a bonded"
                        + " remote: not paired automatically ('Pair remote' pairs it)");
            }
            return;
        }
        bondNow(d, name);
    }

    /**
     * Automatic (unattended) windows: only a remote of the table or the stock pairing advert (RemoteMatch.automatic).
     * The broader match (an XGIMI name with RC / REMOTE / HID / limited, any HID remote or keyboard in pairing mode)
     * is kept for the user-started startPairing() window only.
     */
    private static boolean looksLikeRemote(RemoteAdvert a, boolean manualNow) {
        return manualNow ? RemoteMatch.manual(a, ownName())
                : RemoteMatch.automatic(a, ownName(), anyRemoteBonded(), !setupComplete());
    }

    private static void dropRepairCandidate(String why) {
        if (sRepairCand == null) return;
        if (why != null) Log.i(TAG, "re-pair candidate " + sRepairCand + ": " + why + "; bond kept");
        sRepairCand = null;
        if (sH != null) sH.removeCallbacks(REPAIR_CHECK);
    }

    /** REPAIR_CONFIRM_MS after the first Limited advert of a bonded, not connected remote. */
    private static void repairCheck() {
        String addr = sRepairCand;
        if (addr == null) return;
        long first = sRepairCandFirst, last = sRepairCandLast, fresh = sRepairCandFresh;
        long confirm = sRepairCandConfirm;
        boolean acl = sRepairCandAcl;
        sRepairCand = null;
        long now = SystemClock.elapsedRealtime();
        boolean manualNow = sMode == MODE_MANUAL && now < sManualUntil;
        String keep = null;
        BluetoothAdapter a = adapter();
        BluetoothDevice d = null;
        try { d = a == null ? null : a.getRemoteDevice(addr); } catch (Throwable ignored) { }
        if (!sScanning || sMode == MODE_IDLE || sBondingAddr != null || sRepairAddr != null || d == null) {
            keep = "window closed";
        } else if (now - last > fresh || last - first < confirm - fresh) {
            keep = "stopped advertising pairing mode within " + confirm / 1000 + " s (a waking remote)";
        } else if (acl && !manualNow) {
            keep = "ACL connected meanwhile (a waking remote)";
        } else if (d.getBondState() != BluetoothDevice.BOND_BONDED) {
            keep = "no longer bonded";
        } else if (connected(d)) {
            keep = "connected now";
        }
        if (keep != null) {
            Log.i(TAG, "re-pair candidate " + addr + ": " + keep + "; bond kept");
            sH.removeCallbacks(EVALUATE);
            sH.postDelayed(EVALUATE, 1_000);
            return;
        }
        Log.i(TAG, "bonded " + addr + " kept advertising pairing mode for " + (last - first) / 1000
                + " s while not connected: removeBond, then pair again (" + MODE_NAMES[sMode] + ")");
        rememberBonded(addr);   // the re-pair may finish as a new bond in a RECOVER window
        stopScan();
        sRepairAddr = addr;
        boolean ok = false;
        try { ok = d.removeBond(); } catch (Throwable t) { Log.w(TAG, "removeBond: " + t); }
        if (!ok) {
            sRepairAddr = null;
            recordFail(addr);
            sH.postDelayed(EVALUATE, 2_000);
        } else {
            sH.removeCallbacks(BOND_TIMEOUT);
            sH.postDelayed(BOND_TIMEOUT, BOND_TIMEOUT_MS);
        }
    }

    private static void bondNow(BluetoothDevice d, String name) {
        stopScan();
        sBondingAddr = d.getAddress();
        boolean ok = false;
        try {
            ok = d.createBond(BluetoothDevice.TRANSPORT_LE);
        } catch (Throwable t) {
            Log.w(TAG, "createBond: " + t);
        }
        Log.i(TAG, "createBond(LE) " + sBondingAddr + " (" + name + ") -> " + ok);
        if (!ok) {
            recordFail(sBondingAddr);
            sBondingAddr = null;
            sH.postDelayed(EVALUATE, 2_000);
            return;
        }
        sBondingName = name;
        // the remote-lost prompt shows the same state itself ("Found <name>")
        if (!sPromptOpen) notify(R.string.lumen_remote_pairing, 0);
        stateChanged();
        sH.removeCallbacks(BOND_TIMEOUT);
        sH.postDelayed(BOND_TIMEOUT, BOND_TIMEOUT_MS);
    }

    private static void bondTimeout() {
        BluetoothAdapter a = adapter();
        String addr = sBondingAddr != null ? sBondingAddr : sRepairAddr;
        if (addr == null) return;
        Log.w(TAG, "bonding " + addr + " timed out");
        if (sBondingAddr != null && a != null) {
            try {
                BluetoothDevice d = a.getRemoteDevice(addr);
                if (d.getBondState() == BluetoothDevice.BOND_BONDING) d.cancelBondProcess();
            } catch (Throwable t) {
                Log.w(TAG, "cancelBondProcess: " + t);
            }
        }
        recordFail(addr);
        sBondingAddr = null;
        sRepairAddr = null;
        sH.postDelayed(EVALUATE, 2_000);
    }

    private static void recordFail(String addr) {
        if (addr == null) return;
        int[] c = sFails.get(addr);
        if (c == null) sFails.put(addr, c = new int[1]);
        if (++c[0] >= FAILS_BEFORE_COOLDOWN) {
            c[0] = 0;
            sCooldownUntil.put(addr, SystemClock.elapsedRealtime() + FAIL_COOLDOWN_MS);
            Log.i(TAG, addr + " failed " + FAILS_BEFORE_COOLDOWN + " times: cooldown");
        }
    }

    // =================================================================== receivers

    private static void registerReceivers() {
        try {
            IntentFilter pf = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
            pf.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            sApp.registerReceiver(PAIRING, pf, null, sH, Context.RECEIVER_EXPORTED);

            IntentFilter bf = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            bf.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            bf.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            bf.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            bf.addAction(ACTION_A2DP_CONN);
            bf.addAction(ACTION_LE_AUDIO_CONN);
            bf.addAction(ACTION_HEARING_AID_CONN);
            bf.addAction(ACTION_BATTERY);
            sApp.registerReceiver(BT, bf, null, sH, Context.RECEIVER_EXPORTED);

            IntentFilter sf = new IntentFilter(Intent.ACTION_SCREEN_ON);
            sf.addAction(Intent.ACTION_SCREEN_OFF);
            sApp.registerReceiver(SCREEN, sf, null, sH, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            Log.e(TAG, "registerReceivers", t);
        }
    }

    private static final BroadcastReceiver PAIRING = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            try {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                int v = i.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                if (d == null || sBondingAddr == null || !sBondingAddr.equals(d.getAddress())) return;
                Log.i(TAG, "pairing request " + d.getAddress() + " variant=" + v);
                if (v != BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION
                        && v != 3 /* PAIRING_VARIANT_CONSENT */) {
                    Log.w(TAG, "variant " + v + " needs user input; left to the system dialog");
                    return;
                }
                boolean ok = d.setPairingConfirmation(true);
                Log.i(TAG, "setPairingConfirmation(true) -> " + ok);
                if (ok && isOrderedBroadcast()) abortBroadcast();
            } catch (Throwable t) {
                Log.w(TAG, "pairing request: " + t);
            }
        }
    };

    private static final BroadcastReceiver BT = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            try {
                String a = i.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(a)) {
                    int s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    Log.i(TAG, "adapter state " + s);
                    if (s == BluetoothAdapter.STATE_ON) {
                        bindHid();
                        resetNewBudget("Bluetooth on");
                        sH.removeCallbacks(EVALUATE);
                        sH.postDelayed(EVALUATE, 3_000);
                    } else if (s == BluetoothAdapter.STATE_TURNING_OFF || s == BluetoothAdapter.STATE_OFF) {
                        setIdle("Bluetooth turning off");
                        sBondingAddr = null;
                        sRepairAddr = null;
                        sH.removeCallbacks(BOND_TIMEOUT);
                    }
                    return;
                }
                if (ACTION_A2DP_CONN.equals(a) || ACTION_LE_AUDIO_CONN.equals(a) || ACTION_HEARING_AID_CONN.equals(a)) {
                    // Bluetooth audio came / went: the automatic scan mode depends on it
                    if (sMode != MODE_IDLE) {
                        sH.removeCallbacks(EVALUATE);
                        sH.postDelayed(EVALUATE, 1_000);
                    }
                    return;
                }
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                if (d == null) return;
                String addr = d.getAddress();
                if (ACTION_BATTERY.equals(a)) {
                    int level = i.getIntExtra(EXTRA_BATTERY, -1);
                    if (isXgimiRemote(d)) {
                        synchronized (sBattery) { sBattery.put(addr, level); }
                        Log.i(TAG, "battery " + addr + " " + level + " %");
                        stateChanged();
                    }
                    return;
                }
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a) && addr.equals(sRepairCand)) {
                    Log.i(TAG, "re-pair candidate " + addr + ": ACL connected");
                    sRepairCandAcl = true;
                }
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(a)) {
                    int s = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                    onBondState(d, addr, s);
                    if (isXgimiRemote(d)) stateChanged();
                } else if (isXgimiRemote(d)) {
                    // ACL connected / disconnected of the remote: re-check the policy
                    sH.removeCallbacks(EVALUATE);
                    sH.postDelayed(EVALUATE, 1_000);
                    stateChanged();
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) {
                        // the HID input device comes up a moment after the ACL link: report it again
                        sH.postDelayed(RemotePairing::stateChanged, 2_500);
                        checkConnectedSoon(2_500);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "bt receiver: " + t);
            }
        }
    };

    private static final BroadcastReceiver SCREEN = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            try {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    setIdle("screen off: waiting for SCREEN_ON");
                    sH.removeCallbacks(LOST_CHECK);       // OverlayHost closes a shown prompt itself
                } else {
                    // waking from standby is the user's "power on": same recover window as at boot
                    openBootWindow("screen on");
                    connectBonded("screen on");
                    resetNewBudget("screen on");
                    sH.removeCallbacks(EVALUATE);
                    sH.postDelayed(EVALUATE, 2_000);
                    onWake("screen on");
                }
            } catch (Throwable t) {
                Log.w(TAG, "screen receiver: " + t);
            }
        }
    };

    private static void onBondState(BluetoothDevice d, String addr, int s) {
        boolean ours = addr.equals(sBondingAddr);
        boolean repair = addr.equals(sRepairAddr);
        if (!ours && !repair) {
            if (isXgimiRemote(d)) {
                Log.i(TAG, "bond state " + addr + " -> " + s + " (not started by us)");
                if (s == BluetoothDevice.BOND_NONE) {
                    resetNewBudget("a remote's bond was removed");
                    // remote lost: ask for Back + Home if no other XGIMI remote stays bonded (a new
                    // reason, so even after a prompt of this wake; the snooze still applies)
                    sLostDoneThisWake = false;
                    sH.removeCallbacks(LOST_CHECK);
                    sH.postDelayed(LOST_CHECK, UNBOND_GRACE_MS);
                }
                sH.removeCallbacks(EVALUATE);
                sH.postDelayed(EVALUATE, 2_000);
            }
            return;
        }
        Log.i(TAG, "bond state " + addr + " -> " + s + (repair ? " (re-pair)" : ""));
        if (repair) {
            if (s == BluetoothDevice.BOND_NONE) {
                sRepairAddr = null;
                sH.removeCallbacks(BOND_TIMEOUT);
                String name = null;
                try { name = d.getName(); } catch (Throwable ignored) { }
                bondNow(d, name);
            }
            return;
        }
        if (s == BluetoothDevice.BOND_BONDED) {
            sH.removeCallbacks(BOND_TIMEOUT);
            sBondingAddr = null;
            sManualUntil = 0;
            sFails.remove(addr);
            // a just-bonded remote that is not reported connected yet must not look "reset"
            sCooldownUntil.put(addr, SystemClock.elapsedRealtime() + JUST_BONDED_MS);
            rememberBonded(addr);
            String name = null;
            try { name = d.getName(); } catch (Throwable ignored) { }
            Log.i(TAG, "remote bonded: " + addr + " " + name);
            connect(d, "bonded");
            if (!sPromptOpen) Notify.show(sApp, sApp.getString(R.string.lumen_remote_connected), name);
            sH.postDelayed(EVALUATE, 2_000);
        } else if (s == BluetoothDevice.BOND_NONE) {
            sH.removeCallbacks(BOND_TIMEOUT);
            sBondingAddr = null;
            recordFail(addr);
            if (SystemClock.elapsedRealtime() < sManualUntil) {
                notify(R.string.lumen_remote_pair_failed, R.string.lumen_remote_pair_hint);
            }
            sH.postDelayed(EVALUATE, 2_000);
        }
    }

    // =================================================================== Lumen OS 1.0: read-only state (SetupBridge)

    /**
     * Current remote state (bonded count, connected = linked AND its HID input device up, name, scanning, window
     * mode, battery) for the SetupBridge remote_state and the menus. Any thread; before install: an idle snapshot.
     */
    public static RemoteState state(Context ctx) {
        RemoteState st = new RemoteState();
        if (sApp == null) return st;
        st.missingPermission = !permissionsOk();
        st.scanning = sScanning;
        int m = sMode;
        st.mode = m >= 0 && m < MODE_NAMES.length ? MODE_NAMES[m] : "idle";
        String bonding = sBondingAddr, repair = sRepairAddr;
        st.pairing = bonding != null || repair != null;
        if (bonding != null) {
            String n = sBondingName;
            st.found = n == null ? "" : n;
        }
        try {
            BluetoothAdapter a = adapter();
            Set<BluetoothDevice> set = a == null || !a.isEnabled() ? null : a.getBondedDevices();
            if (set != null) {
                for (BluetoothDevice d : set) {
                    if (!isXgimiRemote(d)) continue;
                    st.bonded++;
                    String n = null;
                    try { n = d.getName(); } catch (Throwable ignored) { }
                    if (connected(d) && hidUp(d.getAddress(), n)) {
                        st.connected = true;
                        if (n != null) st.name = n;
                        synchronized (sBattery) {
                            Integer b = sBattery.get(d.getAddress());
                            if (b != null) st.battery = b;
                        }
                    } else if (st.name.isEmpty() && n != null && !st.connected) {
                        st.name = n;
                    }
                    if (st.pairing && st.found.isEmpty() && n != null && d.getAddress().equals(repair)) st.found = n;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "state: " + t);
        }
        return st;
    }

    /**
     * The remote's keys reach Android: an input device with its Bluetooth address exists
     * (InputDevice.getBluetoothAddress, hidden, needs android.permission.BLUETOOTH). Otherwise an
     * external one with an XGIMI remote vendor id or with the remote's Bluetooth name counts, also when
     * it reports another address (an LE remote's input device may carry another form of its address
     * than BluetoothDevice.getAddress; such a device can only exist while an XGIMI remote's HID is up,
     * so a mismatch must not make a working remote look lost). Any thread.
     */
    static boolean hidUp(String addr, String btName) {
        if (addr == null || sApp == null) return false;
        try {
            InputManager im = sApp.getSystemService(InputManager.class);
            if (im == null) return false;
            boolean byIdentity = false;
            for (int id : im.getInputDeviceIds()) {
                InputDevice dev = im.getInputDevice(id);
                if (dev == null || !dev.isExternal()) continue;
                String a = null;
                try { a = dev.getBluetoothAddress(); } catch (Throwable ignored) { }
                if (a != null && a.equalsIgnoreCase(addr)) return true;
                String dn = dev.getName();
                if (RemoteMatch.isRemoteVendor(dev.getVendorId())
                        || (btName != null && !btName.isEmpty() && dn != null && dn.startsWith(btName))) {
                    byIdentity = true;
                }
            }
            return byIdentity;
        } catch (Throwable t) {
            Log.w(TAG, "input devices: " + t);
            return false;
        }
    }

    /** Bond / connection of a remote changed: tell the first-run setup (lossy event, it also polls). */
    private static void stateChanged() {
        try {
            if (sHost != null) sHost.remoteChanged(sApp);
        } catch (Throwable t) {
            Log.w(TAG, "setup event: " + t);
        }
        if (sPromptOpen) postPromptState();
    }

    // =================================================================== Lumen OS 1.0: remote lost prompt

    /** Re-checks of CONN_CHECK left while the link is up but the HID input device is not (yet). */
    private static int sConnChecksLeft;

    /** After a remote's link came up: is it usable (HID up)? Re-checked a few times, lumen-remote. */
    private static final Runnable CONN_CHECK = () -> {
        BluetoothAdapter a = adapter();
        if (a == null || !a.isEnabled()) return;
        if (usableRemote(a)) {
            noteRemoteConnected("hid up");
        } else if (sConnChecksLeft-- > 0 && xgimiState(a)[1]) {
            sH.postDelayed(RemotePairing.CONN_CHECK, 4_000);
        }
    };

    private static void checkConnectedSoon(long delayMs) {
        sConnChecksLeft = 3;
        sH.removeCallbacks(CONN_CHECK);
        sH.postDelayed(CONN_CHECK, delayMs);
    }

    /** The reboot reason of this boot when it names a restart nobody started at the remote (RemoteHost), else null. */
    private static String unattendedBootReason() {
        try {
            return sHost == null ? null : sHost.unattendedBootReason();
        } catch (Throwable t) {
            Log.w(TAG, "boot reason: " + t);
            return null;
        }
    }

    /** Boot / SCREEN_ON (lumen-remote): a new wake; a bonded remote has LOST_AFTER_MS to connect. */
    private static void onWake(String why) {
        sWakeAt = SystemClock.elapsedRealtime();
        sConnSinceWake = false;
        sLostDoneThisWake = false;
        sLinkOnlyChecks = 0;
        sH.removeCallbacks(LOST_CHECK);
        sH.postDelayed(LOST_CHECK, LOST_AFTER_MS);
    }

    /**
     * lumen-remote: an XGIMI remote is usable (HID up): the projector "had a remote", the snooze and the
     * dismissal count are cleared, no prompt this wake.
     */
    private static void noteRemoteConnected(String why) {
        sConnSinceWake = true;
        sH.removeCallbacks(LOST_CHECK);
        try {
            SharedPreferences p = prefs();
            if (!p.getBoolean(KEY_HAD_REMOTE, false) || p.contains(KEY_LOST_DISMISSALS) || p.contains(KEY_LOST_SNOOZE)) {
                p.edit().putBoolean(KEY_HAD_REMOTE, true).remove(KEY_LOST_DISMISSALS).remove(KEY_LOST_SNOOZE).apply();
                Log.i(TAG, "remote usable (" + why + "): remote-lost prompt armed, snooze cleared");
            }
        } catch (Throwable t) {
            Log.w(TAG, "prefs: " + t);
        }
        if (sPromptOpen) postPromptState();
    }

    /** lumen-remote: is the remote-lost prompt due now? See the class comment for the rules. */
    private static void lostCheck() {
        sH.removeCallbacks(LOST_CHECK);
        if (sPromptOpen || sLostDoneThisWake || !setupComplete() || !interactive()) return;
        BluetoothAdapter a = adapter();
        if (a == null || !a.isEnabled()) return;
        SharedPreferences p = prefs();
        boolean had = p.getBoolean(KEY_HAD_REMOTE, false)
                || !p.getStringSet(KEY_BONDED, new HashSet<>()).isEmpty();
        if (!had) return;                                   // never had an XGIMI remote: nothing was lost
        if (sBondingAddr != null || sRepairAddr != null) {
            sH.postDelayed(LOST_CHECK, LOST_RETRY_MS);
            return;
        }
        if (usableRemote(a)) {
            noteRemoteConnected("lost check");
            return;
        }
        boolean[] st = xgimiState(a);                       // {bonded, ACL connected}
        if (st[1]) {                                        // link up, input device not (yet)
            if (++sLinkOnlyChecks <= LINK_ONLY_CHECKS) {
                sH.postDelayed(LOST_CHECK, 5_000);
                return;
            }
            // the link has stayed up ~30 s without a recognised input device: the remote came back
            // this wake (only its input device is not recognised); no prompt when it sleeps later,
            // and no endless 5 s polling
            Log.i(TAG, "XGIMI remote linked " + LINK_ONLY_CHECKS * 5 + " s without a recognised input device:"
                    + " counted as back this wake");
            sConnSinceWake = true;
            return;
        }
        sLinkOnlyChecks = 0;
        long now = SystemClock.elapsedRealtime();
        final String reason;
        if (!st[0]) {
            reason = "no XGIMI remote bonded any more";
        } else if (sConnSinceWake) {
            return;                                         // used this wake, asleep now: normal
        } else if (now - sWakeAt < LOST_AFTER_MS) {
            sH.postDelayed(LOST_CHECK, sWakeAt + LOST_AFTER_MS - now);
            return;
        } else {
            reason = "no XGIMI remote connected " + (now - sWakeAt) / 1000 + " s after the wake";
        }
        int dismissed = p.getInt(KEY_LOST_DISMISSALS, 0);
        long snooze = p.getLong(KEY_LOST_SNOOZE, 0);
        if (dismissed >= LOST_MAX_DISMISSALS || System.currentTimeMillis() < snooze) {
            Log.i(TAG, "remote lost (" + reason + "): prompt quiet (" + dismissed + " dismissals"
                    + (snooze > 0 ? ", snoozed" : "") + ")");
            sLostDoneThisWake = true;
            return;
        }
        final boolean bonded = st[0];
        Ui.main().post(() -> {
            int r = RemotePrompt.maybeShow(sApp, reason, bonded, sHost);
            sH.post(() -> onPromptDecision(r, reason));
        });
    }

    /** lumen-remote: RemotePrompt.maybeShow's answer. */
    private static void onPromptDecision(int r, String reason) {
        if (r == RemotePrompt.SHOWN) {
            Log.i(TAG, "remote lost (" + reason + "): prompt shown, pairing window open");
            sLostDoneThisWake = true;
            sH.removeCallbacks(EVALUATE);
            sH.post(EVALUATE);
            postPromptState();
        } else if (r == RemotePrompt.LATER) {
            sH.postDelayed(LOST_CHECK, LOST_RETRY_MS);
        } else {
            sLostDoneThisWake = true;
        }
    }

    /** Main thread (RemotePrompt): the prompt is on screen; the scan becomes a fast NEW window. */
    static void onPromptShown() {
        sPromptOpen = true;
    }

    /**
     * Any thread (RemotePrompt.onDismissed): how = connected | user | off. A user's dismissal snoozes the
     * next prompt (30 min, 4 h, 24 h) and after LOST_MAX_DISMISSALS keeps it quiet until a remote connects.
     */
    static void onPromptClosed(String how) {
        SafeHandler h = sH;
        if (h == null) return;
        h.post(() -> {
            sPromptOpen = false;
            if (RemotePrompt.CLOSED_CONNECTED.equals(how)) {
                noteRemoteConnected("prompt");
            } else if (RemotePrompt.CLOSED_USER.equals(how)) {
                try {
                    SharedPreferences p = prefs();
                    int n = p.getInt(KEY_LOST_DISMISSALS, 0) + 1;
                    long ms = LOST_SNOOZE_MS[Math.min(n, LOST_SNOOZE_MS.length) - 1];
                    p.edit().putInt(KEY_LOST_DISMISSALS, n).putLong(KEY_LOST_SNOOZE, System.currentTimeMillis() + ms).apply();
                    Log.i(TAG, "remote-lost prompt dismissed (" + n + "): quiet for " + ms / 60_000 + " min"
                            + (n >= LOST_MAX_DISMISSALS ? ", then until a remote connects" : ""));
                } catch (Throwable t) {
                    Log.w(TAG, "prefs: " + t);
                }
            } else {
                Log.i(TAG, "remote-lost prompt closed (" + how + ")");
            }
            sH.removeCallbacks(EVALUATE);
            sH.post(EVALUATE);
        });
    }

    /** Main thread (RemotePrompt poll): computes the state on lumen-remote, shows it on the main thread. */
    static void requestPromptState() {
        SafeHandler h = sH;
        if (h != null) h.post(RemotePairing::postPromptState);
    }

    private static void postPromptState() {
        final RemoteState st = state(sApp);
        Ui.main().post(() -> RemotePrompt.apply(st));
    }

    /** A bonded XGIMI remote with its link and its HID input device up. */
    private static boolean usableRemote(BluetoothAdapter a) {
        try {
            Set<BluetoothDevice> set = a.getBondedDevices();
            if (set == null) return false;
            for (BluetoothDevice d : set) {
                if (!isXgimiRemote(d) || !connected(d)) continue;
                String n = null;
                try { n = d.getName(); } catch (Throwable ignored) { }
                if (hidUp(d.getAddress(), n)) return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "bonded devices: " + t);
        }
        return false;
    }

    // =================================================================== helpers

    /** {bonded, connected} for XGIMI remotes among the bonded devices. */
    private static boolean[] xgimiState(BluetoothAdapter a) {
        boolean bonded = false, conn = false;
        try {
            Set<BluetoothDevice> set = a.getBondedDevices();
            if (set != null) {
                for (BluetoothDevice d : set) {
                    if (!isXgimiRemote(d)) continue;
                    bonded = true;
                    if (connected(d)) conn = true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "bonded devices: " + t);
        }
        return new boolean[] {bonded, conn};
    }

    /** Bonded/known device that is a remote: a remote name of the table (RemoteMatch) or bonded by us earlier. */
    private static boolean isXgimiRemote(BluetoothDevice d) {
        try {
            if (RemoteMatch.isRemoteName(d.getName())) return true;
        } catch (Throwable ignored) { }
        try {
            return prefs().getStringSet(KEY_BONDED, new HashSet<>()).contains(d.getAddress());
        } catch (Throwable t) {
            return false;
        }
    }

    /** An address we bonded (or re-paired) earlier. */
    private static boolean knownRemote(String addr) {
        try {
            return prefs().getStringSet(KEY_BONDED, new HashSet<>()).contains(addr);
        } catch (Throwable t) {
            return false;
        }
    }

    /** A Bluetooth audio sink (A2DP, LE audio, hearing aid) is connected: share the radio. */
    private static boolean btAudioConnected(BluetoothAdapter a) {
        for (int p : new int[] {BluetoothProfile.A2DP, PROFILE_LE_AUDIO, PROFILE_HEARING_AID}) {
            try {
                if (a.getProfileConnectionState(p) == BluetoothProfile.STATE_CONNECTED) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    private static boolean connected(BluetoothDevice d) {
        try {
            return d.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void rememberBonded(String addr) {
        try {
            Set<String> s = new HashSet<>(prefs().getStringSet(KEY_BONDED, new HashSet<>()));
            if (s.add(addr)) prefs().edit().putStringSet(KEY_BONDED, s).apply();
        } catch (Throwable t) {
            Log.w(TAG, "prefs: " + t);
        }
    }

    private static SharedPreferences prefs() {
        return sApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String ownName() {
        try {
            BluetoothAdapter a = adapter();
            String n = a == null ? null : a.getName();
            return n == null ? "" : n;
        } catch (Throwable t) {
            return "";
        }
    }

    private static BluetoothAdapter adapter() {
        try {
            BluetoothManager m = sApp.getSystemService(BluetoothManager.class);
            return m == null ? null : m.getAdapter();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean interactive() {
        try {
            PowerManager pm = sApp.getSystemService(PowerManager.class);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Both setup flags (USER_SETUP_COMPLETE and the TV one) must be set. */
    private static boolean setupComplete() {
        return setupDone(sApp);
    }

    /** Both setup flags (USER_SETUP_COMPLETE and the TV one) are set (RemotePrompt too). */
    static boolean setupDone(Context c) {
        try {
            return Settings.Secure.getInt(c.getContentResolver(), "user_setup_complete", 0) != 0
                    && Settings.Secure.getInt(c.getContentResolver(), "tv_user_setup_complete", 0) != 0;
        } catch (Throwable t) {
            return true;
        }
    }

    /** Any remote of the table bonded (the no-flags HID rule only bonds while none is). */
    private static boolean anyRemoteBonded() {
        BluetoothAdapter a = adapter();
        return a != null && xgimiState(a)[0];
    }

    // =================================================================== HID host (from the X50 RemotePair)

    /** Asks the stack for the HID host proxy (again after Bluetooth on); connectBonded runs once it is there. */
    private static void bindHid() {
        try {
            BluetoothAdapter a = adapter();
            if (a == null || sHid != null) return;
            a.getProfileProxy(sApp, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int p, BluetoothProfile proxy) {
                    sH.post(() -> {
                        sHid = (BluetoothHidHost) proxy;
                        Log.i(TAG, "HID host proxy connected");
                        connectBonded("HID host ready");
                    });
                }

                @Override public void onServiceDisconnected(int p) {
                    sH.post(() -> sHid = null);
                }
            }, BluetoothProfile.HID_HOST);
        } catch (Throwable t) {
            Log.w(TAG, "HID host proxy: " + t);
        }
    }

    /** Every bonded remote that is not connected is asked to connect (boot, screen on, recover window). */
    private static void connectBonded(String why) {
        sConnectAskedAt = SystemClock.elapsedRealtime();
        BluetoothAdapter a = adapter();
        if (a == null || !a.isEnabled() || !permissionsOk()) return;
        try {
            Set<BluetoothDevice> set = a.getBondedDevices();
            if (set == null) return;
            for (BluetoothDevice d : set) if (isXgimiRemote(d) && !connected(d)) connect(d, why);
        } catch (Throwable t) {
            Log.w(TAG, "connect bonded: " + t);
        }
    }

    /**
     * Allows the HID host for d, which also connects it (AOSP HidHostService.setConnectionPolicy connects a device
     * set to CONNECTION_POLICY_ALLOWED). A direct system API call (BLUETOOTH_PRIVILEGED), no reflection.
     */
    private static void connect(BluetoothDevice d, String why) {
        BluetoothHidHost p = sHid;
        if (p == null) {
            bindHid();
            return;
        }
        try {
            boolean ok = p.setConnectionPolicy(d, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            Log.i(TAG, "HID allow + connect " + d.getAddress() + " (" + why + ") -> " + ok);
        } catch (Throwable t) {
            Log.w(TAG, "HID connect " + d.getAddress() + ": " + t);
        }
    }

    private static boolean permissionsOk() {
        return granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT);
    }

    private static boolean granted(String p) {
        return sApp.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    /** Self-grant fallback when the default-permissions XML did not run (GRANT_RUNTIME_PERMISSIONS). */
    private static void ensurePermissions() {
        for (String p : new String[] {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}) {
            if (granted(p)) continue;
            try {
                sApp.getPackageManager().grantRuntimePermission(sApp.getPackageName(), p, Process.myUserHandle());
                Log.i(TAG, "self-granted " + p + " -> " + granted(p));
            } catch (Throwable t) {
                Log.w(TAG, "self-grant " + p + ": " + t);
            }
        }
    }

    private static void notify(int titleRes, int descRes) {
        try {
            Notify.show(sApp, sApp.getString(titleRes), descRes != 0 ? sApp.getString(descRes) : null);
        } catch (Throwable t) {
            Log.w(TAG, "notify: " + t);
        }
    }
}
