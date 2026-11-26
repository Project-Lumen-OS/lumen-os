// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Host tests of the firmware copy: the per-model sets against the stock dumps, the plan, every branch of a real run
 * (consent, the boot record, a source that changes, read and write errors, a corrupted write, power cuts with and
 * without the journal), the dry run and the guard that the running half is never opened for writing.
 * Run: test/run.sh. Exit 1 on any failure.
 */
public final class FwCopyTests {
    static int pass, fail;
    static final List<String> FAILED = new ArrayList<>();

    interface Body { void run() throws Exception; }

    static void t(String name, Body b) {
        try {
            b.run();
            pass++;
            System.out.println("ok   " + name);
        } catch (Throwable e) {
            fail++;
            FAILED.add(name);
            System.out.println("FAIL " + name + ": " + e);
        }
    }

    static void check(boolean c, String what) { if (!c) throw new AssertionError(what); }

    static void eq(Object a, Object b, String what) { if (!String.valueOf(a).equals(String.valueOf(b))) throw new AssertionError(what + ": got " + a + ", want " + b); }

    // ------------------------------------------------------------------------------------------------ fixtures

    static final Random R = new Random(15);

    static byte[] rnd(int n) {
        byte[] b = new byte[n];
        R.nextBytes(b);
        b[0] = 1;   // never a zero head
        return b;
    }

    static final int SZ = 3 * FwCopy.CHUNK + 2 * FwCopy.HEAD;   // more than one chunk, a tail chunk

    /** No real model keeps a part out of the copy since 2026-11-17: the HB row as it was, for the rule itself. */
    static final FwModel KEEPS = new FwModel("keeps", FwModel.list("X0003"), FwModel.HB.stockList, FwModel.HB.pairs, FwModel.list("mboot"), null,
            "test", FwModel.list(""));

    /** a stand-in post-install step: no real model's writer (its sha256 is in no row but the test's own) */
    static final byte[] WRITER = "a stand-in post-install step".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /**
     * A projector of the model: every pair of its dump, running half s, the listed parts different in half t. A first
     * stage of the model's data (PostWrite): the running vendor's file in both areas (one build in both halves) with a
     * byte the projector rewrites in each, the other half's area an older first stage when listed, the stand-in step.
     */
    static FakeBlocks unit(FwModel m, String s, String... differ) {
        FakeBlocks b = new FakeBlocks();
        String t = "_a".equals(s) ? "_b" : "_a";
        for (String n : m.pairs) {
            PostWrite w = null;
            for (PostWrite x : m.postWrites) if (x.name.equals(n)) w = x;
            if (w != null) {
                long end = 0;
                for (long[] r : w.deviceWritten) end = Math.max(end, r[1]);
                byte[] f = rnd((int) end + 3000);
                b.files.put(w.file, f);
                b.files.put(w.writer, WRITER);
                b.put(n + s, area(f, w, 1));
                b.put(n + t, Arrays.asList(differ).contains(n) ? area(rnd(f.length), w, 2) : area(f, w, 2));
                continue;
            }
            byte[] x = rnd(n.equals("satf") ? 2 * FwCopy.HEAD : SZ);
            b.put(n + s, x);
            b.put(n + t, Arrays.asList(differ).contains(n) ? rnd(x.length) : x.clone());
        }
        for (String n : new String[] {"misc", "super", "xgimicri", "persist", "uenv"}) b.put(n, rnd(FwCopy.HEAD));
        return b;
    }

    static final class Hal implements Halves {
        final List<String> calls = new ArrayList<>();
        final FakeBlocks b;
        boolean refuse;

        Hal(FakeBlocks b) { this.b = b; }

        @Override public boolean markUnbootable(int half) {
            calls.add("unbootable " + half);
            if (b != null) b.events.add("hal unbootable " + half);
            return !refuse;
        }
    }

    static final class Mem implements Journal {
        String state;
        int writes;

        @Override public String read() { return state; }

        @Override public void write(String s) { state = s; writes++; }
    }

    /** an eMMC boot area (SZ bytes) holding a first stage at 0, zero after it, one byte the projector wrote (mark) */
    static byte[] area(byte[] f, PostWrite w, int mark) {
        byte[] a = new byte[SZ];
        System.arraycopy(f, 0, a, 0, f.length);
        if (!w.deviceWritten.isEmpty()) a[(int) w.deviceWritten.get(0)[0] + 4] ^= mark;
        return a;
    }

    /**
     * The eMMC boot areas of the fake: the stock by-name links, EXT_CSD[179] on the running half's area, force_ro 1, the
     * vendor files of the FakeBlocks; a write open of an area fails while its force_ro is 1. Faults for the tests.
     */
    static final class Areas implements BootAreas {
        final FakeBlocks b;
        final Map<String, String> links = new HashMap<>();
        final Map<String, Boolean> ro = new HashMap<>();
        int pc;
        boolean noExtCsd, stuckRo, failRelock;

        Areas(FakeBlocks b, String s) {
            this.b = b;
            links.put("rom_emmc_boot_a", "mmcblk0boot0");
            links.put("rom_emmc_boot_b", "mmcblk0boot1");
            pc = "_a".equals(s) ? 0x08 : 0x10;
            b.locked = n -> links.containsKey(n) && ro.getOrDefault(links.get(n), true);
        }

