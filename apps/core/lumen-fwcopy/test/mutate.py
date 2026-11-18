#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation test of the shared firmware copy (apps/core/lumen-fwcopy).

Every mutant breaks one safety rule of the copy (consent, the other half unbootable first, the second read, the
fence, the head written last, the read back, the journal, the dry run, the per-model set). Each one is applied to a
scratch copy of the module, compiled and run against test/FwCopyTests and test/FwListsTest; the run must fail. A
survivor means a rule no test pins. Host only, writes only a temp dir.

  python3 apps/core/lumen-fwcopy/test/mutate.py          every mutant
  MUT_ONLY=copy_,plan_ python3 ...                       the mutants whose names start so
Exit 0 only when every mutant is killed and the clean tree passes.
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
GSI = os.path.dirname(os.path.dirname(os.path.dirname(MOD)))
S = "src/org/lumen/fwcopy/"
C, P, F, M, J = S + "FwCopy.java", S + "FwPlan.java", S + "FwSet.java", S + "FwModel.java", S + "Journal.java"

# name, file, old, new (old must occur exactly once; tuples change several places together)
MUTANTS = [
    # ---- the plan
    ("plan_missing_ok", C, "if (refusal == null && e.verdict == Verdict.MISSING) refusal", "if (false) refusal"),
    ("plan_size_differ_ok", C, "if (sa != sb) refusal =", "if (false) refusal ="),
    ("plan_odd_size_ok", C, "else if (sa % HEAD != 0 || sa < HEAD) refusal", "else if (false) refusal"),
    ("plan_zero_source_ok", C, "else if (zeroHead(a, sa)) refusal", "else if (false) refusal"),
    ("plan_keep_differs_ok", C, "else if (p.keep && !p.same()) refusal", "else if (false) refusal"),
    ("plan_leave_fenced_ok", C, "else if (p.leave && fenced) refusal", "else if (false) refusal"),
    ("plan_known_ignored", C, "known.containsKey(a) ? known.get(a) : sha(a, sa)", "sha(a, sa)"),
    ("plan_same_halves", C, 'if (!half(s) || !half(t) || s.equals(t)) throw', "if (!half(s) || !half(t)) throw"),
    ("plan_leave_ignored", C, "leave.contains(n) && !keep.contains(n));", "false);"),
    ("plan_fence_blind", C, "boolean fenced = zeroHead(b, sb);", "boolean fenced = false;"),
    # ---- the run: consent, boot record, second read
    ("run_refusal_ignored", C, 'if (p.refusal != null) return FwResult.stop("plan"', 'if (false) return FwResult.stop("plan"'),
    ("run_no_consent_needed", C, 'if (!consent) return FwResult.stop("consent"', 'if (false) return FwResult.stop("consent"'),
    ("run_hal_ignored", C, "else if (!halves.markUnbootable(ti)) return stop(", "else if (!halves.markUnbootable(ti) && false) return stop("),
    ("run_no_hal", C, "else if (!halves.markUnbootable(ti)) return stop(", "else if (false) return stop("),
    ("run_wrong_half", C, 'int ti = "_a".equals(p.t) ? 0 : 1;', 'int ti = "_a".equals(p.t) ? 1 : 0;'),
    ("run_no_second_read", C, 'if (!again.equals(x.shaS))\n                    return stop(p, real, "source"',
     'if (false)\n                    return stop(p, real, "source"'),
    ("run_resume_unseen", C, 'boolean resumed = prior != null && !prior.contains("\\nstate=verified");', "boolean resumed = false;"),
    ("run_verified_as_resumed", C, 'boolean resumed = prior != null && !prior.contains("\\nstate=verified");', "boolean resumed = prior != null;"),
    # ---- the writes
    ("copy_no_fence", C, "                write(p, x, 0, new byte[HEAD], written);\n", ""),
    ("copy_dry_fences", C, '                if (!real) { say("WOULD: zero the first 4 KiB of " + x.name + p.t); continue; }\n', ""),
    ("copy_dry_copies", C, '                if (!real) { say("WOULD: copy " + x.name + p.s', '                if (false) { say("WOULD: copy " + x.name + p.s'),
    ("copy_head_first", C, ("            md.update(head);\n            for (long off = HEAD;", "            dst.sync();\n            dst.write(0, head, 0, HEAD);\n            dst.sync();\n"),
     ("            md.update(head);\n            dst.write(0, head, 0, HEAD);\n            for (long off = HEAD;", "            dst.sync();\n")),
    ("copy_no_head", C, "            dst.write(0, head, 0, HEAD);\n", ""),
    ("copy_body_unsynced", C, "            dst.sync();\n            dst.write(0, head, 0, HEAD);", "            dst.write(0, head, 0, HEAD);"),
    ("copy_source_change_ok", C, 'if (!got.equals(x.shaS))\n                    return stop(p, real, "source"', 'if (false)\n                    return stop(p, real, "source"'),
    ("copy_no_read_back", C, 'if (!back.equals(x.shaS)) return stop(p, real, "verify"', 'if (false) return stop(p, real, "verify"'),
    ("copy_read_back_source", C, 'String back = sha(x.name + p.t, x.bytes);', 'String back = sha(x.name + p.s, x.bytes);'),
    ("copy_size_unchecked", C, "if (src.size() != x.bytes || dst.size() != x.bytes) throw", "if (false) throw"),
    # not a mutant: written[0] = true in copy() is equivalent (the fence of every part is written before any copy)
    ("write_written_unflagged", C, "        written[0] = true;\n        try (Blocks.Handle h = blocks.open(name, true))", "        try (Blocks.Handle h = blocks.open(name, true))"),
    ("write_unsynced", C, "            h.write(off, data, 0, data.length);\n            h.sync();", "            h.write(off, data, 0, data.length);"),
    ("stop_written_lost", C, "return FwResult.stop(step, reason, written, real, false);", "return FwResult.stop(step, reason, false, real, false);"),
    # ---- the journal
    ("journal_never", C, "        if (!real) return;\n        journal.write(", "        if (true) return;\n        journal.write("),
    ("journal_in_dry_run", C, "        if (!real) return;\n        journal.write(", "        journal.write("),
    ("journal_no_done", C, '+ "\\ndone=" + String.join(",", done)', ""),
    ("journal_verified_early", C, '            mark(p, real, "fenced", done, null);', '            mark(p, real, "verified", done, null);'),
    ("journal_no_rename", J, "if (!tmp.renameTo(f)) throw", "if (false) throw"),
    # ---- verifyEqual
    ("verify_leave_checked", C, "                if (x.leave) continue;\n", ""),
    ("verify_source_unchecked", C, 'if (!a.equals(x.shaS)) return x.name + p.s', 'if (false) return x.name + p.s'),
    ("verify_equal_unchecked", C, 'if (!a.equals(b)) return x.name + " is not the same in both halves";', ""),
    ("verify_io_ok", C, '            return "cannot read the halves: " + e.getMessage();', "            return null;"),
    # ---- reads
    ("zero_head_any", C, "        for (byte x : b) if (x != 0) return false;\n", ""),
    ("sha_size_unchecked", C, 'if (h.size() != n) throw new IOException(name + " is "', 'if (false) throw new IOException(name + " is "'),
    # ---- the plan object
    ("toCopy_keep", P, "if (!p.keep && !p.leave && !p.same()) r.add(p);", "if (!p.leave && !p.same()) r.add(p);"),
    ("toCopy_leave", P, "if (!p.keep && !p.leave && !p.same()) r.add(p);", "if (!p.keep && !p.same()) r.add(p);"),
    ("toCopy_same", P, "if (!p.keep && !p.leave && !p.same()) r.add(p);", "if (!p.keep && !p.leave) r.add(p);"),
    ("id_no_parts", P, "for (Part p : parts) b.append('\\n').append(p.line(s, t));", ""),
    ("id_no_refusal", P, 'b.append("\\nrefusal=").append(refusal == null ? "-" : refusal);', ""),
    # ---- the set
    ("set_vbmeta_copied", F, '"vbmeta", "mmcblk0",', '"mmcblk0",'),
    ("set_calibration_copied", F, '"vendor_boot", "xgimicri", "xgimisps",', '"vendor_boot", "xgimisps",'),
    ("set_tvcustomer_copied", F, '"tvcertificate", "tvcustomer",', '"tvcertificate",'),
    ("set_vendor_copied", F, '"product", "vendor", "odm",', '"product", "odm",'),
    ("set_single_copied", F, "else if (!modelPair) out.add(", "else if (false) out.add("),
    ("set_missing_copied", F, "else if (!unitPair) out.add(", "else if (false) out.add("),
    ("set_keep_copied", F, "else if (m.keepEqual.contains(n)) out.add(", "else if (false) out.add("),
    ("set_keep_uncompared", F, "if (e.verdict == Verdict.COPY || e.verdict == Verdict.KEEP_EQUAL) r.add(e.name);", "if (e.verdict == Verdict.COPY) r.add(e.name);"),
    ("set_update_list_any", F, "return want.equals(list.trim()) ? null :", "return true ? null :"),
    # ---- the model rows
    ("model_hb_mboot_kept", M, 'HB_STOCK, HB_PAIRS, list(""), null,', 'HB_STOCK, HB_PAIRS, list("mboot"), null,'),
    ("model_hb_mboot_in_update", M, 'to 6.17.12 (2026-11-17): mboot_b is the mboot.img of that update", list("mboot"));',
     'to 6.17.12 (2026-11-17): mboot_b is the mboot.img of that update", list(""));'),
    ("set_update_ignores_install_only", F, "        r.removeAll(m.installOnly);\n", ""),
    ("model_z9x_mboot_install_only", M, 'lumendump by-name.txt", list(""));', 'lumendump by-name.txt", list("mboot"));'),
    ("model_x50_keeps_boot", M, 'MT5877_PAIRS,\n            list(""), null, "G0111', 'MT5877_PAIRS,\n            list("boot"), null, "G0111'),
    ("model_rs30_copies", M, '"the RS30 has no install and no updater (trial only): its second half is not a way back"', "null"),
    ("model_play6_copies", M, '"the Play 6 has one half only: there is nothing to copy"', "null"),
    ("model_no_g0148", M, 'list("G0130,G0118,G0148")', 'list("G0130,G0118")'),
    ("model_hb_owl", M, 'list("armfw,boot,bootdata,dtbo,mboot,optee,product,', 'list("armfw,boot,bootdata,dtbo,mboot,optee,owl,product,'),
    ("model_mt5877_no_satf", M, 'list("boot,bootdata,dtbo,mboot,optee,owl,product,satf,', 'list("boot,bootdata,dtbo,mboot,optee,owl,product,'),
]


