// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.lumen.fwcopy.FwCopyTests.Mem;
import static org.lumen.fwcopy.FwCopyTests.Run;
import static org.lumen.fwcopy.FwCopyTests.WRITER;
import static org.lumen.fwcopy.FwCopyTests.check;
import static org.lumen.fwcopy.FwCopyTests.eq;
import static org.lumen.fwcopy.FwCopyTests.halfUntouched;
import static org.lumen.fwcopy.FwCopyTests.halvesEqual;
import static org.lumen.fwcopy.FwCopyTests.hashes;
import static org.lumen.fwcopy.FwCopyTests.rnd;
import static org.lumen.fwcopy.FwCopyTests.t;
import static org.lumen.fwcopy.FwCopyTests.unit;

/**
 * The first boot stage (2026-11-18, {@link PostWrite}): written by the install the way XGIMI's post-install step
 * writes it, only where every rule holds. A test model carries the HB row with a first stage whose writer is the
 * stand-in step (no real writer's bytes are in the tree); the real rows are pinned to the stock evidence.
 */
final class FirstStageTests {
    private FirstStageTests() {}

    static String sha(byte[] b) { return FwPlan.hex(FwPlan.sha256().digest(b)); }

    /** the HB first stage's rules with the stand-in writer and short ranges (a file of 3 x 4 KiB + 300 bytes) */
    static final PostWrite STAGE_W = new PostWrite("rom_emmc_boot", "/vendor/bin/rom_emmc_boot.bin", "/vendor/bin/postinst_mtk",
            FwModel.list(sha(WRITER)), "mmcblk0boot0", "mmcblk0boot1", new long[] {0x1100, 0x1200}, new long[] {0x2400, 0x2500});
    static final FwModel STAGE = new FwModel("stage", FwModel.list("X0004"), FwModel.HB.stockList, FwModel.HB.pairs, FwModel.list(""), null,
            "test", FwModel.list("mboot"), STAGE_W);

    /** an HB-like unit after XGIMI's update: mboot and optee differ, the other half's area holds an older first stage */
    static FakeBlocks updated(String s) { return unit(STAGE, s, "mboot", "optee", "rom_emmc_boot"); }

    static byte[] file(FakeBlocks b) { return b.files.get(STAGE_W.file); }

    static void all() {
        data();
        copies();
        refusals();
        faults();
        cuts();
    }

    static void data() {
        t("first stage data: the HB, Z9X and X50 rows list it with the writers read (d57af229, 303152c9), the RS30 and Play 6 none", () -> {
            eq(FwModel.HB.postWrites, Collections.singletonList(PostWrite.HB_FIRST_STAGE), "HB");
            eq(FwModel.Z9X.postWrites, Collections.singletonList(PostWrite.MT5877_FIRST_STAGE), "Z9X");
            eq(FwModel.X50.postWrites, Collections.singletonList(PostWrite.MT5877_FIRST_STAGE), "X50 family");
            check(FwModel.RS30.postWrites.isEmpty() && FwModel.PLAY6.postWrites.isEmpty(), "RS30 (no install) and Play 6 (no A/B): none");
            eq(PostWrite.HB_FIRST_STAGE.writerSha, "[d57af229c8a351b9f1613a5de5d6fdf64e266e75f56564908c859a74d67ed8ea]", "HB writer");
            eq(PostWrite.MT5877_FIRST_STAGE.writerSha, "[303152c9c20eedfbe65a6b5f93c2cc48ac382c0fd0aa75632e4efec6cdc6181c]", "mt5877 writer");
            for (PostWrite w : new PostWrite[] {PostWrite.HB_FIRST_STAGE, PostWrite.MT5877_FIRST_STAGE}) {
                eq(w.name + " " + w.file + " " + w.writer, "rom_emmc_boot /vendor/bin/rom_emmc_boot.bin /vendor/bin/postinst_mtk", "names");
                eq(w.node("_a") + " " + w.node("_b") + " " + w.enable("_a") + " " + w.enable("_b") + " " + w.disk(), "mmcblk0boot0 mmcblk0boot1 1 2 mmcblk0", "areas");
            }
            eq(ranges(PostWrite.HB_FIRST_STAGE), "46000-46300 6cc00-6d000", "HB ranges (the DRAM table and the blob of the dumps)");
            eq(ranges(PostWrite.MT5877_FIRST_STAGE), "66c00-67000 9a400-9a800", "mt5877 ranges");
            check(PostWrite.HB_FIRST_STAGE.deviceWritten(0x46004) && PostWrite.HB_FIRST_STAGE.deviceWritten(0x6cfff) && !PostWrite.HB_FIRST_STAGE.deviceWritten(0x46300)
                    && !PostWrite.HB_FIRST_STAGE.deviceWritten(0x45fff), "range edges");
        });
        t("EXT_CSD from the kernel's debugfs text: [179] read, BOOT_PARTITION_ENABLE in bits 3..5, anything else refused", () -> {
            byte[] e = new byte[512];
            e[179] = 0x10;
            e[192] = 8;
            eq(BootAreas.parseExtCsd(FwPlan.hex(e) + "\n"), 0x10, "the live HB value (half b)");
            eq(BootAreas.bootEnable(0x10) + " " + BootAreas.bootEnable(0x08) + " " + BootAreas.bootEnable(0x48) + " " + BootAreas.bootEnable(0x38)
                    + " " + BootAreas.bootEnable(0), "2 1 1 7 0", "enable bits");
            for (String bad : new String[] {"", "00", FwPlan.hex(e).substring(2), FwPlan.hex(e).replace('0', 'x'), null})
                try {
                    BootAreas.parseExtCsd(bad);
                    check(false, "accepted " + (bad == null ? null : bad.length()));
                } catch (IOException expected) { }
        });
    }