        @Override public String node(String name) { return links.getOrDefault(name, name); }

        @Override public int partitionConfig(String disk) throws IOException {
            if (noExtCsd || !"mmcblk0".equals(disk)) throw new IOException("no ext_csd of " + disk);
            return pc;
        }

        @Override public boolean readOnly(String node) { return ro.getOrDefault(node, true); }

        @Override public void setReadOnly(String node, boolean v) throws IOException {
            b.events.add("ro " + node + " " + (v ? 1 : 0));
            if (v && failRelock) throw new IOException("sysfs write failed");
            if (v || !stuckRo) ro.put(node, v);
        }

        @Override public byte[] vendorFile(String path) throws IOException {
            byte[] f = b.files.get(path);
            if (f == null) throw new IOException("no " + path);
            return f.clone();
        }
    }

    static final class Run {
        final FakeBlocks b;
        final Hal hal;
        final Mem j;
        final List<String> lines = new ArrayList<>();
        final FwCopy c;
        final FwSet set;
        final String s, t;
        final Areas areas;

        Run(FwModel m, FakeBlocks b, String s, Mem j) {
            this.b = b; this.hal = new Hal(b); this.j = j; this.s = s; this.t = "_a".equals(s) ? "_b" : "_a";
            this.areas = new Areas(b, s);
            this.c = new FwCopy(b, hal, j, lines::add, areas);
            this.set = FwSet.of(m, b.parts.keySet());
        }

        FwPlan plan() throws IOException { return c.plan(set, s, t, Collections.emptyMap()); }

        boolean has(String part) { for (String l : lines) if (l.contains(part)) return true; return false; }
    }

