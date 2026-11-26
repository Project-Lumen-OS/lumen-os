// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.lumen.fwcopy.FwModel;
import org.lumen.fwcopy.FwSet;

/**
 * Checks before an update is offered or installed. Every check reads a property, a file of the
 * image, a sticky broadcast or asks update_engine. install() runs this model's list (UpdateTarget.checks(),
 * InstallChecks) in order, the first refusal wins. The one write: the lumen_ota gate probe asks the boot gate for a
 * fresh read (sys.z9x.ota.request=probe) and waits for it, so install() must run on a worker thread
 * (UpdaterActivity.bg and the UpdateService worker are its only callers).
 * <ul>
 * <li>VBMETA: the other slot (the one update_engine writes) must start an updated system: its vbmeta has
 *     verification off (flags bit 2). An in-DSU install that updates into the stock slot leaves that slot with
 *     flags 3 (HB 1.0.10-beta6); an install without it is refused with the vbmeta step.</li>
 * <li>PENDING: no update waits for its first start (sys.z9x.ota pending).</li>
 * <li>STORAGE, GATE_PROBE, SLOT_STATE: the lumen_ota gate (tools/devices/ota/image/lumen_ota.sh, decide service):
 *     Virtual A/B with the compression this model's payloads use, this build's own partition list without vbmeta
 *     or mboot; a fresh probe; sys.z9x.ota.hal "ok", the next boot is this slot, the slot is marked successful, no merge.</li>
 * <li>RECOVERY_CERT, POWER: the recovery install (Play 6): the recovery trusts the Lumen certificate
 *     (sys.lumen.recovery_cert "ok"), mains power or a battery of at least 50 %.</li>
 * <li>THERMAL, ENGINE: not too hot, update_engine answers and no merge cleans up.</li>
 * </ul>
 */
public final class Preflight {
    private Preflight() {}

    /** A blocking problem: string resource name + argument. */
    public static final class Block {
        public final String res, arg;
        Block(String res, String arg) { this.res = res; this.arg = arg; }
    }

    /** Is this manifest an update for this projector at all? null = yes. "uptodate" = nothing newer. */
    public static Block applicable(UpdateManifest m) {
        return applicable(m.schema, m.device, m.channel, m.vendorIncremental, m.versionCode, m.minVersionCode);
    }

    static Block applicable(int schema, String device, String channel, List<String> vendorIncremental,
                            int versionCode, int minVersionCode) {
        String mine = Ota.device();
        if (schema != 1 || mine.isEmpty() || !mine.equals(device)) return new Block("err_signature", "");
        // the manifest's channel must be a file this check reads (docs/ota.md "Variants", Channels): a USB stick
        // or a mirror may carry another edition's signed manifest, which must never be installed here; a beta
        // manifest counts only while beta updates are on, the stable one always
        if (!Channels.accepts(channel)) return new Block("blk_channel", channel);
        String vendor = Ota.prop("ro.vendor.build.version.incremental");
        if (!vendorIncremental.isEmpty() && !vendorIncremental.contains(vendor)) {
            return new Block("blk_vendor", vendor.isEmpty() ? "?" : vendor);
        }
        // never a downgrade: also after beta updates are turned off, nothing is offered until the stable
        // channel has a version above the installed beta
        int cur = Ota.currentVersionCode();
        if (versionCode <= cur) return new Block("uptodate", "");
        if (cur < minVersionCode) return new Block("blk_min_version", Ota.versionName(minVersionCode));
        return null;
    }

    /** Protected-video blob set check: warning only. */
    public static boolean blobsMissing(UpdateManifest m) {
        if (m.blobsSets.isEmpty()) return false;
        return !"ok".equals(Ota.prop("sys.z9x.blobs")) || !m.blobsSets.contains(Ota.prop("sys.z9x.blobs.set"));
    }

    /** Space for the download in our directory (zip + 256 MB margin). */
    public static Block downloadSpace(Context c, UpdateManifest.Pkg p, long have) {
        File d = Ota.dir(c);
        long need = p.size - have + (256L << 20);
        if (d.getUsableSpace() < need) {
            return new Block("blk_space", android.text.format.Formatter.formatShortFileSize(c, need));
        }
        return null;
    }

    /** Everything that must hold right before the install starts: this model's checks in order. */
    public static Block install(Context c) {
        for (Check k : Ota.T.checks().list) {
            Block b = run(k, c);
            if (b != null) return b;
        }
        return null;
    }

    static Block run(Check k, Context c) {
        switch (k) {
            case VBMETA: return vbmeta();
            case PENDING: return pending();
            case STORAGE: return storage();
            case GATE_PROBE: return probe();
            case SLOT_STATE: return slotState();
            case RECOVERY_CERT: return recoveryCert();
            case POWER: return power(c);
            case THERMAL: return thermal(c);
            case ENGINE: return engine();
            default: return new Block("blk_hal", "boot gate " + Ota.prop(Ota.T.gateProp == null ? "" : Ota.T.gateProp));
        }
    }

