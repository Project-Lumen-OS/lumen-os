#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of lumen-files-access (policy, Android side, wiring): each one must turn test/run.sh red.
python3 apps/core/lumen-files-access/test/mutate.py   (a copy of the tree per mutant, nothing changes in it)"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.normpath(os.path.join(HERE, "..", "..", "..", ".."))
P = "apps/core/lumen-files-access/src/org/lumen/filesaccess/"
COPY = ["apps/core/lumen-files-access", "apps/LumenProjector/core/AndroidManifest.xml", 
        "apps/LumenProjector/host/activity/AndroidManifest.xml", "devices", "tools/devices"]
MUTANTS = [
    ("runtime grant over a fixed choice", P + "FilesAccessPolicy.java", "        return !fixed && shouldGrant(", "        return shouldGrant("),
    ("runtime grant without the key", P + "FilesAccessPolicy.java",
     "        return !fixed && shouldGrant(pkg, signerSha256, requested, granted, stamp, handledStamp);",
     "        return !fixed && requested && !granted;"),
    ("audio not in the table", P + "FilesAccessPolicy.java", '        "android.permission.READ_MEDIA_AUDIO",\n', ""),
    ("video not in the table", P + "FilesAccessPolicy.java", '        "android.permission.READ_MEDIA_VIDEO",\n', ""),
    ("runtime shares the app op key", P + "FilesAccessPolicy.java", "        return OP.equals(op) ? pkg : pkg + \"|\" + op;", "        return pkg;"),
    ("runtime loop not run", P + "FilesAccess.java", "            for (String perm : FilesAccessPolicy.runtimeGrants()) {",
     "            for (String perm : new String[0]) {"),
    ("runtime bypasses the policy", P + "FilesAccess.java",
     "                if (!FilesAccessPolicy.shouldGrantRuntime(pkg, signers, requested, granted, fixed, pi.lastUpdateTime, handled)) {",
     "                if (granted) {"),
    ("X50 manifest without the permission", "apps/LumenProjector/host/activity/AndroidManifest.xml",
     '    <uses-permission android:name="android.permission.GRANT_RUNTIME_PERMISSIONS" />\n', ""),
]


def one(m):
    name, rel, old, new = m
    tmp = tempfile.mkdtemp(prefix="fa_mut.", dir=os.environ.get("TMPDIR"))
    try:
        for c in COPY:
            s, d = os.path.join(G, c), os.path.join(tmp, c)
            os.makedirs(os.path.dirname(d), exist_ok=True)
            (shutil.copytree if os.path.isdir(s) else shutil.copy2)(s, d)
        p = os.path.join(tmp, rel)
        t = open(p, encoding="utf-8").read()
        if t.count(old) != 1:
            return name, "BROKEN (anchor found %d times)" % t.count(old)
        open(p, "w", encoding="utf-8").write(t.replace(old, new))
        r = subprocess.run(["sh", os.path.join(tmp, "apps/core/lumen-files-access/test/run.sh")], capture_output=True, text=True)
        return name, "killed" if r.returncode != 0 else "SURVIVED"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    res = [one(m) for m in MUTANTS]
    for n, r in res:
        print("%-40s %s" % (n, r))
    k = sum(r == "killed" for _, r in res)
    print("lumen-files-access mutants: %d/%d killed" % (k, len(res)))
    sys.exit(0 if k == len(res) else 1)


if __name__ == "__main__":
    main()
