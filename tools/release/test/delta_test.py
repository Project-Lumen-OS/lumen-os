#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Host test of tools/release/delta.py entry (the manifest's incremental entry) and of its refusals, on synthetic files
(no image, no delta_generator, no key). The real build, sign, finish and apply ran on a release (README "Deltas").
python3 tools/release/test/delta_test.py (a temp dir only)."""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "..", "delta.py")
BASE = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/z9x-1.1.0-beta7/"
passed = failed = 0


def eq(what, want, got):
    global passed, failed
    if want == got:
        passed += 1
    else:
        failed += 1
        print("FAIL %s: want %r, got %r" % (what, want, got))


def run(*args):
    r = subprocess.run([sys.executable, "-B", TOOL] + list(args), capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def kit(t):
    d = os.path.join(t, "d")
    os.makedirs(d)
    payload = b"CrAU" + bytes(range(256)) * 40
    z = os.path.join(d, "lumen-os-1.1.0-beta7-nogms-from-1.1.0-beta4-ota.zip")
    with zipfile.ZipFile(z, "w", zipfile.ZIP_STORED) as zf:
        zf.writestr("payload.bin", payload)
        zf.writestr("payload_properties.txt", "FILE_HASH=x\nFILE_SIZE=%d\nMETADATA_HASH=y\nMETADATA_SIZE=100\n" % len(payload))
    open(os.path.join(d, "lumen-os-1.1.0-beta7-nogms-from-1.1.0-beta4-payload_metadata.bin"), "wb").write(payload[:120])
    json.dump({"version": "1.1.0-beta7", "type": "delta"}, open(os.path.join(d, "ota.json"), "w"))
    json.dump({"tag": "z9x-1.1.0-beta4", "version": "1.1.0-beta4", "version_code": 1010004, "sha256": "c" * 64,
               "build_id": "lumen-1.1.0-beta4-20261124bnp", "image": "x.img", "size": 1},
              open(os.path.join(d, "source.json"), "w"))
    return d, z, payload


def manifest(t, name="m.json", version="1.1.0-beta7", full=True, url=BASE):
    p = os.path.join(t, name)
    pk = [{"type": "full", "url": url + "lumen-os-1.1.0-beta7-nogms-full-ota.zip", "size": 9, "sha256": "a" * 64}] if full else []
    json.dump({"schema": 1, "device": "z9x", "version": version, "version_code": 1010007, "build_id": "b7",
               "packages": pk}, open(p, "w"), indent=1)
    open(p + ".sig", "wb").write(b"old")
    return p


def main():
    with tempfile.TemporaryDirectory() as t:
        d, z, payload = kit(t)
        m = manifest(t)
        rc, out = run("entry", "horse_popular", d, "--manifest", m, "--base-url", BASE)
        eq("entry rc", 0, rc)
        got = json.load(open(m))
        e = got.get("incremental", [{}])[0]
        eq("one entry", 1, len(got.get("incremental", [])))
        eq("packages unchanged", 1, len(got["packages"]))
        eq("from build id", "lumen-1.1.0-beta4-20261124bnp", e.get("from_build_id"))
        eq("from version code", 1010004, e.get("from_version_code"))
        eq("from sha256", "c" * 64, e.get("from_sha256"))
        eq("url", BASE + os.path.basename(z), e.get("url"))
        eq("sha256", hashlib.sha256(open(z, "rb").read()).hexdigest(), e.get("sha256"))
        with open(z, "rb") as f:
            f.seek(e["payload_offset"])
            eq("payload range", payload, f.read(e["payload_size"]))
        eq("metadata", (120, hashlib.sha256(payload[:120]).hexdigest()), (e.get("metadata_size"), e.get("metadata_sha256")))
        eq("properties", 4, len(e.get("payload_properties", [])))
        eq("old signature removed", False, os.path.exists(m + ".sig"))
        eq("indent kept", True, open(m).read().startswith('{\n "'))
        rc, _ = run("entry", "horse_popular", d, "--manifest", m, "--base-url", BASE)
        eq("again: still one entry", (0, 1), (rc, len(json.load(open(m))["incremental"])))
        eq("other release refused", 1, run("entry", "horse_popular", d, "--manifest", manifest(t, "o.json"), "--base-url",
                                             BASE.replace("beta7", "beta8"))[0])
        eq("other version refused", 1, run("entry", "horse_popular", d, "--manifest", manifest(t, "v.json", "1.1.0-beta8"),
                                             "--base-url", BASE)[0])
        eq("no full package refused", 1, run("entry", "horse_popular", d, "--manifest", manifest(t, "f.json", full=False),
                                              "--base-url", BASE)[0])
        eq("http refused", 1, run("entry", "horse_popular", d, "--manifest", manifest(t, "h.json"), "--base-url",
                                  BASE.replace("https", "http"))[0])
        open(os.path.join(d, "lumen-os-1.1.0-beta7-nogms-from-1.1.0-beta4-payload_metadata.bin"), "wb").write(b"x" * 120)
        eq("side-car not the zip's metadata refused", 1,
           run("entry", "horse_popular", d, "--manifest", manifest(t, "s.json"), "--base-url", BASE)[0])
        eq("no delta for a model without update_engine", 1, run("entry", "dragon_healthy", d, "--manifest",
                                                                 manifest(t, "p.json"), "--base-url", BASE)[0])
    print("delta.py passed=%d failed=%d" % (passed, failed))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