    /** The other slot (the one update_engine writes) must start an updated system: verification off (bit 2). */
    static Block vbmeta() {
        String other = Ota.otherSlot();
        int flags = Ota.otherVbmetaFlags();
        if (other.isEmpty() || flags < 0) return new Block("blk_vbmeta_unknown", "");
        if ((flags & 2) == 0) return new Block("blk_vbmeta_title", Ota.slotLetter(other));
        return null;
    }

    static Block pending() {
        return "pending".equals(Ota.prop("sys.z9x.ota")) ? new Block("blk_pending", "") : null;
    }

    static Block thermal(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        if (pm != null && pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) {
            return new Block("blk_thermal", "");
        }
        return null;
    }

    static Block engine() {
        int s = Engine.get().status();
        if (s < 0) return new Block("blk_engine", "");
        if (s == Ota.UE_CLEANUP) return new Block("blk_merge", "");
        return null;
    }

    /**
     * The recovery trusts the Lumen certificate: lumen_recovery.sh publishes sys.lumen.recovery_cert at boot ("ok" =
     * a Lumen recovery, "stock" = XGIMI's own, which would refuse the package).
     */
    static Block recoveryCert() {
        String rc = Ota.prop("sys.lumen.recovery_cert");
        return "ok".equals(rc) ? null : new Block("blk_recovery", rc.isEmpty() ? "?" : rc);
    }

