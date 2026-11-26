// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The one copy of the XGIMI set from the running half into the other half, unchanged, as XGIMI's own update and
 * update_engine do it (decision 2026-11-15). The running half is only read. Order of a real run:
 * <ol>
 * <li>the plan the user allowed ({@link #plan}: both halves hashed once), refused when it has a refusal or the user did
 * not allow it, a no-op when the halves are already the same,</li>
 * <li>the other half marked unbootable through the platform ({@link Halves}), read back,</li>
 * <li>every source partition read a second time: its sha256 must equal the plan's, before the first write,</li>
 * <li>the fence: the first 4 KiB of every partition to write set to zero (no stock partition starts with 4 KiB of
 * zeros, so a copy cut at any later point is seen as unfinished, also with its journal lost),</li>
 * <li>per partition: the body (from 4 KiB on), then the first 4 KiB, sync, the sha256 of the bytes read from the
 * source during the copy must be the plan's, and the other half read back must be it too,</li>
 * <li>the journal says verified. The other half stays unbootable: the caller makes it usable for Lumen OS, and only
 * after {@link #verifyEqual} read both halves equal again.</li>
 * </ol>
 * Any mismatch or read error stops the run: the other half stays unbootable, the running half is never written, the
 * reason goes to the result and the journal. A new run after a power loss plans again from the bytes and copies only
 * what still differs. {@link Mode#DRY} does every read and hash of a real run and reports each write as WOULD.
 * <p>
 * The first boot stage ({@link PostWrite}, a model whose stock post-install step writes it) comes last, after mboot and
 * the rest of the set, the stock way: the running vendor's own file (the file stock wrote into the running area, which
 * holds it outside the projector's own ranges), at offset 0 of the other half's eMMC boot area, force_ro cleared only
 * around each write and set back on every path, read back against the file. Only when the writer of this firmware is
 * one whose code was read, the by-name links are the model's areas, and EXT_CSD[179] enables the running half's area
 * and not the one written. The running area is never opened for writing.
 */
public final class FwCopy {
    public static final int HEAD = 4096;
    static final int CHUNK = 1 << 20;

    /** Lines for the report and the progress of long reads and writes. */
    public interface Log {
        void line(String s);

        default void progress(long done, long total) {}
    }

    private final Blocks blocks;
    private final Halves halves;
    private final Journal journal;
    private final Log log;
    private final BootAreas areas;

    public FwCopy(Blocks blocks, Halves halves, Journal journal, Log log) {
        this(blocks, halves, journal, log, BootAreas.NONE);
    }

    public FwCopy(Blocks blocks, Halves halves, Journal journal, Log log, BootAreas areas) {
        this.blocks = blocks; this.halves = halves; this.journal = journal; this.log = log; this.areas = areas;
    }

    // ============================================================================================ plan (read only)

    /**
     * Read both halves of every compared partition of the set. known: sha256 of a partition with its half (boot_a)
     * from an earlier full read of the same check, else it is read here.
     */
    public FwPlan plan(FwSet set, String s, String t, Map<String, String> known) throws IOException {
        return plan(set, s, t, known, Collections.emptyList());
    }

    /**
     * The same, with partitions that stay the other half's own when they differ (leave: the caller proved the other
     * half holds a whole stock build of the same boot chain). Never a part of a half a copy left unfinished.
     */
    public FwPlan plan(FwSet set, String s, String t, Map<String, String> known, Collection<String> leave) throws IOException {
        if (!half(s) || !half(t) || s.equals(t)) throw new IllegalArgumentException("halves " + s + " and " + t);
        String refusal = set.model.refusal;
        for (FwSet.Entry e : set.entries)
            if (refusal == null && e.verdict == Verdict.MISSING) refusal = e.name + " of the XGIMI set is not in both halves on this projector";
        List<FwPlan.Part> parts = new ArrayList<>();
        List<String> keep = set.names(Verdict.KEEP_EQUAL);
        for (String n : set.compared()) {
            PostWrite w = set.post(n);
            if (w != null) {
                FwPlan.Part p = planPost(w, s, t, leave.contains(n));
                parts.add(p);
                if (refusal == null && p.leave && p.fenced) refusal = n + t + " was left unfinished by an earlier copy: it cannot stay as it is";
                continue;
            }
            String a = n + s, b = n + t;
            if (!blocks.exists(a) || !blocks.exists(b)) throw new IOException(n + " is not in both halves");
            long sa = size(a), sb = size(b);
            String ha = known.containsKey(a) ? known.get(a) : sha(a, sa), hb = known.containsKey(b) ? known.get(b) : sha(b, sb);
            boolean fenced = zeroHead(b, sb);
            FwPlan.Part p = new FwPlan.Part(n, keep.contains(n), sa, ha, hb, fenced, leave.contains(n) && !keep.contains(n));
            parts.add(p);
            if (refusal != null) continue;
            if (sa != sb) refusal = n + s + " and " + n + t + " differ in size (" + sa + " and " + sb + " bytes)";
            else if (sa % HEAD != 0 || sa < HEAD) refusal = n + ": " + sa + " bytes is no whole number of 4 KiB blocks";
            else if (zeroHead(a, sa)) refusal = n + s + " of the running half starts with 4 KiB of zeros: it is no whole XGIMI file, nothing is copied from it";
            else if (p.keep && !p.same()) refusal = n + " differs between the halves and is never written on this projector";
            else if (p.leave && fenced) refusal = n + t + " was left unfinished by an earlier copy: it cannot stay as it is";
        }
        return new FwPlan(s, t, parts, refusal);
    }

    static boolean half(String x) { return "_a".equals(x) || "_b".equals(x); }

    /**
     * The first stage: the running vendor's file, the other half's area over its length, the running area. Blocked
     * (a reason) when it would have to be written and it cannot be written the way stock does it here.
     */
    private FwPlan.Part planPost(PostWrite w, String s, String t, boolean leave) throws IOException {
        String a = w.name + s, b = w.name + t;
        if (!blocks.exists(a) || !blocks.exists(b)) throw new IOException(w.name + " is not in both halves");
        long sa = size(a), sb = size(b);
        String shaRun = sha(a, sa);
        boolean fenced = zeroHead(b, sb);
        byte[] f;
        try {
            f = areas.vendorFile(w.file);
        } catch (IOException e) {
            return new FwPlan.Part(w.name, false, 0, "-", "-", fenced, leave, w, shaRun, false,
                    w.file + " of the running vendor cannot be read (" + e.getMessage() + ")");
        }
        byte[] other = head(b, (int) Math.min(f.length, sb));
        boolean matched = f.length >= HEAD && w.differs(f, other) == null;
        String blocked = leave || matched ? null : whyNot(w, s, t, f, head(a, (int) Math.min(f.length, sa)), sa, sb);
        return new FwPlan.Part(w.name, false, f.length, FwPlan.hex(FwPlan.sha256().digest(f)), FwPlan.hex(FwPlan.sha256().digest(other)),
                fenced, leave, w, shaRun, matched, blocked);
    }

    /** why the first stage may not be written now, null when it may (every rule of {@link PostWrite}) */
    private String whyNot(PostWrite w, String s, String t, byte[] f, byte[] running, long sa, long sb) {
        if (f.length < HEAD) return w.file + " is " + f.length + " bytes";
        if (sa != sb || f.length > sb) return "the areas are " + sa + " and " + sb + " bytes, " + w.file + " " + f.length;
        boolean zero = true;
        for (int i = 0; i < HEAD; i++) zero &= f[i] == 0;
        if (zero) return w.file + " starts with 4 KiB of zeros";
        if (new String(f, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1).equals(PostWrite.PACKAGE_MAGIC))
            return w.file + " is a multi-chip package: the stock step writes one part of it";
        for (long[] r : w.deviceWritten) if (r[1] > f.length) return "the projector's own ranges of the model do not fit " + w.file;
        String bad = bootAreas(w, s, t);
        if (bad != null) return bad;
        Long d = w.differs(f, running);
        if (d != null) return w.name + s + " differs from " + w.file + " at 0x" + Long.toHexString(d) + ": the running area is not the file stock wrote";
        try {
            String ws = FwPlan.hex(FwPlan.sha256().digest(areas.vendorFile(w.writer)));
            if (!w.writerSha.contains(ws))
                return "the stock post-install step " + w.writer + " (sha256 " + ws + ") is not one whose code was read, so how stock writes the first stage here is not known";
        } catch (IOException e) {
            return w.writer + " of the running vendor cannot be read (" + e.getMessage() + ")";
        }
        return null;
    }

    /** the by-name links are the model's areas and EXT_CSD[179] starts the running half's area, never the other's */
    private String bootAreas(PostWrite w, String s, String t) {
        try {
            for (String h : new String[] {s, t}) {
                String node = areas.node(w.name + h);
                if (!w.node(h).equals(node)) return w.name + h + " points at " + node + ", not " + w.node(h);
            }
            int pc = areas.partitionConfig(w.disk()), en = BootAreas.bootEnable(pc);
            if (en != w.enable(s) || en == w.enable(t))
                return "EXT_CSD[179] of " + w.disk() + " is 0x" + Integer.toHexString(pc) + " (boot area " + en + "), the running half starts from area "
                        + w.enable(s);
            return null;
        } catch (IOException e) {
            return "the eMMC boot areas cannot be read (" + e.getMessage() + ")";
        }
    }

    // ============================================================================================ run

    public FwResult run(FwPlan p, Mode mode, boolean consent) {
        boolean real = mode == Mode.REAL;
        String prior = prior();
        boolean resumed = prior != null && !prior.contains("\nstate=verified");
        if (resumed) say("an earlier copy did not finish (" + oneLine(prior) + "): this run copies what still differs");
        if (p.refusal != null) return FwResult.stop("plan", p.refusal, false, real, resumed);
        List<FwPlan.Part> todo = p.toCopy();
        if (todo.isEmpty()) {
            say("the XGIMI files are the same in both halves: nothing to copy");
            return FwResult.done(real, resumed, Collections.emptyList());
        }
        if (!consent) return FwResult.stop("consent", "the copy of the XGIMI files was not allowed", false, real, resumed);
        int ti = "_a".equals(p.t) ? 0 : 1;
        List<String> done = new ArrayList<>();
        boolean[] written = {false};
        try {
            mark(p, real, "started", done, null);
            // ---- the other half unbootable first: MBOOT never tries a half that is being written
            if (!real) say("WOULD: mark half " + p.t.substring(1) + " unbootable");
            else if (!halves.markUnbootable(ti)) return stop(p, real, "unbootable", "half " + p.t.substring(1) + " could not be marked unbootable", written[0], done);
            // ---- every source read a second time before the first write: the copy starts only from a stable running half
            long total = 0;
            for (FwPlan.Part x : todo) total += x.bytes;
            Map<FwPlan.Part, byte[]> files = new java.util.HashMap<>();
            for (FwPlan.Part x : todo) {
                if (x.post != null) {
                    byte[] f = areas.vendorFile(x.post.file);
                    String again = FwPlan.hex(FwPlan.sha256().digest(f));
                    if (!again.equals(x.shaS))
                        return stop(p, real, "source", x.post.file + " read twice gave two different sha256 (" + x.shaS + ", " + again + ")", written[0], done);
                    String bad = bootAreas(x.post, p.s, p.t);
                    if (bad != null) return stop(p, real, "bootarea", bad, written[0], done);
                    files.put(x, f);
                    continue;
                }
                String again = sha(x.name + p.s, x.bytes);
                if (!again.equals(x.shaS))
                    return stop(p, real, "source", x.name + p.s + " read twice gave two different sha256 (" + x.shaS + ", " + again + ")", written[0], done);
            }
            say("the XGIMI files to copy: " + names(todo) + " (" + mib(total) + " MiB) from half " + p.s.substring(1) + " to half " + p.t.substring(1)
                    + ", each source read twice with the same sha256");
            // ---- the fence: an unfinished copy is seen from the bytes of the other half alone
            // (the first stage gets its own fence right before its write, after every other partition: copyFile)
            for (FwPlan.Part x : todo) {
                if (x.post != null) continue;
                if (!real) { say("WOULD: zero the first 4 KiB of " + x.name + p.t); continue; }
                write(p, x, 0, new byte[HEAD], written);
            }
            mark(p, real, "fenced", done, null);
            long copied = 0;
            for (FwPlan.Part x : todo) {
                if (!real && x.post != null) {
                    say("WOULD: write " + x.post.file + " (" + x.bytes + " bytes, sha256 " + x.shaS + ") at 0 of " + x.name + p.t + unlockNote(p, x)
                            + ": the first 4 KiB zeroed, the rest, then the first 4 KiB, read back");
                    continue;
                }
                if (!real) { say("WOULD: copy " + x.name + p.s + " -> " + x.name + p.t + " (" + mib(x.bytes) + " MiB), sha256 " + x.shaS + ", read back"); continue; }
                if (x.post != null) {
                    byte[] f = files.get(x);
                    unlocked(p, x, () -> copyFile(p, x, f, written));
                    String back = FwPlan.hex(FwPlan.sha256().digest(head(x.name + p.t, (int) x.bytes)));
                    if (!back.equals(x.shaS)) return stop(p, real, "verify", x.name + p.t + " reads back differently (" + back + ")", written[0], done);
                    copied += x.bytes;
                    done.add(x.name);
                    mark(p, true, "copying", done, null);
                    say("written and read back: " + x.post.file + " -> " + x.name + p.t + " (" + x.shaS + ")");
                    continue;
                }
                String got = copy(p, x, copied, total, written);
                if (!got.equals(x.shaS))
                    return stop(p, real, "source", x.name + p.s + " changed while it was copied (" + got + ")", written[0], done);
                String back = sha(x.name + p.t, x.bytes);
                if (!back.equals(x.shaS)) return stop(p, real, "verify", x.name + p.t + " reads back differently (" + back + ")", written[0], done);
                copied += x.bytes;
                done.add(x.name);
                mark(p, true, "copying", done, null);
                say("copied and read back: " + x.name + p.s + " -> " + x.name + p.t + " (" + x.shaS + ")");
            }
            if (!real) done.addAll(p.copyNames());
            mark(p, real, "verified", done, null);
            return FwResult.done(real, resumed, done);
        } catch (IOException e) {
            return stop(p, real, "io", e.getMessage(), written[0], done);
        }
    }

    /**
     * Both halves read again right before the other half is made usable: every compared partition the same in both,
     * no fence left. null = yes, else what differs.
     */
    public String verifyEqual(FwPlan p) {
        try {
            for (FwPlan.Part x : p.parts) {
                if (x.leave) continue;
                if (x.post != null) {
                    String bad = verifyPost(p, x);
                    if (bad != null) return bad;
                    continue;
                }
                String a = sha(x.name + p.s, x.bytes), b = sha(x.name + p.t, x.bytes);
                if (!a.equals(x.shaS)) return x.name + p.s + " of the running half changed since the plan";
                if (!a.equals(b)) return x.name + " is not the same in both halves";
            }
            return null;
        } catch (IOException e) {
            return "cannot read the halves: " + e.getMessage();
        }
    }

    /**
     * The first stage before the switch: the running vendor's file and the running area as planned, the other half's
     * area holding the file (outside the projector's own ranges, byte for byte after a copy, read back then), the
     * boot areas still as planned when this plan wrote it. null = yes.
     */
    private String verifyPost(FwPlan p, FwPlan.Part x) throws IOException {
        PostWrite w = x.post;
        byte[] f = areas.vendorFile(w.file);
        if (!FwPlan.hex(FwPlan.sha256().digest(f)).equals(x.shaS)) return w.file + " of the running vendor changed since the plan";
        if (!sha(x.name + p.s, size(x.name + p.s)).equals(x.shaRun)) return x.name + p.s + " of the running half changed since the plan";
        Long d = w.differs(f, head(x.name + p.t, (int) Math.min(f.length, size(x.name + p.t))));
        if (d != null) return x.name + p.t + " does not hold " + w.file + " (0x" + Long.toHexString(d) + ")";
        // the boot areas as planned, for a first stage this plan wrote (one that was already the same needs no EXT_CSD)
        return p.toCopy().contains(x) ? bootAreas(w, p.s, p.t) : null;
    }

    // ============================================================================================ writes

    private interface Step {
        void run() throws IOException;
    }

    /**
     * A write into the other half's eMMC boot area, as the stock post-install step does it: force_ro 0 (read back), the
     * write, force_ro 1 again on every path (read back; a failure to set it back stops the run after a good write).
     */
    private void unlocked(FwPlan p, FwPlan.Part x, Step body) throws IOException {
        String node = x.post.node(p.t);
        if (node.equals(x.post.node(p.s)) || !node.equals(areas.node(x.name + p.t)))
            throw new IllegalStateException("refused force_ro of " + node + ": not the other half's area");
        boolean ok = false;
        try {
            areas.setReadOnly(node, false);
            if (areas.readOnly(node)) throw new IOException("force_ro of " + node + " stays 1");
            body.run();
            ok = true;
        } finally {
            String back = relock(node);
            if (back != null) {
                say(back);
                if (ok) throw new IOException(back);
            }
        }
    }

    private String relock(String node) {
        try {
            areas.setReadOnly(node, true);
            return areas.readOnly(node) ? null : "force_ro of " + node + " is not 1 again";
        } catch (IOException e) {
            return "force_ro of " + node + " cannot be set back to 1: " + e.getMessage();
        }
    }

    private static String unlockNote(FwPlan p, FwPlan.Part x) { return " (" + x.post.node(p.t) + ", force_ro 0 only around the write)"; }

    /**
     * The running vendor's file into the other half's area, after every other partition of the plan: the fence (the
     * first 4 KiB zeroed), sync, from 4 KiB on, sync, then the first 4 KiB, sync. Nothing past the file's length.
     */
    private void copyFile(FwPlan p, FwPlan.Part x, byte[] f, boolean[] written) throws IOException {
        String to = x.name + p.t;
        if (p.s.equals(p.t) || !p.toCopy().contains(x) || f.length != x.bytes || f.length < HEAD)
            throw new IllegalStateException("refused a write outside the plan: " + to);
        written[0] = true;
        try (Blocks.Handle dst = blocks.open(to, true)) {
            if (dst.size() < x.bytes) throw new IOException(to + ": smaller than " + x.post.file);
            dst.write(0, new byte[HEAD], 0, HEAD);
            dst.sync();
            if (f.length > HEAD) dst.write(HEAD, f, HEAD, f.length - HEAD);
            dst.sync();
            dst.write(0, f, 0, HEAD);
            dst.sync();
        }
    }

    /** The only writes of this module: a planned partition of the other half, never the running one. */
    private void write(FwPlan p, FwPlan.Part x, long off, byte[] data, boolean[] written) throws IOException {
        String name = x.name + p.t;
        if (p.s.equals(p.t) || !p.toCopy().contains(x) || off < 0 || off + data.length > x.bytes)
            throw new IllegalStateException("refused a write outside the plan: " + name + " at " + off);
        written[0] = true;
        try (Blocks.Handle h = blocks.open(name, true)) {
            h.write(off, data, 0, data.length);
            h.sync();
        }
    }

    /** The body from 4 KiB on, then the first 4 KiB; returns the sha256 of the source bytes read on the way. */
    private String copy(FwPlan p, FwPlan.Part x, long before, long total, boolean[] written) throws IOException {
        String to = x.name + p.t;
        if (p.s.equals(p.t) || !p.toCopy().contains(x)) throw new IllegalStateException("refused a write outside the plan: " + to);
        MessageDigest md = FwPlan.sha256();
        byte[] head = new byte[HEAD], buf = new byte[CHUNK];
        written[0] = true;
        try (Blocks.Handle src = blocks.open(x.name + p.s, false); Blocks.Handle dst = blocks.open(to, true)) {
            if (src.size() != x.bytes || dst.size() != x.bytes) throw new IOException(x.name + ": size changed since the plan");
            src.read(0, head, 0, HEAD);
            md.update(head);
            for (long off = HEAD; off < x.bytes; ) {
                int k = (int) Math.min(buf.length, x.bytes - off);
                src.read(off, buf, 0, k);
                md.update(buf, 0, k);
                dst.write(off, buf, 0, k);
                off += k;
                log.progress(before + off, total);
            }
            dst.sync();
            dst.write(0, head, 0, HEAD);
            dst.sync();
        }
        return FwPlan.hex(md.digest());
    }

    // ============================================================================================ reads, journal

    long size(String name) throws IOException {
        try (Blocks.Handle h = blocks.open(name, false)) {
            return h.size();
        }
    }

    String sha(String name, long n) throws IOException {
        MessageDigest md = FwPlan.sha256();
        byte[] buf = new byte[CHUNK];
        try (Blocks.Handle h = blocks.open(name, false)) {
            if (h.size() != n) throw new IOException(name + " is " + h.size() + " bytes, expected " + n);
            for (long off = 0; off < n; ) {
                int k = (int) Math.min(buf.length, n - off);
                h.read(off, buf, 0, k);
                md.update(buf, 0, k);
                off += k;
            }
        }
        return FwPlan.hex(md.digest());
    }

    /** the first n bytes of a partition */
    byte[] head(String name, int n) throws IOException {
        byte[] b = new byte[n];
        try (Blocks.Handle h = blocks.open(name, false)) {
            if (h.size() < n) throw new IOException(name + " is " + h.size() + " bytes, " + n + " wanted");
            h.read(0, b, 0, n);
        }
        return b;
    }

    boolean zeroHead(String name, long size) throws IOException {
        byte[] b = new byte[(int) Math.min(HEAD, size)];
        try (Blocks.Handle h = blocks.open(name, false)) {
            h.read(0, b, 0, b.length);
        }
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private String prior() {
        try {
            return journal.read();
        } catch (IOException e) {
            say("the journal cannot be read (" + e.getMessage() + "): planned from the bytes alone");
            return null;
        }
    }

    private void mark(FwPlan p, boolean real, String state, List<String> done, String reason) throws IOException {
        if (!real) return;
        journal.write("plan=" + p.id() + "\nstate=" + state + "\nhalves=" + p.s + "->" + p.t + "\ndone=" + String.join(",", done)
                + (reason == null ? "" : "\nreason=" + reason.replace('\n', ' ')) + "\n");
    }

    private FwResult stop(FwPlan p, boolean real, String step, String reason, boolean written, List<String> done) {
        say("STOP at " + step + ": " + reason + (written ? "; half " + p.t.substring(1) + " stays unbootable" : "; nothing was written"));
        try {
            mark(p, real, "failed", done, step + ": " + reason);
        } catch (IOException e) {
            say("the journal cannot be written: " + e.getMessage());
        }
        return FwResult.stop(step, reason, written, real, false);
    }

    private void say(String s) { log.line(s); }

    static String names(List<FwPlan.Part> parts) {
        List<String> r = new ArrayList<>();
        for (FwPlan.Part x : parts) r.add(x.name);
        return String.join(", ", r);
    }

    static String oneLine(String s) { return s.trim().replace('\n', ' '); }

    static long mib(long b) { return (b + (1 << 20) - 1) >> 20; }
}
