#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""check_release_x50_test.py: tools/devices/ota/check_release.py on the X50 family's shared tag x50 (no network).

    python3 -B tools/devices/test/check_release_x50_test.py

A fake GitHub (releases and downloads from a scratch folder) and a throwaway key in place of every certificate (the
audit's signature check is pointed at it; nothing of ~/.lumen-keys is read). The real registry of this tree gives the
devices. Cases:
  good           x50 holds the X50 Ultra's and the Ultra Max's installer files (update-<id>-beta.json, an image and no
                 package) and the Ultra Max's updater beta: the X50 Ultra audit is OK, the sibling's files are only
                 noted, the installer file is checked against its image asset, and the Ultra Max audit is OK as well
  foreign        another asset on x50 is still an error
  stable_closed  update-x50-nogms.json (the updater's stable channel) before T7 is recorded: still refused
  package        an installer file that lists an OTA package: refused
  size           the image asset of another size than the manifest says: refused
  model          the installer file of another model code: refused
  tampered       a byte changed after signing: refused
  no_release     the version has no x50-<version> release: refused
  hb             the HB's installer channel hb-test is an updater channel: it keeps every OTA check (no installer file)
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(G, "tools", "devices", "ota"))
sys.dont_write_bytecode = True
import check_release as cr  # noqa: E402
import ota_conf  # noqa: E402
import verify_manifest as vm  # noqa: E402

V = "1.0.10-beta9"
REPO = "Project-Lumen-OS/lumen-os"


class FakeGh:
    def __init__(self, root, meta):
        self.root, self.meta = root, meta   # meta: {tag: {"prerelease": bool}, "latest": tag}

    def rel(self, t):
        d = os.path.join(self.root, t)
        return {"tag_name": t, "prerelease": self.meta[t]["prerelease"], "draft": self.meta[t].get("draft", False),
                "assets": [{"name": n, "size": os.path.getsize(os.path.join(d, n))} for n in sorted(os.listdir(d))]}

    def latest(self):
        return self.rel(self.meta["latest"])

    def releases(self):
        return [self.rel(t) for t in sorted(self.meta) if t != "latest"]

    def download(self, tag, names, d):
        for n in names:
            shutil.copy(os.path.join(self.root, tag, n), d)


class X50Tag(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.w = tempfile.mkdtemp(prefix="cr-x50-")
        k = os.path.join(cls.w, "key")
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", k + ".pem",
                        "-out", k + ".x509.pem", "-days", "2", "-subj", "/CN=check_release test"],
                       check=True, capture_output=True)
        cls.key, cls.cert = k + ".pem", k + ".x509.pem"
        cls.orig = vm.signature_ok
        vm.signature_ok = lambda mf, sig, cert: cls.orig(mf, sig, cls.cert)
        cls.devs = {c: ota_conf.device(G, c, publish=True) for c in ("g0111", "g0100", "goat_elegant")}

    @classmethod
    def tearDownClass(cls):
        vm.signature_ok = cls.orig
        shutil.rmtree(cls.w, ignore_errors=True)

    def sign(self, path):
        subprocess.run(["openssl", "dgst", "-sha256", "-sign", self.key, "-out", path + ".sig", path], check=True)

    def put(self, tag, name, data):
        d = os.path.join(self.gf, tag)
        os.makedirs(d, exist_ok=True)
        p = os.path.join(d, name)
        open(p, "wb" if isinstance(data, bytes) else "w").write(data)
        return p

    def installer_file(self, wid, model, **over):
        img = "lumen-os-%s-%s-nogms-system.img" % (wid, V)
        m = {"schema": 1, "device": wid, "channel": wid + "-beta", "version": V, "version_code": 1001009,
             "build_id": "lumen-%s-%s-1" % (wid, V), "requires": {}, "model": model, "trial_only": False,
             "image": {"name": img, "size": 7, "sha256": "0" * 64,
                       "url": "https://github.com/%s/releases/download/%s-%s/%s" % (REPO, wid, V, img)},
             "changelog": {"en": "x"}, "packages": []}
        m.update(over)
        p = self.put("x50", "update-%s-beta.json" % wid, json.dumps(m, indent=1))
        self.sign(p)
        self.put("%s-%s" % (wid, V), img, b"0123456")

    def setUp(self):
        self.gf = tempfile.mkdtemp(prefix="gh-", dir=self.w)
        p = self.put("v1.0.7", "update-public-nogms.json", json.dumps({"device": "z9x", "channel": "public-nogms",
                                                                        "version": "1.0.7"}))
        self.sign(p)
        self.installer_file("x50", "G0111")
        self.installer_file("x50max", "G0100")
        for n in ("update-x50max-test.json", "update-x50max-test.json.sig"):
            self.put("x50", n, "sibling file, audited by the Ultra Max run")
        self.meta = {"latest": "v1.0.7", "v1.0.7": {"prerelease": False}, "x50": {"prerelease": True},
                     "x50-" + V: {"prerelease": True}, "x50max-" + V: {"prerelease": True}}

    def run_audit(self, c="g0111"):
        errs, notes = [], []
        cr.audit(self.devs[c], FakeGh(self.gf, self.meta), errs, notes)
        return errs, notes

    def refused(self, text, c="g0111"):
        errs, _ = self.run_audit(c)
        self.assertTrue(any(text in e for e in errs), "%r not in %r" % (text, errs))

    def test_good(self):
        errs, notes = self.run_audit()
        self.assertEqual(errs, [])
        self.assertTrue(any("update-x50max-test.json belongs to XGIMI X50 Ultra Max" in n for n in notes), notes)
        self.assertTrue(any("the installer's x50-beta %s" % V in n and "no T7 gate" in n for n in notes), notes)
        os.remove(os.path.join(self.gf, "x50", "update-x50max-test.json"))
        os.remove(os.path.join(self.gf, "x50", "update-x50max-test.json.sig"))
        errs, notes = self.run_audit("g0100")
        self.assertEqual(errs, [])
        self.assertTrue(any("the installer's x50max-beta" in n for n in notes), notes)

    def test_foreign(self):
        self.put("x50", "notes.txt", "x")
        self.refused("x50: asset notes.txt is not update-<channel>.json(.sig)")

    def test_stable_closed(self):
        self.sign(self.put("x50", "update-x50-nogms.json", "{}"))
        self.refused("update-x50-nogms.json (or its .sig) is on the tag: channel x50-nogms is public")

    def test_package(self):
        self.installer_file("x50", "G0111", packages=[{"type": "full"}])
        self.refused("lists 1 OTA package(s)")

    def test_size(self):
        self.put("x50-" + V, "lumen-os-x50-%s-nogms-system.img" % V, b"01234567")
        self.refused("is 8 bytes on x50-%s, the manifest says 7" % V)

    def test_model(self):
        self.installer_file("x50", "G0100")
        self.refused("model 'G0100' is not one of G0111")

    def test_tampered(self):
        p = os.path.join(self.gf, "x50", "update-x50-beta.json")
        open(p, "a").write(" ")
        self.refused("x50/update-x50-beta.json does not verify")

    def test_no_release(self):
        del self.meta["x50-" + V]
        shutil.rmtree(os.path.join(self.gf, "x50-" + V))
        self.refused("version %s has no release x50-%s" % (V, V))

    def test_hb(self):
        self.assertIsNone(cr.installer_channel(self.devs["goat_elegant"]))
        self.assertEqual(cr.installer_channel(self.devs["g0111"]), "x50-beta")
        self.assertEqual(sorted(cr.siblings(self.devs["g0111"])), ["x50max-beta", "x50max-nogms", "x50max-test"])
        self.assertEqual(cr.siblings(self.devs["goat_elegant"]), {})


if __name__ == "__main__":
    unittest.main(verbosity=1)