    /** Mains power or a well charged battery: the recovery install takes a few minutes and must not run out. */
    static Block power(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null && b.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
            boolean plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = level < 0 || scale <= 0 ? -1 : level * 100 / scale;
            if (!plugged && pct < 50) return new Block("blk_power", "");
        }
        return null;
    }

    /**
     * The lumen_ota gate's state of the running slot: not pending, rollback possible (HAL ok), the next boot is
     * this slot, slot marked successful, no merge. null = an install may start.
     */
    static Block slotState() {
        if ("pending".equals(Ota.prop("sys.z9x.ota"))) return new Block("blk_pending", "");
        String hal = Ota.prop("sys.z9x.ota.hal").trim();
        if (!"ok".equals(hal)) {
            String why = hal.startsWith("bad:") ? hal.substring(4).trim() : hal;
            return new Block("blk_hal", why.isEmpty() ? "?" : why);
        }
        // the next boot must be this slot: else an update or a rollback is already staged
        String run = Ota.prop("ro.boot.slot_suffix").trim(), act = Ota.prop("sys.z9x.ota.active").trim();
        if (run.isEmpty() || !run.equals(act)) return new Block("blk_active", Ota.slotLetter(act));
        if (!"1".equals(Ota.prop("sys.z9x.ota.marked").trim())) return new Block("blk_not_marked", "");
        String merge = Ota.prop("sys.z9x.ota.merge").trim();
        if (merge.isEmpty()) return new Block("blk_hal", "merge");
        if (!"none".equals(merge)) return new Block("blk_merge", "");
        return null;
    }

    /** The init request property of the gate (lumen_ota.rc: request=probe starts z9x_ota_decide). */
    static final String REQUEST = "sys.z9x.ota.request";
    /** The gate's answer property: "probing" while decide runs, then "ok" or "bad:<why>". */
    static final String HAL = "sys.z9x.ota.hal";
    /** init's state of the probe service ("running" while it runs; may not be readable under enforcing). */
    static final String SVC = "init.svc.z9x_ota_decide";
    /** How long probe() waits for the gate in all, how often it looks, and when it asks once more. */
    static final long PROBE_MS = 20000, STEP_MS = 25, RESEND_MS = 1000;

    /** The wait of probe(); the host test steps a fake gate here. */
    interface Pause { void ms(long ms) throws InterruptedException; }
    static Pause pause = Thread::sleep;

    /**
     * Asks the gate to read the boot HAL again and waits for the answer (worker thread only). The props it
     * publishes are otherwise as old as the boot (or the last probe), so a slot marked since would still read
     * as unmarked. A fresh answer is one seen after this request: hal cleared by us and then set again, or
     * hal "probing" or the service running, then a final hal value. null = fresh; the caller then reads
     * slotState().
     * One install probes twice (UpdaterActivity, then UpdateService), and init ignores a start while the
     * service still runs: so a running service is waited for first, and an unanswered request is sent once more.
     */
    static Block probe() {
        if ("pending".equals(Ota.prop("sys.z9x.ota"))) return new Block("blk_pending", "");
        long waited = 0;
        try {
            for (; "running".equals(Ota.prop(SVC).trim()); waited += STEP_MS) {
                if (waited >= PROBE_MS) return new Block("blk_probe", "timeout");
                pause.ms(STEP_MS);
            }
            // an init trigger needs the value to change: clear it first (it is still "probe" from the last time)
            if (!Ota.setProp(REQUEST, "")) return new Block("blk_probe", "setprop refused");
            // best effort: with hal cleared, a gate faster than one look still reads as fresh. decide then takes
            // this run for the first of the boot, which acts only while sys.z9x.ota is pending: refused above,
            // and count sets pending only at post-fs-data, so it never comes back later in a boot
            boolean cleared = Ota.setProp(HAL, "");
            if (!Ota.setProp(REQUEST, "probe")) return new Block("blk_probe", "setprop refused");
            boolean started = false, resent = false;
            for (long asked = 0; ; waited += STEP_MS, asked += STEP_MS) {
                String hal = Ota.prop(HAL).trim();
                if ("running".equals(Ota.prop(SVC).trim()) || "probing".equals(hal)) started = true;
                else if ((started || cleared) && !hal.isEmpty()) return null;
                if (waited >= PROBE_MS) return new Block("blk_probe", started ? "timeout" : "no answer");
                if (!started && !resent && asked >= RESEND_MS) {
                    resent = true;
                    if (!Ota.setProp(REQUEST, "") || !Ota.setProp(REQUEST, "probe")) {
                        return new Block("blk_probe", "setprop refused");
                    }
                }
                pause.ms(STEP_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Block("blk_probe", "interrupted");
        }
    }

    /** This build's own copy of the partition list (the image writes it there, hb_ota_props.txt). */
    static final String BUILD_PROP = "/system/product/etc/build.prop";

    /**
     * Build facts update_engine relies on: Virtual A/B, its compression as this model's payloads use it
     * (UpdateTarget.compressedSnapshots: the HB legacy snapshots, the X50 family compressed ones as its vendor
     * declares, written, booted and merged by update_engine on the X50 Ultra), and the partition list.
     */
    static Block storage() {
        if (!"true".equals(Ota.prop("ro.virtual_ab.enabled").trim())) return new Block("blk_vab", "virtual A/B off");
        boolean on = "true".equals(Ota.prop("ro.virtual_ab.compression.enabled").trim());
        if (on != Ota.T.compressedSnapshots()) return new Block("blk_vab", on ? "compression on" : "compression off");
        return partitions(Ota.prop("ro.vendor.build.ab_ota_partitions"), Ota.prop("ro.product.ab_ota_partitions"),
                buildList(new File(BUILD_PROP)));
    }

    static Block partitions(String vendor, String product, String own) {
        return partitions(vendor, product, own, Ota.prop("ro.boot.xgimi.modelname").trim());
    }

    /**
     * The static partitions update_engine copies to the other slot: it reads the vendor list first, then the
     * product one. The vendor list must be empty, the product one must be this build's own (a stock product
     * partition lists vbmeta and the logical partitions), and it must be exactly this model's update list of
     * apps/core/lumen-fwcopy (the boot chain with it, never vbmeta, never the HB mboot): the set the installer copied,
     * on the HB without the mboot the installer has made the same in both slots.
     * The block names the first entry that is not in the set, or the set's entry that is missing.
     */
    static Block partitions(String vendor, String product, String own, String model) {
        if (!vendor.trim().isEmpty()) return new Block("blk_partitions", "vendor list");
        String list = product.trim();
        if (own == null) return new Block("blk_partitions", "no build list");
        if (list.isEmpty() || !list.equals(own.trim())) return new Block("blk_partitions", "not the build's list");
        List<String> names = new ArrayList<>();
        for (String p : list.split(",", -1)) {
            String n = p.trim();
            if (!n.matches("[a-z0-9_]+")) return new Block("blk_partitions", "bad entry");
            names.add(n.endsWith("_a") || n.endsWith("_b") ? n.substring(0, n.length() - 2) : n);
        }
        if (FwSet.checkUpdateList(model, list) == null) return null;
        FwModel m = FwModel.of(model);
        if (m == null || m.refusal != null) return new Block("blk_partitions", "model " + (model.isEmpty() ? "?" : model));
        List<String> want = FwSet.updateNames(m);
        for (String n : names) if (!want.contains(n)) return new Block("blk_partitions", n);
        for (String n : want) if (!names.contains(n)) return new Block("blk_partitions", "no " + n);
        return new Block("blk_partitions", "order");
    }

    /** The last ro.product.ab_ota_partitions line of a build.prop (init keeps the last one); null = none. */
    static String buildList(File f) {
        String key = "ro.product.ab_ota_partitions=", v = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f),
                StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                l = l.trim();
                if (l.startsWith(key)) v = l.substring(key.length());
            }
        } catch (IOException | SecurityException e) {
            return null;
        }
        return v;
    }
}
