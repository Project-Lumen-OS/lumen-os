// SPDX-License-Identifier: Apache-2.0
package org.lumen.fwcopy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The XGIMI set of one projector: every partition of its model's stock update list with what the copy does with it
 * ({@link Verdict}), then the partitions the model's vendor post-install step writes ({@link FwModel#postWrites}, the
 * first boot stage: copied by the install the stock way, never in Lumen's own update list). A partition of the stock
 * list is copied only when all of these hold:
 * <ol>
 * <li>XGIMI's own update writes it into the other half (the model's stock ro.product.ab_ota_partitions),</li>
 * <li>it is not on {@link #NEVER} and not a logical partition inside super ({@link #DYNAMIC}),</li>
 * <li>this projector has it in both halves (_a and _b by name),</li>
 * <li>the model does not keep it out ({@link FwModel#keepEqual}: no model does now, the HB mboot is copied since
 * 2026-11-17).</li>
 * </ol>
 * The order is the stock list's (alphabetical), the order XGIMI's update copies them in, then the post-install writes:
 * the first stage is written after mboot and the rest, as XGIMI's postinstall runs after every partition is written.
 */
public final class FwSet {
    /**
     * Never copied, whatever a model's data says (owner rules): the AVB root (the installer writes its own copy with
     * verification off, an update never writes it), the whole eMMC and its boot areas by their kernel names, the boot
     * environment, calibration, factory and per-unit data (one copy shared by both halves), the boot request and the
     * partitions of user data. The first boot stage (rom_emmc_boot) is no longer here (2026-11-18): it is written
     * only as a {@link PostWrite} of a model whose stock post-install step writes it, the stock way.
     */
    public static final List<String> NEVER = Collections.unmodifiableList(Arrays.asList(
            "vbmeta", "mmcblk0", "mmcblk0boot0", "mmcblk0boot1", "uenv", "recovery", "init_boot",
            "vendor_boot", "xgimicri", "xgimisps", "xgimidatabase", "persist", "project_id", "tvcertificate", "tvcustomer",
            "factory", "calibration", "frp", "misc", "metadata", "userdata", "cache", "super"));
    /**
     * Never copied as a partition of the stock list, only as a model's {@link PostWrite} (the running vendor's file, the
     * stock way): a raw copy of an eMMC boot area would carry this unit's own memory training data across.
     */
    public static final List<String> POST_ONLY = Collections.unmodifiableList(Arrays.asList("rom_emmc_boot"));
    /** Logical partitions inside super: the install writes its own table, an update its payload. Never copied here. */
    public static final List<String> DYNAMIC = Collections.unmodifiableList(Arrays.asList(
            "system", "system_ext", "product", "vendor", "odm", "vendor_dlkm", "odm_dlkm", "system_dlkm"));

    /** One partition of the stock list. */
    public static final class Entry {
        public final String name, why;
        public final Verdict verdict;

        Entry(String name, Verdict verdict, String why) { this.name = name; this.verdict = verdict; this.why = why; }

        @Override public String toString() { return name + " " + verdict + " (" + why + ")"; }
    }

    public final FwModel model;
    public final List<Entry> entries;

    private FwSet(FwModel model, List<Entry> entries) { this.model = model; this.entries = Collections.unmodifiableList(entries); }

    /**
     * The set on a projector of this model. byName: the names under /dev/block/by-name of this unit; null = the
     * model's stock dump (the list an image carries for update_engine).
     */
    public static FwSet of(FwModel m, Collection<String> byName) {
        Set<String> names = byName == null ? null : new LinkedHashSet<>(byName);
        List<Entry> out = new ArrayList<>();
        for (String n : m.stockList) {
            boolean modelPair = m.pairs.contains(n);
            boolean unitPair = names == null ? modelPair : names.contains(n + "_a") && names.contains(n + "_b");
            if (NEVER.contains(n) || POST_ONLY.contains(n)) out.add(new Entry(n, Verdict.LEFT_OUT, n.equals("vbmeta")
                    ? "the AVB root: the install writes its own copy, an update never" : "never written"));
            else if (DYNAMIC.contains(n)) out.add(new Entry(n, Verdict.LEFT_OUT, "inside super: written by the install table or the update"));
            else if (!modelPair) out.add(new Entry(n, Verdict.LEFT_OUT, "one copy for both halves on this model"));
            else if (!unitPair) out.add(new Entry(n, Verdict.MISSING, "not in both halves on this projector"));
            else if (m.keepEqual.contains(n)) out.add(new Entry(n, Verdict.KEEP_EQUAL, "never written on this model, must already be the same"));
            else out.add(new Entry(n, Verdict.COPY, "XGIMI's own update copies it"));
        }
        for (PostWrite w : m.postWrites) {
            String n = w.name;
            boolean unitPair = names == null ? m.pairs.contains(n) : names.contains(n + "_a") && names.contains(n + "_b");
            if (NEVER.contains(n) || DYNAMIC.contains(n) || m.stockList.contains(n) || !m.pairs.contains(n))
                out.add(new Entry(n, Verdict.LEFT_OUT, "never written"));
            else if (!unitPair) out.add(new Entry(n, Verdict.MISSING, "not in both halves on this projector"));
            else out.add(new Entry(n, Verdict.COPY, "XGIMI's update writes it from the new vendor's post-install step"));
        }
        return new FwSet(m, out);
    }

    /** the post-install write of this name on the model, null for a partition of the stock list */
    public PostWrite post(String name) {
        for (PostWrite w : model.postWrites) if (w.name.equals(name)) return w;
        return null;
    }

    /** the names of the model's post-install writes (the first boot stage) */
    public static List<String> postNames(FwModel m) {
        List<String> r = new ArrayList<>();
        for (PostWrite w : m.postWrites) r.add(w.name);
        return r;
    }

    public List<String> names(Verdict v) {
        List<String> r = new ArrayList<>();
        for (Entry e : entries) if (e.verdict == v) r.add(e.name);
        return r;
    }

    public List<String> copy() { return names(Verdict.COPY); }

    /** what the copy reads in both halves: the copied set and the parts that must already be equal */
    public List<String> compared() {
        List<String> r = new ArrayList<>();
        for (Entry e : entries) if (e.verdict == Verdict.COPY || e.verdict == Verdict.KEEP_EQUAL) r.add(e.name);
        return r;
    }

    /**
     * The partitions Lumen's own update copies on this model: the copied set without what only the install writes
     * ({@link FwModel#installOnly}: the HB mboot, a project decision for updates) and without the post-install writes
     * (update_engine cannot write an eMMC boot area; the install has made both first stages the same).
     */
    public static List<String> updateNames(FwModel m) {
        List<String> r = of(m, null).copy();
        r.removeAll(m.installOnly);
        r.removeAll(postNames(m));
        return r;
    }

    /** The list a Lumen image of this model gives update_engine (ro.product.ab_ota_partitions). */
    public static String updateList(FwModel m) { return String.join(",", updateNames(m)); }

    /**
     * The updater's check of the running image's list: it must be exactly this model's update list, else update_engine
     * would copy too much (vbmeta, the HB mboot) or too little (an older boot chain next to a newer one). null = right.
     */
    public static String checkUpdateList(String code, String list) {
        FwModel m = FwModel.of(code);
        if (m == null) return "unknown model " + code;
        if (m.refusal != null) return m.refusal;
        String want = updateList(m);
        return want.equals(list.trim()) ? null : "the image lists " + list.trim() + ", the XGIMI set of " + m.family + " is " + want;
    }
}
