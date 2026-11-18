// SPDX-License-Identifier: Apache-2.0
package org.lumen.fwcopy;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * FwSim: the module on real partition images of a projector, on a computer. A directory holds name_a.img and
 * name_b.img files (a stock dump, or a dump with XGIMI's own update images written over one half); FwSim plans and
 * runs the copy on those files exactly as on the projector, only the block devices are files and the boot record is a
 * text file (halves.txt). Used by test/fwsim.sh, never on a projector.
 *
 *   java org.lumen.fwcopy.FwSim DIR MODEL RUNNING plan|dry|real|verify [CUT_BYTES]
 *
 * CUT_BYTES: the power goes off after that many bytes were written in this run (the write that crosses it lands
 * partly, every later one never). Prints the plan lines, the run lines and the result; exit 0 verified, 2 stopped,
 * 1 a wrong call or a refused plan.
 */
public final class FwSim {
    private FwSim() {}

    static final class PowerOff extends IOException {
        PowerOff() { super("power off (simulated)"); }
    }

    /** name_a.img files; a write to the running half or to a file of no plan is a bug of the module: exit 3. */
    static final class FileBlocks implements Blocks {
        final File dir;
        final String running;
        long budget;
        long written;
        FileAreas areas;

        FileBlocks(File dir, String running, long budget) { this.dir = dir; this.running = running; this.budget = budget; }

        File file(String name) { return new File(dir, name + ".img"); }

        @Override public boolean exists(String name) { return file(name).isFile(); }

        @Override public Handle open(String name, boolean write) throws IOException {
            if (write && name.endsWith(running)) {
                System.out.println("BUG: the running half opened for writing: " + name);
                System.exit(3);
            }
            if (write && areas != null && name.startsWith("rom_emmc_boot") && areas.readOnly(areas.node(name)))
                throw new IOException(name + ": Operation not permitted (force_ro 1)");
            RandomAccessFile f = new RandomAccessFile(file(name), write ? "rw" : "r");
            return new Handle() {
                @Override public long size() throws IOException { return f.length(); }

                @Override public void read(long off, byte[] buf, int o, int n) throws IOException {
                    f.seek(off);
                    f.readFully(buf, o, n);
                }

                @Override public void write(long off, byte[] buf, int o, int n) throws IOException {
                    if (off + n > f.length()) throw new IOException(name + ": a write past the end");
                    long can = budget < 0 ? n : Math.min(n, budget - written);
                    if (can <= 0) throw new PowerOff();
                    f.seek(off);
                    f.write(buf, o, (int) can);
                    written += can;
                    if (can < n) throw new PowerOff();
                }

                @Override public void sync() throws IOException { f.getFD().sync(); }

                @Override public void close() throws IOException { f.close(); }
            };
        }
    }

    /** The boot record as a file: "_a=bootable" / "_b=unbootable". */
    static final class FileHalves implements Halves {
        final File f;

        FileHalves(File f) { this.f = f; }

        @Override public boolean markUnbootable(int half) throws IOException {
            Map<String, String> m = read(f);
            m.put(half == 0 ? "_a" : "_b", "unbootable");
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, String> e : m.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        }

        static Map<String, String> read(File f) throws IOException {
            Map<String, String> m = new TreeMap<>();
            m.put("_a", "bootable");
            m.put("_b", "bootable");
            if (f.isFile()) for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] kv = l.split("=", 2);
                if (kv.length == 2) m.put(kv[0], kv[1]);
            }
            return m;
        }
    }

    /**
     * The eMMC boot areas over the directory: the by-name links of the stock dumps (rom_emmc_boot_a -> mmcblk0boot0,
     * _b -> mmcblk0boot1), EXT_CSD from ext_csd.txt (the kernel's debugfs text read on the projector), force_ro as
     * force_ro_NODE files (missing = 1, as after every start), the vendor files under vendor/. A write open of an area
     * fails while its force_ro is 1, like the kernel's.
     */
    static final class FileAreas implements BootAreas {
        final File dir;

        FileAreas(File dir) { this.dir = dir; }

        @Override public String node(String name) {
            return name.equals("rom_emmc_boot_a") ? "mmcblk0boot0" : name.equals("rom_emmc_boot_b") ? "mmcblk0boot1" : name;
        }

        @Override public int partitionConfig(String disk) throws IOException {
            return BootAreas.parseExtCsd(new String(Files.readAllBytes(new File(dir, "ext_csd.txt").toPath()), StandardCharsets.US_ASCII));
        }

        @Override public boolean readOnly(String node) throws IOException {
            File f = new File(dir, "force_ro_" + node);
            return !f.isFile() || new String(Files.readAllBytes(f.toPath()), StandardCharsets.US_ASCII).trim().equals("1");
        }

        @Override public void setReadOnly(String node, boolean ro) throws IOException {
            System.out.println("  | force_ro " + node + " " + (ro ? 1 : 0));
            Files.write(new File(dir, "force_ro_" + node).toPath(), (ro ? "1" : "0").getBytes(StandardCharsets.US_ASCII));
        }

        @Override public byte[] vendorFile(String path) throws IOException {
            if (!path.startsWith("/vendor/")) throw new IOException(path);
            return Files.readAllBytes(new File(dir, path.substring(1)).toPath());
        }
    }

    public static void main(String[] a) throws IOException {
        if (a.length < 4) { System.out.println("usage: FwSim DIR MODEL RUNNING plan|dry|real|verify [CUT_BYTES]"); System.exit(1); }
        File dir = new File(a[0]);
        FwModel m = FwModel.of(a[1]);
        String s = a[2], t = "_a".equals(s) ? "_b" : "_a";
        long cut = a.length > 4 ? Long.parseLong(a[4]) : -1;
        if (m == null) { System.out.println("no XGIMI set for " + a[1]); System.exit(1); }
        FileBlocks blocks = new FileBlocks(dir, s, cut);
        blocks.areas = new FileAreas(dir);
        List<String> names = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.getName().endsWith(".img")) names.add(f.getName().substring(0, f.getName().length() - 4));
        Collections.sort(names);
        FwSet set = FwSet.of(m, names);
        System.out.println("model " + m.family + " (" + a[1] + "), running half " + s.substring(1) + ", set:");
        for (FwSet.Entry e : set.entries) System.out.println("  " + e);
        FwCopy c = new FwCopy(blocks, new FileHalves(new File(dir, "halves.txt")), new Journal.OnFile(new File(dir, "fwcopy.state")),
                new FwCopy.Log() {
                    @Override public void line(String x) { System.out.println("  | " + x); }

                    @Override public void progress(long done, long total) {}
                }, blocks.areas);
        FwPlan p = c.plan(set, s, t, Collections.emptyMap());
        System.out.println("plan " + p.id() + (p.refusal == null ? "" : " REFUSED: " + p.refusal));
        for (FwPlan.Part x : p.parts) System.out.println("  " + x.line(s, t));
        System.out.println("copy: " + p.copyNames() + ", unfinished in half " + t.substring(1) + ": " + p.fenced());
        switch (a[3]) {
            case "plan":
                System.exit(p.refusal == null ? 0 : 1);
                return;
            case "verify": {
                String v = c.verifyEqual(p);
                System.out.println("verifyEqual: " + (v == null ? "the set is the same in both halves" : v));
                System.exit(v == null ? 0 : 2);
                return;
            }
            case "dry":
            case "real": {
                long t0 = System.nanoTime();
                FwResult r = c.run(p, "real".equals(a[3]) ? Mode.REAL : Mode.DRY, true);
                System.out.println("result: " + r + " (" + blocks.written + " bytes written, " + (System.nanoTime() - t0) / 1000000 + " ms)");
                System.out.println("halves: " + FileHalves.read(new File(dir, "halves.txt")));
                System.exit(r.verified ? 0 : 2);
                return;
            }
            default:
                System.out.println("unknown mode " + a[3]);
                System.exit(1);
        }
    }
}
