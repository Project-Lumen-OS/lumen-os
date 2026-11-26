// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a copy found, read only ({@link FwCopy#plan}): both halves of every compared partition with size, sha256 and
 * whether the other half's first 4 KiB is zero (the fence of a copy that did not finish, {@link FwCopy}), and the
 * refusal when nothing may be copied. Its {@link #id} names exactly these bytes: a copy runs only on the plan the
 * user saw.
 */
public final class FwPlan {
    /** One compared partition. */
    public static final class Part {
        public final String name;
        /** a {@link Verdict#KEEP_EQUAL} partition: compared, never written */
        public final boolean keep;
        public final long bytes;
        public final String shaS, shaT;
        /** the other half's first 4 KiB are zero: a copy into it was cut before this partition was finished */
        public final boolean fenced;
        /** differs and stays the other half's own by the caller's choice (its own stock build, SlotSet): not written */
        public final boolean leave;
        /**
         * a post-install write (the first boot stage, {@link PostWrite}), else null. Then bytes is the length of the
         * running vendor's file, shaS its sha256, shaT the sha256 of the other half's area over that length, shaRun the
         * sha256 of the whole running area (never written, read again before the switch)
         */
        public final PostWrite post;
        public final String shaRun;
        /** post: the other half's area holds the file as stock leaves it (equal outside the projector's own ranges) */
        public final boolean matched;
        /** post: why it may not be written on this projector now (null = it may); refuses the plan when it is to be written */
        public final String blocked;

        Part(String name, boolean keep, long bytes, String shaS, String shaT, boolean fenced, boolean leave) {
            this(name, keep, bytes, shaS, shaT, fenced, leave, null, null, false, null);
        }

        Part(String name, boolean keep, long bytes, String shaS, String shaT, boolean fenced, boolean leave, PostWrite post,
             String shaRun, boolean matched, String blocked) {
            this.name = name; this.keep = keep; this.bytes = bytes; this.shaS = shaS; this.shaT = shaT; this.fenced = fenced;
            this.leave = leave; this.post = post; this.shaRun = shaRun; this.matched = matched; this.blocked = blocked;
        }

        public boolean same() { return post != null ? matched && !fenced : shaS.equals(shaT); }

        /**
         * the report line: FW name verdict bytes sha256(running half) sha256(other half); a post-install write names the
         * running vendor's file as its source and adds the running area
         */
        public String line(String s, String t) {
            return "FW\t" + name + "\t" + (same() ? "same" : keep ? "differs-kept" : leave ? "own-build" : fenced ? "unfinished" : "copy") + "\t" + bytes
                    + "\t" + (post == null ? name + s : post.file) + "=" + shaS + "\t" + name + t + "=" + shaT
                    + (post == null ? "" : "\t" + name + s + "=" + shaRun + (blocked == null ? "" : "\tblocked=" + blocked));
        }
    }

    /** the running half and the other half ("_a" / "_b") */
    public final String s, t;
    public final List<Part> parts;
    /** null: the plan may run; else why nothing is copied */
    public final String refusal;
    /**
     * the refusal of the set alone, without a post-install write that may not be written now: a caller that may still
     * keep that part as the other half's own (SlotSet) decides with this one and plans again
     */
    public final String setRefusal;

    FwPlan(String s, String t, List<Part> parts, String refusal) {
        this.s = s; this.t = t; this.parts = Collections.unmodifiableList(parts); this.setRefusal = refusal;
        String r = refusal;
        for (Part p : this.parts) if (r == null && p.blocked != null && toCopy().contains(p)) r = p.name + ": " + p.blocked;
        this.refusal = r;
    }

    /** the parts a run writes: every differing partition of the copied set, in the set's order */
    public List<Part> toCopy() {
        List<Part> r = new ArrayList<>();
        for (Part p : parts) if (!p.keep && !p.leave && !p.same()) r.add(p);
        return r;
    }

    public List<String> copyNames() {
        List<String> r = new ArrayList<>();
        for (Part p : toCopy()) r.add(p.name);
        return r;
    }

    /** the other half's partitions a copy left unfinished (first 4 KiB zero) */
    public List<String> fenced() {
        List<String> r = new ArrayList<>();
        for (Part p : parts) if (p.fenced) r.add(p.name);
        return r;
    }

    public Part part(String name) {
        for (Part p : parts) if (p.name.equals(name)) return p;
        return null;
    }

    public List<String> report() {
        List<String> r = new ArrayList<>();
        for (Part p : parts) r.add(p.line(s, t));
        if (refusal != null) r.add("FW\trefused\t" + refusal);
        return r;
    }

    /** sha256 of the halves, every part's sizes and hashes, and the refusal */
    public String id() {
        StringBuilder b = new StringBuilder("fw1 ").append(s).append(' ').append(t);
        for (Part p : parts) b.append('\n').append(p.line(s, t));
        b.append("\nrefusal=").append(refusal == null ? "-" : refusal);
        return hex(sha256().digest(b.toString().getBytes(StandardCharsets.UTF_8)));
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return s.toString();
    }
}