def run(cmd, cwd):
    p = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, timeout=600)
    return p.returncode, p.stdout


def tree():
    w = tempfile.mkdtemp(prefix="fwmut.", dir=os.environ.get("TMPDIR"))
    shutil.copytree(os.path.join(MOD, "src"), os.path.join(w, "src"))
    shutil.copytree(os.path.join(MOD, "test"), os.path.join(w, "test"))
    return w


def tests(w, jh):
    cls = os.path.join(w, "cls")
    shutil.rmtree(cls, ignore_errors=True)
    os.makedirs(cls)
    srcs = [os.path.join(w, S, f) for f in sorted(os.listdir(os.path.join(w, S))) if f.endswith(".java")]
    srcs += [os.path.join(w, "test", f) for f in sorted(os.listdir(os.path.join(w, "test"))) if f.endswith(".java")]
    rc, out = run([os.path.join(jh, "bin", "javac"), "-nowarn", "-encoding", "UTF-8", "-d", cls] + srcs, w)
    if rc != 0:
        return "compile", out
    rc1, o1 = run([os.path.join(jh, "bin", "java"), "-cp", cls, "org.lumen.fwcopy.FwCopyTests"], w)
    rc2, o2 = run([os.path.join(jh, "bin", "java"), "-Dgsi=" + GSI, "-cp", cls, "org.lumen.fwcopy.FwListsTest"], w)
    return ("pass" if rc1 == 0 and rc2 == 0 else "fail"), o1 + o2