    static String ranges(PostWrite w) {
        StringBuilder b = new StringBuilder();
        for (long[] r : w.deviceWritten) b.append(b.length() == 0 ? "" : " ").append(Long.toHexString(r[0])).append('-').append(Long.toHexString(r[1]));
        return b.toString();
    }

    static void copies() {
        for (String s : new String[] {"_a", "_b"})
            t("first stage, running half " + s.substring(1) + ": the running vendor's file written at 0 of the other half's area after every other part, force_ro 0 "
                    + "only around it, read back; the running area and the bytes past the file untouched; verifyEqual, a second run copies nothing", () -> {
                FakeBlocks b = updated(s);
                Map<String, String> before = hashes(b);
                Run r = new Run(STAGE, b, s, new Mem());
                byte[] tail = Arrays.copyOfRange(b.parts.get("rom_emmc_boot" + r.t), file(b).length, FwCopyTests.SZ);
                tail[tail.length - 1] = 7;
                System.arraycopy(tail, 0, b.parts.get("rom_emmc_boot" + r.t), file(b).length, tail.length);
                FwPlan p = r.plan();
                eq(p.refusal, null, "no refusal");
                eq(p.copyNames(), "[mboot, optee, rom_emmc_boot]", "the first stage is the last copy");
                check(p.report().get(p.report().size() - 1).startsWith("FW\trom_emmc_boot\tcopy\t" + file(b).length + "\t/vendor/bin/rom_emmc_boot.bin="), p.report().toString());
                FwResult res = r.c.run(p, Mode.REAL, true);
                check(res.verified && res.written, "result " + res);
                byte[] a = b.parts.get("rom_emmc_boot" + r.t);
                check(Arrays.equals(Arrays.copyOf(a, file(b).length), file(b)), "the area holds the file byte for byte");
                eq(a[a.length - 1], (byte) 7, "past the file: never written");
                halfUntouched(before, b, s);
                eq(hashes(b).get("rom_emmc_boot" + s), before.get("rom_emmc_boot" + s), "the running area untouched");
                String node = STAGE_W.node(r.t), other = STAGE_W.node(s);
                List<String> ev = b.events;
                int off = ev.indexOf("ro " + node + " 0"), open = ev.indexOf("open-w rom_emmc_boot" + r.t), on = ev.indexOf("ro " + node + " 1");
                int lastOther = -1;
                for (int i = 0; i < ev.size(); i++) if (ev.get(i).startsWith("open-w ") && !ev.get(i).contains("rom_emmc_boot")) lastOther = i;
                check(lastOther >= 0 && lastOther < off && off < open && open < on, "order " + lastOther + " " + off + " " + open + " " + on + ": " + ev);
                eq(ev.stream().filter(e -> e.startsWith("ro ")).count(), 2L, "force_ro switched exactly twice");
                check(ev.stream().noneMatch(e -> e.contains(other) || e.contains("rom_emmc_boot" + s)), "the running area touched: " + ev);
                check(r.areas.readOnly(node), "force_ro 1 again");
                List<String> w = new java.util.ArrayList<>();
                for (String e : ev) if (e.startsWith("write rom_emmc_boot")) w.add(e);
                eq(w, "[write rom_emmc_boot" + r.t + " 0+4096, write rom_emmc_boot" + r.t + " 4096+" + (file(b).length - 4096) + ", write rom_emmc_boot"
                        + r.t + " 0+4096]", "the fence, the rest, the head");
                eq(r.c.verifyEqual(p), null, "verifyEqual");
                halvesEqual(b, r.set);
                FwPlan q = r.plan();
                check(q.toCopy().isEmpty() && q.refusal == null, "a second plan: nothing to copy " + q.report());
            });
        t("first stage: the other half's area holds the file outside the projector's own ranges (it started from it): the same, never written", () -> {
            FakeBlocks b = unit(STAGE, "_a", "mboot");
            Run r = new Run(STAGE, b, "_a", new Mem());
            b.files.put(STAGE_W.writer, "unknown".getBytes(java.nio.charset.StandardCharsets.US_ASCII));   // never asked: nothing to write
            FwPlan p = r.plan();
            eq(p.refusal, null, "no refusal");
            eq(p.copyNames(), "[mboot]", "only mboot");
            check(p.part("rom_emmc_boot").same() && !p.part("rom_emmc_boot").shaS.equals(p.part("rom_emmc_boot").shaT), "same outside the ranges, other bytes inside");
            check(r.c.run(p, Mode.REAL, true).verified, "run");
            check(b.events.stream().noneMatch(e -> e.contains("rom_emmc_boot") || e.startsWith("ro ")), "touched: " + b.events);
        });
        t("first stage: one byte outside the ranges differs: written", () -> {
            FakeBlocks b = unit(STAGE, "_a");
            b.parts.get("rom_emmc_boot_b")[0x1000] ^= 1;
            Run r = new Run(STAGE, b, "_a", new Mem());
            eq(r.plan().copyNames(), "[rom_emmc_boot]", "copy");
        });
        t("first stage, the dry run: every read, a WOULD line naming the area and force_ro, nothing written, force_ro never touched", () -> {
            FakeBlocks b = updated("_a");
            Map<String, String> before = hashes(b);
            Run r = new Run(STAGE, b, "_a", new Mem());
            FwResult res = r.c.run(r.plan(), Mode.DRY, true);
            check(res.verified && !res.written, "result " + res);
            eq(hashes(b), before, "nothing changed");
            check(b.events.stream().noneMatch(e -> e.startsWith("open-w") || e.startsWith("ro ")), "written " + b.events);
            check(r.has("WOULD: write /vendor/bin/rom_emmc_boot.bin") && r.has("rom_emmc_boot_b (mmcblk0boot1, force_ro 0 only around the write)"), "lines " + r.lines);
        });
        t("first stage, slot set: the other half keeps its own older chain (leave): its first stage stays, no writer needed, nothing written", () -> {
            FakeBlocks b = updated("_a");
            b.files.put(STAGE_W.writer, "unknown".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            Run r = new Run(STAGE, b, "_a", new Mem());
            FwPlan p0 = r.plan();
            check(p0.refusal != null && p0.setRefusal == null, "without the slot-set choice the first stage refuses alone: " + p0.refusal);
            FwPlan p = r.c.plan(r.set, "_a", "_b", Collections.emptyMap(), Arrays.asList("mboot", "optee", "rom_emmc_boot"));
            eq(p.refusal, null, "kept whole: no refusal");
            check(p.toCopy().isEmpty() && p.part("rom_emmc_boot").leave, "nothing to copy");
            eq(r.c.verifyEqual(p), null, "verify skips the kept parts");
        });
    }

    static void refusals() {
        String[][] cases = {
                {"writer", "the stock post-install step /vendor/bin/postinst_mtk (sha256 "},
                {"no writer", "/vendor/bin/postinst_mtk of the running vendor cannot be read"},
                {"no file", "/vendor/bin/rom_emmc_boot.bin of the running vendor cannot be read"},
                {"ext_csd other", "EXT_CSD[179] of mmcblk0 is 0x10 (boot area 2), the running half starts from area 1"},
                {"ext_csd user", "(boot area 7)"},
                {"ext_csd none", "the eMMC boot areas cannot be read"},
                {"link", "rom_emmc_boot_b points at mmcblk0boot0, not mmcblk0boot1"},
                {"running link", "rom_emmc_boot_a points at mmcblk0rpmb, not mmcblk0boot0"},
                {"running area", "rom_emmc_boot_a differs from /vendor/bin/rom_emmc_boot.bin at 0x3000"},
                {"package", "is a multi-chip package"},
                {"zero head", "starts with 4 KiB of zeros"},
                {"short", "/vendor/bin/rom_emmc_boot.bin is 4000 bytes"},
                {"ranges", "the projector's own ranges of the model do not fit"},
                {"too big", "the areas are"}};
        for (String[] c : cases)
            t("first stage refused (" + c[0] + "): the plan says why, a run writes nothing, force_ro never touched", () -> {
                FakeBlocks b = updated("_a");
                Run r = new Run(STAGE, b, "_a", new Mem());
                byte[] f = file(b);
                switch (c[0]) {
                    case "writer": b.files.put(STAGE_W.writer, "another step".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); break;
                    case "no writer": b.files.remove(STAGE_W.writer); break;
                    case "no file": b.files.remove(STAGE_W.file); break;
                    case "ext_csd other": r.areas.pc = 0x10; break;
                    case "ext_csd user": r.areas.pc = 0x38; break;
                    case "ext_csd none": r.areas.noExtCsd = true; break;
                    case "link": r.areas.links.put("rom_emmc_boot_b", "mmcblk0boot0"); break;
                    case "running link": r.areas.links.put("rom_emmc_boot_a", "mmcblk0rpmb"); break;
                    case "running area": b.parts.get("rom_emmc_boot_a")[0x3000] ^= 1; break;
                    case "package": System.arraycopy("MOPF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, f, 0, 4); break;
                    case "zero head": Arrays.fill(f, 0, 4096, (byte) 0); break;
                    case "short": f = Arrays.copyOf(f, 4000); break;
                    case "ranges": f = Arrays.copyOf(f, 0x2000); break;
                    case "too big": f = rnd(FwCopyTests.SZ + 10); break;
                    default: throw new AssertionError(c[0]);
                }
                if (!c[0].equals("no file")) b.files.put(STAGE_W.file, f);
                if (c[0].equals("package") || c[0].equals("zero head") || c[0].equals("short") || c[0].equals("ranges"))
                    System.arraycopy(f, 0, b.parts.get("rom_emmc_boot_a"), 0, f.length);
                Map<String, String> before = hashes(b);
                FwPlan p = r.plan();
                check(p.refusal != null && p.refusal.contains(c[1]) && p.setRefusal == null, "refusal " + p.refusal);
                FwResult res = r.c.run(p, Mode.REAL, true);
                check(!res.verified && "plan".equals(res.step) && !res.written, "result " + res);
                eq(hashes(b), before, "nothing changed");
                check(b.events.stream().noneMatch(e -> e.startsWith("open-w") || e.startsWith("ro ")), "written " + b.events);
            });
    }

    static void faults() {
        t("first stage: force_ro stays 1 after the clear: stopped before the area is opened, set back, the other half unbootable", () -> {
            FakeBlocks b = updated("_a");
            Run r = new Run(STAGE, b, "_a", new Mem());
            r.areas.stuckRo = true;
            FwResult res = r.c.run(r.plan(), Mode.REAL, true);
            check(!res.verified && "io".equals(res.step) && res.reason.contains("force_ro of mmcblk0boot1 stays 1"), "result " + res);
            check(b.events.stream().noneMatch(e -> e.equals("open-w rom_emmc_boot_b")), "the area opened: " + b.events);
            eq(b.events.get(b.events.size() - 1), "ro mmcblk0boot1 1", "set back last");
            eq(r.hal.calls, "[unbootable 1]", "the other half unbootable");
        });
        t("first stage: the write fails: force_ro set back to 1, stopped, the other half unbootable, the running area untouched", () -> {
            FakeBlocks b = updated("_a");
            Map<String, String> before = hashes(b);
            b.writeError = "rom_emmc_boot_b";
            Run r = new Run(STAGE, b, "_a", new Mem());
            FwResult res = r.c.run(r.plan(), Mode.REAL, true);
            check(!res.verified && "io".equals(res.step) && res.written, "result " + res);
            eq(b.events.get(b.events.size() - 1), "ro mmcblk0boot1 1", "set back");
            check(r.areas.readOnly("mmcblk0boot1"), "read only again");
            halfUntouched(before, b, "_a");
        });
        t("first stage: force_ro cannot be set back after a good write: the run stops (the reason names it), the other half unbootable", () -> {
            FakeBlocks b = updated("_a");
            Run r = new Run(STAGE, b, "_a", new Mem());
            r.areas.failRelock = true;
            FwResult res = r.c.run(r.plan(), Mode.REAL, true);
            check(!res.verified && res.reason.contains("force_ro of mmcblk0boot1 cannot be set back to 1"), "result " + res);
            check(r.j.state.contains("state=failed"), "journal " + r.j.state);
        });
        t("first stage: the area reads back differently (a bad write): stopped at verify, the other half unbootable", () -> {
            FakeBlocks b = updated("_a");
            b.corrupt = "rom_emmc_boot_b";
            Run r = new Run(STAGE, b, "_a", new Mem());
            FwResult res = r.c.run(r.plan(), Mode.REAL, true);
            check(!res.verified && "verify".equals(res.step), "result " + res);
            eq(b.events.get(b.events.size() - 1), "ro mmcblk0boot1 1", "set back");
        });
        t("first stage: EXT_CSD moved to the other half's area after the plan: stopped before any write", () -> {
            FakeBlocks b = updated("_a");
            Map<String, String> before = hashes(b);
            Run r = new Run(STAGE, b, "_a", new Mem());
            FwPlan p = r.plan();
            r.areas.pc = 0x10;
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(!res.verified && "bootarea".equals(res.step) && !res.written, "result " + res);
            eq(hashes(b), before, "nothing written");
        });
        t("first stage: the vendor file changed after the plan: stopped before any write", () -> {
            FakeBlocks b = updated("_a");
            Run r = new Run(STAGE, b, "_a", new Mem());
            FwPlan p = r.plan();
            file(b)[50] ^= 1;
            FwResult res = r.c.run(p, Mode.REAL, true);
            check(!res.verified && "source".equals(res.step) && !res.written, "result " + res);
        });
        t("first stage, verifyEqual before the switch: the running area, the other area, the vendor file and EXT_CSD read again", () -> {
            for (String what : new String[] {"running", "other", "file", "extcsd"}) {
                FakeBlocks b = updated("_a");
                Run r = new Run(STAGE, b, "_a", new Mem());
                FwPlan p = r.plan();
                check(r.c.run(p, Mode.REAL, true).verified, "copied");
                eq(r.c.verifyEqual(p), null, "equal");
                if (what.equals("running")) b.parts.get("rom_emmc_boot_a")[FwCopyTests.SZ - 1] ^= 1;
                if (what.equals("other")) b.parts.get("rom_emmc_boot_b")[0x3000] ^= 1;
                if (what.equals("file")) file(b)[0x3000] ^= 1;
                if (what.equals("extcsd")) r.areas.pc = 0x10;
                check(r.c.verifyEqual(p) != null, what + ": not seen");
            }
        });
    }

    static void cuts() {
        for (long into : new long[] {0, 100, 4096, 4096 + 3000, 3 * 4096L + 200})
            for (boolean keepJournal : new boolean[] {true, false})
                t("first stage: power cut " + into + " bytes into its write, journal " + (keepJournal ? "kept" : "lost") + ": the running half untouched, "
                        + "the next plan from the bytes sees it unfinished, the run again ends with the file in the area", () -> {
                    FakeBlocks b = updated("_a");
                    Map<String, String> before = hashes(b);
                    Mem j = new Mem();
                    Run r = new Run(STAGE, b, "_a", j);
                    FwPlan p = r.plan();
                    long others = 0;
                    for (FwPlan.Part x : p.toCopy()) if (x.post == null) others += x.bytes + FwCopy.HEAD;   // the fences and the copies before it
                    b.cutAfter = others + into;
                    try {
                        r.c.run(p, Mode.REAL, true);
                        check(false, "no cut");
                    } catch (FakeBlocks.PowerCut expected) { }
                    b.cutAfter = -1;
                    r.areas.ro.clear();   // the projector starts again: every boot area read only
                    halfUntouched(before, b, "_a");
                    eq(hashes(b).get("rom_emmc_boot_a"), before.get("rom_emmc_boot_a"), "the running area untouched");
                    Run again = new Run(STAGE, b, "_a", keepJournal ? j : new Mem());
                    FwPlan q = again.plan();
                    eq(q.copyNames(), "[rom_emmc_boot]", "only the first stage left");
                    FwResult res = again.c.run(q, Mode.REAL, true);
                    check(res.verified, "result " + res);
                    halvesEqual(b, again.set);
                    eq(again.c.verifyEqual(q), null, "verify");
                });
    }
}