    static Map<String, String> hashes(FakeBlocks b) {
        Map<String, String> h = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : b.parts.entrySet()) h.put(e.getKey(), FwPlan.hex(FwPlan.sha256().digest(e.getValue())));
        return h;
    }

    static void halfUntouched(Map<String, String> before, FakeBlocks b, String s) {
        Map<String, String> after = hashes(b);
        for (String k : before.keySet()) if (k.endsWith(s) || !k.matches(".*_[ab]")) eq(after.get(k), before.get(k), k + " untouched");
        for (String e : b.events) check(!(e.startsWith("open-w ") && (e.endsWith(s) || !e.matches(".*_[ab]"))), "opened for writing: " + e);
    }

    static void halvesEqual(FakeBlocks b, FwSet set) {
        for (String n : set.compared()) {
            PostWrite w = set.post(n);
            if (w != null) {
                byte[] f = b.files.get(w.file);
                check(w.differs(f, b.parts.get(n + "_a")) == null && w.differs(f, b.parts.get(n + "_b")) == null, n + ": the vendor's file in both areas");
                continue;
            }
            check(Arrays.equals(b.parts.get(n + "_a"), b.parts.get(n + "_b")), n + " the same in both halves");
        }
    }

    // ------------------------------------------------------------------------------------------------ tests

    public static void main(String[] args) {
        data();
        plans();
        leaves();
        syncs();
        runs();
        faults();
        powerCuts();
        FirstStageTests.all();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) {
            System.out.println("FAILED: " + FAILED);
            System.exit(1);
        }
    }

    static void data() {
        t("z9x: the set is the stock list without vbmeta and the logical partitions (the Z9X updater's list)", () -> {
            FwSet z = FwSet.of(FwModel.Z9X, null);
            eq(z.copy(), "[boot, bootdata, dtbo, mboot, optee, owl, satf, tvconfig, tvservice, vbmeta_system, xgimiconfig, rom_emmc_boot]", "copy");
            eq(FwSet.updateList(FwModel.Z9X), "boot,bootdata,dtbo,mboot,optee,owl,satf,tvconfig,tvservice,vbmeta_system,xgimiconfig", "list");
            eq(z.names(Verdict.LEFT_OUT), "[product, system, system_ext, vbmeta, vendor]", "left out");
        });
        t("hb: mboot is copied by the install (owner 2026-11-17), never by Lumen's own update; armfw is copied, no owl / satf", () -> {
            FwSet h = FwSet.of(FwModel.HB, null);
            eq(h.copy(), "[armfw, boot, bootdata, dtbo, mboot, optee, tvconfig, tvservice, vbmeta_system, xgimiconfig, rom_emmc_boot]", "copy");
            eq(h.names(Verdict.KEEP_EQUAL), "[]", "nothing kept out of the install copy");
            eq(h.compared(), h.copy(), "compared = copied");
            eq(FwModel.HB.installOnly, "[mboot]", "the install only");
            eq(FwSet.updateNames(FwModel.HB), "[armfw, boot, bootdata, dtbo, optee, tvconfig, tvservice, vbmeta_system, xgimiconfig]", "the update names");
            eq(FwSet.updateList(FwModel.HB), "armfw,boot,bootdata,dtbo,optee,tvconfig,tvservice,vbmeta_system,xgimiconfig", "the HB ota.conf list");
            for (FwModel m : new FwModel[] {FwModel.Z9X, FwModel.X50}) {
                check(m.installOnly.isEmpty() && m.keepEqual.isEmpty(), m.family + ": nothing kept out");
                List<String> c = FwSet.of(m, null).copy();
                c.remove("rom_emmc_boot");
                eq(FwSet.updateNames(m), c, m.family + ": the update list is the copied set without the first stage");
            }
            for (FwModel m : new FwModel[] {FwModel.Z9X, FwModel.X50, FwModel.HB}) {
                List<String> c = FwSet.of(m, null).copy();
                check(c.contains("mboot") && c.contains("boot") && !c.contains("vbmeta"), m.family + " copy " + c);
                eq(c.get(c.size() - 1), "rom_emmc_boot", m.family + ": the first stage is the last copy, after mboot and the rest");
                check(!FwSet.updateNames(m).contains("rom_emmc_boot"), m.family + ": never in Lumen's own update");
            }
        });
        t("the rows are the stock dumps: ro.product.ab_ota_partitions and the by-name pairs, word for word", () -> {
            String mt = "boot,bootdata,dtbo,mboot,optee,owl,product,satf,system,system_ext,tvconfig,tvservice,vbmeta,vbmeta_system,vendor,xgimiconfig";
            String mtPairs = "boot,bootdata,dtbo,mboot,optee,owl,rom_emmc_boot,satf,tvconfig,tvservice,vbmeta,vbmeta_system,xgimiconfig";
            for (FwModel m : new FwModel[] {FwModel.Z9X, FwModel.X50, FwModel.RS30}) {
                eq(String.join(",", m.stockList), mt, m.family + " stock list (G0082 v61562, G0111 1.2.64, G0155)");
                eq(String.join(",", m.pairs), mtPairs, m.family + " pairs");
            }
            eq(String.join(",", FwModel.HB.stockList), "armfw,boot,bootdata,dtbo,mboot,optee,product,system,system_ext,tvconfig,tvservice,vbmeta,"
                    + "vbmeta_system,vendor,xgimiconfig", "HB stock list (G0130 6.16.24, G0148 6.17.12)");
            eq(String.join(",", FwModel.HB.pairs), "armfw,boot,bootdata,dtbo,mboot,optee,rom_emmc_boot,tvconfig,tvservice,vbmeta,vbmeta_system,xgimiconfig", "HB pairs");
            check(FwModel.PLAY6.stockList.isEmpty() && FwModel.PLAY6.pairs.isEmpty(), "the Play 6 has no A/B");
        });
        t("x50 family: the Z9X set (one XGIMI product image), all four model codes", () -> {
            eq(FwSet.updateList(FwModel.X50), FwSet.updateList(FwModel.Z9X), "list");
            for (String c : new String[] {"G0111", "G0100", "H0061", "H0062", "H0063"}) eq(FwModel.of(c).family, "x50", c);
        });
        t("every model code is in one row only; the HB has three; unknown codes have none", () -> {
            List<String> seen = new ArrayList<>();
            for (FwModel m : FwModel.ALL) for (String c : m.codes) { check(!seen.contains(c), "twice: " + c); seen.add(c); }
            eq(FwModel.of("G0118").family, "hb", "G0118");
            eq(FwModel.of("G0148").family, "hb", "G0148");
            eq(FwModel.of("G0082").family, "z9x", "G0082");
            check(FwModel.of("G9999") == null && FwModel.of("") == null, "unknown");
        });
        t("rs30 and play 6 never copy (reason given); the Play 6 has no set at all", () -> {
            check(FwModel.RS30.refusal != null && FwModel.PLAY6.refusal != null, "refusals");
            check(FwModel.Z9X.refusal == null && FwModel.HB.refusal == null && FwModel.X50.refusal == null, "the others copy");
            check(FwSet.of(FwModel.PLAY6, null).copy().isEmpty(), "play 6 empty");
            check(FwSet.checkUpdateList("G0155", "boot") != null && FwSet.checkUpdateList("G0058", "") != null, "no update list");
        });
        t("never copied whatever the data says: calibration, factory data, eMMC boot areas, vbmeta, logical partitions", () -> {
            List<String> bad = Arrays.asList("xgimicri", "xgimisps", "xgimidatabase", "persist", "project_id", "tvcertificate",
                    "tvcustomer", "frp", "rom_emmc_boot", "uenv", "vbmeta", "misc", "metadata", "userdata", "system", "vendor", "super");
            FwModel m = new FwModel("evil", FwModel.list("X0001"), bad, bad, FwModel.list(""), null, "test", FwModel.list(""));
            FwSet s = FwSet.of(m, null);
            check(s.copy().isEmpty(), "copied: " + s.copy());
            for (FwSet.Entry e : s.entries) eq(e.verdict, Verdict.LEFT_OUT, e.name);
            check(FwSet.POST_ONLY.contains("rom_emmc_boot") && !FwSet.NEVER.contains("rom_emmc_boot") && FwSet.NEVER.contains("vbmeta"), "never list");
            for (String n : new String[] {"mmcblk0", "mmcblk0boot0", "mmcblk0boot1"}) check(FwSet.NEVER.contains(n), n + " never");
        });
        t("one copy for both halves on the model (not a pair in its dump): left out", () -> {
            FwModel m = new FwModel("one", FwModel.list("X0002"), FwModel.list("boot,tvconfig"), FwModel.list("boot"), FwModel.list(""), null, "test",
                    FwModel.list(""));
            FwSet s = FwSet.of(m, null);
            eq(s.copy(), "[boot]", "copy");
            eq(s.entries.get(1).verdict, Verdict.LEFT_OUT, "tvconfig");
        });
        t("a pair of the model missing on this unit: MISSING, and the plan refuses", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot");
            b.parts.remove("owl_b");
            FwSet s = FwSet.of(FwModel.X50, b.parts.keySet());
            eq(s.names(Verdict.MISSING), "[owl]", "missing");
            FwPlan p = new Run(FwModel.X50, b, "_a", new Mem()).plan();
            check(p.refusal != null && p.refusal.contains("owl"), "refusal " + p.refusal);
            check(p.toCopy().size() == 1 && p.part("owl") == null, "owl not read");
        });
        t("the updater's list check: exactly the model's set, nothing more, nothing less", () -> {
            eq(FwSet.checkUpdateList("G0082", "boot,bootdata,dtbo,mboot,optee,owl,satf,tvconfig,tvservice,vbmeta_system,xgimiconfig"), null, "z9x");
            eq(FwSet.checkUpdateList("G0130", " armfw,boot,bootdata,dtbo,optee,tvconfig,tvservice,vbmeta_system,xgimiconfig "), null, "hb");
            eq(FwSet.checkUpdateList("G0100", FwSet.updateList(FwModel.X50)), null, "x50 max");
            check(FwSet.checkUpdateList("G0111", "optee,owl,satf,xgimiconfig") != null, "the old X50 list (no boot chain) refused");
            check(FwSet.checkUpdateList("G0130", "armfw,boot,bootdata,dtbo,mboot,optee,tvconfig,tvservice,vbmeta_system,xgimiconfig") != null, "hb mboot refused");
            check(FwSet.checkUpdateList("G0082", FwSet.updateList(FwModel.Z9X) + ",vbmeta") != null, "vbmeta refused");
            check(FwSet.checkUpdateList("G9999", "boot") != null, "unknown model");
        });
    }

    static void plans() {
        t("plan: equal halves = nothing to copy; a run writes nothing, asks no consent, calls no HAL", () -> {
            Run r = new Run(FwModel.X50, unit(FwModel.X50, "_a"), "_a", new Mem());
            FwPlan p = r.plan();
            eq(p.refusal, null, "refusal");
            check(p.toCopy().isEmpty(), "copy " + p.copyNames());
            FwResult res = r.c.run(p, Mode.REAL, false);
            check(res.verified && !res.written && res.copied.isEmpty(), "result " + res);
            check(r.hal.calls.isEmpty() && r.b.events.isEmpty() && r.j.writes == 0, "touched: " + r.hal.calls + r.b.events);
        });
        t("plan: differing parts in the set's order, a report line per part, the id names the bytes", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "tvservice", "boot", "mboot");
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwPlan p = r.plan();
            eq(p.copyNames(), "[boot, mboot, tvservice]", "order");
            eq(p.report().size(), FwSet.of(FwModel.X50, null).compared().size(), "report lines");
            check(p.report().get(0).startsWith("FW\tboot\tcopy\t"), p.report().get(0));
            String id = p.id();
            eq(r.plan().id(), id, "same bytes, same id");
            b.parts.get("dtbo_b")[100] ^= 1;
            check(!r.plan().id().equals(id), "a changed byte changes the id");
        });
        t("plan: known hashes of the check are used, not read again", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot");
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            Map<String, String> known = new HashMap<>(hashes(b));
            r.c.plan(r.set, "_a", "_b", known);
            eq(b.reads.get("boot_a"), 1, "boot_a: only its first 4 KiB read");
            eq(b.reads.get("boot_b"), 1, "boot_b: only its first 4 KiB read");
            new Run(FwModel.X50, b, "_a", new Mem()).plan();
            eq(b.reads.get("boot_a"), 3, "without them: hashed and the head read");
        });
        t("plan refuses: sizes differ, odd size, the running half starts with zeros, s = t", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot");
            b.put("dtbo_b", rnd(SZ + FwCopy.HEAD));
            check(new Run(FwModel.X50, b, "_a", new Mem()).plan().refusal.contains("differ in size"), "size");
            FakeBlocks o = unit(FwModel.X50, "_a");
            o.put("satf_a", rnd(FwCopy.HEAD + 7)).put("satf_b", rnd(FwCopy.HEAD + 7));
            check(new Run(FwModel.X50, o, "_a", new Mem()).plan().refusal.contains("4 KiB"), "odd");
            FakeBlocks z = unit(FwModel.X50, "_a", "boot");
            Arrays.fill(z.parts.get("boot_a"), 0, FwCopy.HEAD, (byte) 0);
            check(new Run(FwModel.X50, z, "_a", new Mem()).plan().refusal.contains("zeros"), "zero head");
            try {
                new Run(FwModel.X50, z, "_a", new Mem()).c.plan(FwSet.of(FwModel.X50, null), "_a", "_a", Collections.emptyMap());
                check(false, "s = t accepted");
            } catch (IllegalArgumentException expected) { }
        });
        t("hb: a differing mboot is copied from the running half like optee (owner 2026-11-17), read back, the running half untouched", () -> {
            for (String s : new String[] {"_a", "_b"}) {
                FakeBlocks b = unit(FwModel.HB, s, "optee", "mboot", "vbmeta");   // as after a stock update (one first stage: FirstStageTests)
                Map<String, String> before = hashes(b);
                Run r = new Run(FwModel.HB, b, s, new Mem());
                FwPlan p = r.plan();
                eq(p.refusal, null, "no refusal");
                eq(p.copyNames(), "[mboot, optee]", "copy");
                check(!p.part("mboot").keep, "mboot is no kept part on the HB");
                FwResult res = r.c.run(p, Mode.REAL, true);
                check(res.verified && res.written, "result " + res);
                eq(res.copied, "[mboot, optee]", "copied");
                halfUntouched(before, b, s);
                halvesEqual(b, r.set);
                eq(hashes(b).get("mboot" + r.t), before.get("mboot" + s), "mboot of the other half is the running one");
                for (String n : new String[] {"vbmeta", "rom_emmc_boot"}) {
                    eq(hashes(b).get(n + r.t), before.get(n + r.t), n + r.t + " untouched");
                    final String w = n + r.t;
                    check(b.events.stream().noneMatch(e -> e.endsWith(" " + w) || e.contains(" " + w + " ")), w + " touched: " + b.events);
                }
            }
        });
        t("a part a model keeps out of the copy (no model now, the HB mboot until 2026-11-17): compared, a difference refuses, never written", () -> {
            FakeBlocks b = unit(KEEPS, "_a", "optee");
            Run r = new Run(KEEPS, b, "_a", new Mem());
            eq(r.set.names(Verdict.KEEP_EQUAL), "[mboot]", "kept");
            check(r.set.compared().contains("mboot") && !r.set.copy().contains("mboot"), "compared, not copied");
            FwPlan ok = r.plan();
            eq(ok.refusal, null, "optee only");
            eq(ok.copyNames(), "[optee]", "copy");
            b.parts.get("mboot_b")[5] ^= 1;
            FwPlan p = r.plan();
            check(p.refusal != null && p.refusal.startsWith("mboot differs"), "refusal " + p.refusal);
            eq(p.copyNames(), "[optee]", "mboot is never planned");
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(!res.verified && !res.written && "plan".equals(res.step), "result " + res);
            check(b.events.isEmpty() && r.hal.calls.isEmpty(), "touched");
        });
        t("rs30: the plan carries the model's refusal and a run writes nothing", () -> {
            Run r = new Run(FwModel.RS30, unit(FwModel.RS30, "_b", "boot"), "_b", new Mem());
            FwPlan p = r.plan();
            eq(p.refusal, FwModel.RS30.refusal, "refusal");
            check(!r.c.run(p, Mode.REAL, true).written && r.b.events.isEmpty(), "written");
        });
    }

    static void leaves() {
        t("leave: a differing part the caller keeps as the other half's own is never written, never verified equal", () -> {
            FakeBlocks b = unit(FwModel.Z9X, "_a", "boot", "optee");
            Map<String, String> before = hashes(b);
            Run r = new Run(FwModel.Z9X, b, "_a", new Mem());
            FwPlan p = r.c.plan(r.set, "_a", "_b", Collections.emptyMap(), Arrays.asList("boot"));
            eq(p.refusal, null, "refusal");
            eq(p.copyNames(), "[optee]", "only optee copied");
            check(p.part("boot").leave && !p.part("optee").leave, "leave flags");
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(res.verified, "result " + res);
            eq(hashes(b).get("boot_b"), before.get("boot_b"), "boot_b stays its own");
            check(b.events.stream().noneMatch(e -> e.startsWith("open-w boot_b") || e.startsWith("write boot_b")), "boot_b opened " + b.events);
            eq(r.c.verifyEqual(p), null, "verifyEqual skips the kept part");
            FwPlan all = r.plan();
            check(!all.id().equals(p.id()), "the id names what is kept");
        });
        t("leave never covers a part a model keeps out of the copy or a part a cut copy left unfinished", () -> {
            FakeBlocks b = unit(KEEPS, "_a", "mboot");
            Run r = new Run(KEEPS, b, "_a", new Mem());
            FwPlan p = r.c.plan(r.set, "_a", "_b", Collections.emptyMap(), Arrays.asList("mboot"));
            check(p.refusal != null && p.refusal.startsWith("mboot differs"), "mboot " + p.refusal);
            check(!p.part("mboot").leave, "no leave on a kept part");
            FakeBlocks z = unit(FwModel.Z9X, "_a", "boot");
            Arrays.fill(z.parts.get("boot_b"), 0, FwCopy.HEAD, (byte) 0);
            Run rz = new Run(FwModel.Z9X, z, "_a", new Mem());
            FwPlan pz = rz.c.plan(rz.set, "_a", "_b", Collections.emptyMap(), Arrays.asList("boot"));
            check(pz.refusal != null && pz.refusal.startsWith("boot_b was left unfinished"), "fenced " + pz.refusal);
            eq(pz.fenced(), "[boot]", "fenced");
            FwPlan copy = rz.plan();
            eq(copy.refusal, null, "without leave a fenced part is copied");
            eq(copy.copyNames(), "[boot]", "copied");
        });
        t("the plan id: the same bytes give the same id, a changed byte or another running half another one", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot");
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            String id = r.plan().id();
            eq(r.plan().id(), id, "stable");
            check(id.matches("[0-9a-f]{64}"), "sha256 hex " + id);
            b.parts.get("owl_b")[9] ^= 1;
            String other = r.plan().id();
            check(!other.equals(id), "a byte of the other half");
            b.parts.get("owl_b")[9] ^= 1;
            eq(r.plan().id(), id, "back");
            check(!r.c.plan(r.set, "_b", "_a", Collections.emptyMap()).id().equals(id), "the direction");
            FwPlan refused = new FwPlan("_a", "_b", r.plan().parts, "test");
            check(!refused.id().equals(id), "the refusal");
        });
        t("the file journal: replaced as a whole, read back, no temporary file left", () -> {
            java.io.File dir = java.nio.file.Files.createTempDirectory("fwj").toFile();
            java.io.File f = new java.io.File(dir, "fwcopy.state");
            Journal j = new Journal.OnFile(f);
            eq(j.read(), null, "none yet");
            j.write("plan=x\nstate=started\n");
            j.write("plan=x\nstate=verified\n");
            eq(j.read(), "plan=x\nstate=verified\n", "the last state");
            check(!new java.io.File(dir, "fwcopy.state.new").exists(), "no temporary file");
            check(f.delete() && dir.delete(), "cleanup");
        });
    }

    static void syncs() {
        t("every write is synced in order: each fence before any body, each body before its head, each head before the read back", () -> {
            FakeBlocks b = unit(FwModel.Z9X, "_a", "boot", "optee", "satf");
            Run r = new Run(FwModel.Z9X, b, "_a", new Mem());
            check(r.c.run(r.plan(), Mode.REAL, true).verified, "verified");
            int firstBody = Integer.MAX_VALUE;
            for (int i = 0; i < b.events.size(); i++) if (b.events.get(i).matches("write \\S+ 4096\\+.*")) { firstBody = i; break; }
            for (String n : new String[] {"boot_b", "optee_b", "satf_b"}) {
                List<String> e = b.events;
                int fence = e.indexOf("write " + n + " 0+4096");
                check(fence >= 0 && e.indexOf("sync " + n) == fence + 1 && fence + 1 < firstBody, n + " fence synced before any body: " + e);
                int head = e.lastIndexOf("write " + n + " 0+4096"), lastBody = -1;
                for (int i = 0; i < e.size(); i++) if (e.get(i).startsWith("write " + n + " ") && i != fence && i != head) lastBody = i;
                check(head > fence && lastBody > fence && lastBody < head, n + " body before head: " + e);
                boolean synced = false;
                for (int i = lastBody + 1; i < head; i++) synced |= e.get(i).equals("sync " + n);
                check(synced, n + " body synced before its head: " + e);
                check(head + 1 < e.size() && e.get(head + 1).equals("sync " + n), n + " head synced: " + e);
            }
        });
        t("a verified journal is no unfinished copy: the next runs are not resumed", () -> {
            FakeBlocks b = unit(FwModel.Z9X, "_a", "boot");
            Mem j = new Mem();
            Run r = new Run(FwModel.Z9X, b, "_a", j);
            check(r.c.run(r.plan(), Mode.REAL, true).verified, "first");
            check(j.state.contains("state=verified"), "journal " + j.state);
            Run again = new Run(FwModel.Z9X, b, "_a", j);
            FwResult res = again.c.run(again.plan(), Mode.REAL, true);
            check(res.verified && !res.resumed && !again.has("did not finish"), "same halves " + res + " " + again.lines);
            b.parts.get("owl_b")[7] ^= 1;
            Run third = new Run(FwModel.Z9X, b, "_a", j);
            res = third.c.run(third.plan(), Mode.REAL, true);
            check(res.verified && !res.resumed && !third.has("did not finish"), "a new difference " + res);
            eq(res.copied, "[owl]", "copied");
        });
        t("the other half changes size after the plan: stopped, the reason says so, the half stays unbootable", () -> {
            FakeBlocks b = unit(FwModel.Z9X, "_a", "boot");
            Run r = new Run(FwModel.Z9X, b, "_a", new Mem());
            FwPlan p = r.plan();
            b.parts.put("boot_b", rnd(SZ + FwCopy.HEAD));
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(!res.verified && "io".equals(res.step) && res.reason.contains("size changed since the plan"), "result " + res);
            eq(r.hal.calls, "[unbootable 1]", "unbootable");
        });
        t("the running half changes size after the plan: stopped before any write", () -> {
            FakeBlocks b = unit(FwModel.Z9X, "_a", "boot");
            Run r = new Run(FwModel.Z9X, b, "_a", new Mem());
            FwPlan p = r.plan();
            b.parts.put("boot_a", Arrays.copyOf(b.parts.get("boot_a"), SZ + FwCopy.HEAD));
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(!res.verified && !res.written, "result " + res);
            check(b.events.stream().noneMatch(e -> e.startsWith("write") || e.startsWith("open-w")), "written " + b.events);
        });
    }

    static void runs() {
        for (String s : new String[] {"_a", "_b"}) {
            t("real copy, running half " + s + ": unbootable first, both reads equal, fence, copy, read back; running half untouched", () -> {
                FakeBlocks b = unit(FwModel.X50, s, "boot", "dtbo", "mboot", "tvconfig", "tvservice", "bootdata", "optee", "owl", "xgimiconfig");
                Map<String, String> before = hashes(b);
                Run r = new Run(FwModel.X50, b, s, new Mem());
                FwPlan p = r.plan();
                FwResult res = r.c.run(p, Mode.REAL, true);
                check(res.verified && res.written && res.real, "result " + res);
                eq(res.copied, "[boot, bootdata, dtbo, mboot, optee, owl, tvconfig, tvservice, xgimiconfig]", "copied");
                halvesEqual(b, r.set);
                halfUntouched(before, b, s);
                eq(b.events.get(0), "hal unbootable " + ("_a".equals(s) ? 1 : 0), "the boot record first");
                eq(r.hal.calls.size(), 1, "one HAL call, never a 'bootable' one");
                // the fence: the first 4 KiB of every copied partition is zeroed before any body is written
                int lastFence = -1, firstBody = Integer.MAX_VALUE;
                for (int i = 0; i < b.events.size(); i++) {
                    String e = b.events.get(i);
                    if (e.matches("write \\S+ 0\\+4096") && i < firstBody) lastFence = i;
                    if (e.matches("write \\S+ 4096\\+.*")) firstBody = Math.min(firstBody, i);
                }
                check(lastFence >= 0 && lastFence < firstBody, "fence before bodies");
                eq(r.j.state.contains("state=verified") && r.j.state.contains("done=boot,bootdata"), true, "journal " + r.j.state);
                eq(r.c.verifyEqual(p), null, "verifyEqual");
                // every source read: plan + second read + the copy itself
                check(b.reads.get("boot" + s) >= 3, "boot" + s + " reads " + b.reads.get("boot" + s));
            });
        }
        t("no consent: nothing written, no HAL call, the journal untouched", () -> {
            Run r = new Run(FwModel.Z9X, unit(FwModel.Z9X, "_a", "boot"), "_a", new Mem());
            FwResult res = r.c.run(r.plan(), Mode.REAL, false);
            check(!res.verified && "consent".equals(res.step) && !res.written, "result " + res);
            check(r.b.events.isEmpty() && r.hal.calls.isEmpty() && r.j.writes == 0, "touched");
        });
        t("the boot HAL refuses: nothing written, the journal says failed", () -> {
            Run r = new Run(FwModel.Z9X, unit(FwModel.Z9X, "_a", "boot"), "_a", new Mem());
            r.hal.refuse = true;
            FwResult res = r.c.run(r.plan(), Mode.REAL, true);
            check(!res.verified && "unbootable".equals(res.step) && !res.written, "result " + res);
            check(r.b.events.stream().noneMatch(e -> e.startsWith("write")), "written");
            check(r.j.state.contains("state=failed"), "journal " + r.j.state);
        });
        t("dry run: every read of a real run, WOULD lines, no write, no HAL, no journal", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot", "mboot");
            Map<String, String> before = hashes(b);
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwResult res = r.c.run(r.plan(), Mode.DRY, true);
            check(res.verified && !res.real && !res.written, "result " + res);
            eq(res.copied, "[boot, mboot]", "would copy");
            eq(hashes(b), before, "bytes");
            check(b.events.isEmpty() && r.hal.calls.isEmpty() && r.j.writes == 0, "touched " + b.events);
            check(r.has("WOULD: mark half b unbootable") && r.has("WOULD: zero the first 4 KiB of boot_b") && r.has("WOULD: copy boot_a -> boot_b"), "lines " + r.lines);
            check(b.reads.get("boot_a") >= 2, "second read in the dry run");
        });
        t("dry run without consent: refused like the real one", () -> {
            Run r = new Run(FwModel.X50, unit(FwModel.X50, "_a", "boot"), "_a", new Mem());
            FwResult res = r.c.run(r.plan(), Mode.DRY, false);
            check(!res.verified && "consent".equals(res.step), "result " + res);
        });
        t("verifyEqual: a later change in either half is found", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot");
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwPlan p = r.plan();
            check(r.c.run(p, Mode.REAL, true).verified, "copy");
            b.parts.get("tvconfig_b")[9000] ^= 1;
            check(r.c.verifyEqual(p).contains("tvconfig"), "other half");
            b.parts.get("tvconfig_b")[9000] ^= 1;
            b.parts.get("owl_a")[9000] ^= 1;
            check(r.c.verifyEqual(p).contains("owl_a"), "running half");
            b.readError = "dtbo_b";
            check(r.c.verifyEqual(p).contains("cannot read"), "read error");
        });
    }

    static void faults() {
        t("the source changes between the plan and the second read: nothing written", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot", "mboot");
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwPlan p = r.plan();
            b.parts.get("mboot_a")[77] ^= 1;
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(!res.verified && "source".equals(res.step) && !res.written, "result " + res);
            check(b.events.stream().noneMatch(e -> e.startsWith("write")), "written");
        });
        t("the source changes during the copy: stopped, the other half stays unbootable, never marked bootable", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot");
            Map<String, String> before = hashes(b);
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwPlan p = r.plan();
            // the first read at 4 KiB is the copy's body (hashing reads whole chunks from 0)
            b.onRead.put("boot_a", (off, x) -> { if (off == FwCopy.HEAD) x[SZ - 5] ^= 1; });
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(!res.verified && "source".equals(res.step) && res.written, "result " + res);
            eq(r.hal.calls, "[unbootable 1]", "HAL");
            check(r.j.state.contains("state=failed"), "journal");
            before.remove("boot_a");
            halfUntouched(before, b, "_a");
        });
        t("a write lands corrupted: the read back stops it", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "tvservice");
            b.corrupt = "tvservice_b";
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwResult res = r.c.run(r.plan(), Mode.REAL, true);
            check(!res.verified && "verify".equals(res.step) && res.written, "result " + res);
        });
        t("read error on the source: stopped before any write", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "owl");
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwPlan p = r.plan();
            b.readError = "owl_a";
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(!res.verified && "io".equals(res.step) && !res.written, "result " + res);
            check(b.events.stream().noneMatch(e -> e.startsWith("write")), "written");
        });
        t("write error on the other half: stopped, written, reason kept in the journal", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "owl");
            b.writeError = "owl_b";
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwResult res = r.c.run(r.plan(), Mode.REAL, true);
            check(!res.verified && "io".equals(res.step) && res.written, "result " + res);
            check(r.j.state.contains("reason=io: write error on owl_b"), "journal " + r.j.state);
        });
        t("a stale plan (the other half changed after the plan): the copy still ends equal, nothing of the running half", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "boot");
            Run r = new Run(FwModel.X50, b, "_a", new Mem());
            FwPlan p = r.plan();
            b.parts.get("boot_b")[3] ^= 1;
            check(r.c.run(p, Mode.REAL, true).verified, "copied");
            halvesEqual(b, r.set);
        });
    }

    static void powerCuts() {
        long[] cuts = {0, 100, FwCopy.HEAD, FwCopy.HEAD * 2 + 5, (long) SZ, (long) SZ * 3 + 12345, (long) SZ * 5};
        for (long cut : cuts) for (boolean keepJournal : new boolean[] {true, false}) {
            t("power cut after " + cut + " written bytes, journal " + (keepJournal ? "kept" : "lost") + ": the other half reads as unfinished, a new run ends equal", () -> {
                FakeBlocks b = unit(FwModel.X50, "_a", "boot", "dtbo", "mboot", "tvconfig", "xgimiconfig");
                Map<String, String> before = hashes(b);
                Mem j = new Mem();
                Run r = new Run(FwModel.X50, b, "_a", j);
                FwPlan p = r.plan();
                b.cutAfter = cut;
                try {
                    r.c.run(p, Mode.REAL, true);
                    check(false, "no cut");
                } catch (FakeBlocks.PowerCut expected) { }
                b.cutAfter = -1;
                halfUntouched(before, b, "_a");
                Run again = new Run(FwModel.X50, b, "_a", keepJournal ? j : new Mem());
                FwPlan q = again.plan();
                check(!q.toCopy().isEmpty(), "still something to copy");
                if (cut >= FwCopy.HEAD) check(!q.fenced().isEmpty(), "an unfinished part has a zero head: " + q.report());
                FwResult res = again.c.run(q, Mode.REAL, true);
                check(res.verified, "result " + res);
                eq(res.resumed, keepJournal, "resumed");
                halvesEqual(b, again.set);
                eq(again.c.verifyEqual(q), null, "verify");
                check(again.plan().toCopy().isEmpty() && again.plan().fenced().isEmpty(), "nothing left");
            });
        }
        t("power cut in the last head write: the next run finds the parts equal or unfinished, never a stale fence", () -> {
            FakeBlocks b = unit(FwModel.X50, "_a", "satf");
            Mem j = new Mem();
            Run r = new Run(FwModel.X50, b, "_a", j);
            FwPlan p = r.plan();
            b.cutAfter = 3L * FwCopy.HEAD - 1;   // the fence (4 KiB), the body (4 KiB), then a cut one byte before the head is whole
            try {
                r.c.run(p, Mode.REAL, true);
            } catch (FakeBlocks.PowerCut expected) { }
            b.cutAfter = -1;
            FwPlan q = new Run(FwModel.X50, b, "_a", j).plan();
            eq(q.copyNames(), "[satf]", "still to copy");
        });
    }
}
