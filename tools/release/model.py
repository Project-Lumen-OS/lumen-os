#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""model.py: the release facts of one model, read from the registry (devices/README.md), for the shared release kit
(tools/release/README.md). No model is named here: a new model is a new devices/<codename> and no change in this kit.

    python3 tools/release/model.py CODENAME [VERSION]          one KEY=value line each (shell: eval "$(...)")
    python3 tools/release/model.py CODENAME VERSION --json     the same as JSON

Keys: CODENAME MODEL NAME PREFIX KIND (ota: installs and OTA, trial: trial image only) KEYS (the model's key folder, Mac
only) CERT (its public OTA certificate) SIBLING_CERT (the OTA certificate of another model of its family: a signature
that verifies with it is a wrong-key build) REPO TAG (and TITLE, both from naming.py) ASSETS (the release assets in upload order) IMAGE (the image
asset) and for KIND=ota: WIRE MANIFEST_TAG CHANNELS (the installer and updater channel files with their .sig) VCODE.
"""
import json
import os
import re
import sys

G = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path[:0] = [os.path.join(G, "tools", "devices"), os.path.join(G, "tools", "devices", "ota")]
import devices  # noqa: E402
import layered  # noqa: E402
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import naming  # noqa: E402

REPO = "Project-Lumen-OS/lumen-os"


def version_code(v):
    """x.y.z[-betaN] -> the O1 code of the release (1.0.10-beta12 = 1001012, the published X50 manifests' value)."""
    m = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(?:-(?:beta|rc)(\d+))?", v)
    if not m:
        raise ValueError("version %r is not x.y.z or x.y.z-betaN" % v)
    a, b, c, n = (int(x or 0) for x in m.groups())
    return a * 1000000 + b * 10000 + c * 100 + n


def facts(codename, version=None):
    d = devices.get(G, codename)
    bconf = layered.effective(os.path.join(G, "devices", codename, "build.conf"))
    prefix = re.sub(r"\{([A-Z][A-Z0-9_]*)\}", lambda m: bconf.get("var." + m.group(1), m.group(0)), bconf["tag"].split("-{v}")[0])
    kind = "trial" if d.get("ota_engine", "none") == "none" else "ota"
    fam = layered.files(os.path.join(G, "devices", codename, "device.conf"))[1:]
    # the sibling: another model of the nearest family file that has an OTA certificate (a model alone in its firmware
    # line file, the RS 20 G0087, takes one of the next family up, never a model without a certificate)
    def has_cert(c):
        return os.path.isfile(os.path.join(G, "devices", c, "certs", "ota.x509.pem"))
    sib = None
    for c in sorted(devices.load(G)):
        if c != codename and fam and layered.files(os.path.join(G, "devices", c, "device.conf"))[1:2] == fam[:1] and has_cert(c):
            sib = c
            break
    for f in fam[1:]:
        if sib is not None:
            break
        for c in sorted(devices.load(G)):
            if c != codename and f in layered.files(os.path.join(G, "devices", c, "device.conf"))[1:] and has_cert(c):
                sib = c
                break
    if sib is None:   # as before: the first model of the vendor file (no certificate yet: sign.sh names it)
        for c in sorted(devices.load(G)):
            if c != codename and fam and fam[-1] in layered.files(os.path.join(G, "devices", c, "device.conf")):
                sib = c
                break
    out = {"CODENAME": codename, "MODEL": d["models"][0], "NAME": re.sub(r"^XGIMI ", "", d["name"]), "PREFIX": prefix,
           "KIND": kind, "KEYS": os.path.expanduser(d["keys_dir"]), "REPO": REPO,
           "CERT": os.path.join(G, "devices", codename, "certs", "ota.x509.pem"),
           "SIBLING_CERT": os.path.join(G, "devices", sib, "certs", "ota.x509.pem") if sib else ""}
    if kind == "ota":
        import ota_conf
        oc = ota_conf.device(G, codename)["ota"]
        out.update({"WIRE": oc["wire_id"], "MANIFEST_TAG": oc["manifest_tag"]})
    if version:
        out["TAG"] = naming.tag(codename, version)
        out["TITLE"] = naming.title(codename, version)
        if kind == "ota":
            base = "lumen-os-%s-%s-nogms" % (prefix, version)
            out["IMAGE"] = base + "-system.img"
            out["ASSETS"] = " ".join(("SHA256SUMS", "SHA256SUMS.sig", base + "-full-payload_metadata.bin",
                                      base + "-full-ota.zip", out["IMAGE"]))
            out["CHANNELS"] = " ".join("update-%s-%s.json%s" % (out["WIRE"], ch, s) for ch in ("beta", "test") for s in ("", ".sig"))
            out["VCODE"] = str(version_code(version))
        else:
            out["IMAGE"] = "system_dsu.img"
            out["ASSETS"] = "SHA256SUMS SHA256SUMS.sig trial.sh system_dsu.img"
    return out


def main(argv):
    if not argv or argv[0].startswith("-"):
        print(__doc__, file=sys.stderr)
        return 2
    f = facts(argv[0], argv[1] if len(argv) > 1 and not argv[1].startswith("-") else None)
    if "--json" in argv:
        print(json.dumps(f, indent=1, sort_keys=True))
    else:
        for k, v in f.items():
            print("%s='%s'" % (k, v.replace("'", "'\\''")))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
