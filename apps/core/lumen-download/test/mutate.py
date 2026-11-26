#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""mutate.py: targeted mutants of the shared download (apps/core/lumen-download). Each mutant changes one line, runs
the module's test (and the apps' download tests that own the line) and must make it fail; the file is restored after
every mutant, also on an error. Run from the tree root:

  python3 apps/core/lumen-download/test/mutate.py [NAME...]

Prints KILLED / ALIVE per mutant and exits 1 when one stays alive or a mutation no longer applies. The apps' wiring
has its own mutants: apps/core/lumen-updater/test/mutate.py, apps/LumenInstaller/test/mutate.py.
"""
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
SRC = "apps/core/lumen-download/src/org/lumen/download/"
T_RUN = "sh apps/core/lumen-download/test/run.sh"

# name, file, old, new, tests (any failing test kills it): ranges never overlap, links renewed on 403, If-Range guards
# a changed file, resume, the fallback to one connection, the scaler, the speed window, the takeover of a slow end
MUTANTS = [
    ("dl_reserve_past_cut", SRC + "Segments.java", "s.reserved = Math.max(0, Math.min(n, s.end - s.pos));",
     "s.reserved = Math.max(0, n);", [T_RUN]),
    ("dl_split_whole", SRC + "Segments.java", "if (free(big) >= 2 * minSplit) return cut(",
     "if (free(big) < 0) return cut(", [T_RUN]),
    ("dl_no_takeover", SRC + "Segments.java", "return slow == null ? null : cut(slow, slow.pos + slow.reserved, nowMs);",
     "return null;", [T_RUN]),
    ("dl_takeover_in_flight", SRC + "Segments.java", "cut(slow, slow.pos + slow.reserved, nowMs)",
     "cut(slow, slow.pos, nowMs)", [T_RUN]),
    ("dl_takeover_any_speed", SRC + "Segments.java", "            if (own * 2 >= bps) continue;\n", "", [T_RUN]),
    ("dl_takeover_new", SRC + "Segments.java", "free(s) <= 0 || age < GRACE_MS", "free(s) <= 0", [T_RUN]),
    ("dl_takeover_ends_soon", SRC + "Segments.java", "double late = STEAL_MS;", "double late = 0;", [T_RUN]),
    ("dl_speed_not_counted", SRC + "Segments.java", "        s.got += k;\n", "", [T_RUN]),
    ("dl_idle_leaves", SRC + "ParallelDownload.java", "if (!ranges || segs.complete()) return;", "return;", [T_RUN]),
    ("dl_own_speed_unknown", SRC + "ParallelDownload.java", "busy == 0 ? 0 : bytes * 1000 / busy", "0", [T_RUN]),
    ("dl_no_renew", SRC + "ParallelDownload.java", "                url = pr.url;\n", "", [T_RUN]),
    ("dl_renew_waits", SRC + "ParallelDownload.java", "pause(e instanceof Renewed && fails == 1 ? 0 :",
     "pause(e instanceof Renewed && fails == 99 ? 0 :", [T_RUN]),
    ("dl_no_if_range", SRC + "ParallelDownload.java",
     'if (!etag.isEmpty() && !etag.startsWith("W/")) c.setRequestProperty("If-Range", etag);', "", [T_RUN]),
    ("dl_no_direct_probe", SRC + "ParallelDownload.java", "return direct.ranges ? direct : p;", "return p;", [T_RUN]),
    ("dl_weak_if_range", SRC + "ParallelDownload.java", '!etag.isEmpty() && !etag.startsWith("W/")',
     "!etag.isEmpty()", [T_RUN]),
    ("dl_ignore_range_flag", SRC + "ParallelDownload.java", "ranges &= pr.ranges;", "", [T_RUN]),
    ("dl_no_resume", SRC + "ParallelDownload.java", "Segments segs = saved != null && part.isFile()",
     "Segments segs = saved != null && !part.isFile()", [T_RUN]),
    ("dl_any_package", SRC + "ParallelDownload.java", "t != total || key != null && !key.equals(k)",
     "t != total", [T_RUN]),
    ("dl_no_legacy", SRC + "ParallelDownload.java", "if (dst.length() < total && dst.length() > 0 && dst.renameTo(part))",
     "if (dst.length() < 0 && dst.renameTo(part))", [T_RUN]),
    ("dl_one_connection", SRC + "ParallelDownload.java",
     "new ConnectionScaler(ranges ? tune.start : 1, ranges ? tune.max : 1, GAIN)", "new ConnectionScaler(1, 1, GAIN)",
     [T_RUN]),
    ("dl_no_growth", SRC + "ConnectionScaler.java", "if (before < 0 || speed >= before * gain)", "if (false)", [T_RUN]),
    ("dl_no_warmup", SRC + "ConnectionScaler.java", "if (warmup || !clean || speed <= 0) {",
     "if (!clean || speed <= 0) {", [T_RUN]),
    ("dl_failed_window_counts", SRC + "ConnectionScaler.java", "if (warmup || !clean || speed <= 0) {",
     "if (warmup || speed <= 0) {", [T_RUN]),
    ("dl_empty_window_counts", SRC + "ConnectionScaler.java", "if (warmup || !clean || speed <= 0) {",
     "if (warmup || !clean) {", [T_RUN]),
    ("dl_rate_whole", SRC + "Rate.java", "nowMs - samples.peekFirst()[0] > windowMs", "false", [T_RUN]),
]


def run(cmd):
    r = subprocess.run(cmd, shell=True, cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return r.returncode


def main(names):
    alive, broken, killed = [], [], 0
    for name, path, old, new, tests in MUTANTS:
        if names and name not in names:
            continue
        p = os.path.join(ROOT, path)
        src = open(p, encoding="utf-8").read()
        if src.count(old) != 1:
            print("BROKEN %s: %s does not hold the line once" % (name, path))
            broken.append(name)
            continue
        try:
            open(p, "w", encoding="utf-8").write(src.replace(old, new))
            by = next((t for t in tests if run(t) != 0), None)
        finally:
            open(p, "w", encoding="utf-8").write(src)
        if by:
            killed += 1
            print("KILLED %s by %s" % (name, by.split()[1]))
        else:
            alive.append(name)
            print("ALIVE  %s" % name)
        sys.stdout.flush()
    print("download mutants killed=%d alive=%d broken=%d" % (killed, len(alive), len(broken)))
    return 1 if alive or broken else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