def main():
    jh = os.environ.get("JAVA_HOME") or "/usr/lib/jvm/java-21-openjdk-amd64"
    only = [x for x in os.environ.get("MUT_ONLY", "").split(",") if x]
    w = tree()
    try:
        st, out = tests(w, jh)
        if st != "pass":
            print("the clean tree does not pass (%s):\n%s" % (st, out[-3000:]))
            return 1
        killed, survived, broken = [], [], []
        for m in MUTANTS:
            name, fn, old, new = m
            if only and not any(name.startswith(o) for o in only):
                continue
            path = os.path.join(w, fn)
            orig = open(path, encoding="utf-8").read()
            olds, news = (old, new) if isinstance(old, tuple) else ((old,), (new,))
            src = orig
            bad = None
            for o, n in zip(olds, news):
                if src.count(o) != 1:
                    bad = "anchor %r occurs %d times" % (o[:70], src.count(o))
                    break
                src = src.replace(o, n, 1)
            if bad:
                broken.append(name)
                print("BROKEN  %s: %s" % (name, bad))
                continue
            open(path, "w", encoding="utf-8").write(src)
            try:
                st, out = tests(w, jh)
            finally:
                open(path, "w", encoding="utf-8").write(orig)
            if st == "fail":
                killed.append(name)
                first = next((l for l in out.splitlines() if l.startswith("FAIL")), "")
                print("killed  %s  (%s)" % (name, first[:150]))
            elif st == "compile":
                broken.append(name)
                print("BROKEN  %s: does not compile\n%s" % (name, out[-1500:]))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
        total = len(killed) + len(survived) + len(broken)
        print("lumen-fwcopy mutants: %d killed, %d survived, %d broken of %d" % (len(killed), len(survived), len(broken), total))
        if survived:
            print("SURVIVED: " + ", ".join(survived))
        return 0 if not survived and not broken else 1
    finally:
        shutil.rmtree(w, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
