#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutants of the shared statistics client that test/run.sh must kill. Mutates the files IN PLACE and restores them:
run it on a frozen snapshot (a builder clone or a detached worktree), never on a tree someone edits.
    python3 apps/core/lumen-stats/test/mutate.py"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = os.path.join(HERE, "src", "org", "lumen", "stats")
MUTANTS = [
    ("Ping.java", "channel from the version", 'PRERELEASE.matcher(version).find() ? "beta" : "stable"', '"stable"'),
    ("Ping.java", "rc is a pre-release", 'Pattern.compile("-(beta|rc)[0-9]*$")', 'Pattern.compile("-(beta)[0-9]*$")'),
    ("Ping.java", "trial in the body", '",\\"trial\\":" + trial', '",\\"trial\\":" + false'),
    ("Ping.java", "body version", '"{\\"v\\":" + VERSION', '"{\\"v\\":" + 2'),
    ("Ping.java", "first ping", 'if (lastVc == 0) return new String[] {"", "0", "first"};', ''),
    ("Ping.java", "installer when unknown", 'known ? method : "installer"', 'method'),
    ("Ping.java", "final = 99", 'm.group(4) == null ? 99 :', 'm.group(4) == null ? 0 :'),
    ("Ping.java", "hold blocks", "return configured && on && setupDone && !hold;", "return configured && on && setupDone;"),
    ("Ping.java", "retry wait", "&& sinceTryMs >= RETRY_MS", ""),
    ("Ping.java", "new day", "(updated || newBoot || newDay)", "(updated || newBoot)"),
    ("Device.java", "registry first", "if (!c.isEmpty()) return c;", ""),
    ("Device.java", "image device before vendor", "return lumenDevice.isEmpty() ? vendorDevice : lumenDevice;", "return vendorDevice;"),
    ("Device.java", "own version code", 'parse(prop("ro.lumen.version_code"), Ping.versionCode(v))', 'parse(prop("ro.z9x.version_code"), Ping.versionCode(v))'),
    ("Device.java", "trial prop", '"1".equals(prop("ro.gsid.image_running"))', 'false'),
    ("Models.java", "registry row", 'case "G0111": return "g0111";', 'case "G0111": return "horse_popular";'),
]


def run():
    return subprocess.run(["sh", os.path.join(HERE, "test", "run.sh")], capture_output=True, text=True)


def main():
    files = {f: open(os.path.join(S, f)).read() for f in {m[0] for m in MUTANTS}}
    bad = [n for f, n, a, _ in MUTANTS if files[f].count(a) != 1]
    if bad:
        print("BROKEN anchors: %s" % ", ".join(bad))
        return 2
    r = run()
    if r.returncode != 0:
        print("FAIL unmutated:\n" + r.stdout[-800:] + r.stderr[-800:])
        return 1
    alive = []
    try:
        for f, n, a, b in MUTANTS:
            open(os.path.join(S, f), "w").write(files[f].replace(a, b, 1))
            r = run()
            open(os.path.join(S, f), "w").write(files[f])
            print("%-28s %s" % (n, "killed" if r.returncode else "ALIVE"))
            if r.returncode == 0:
                alive.append(n)
    finally:
        for f, s in files.items():
            open(os.path.join(S, f), "w").write(s)
    print("mutants: %d killed, %d alive" % (len(MUTANTS) - len(alive), len(alive)))
    return 1 if alive else 0


if __name__ == "__main__":
    sys.exit(main())
