#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""The per-model table of the phone remote (apps/core/lumen-control, org.lumen.control.vcontrol.VcontrolModels): what
the stock XGIMI phone remote (com.xgimi.vcontrol) of each model told the phone, from devices/_control/vcontrol.tsv
plus two registry facts: the stock Build.DEVICE (the entry's family, tools/devices/devices.py) and autofocus
("focus.af_now" in devices/<codename>/menu.json).
    python3 tools/control/gen_vcontrol.py --check     exit 1 when VcontrolModels.java is not the table's or a
                                                      projector app's wiring is off (wiring())
    python3 tools/control/gen_vcontrol.py --write     write it
"""
import json
import os
import sys

GSI = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TSV = os.path.join("devices", "_control", "vcontrol.tsv")
OUT = os.path.join("apps", "core", "lumen-control", "src", "org", "lumen", "control", "vcontrol", "VcontrolModels.java")
sys.path.insert(0, os.path.join(GSI, "tools", "devices"))
import devices  # noqa: E402

COLUMNS = ["model", "code", "name", "source"]


def rows(root=GSI):
    """[(model, code or None, name, source)] in file order; refuses a bad header, a bad code or a duplicate model."""
    out, seen, header = [], set(), False
    for n, line in enumerate(open(os.path.join(root, TSV), encoding="utf-8"), 1):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        f = line.split("\t")
        if not header:
            if f != COLUMNS:
                raise SystemExit("gen_vcontrol: %s:%d header must be %s" % (TSV, n, "\t".join(COLUMNS)))
            header = True
            continue
        if len(f) != len(COLUMNS) or not all(f):
            raise SystemExit("gen_vcontrol: %s:%d needs %d non-empty columns" % (TSV, n, len(COLUMNS)))
        model, code, name, source = f
        if model in seen:
            raise SystemExit("gen_vcontrol: %s:%d model %s twice" % (TSV, n, model))
        seen.add(model)
        if code != "-" and not (code.isdigit() and name.startswith("v")):
            raise SystemExit("gen_vcontrol: %s:%d code must be a number with a v-name, or -" % (TSV, n))
        out.append((model, None if code == "-" else int(code), name, source))
    return out


def autofocus(root, codename):
    path = os.path.join(root, "devices", codename, "menu.json")
    if not os.path.isfile(path):
        return False
    return "focus.af_now" in (json.load(open(path, encoding="utf-8")).get("features") or {})


def table(root=GSI):
    """[(model, device, code, name, autofocus)]: one per registry model, refused when a model has no row."""
    reg = {}
    for code, d in sorted(devices.load(root).items()):
        for m in d["models"]:
            reg[m] = (devices.family(d), autofocus(root, code))
    have = rows(root)
    extra = [r[0] for r in have if r[0] not in reg]
    if extra:
        raise SystemExit("gen_vcontrol: %s names models the registry does not have: %s" % (TSV, " ".join(extra)))
    missing = sorted(set(reg) - {r[0] for r in have})
    if missing:
        raise SystemExit("gen_vcontrol: registry models without a row in %s: %s" % (TSV, " ".join(missing)))
    return [(m, reg[m][0], code, name, reg[m][1]) for m, code, name, _ in sorted(have)]


def wiring(root=GSI):
    """Errors of the projector apps' wiring: a model with a stock phone remote has exactly one app recipe with
    `module lumen-control`, that app starts PhoneRemote and asks INTERNET and INJECT_EVENTS; a model without one
    carries no such recipe (hide, never fake)."""
    served = {m for m, code, _, _ in rows(root) if code is not None}
    errs = []
    for codename, d in sorted(devices.load(root).items()):
        want = any(m in served for m in d["models"])
        recipes = []
        app_dir = os.path.join(root, "devices", codename, "app")
        for name in sorted(os.listdir(app_dir)) if os.path.isdir(app_dir) else []:
            if not name.endswith(".layers"):
                continue
            words = [ln.split() for ln in open(os.path.join(app_dir, name), encoding="utf-8")]
            if ["module", "lumen-control"] in words:
                recipes.append((name, [w[1] for w in words if len(w) == 2 and w[0] == "top"]))
        if not want:
            if recipes:
                errs.append("%s: no stock phone remote on %s but %s takes lumen-control"
                            % (codename, " ".join(d["models"]), recipes[0][0]))
            continue
        if len(recipes) != 1:
            errs.append("%s: %d app recipes take lumen-control, want 1" % (codename, len(recipes)))
            continue
        name, tops = recipes[0]
        if len(tops) != 1:
            errs.append("%s: %s has no single top app" % (codename, name))
            continue
        top = os.path.join(root, tops[0])
        manifest = open(os.path.join(top, "AndroidManifest.xml"), encoding="utf-8").read()
        for perm in ("INTERNET", "INJECT_EVENTS"):
            if '<uses-permission android:name="android.permission.%s" />' % perm not in manifest:
                errs.append("%s: %s/AndroidManifest.xml does not ask %s" % (codename, tops[0], perm))
        starts = 0
        for base, _, files in os.walk(os.path.join(top, "src")):
            for f in files:
                if f.endswith(".java"):
                    starts += open(os.path.join(base, f), encoding="utf-8").read().count(
                        "org.lumen.control.android.PhoneRemote.start(")
        if starts != 1:
            errs.append("%s: %s starts PhoneRemote %d times, want 1" % (codename, tops[0], starts))
    return errs


def java(t):
    def row(m, device, code, name, af):
        if code is None:
            return '            case "%s": return null;\n' % m
        return '            case "%s": return new VcontrolModels("%s", %d, "%s", %s);\n' % (
            m, device, code, name, "true" if af else "false")
    rows_ = "".join(row(*r) for r in t)
    return ("// SPDX-License-Identifier: Apache-2.0\n"
            "// Generated by tools/control/gen_vcontrol.py from devices/_control/vcontrol.tsv and the device registry: do not edit.\n"
            "package org.lumen.control.vcontrol;\n\n"
            "/** What the stock phone remote (com.xgimi.vcontrol) of one XGIMI model told the phone. */\n"
            "public final class VcontrolModels {\n"
            "    /** Stock Build.DEVICE, sent as \"device\". */\n"
            "    public final String device;\n"
            "    /** Stock vcontrol versionCode, sent as \"versioncode\" in the connect reply. */\n"
            "    public final int versionCode;\n"
            "    public final String versionName;\n"
            "    /** The model has autofocus (its menu profile has focus.af_now). */\n"
            "    public final boolean autofocus;\n\n"
            "    VcontrolModels(String device, int versionCode, String versionName, boolean autofocus) {\n"
            "        this.device = device;\n"
            "        this.versionCode = versionCode;\n"
            "        this.versionName = versionName;\n"
            "        this.autofocus = autofocus;\n"
            "    }\n\n"
            "    /** The row of an XGIMI model code; null when its stock has no phone remote or Lumen does not know it. */\n"
            "    public static VcontrolModels of(String xgimiModel) {\n"
            "        switch (xgimiModel == null ? \"\" : xgimiModel.trim()) {\n"
            + rows_ +
            "            default: return null;\n"
            "        }\n"
            "    }\n"
            "}\n")


def main(argv):
    if len(argv) != 2 or argv[1] not in ("--check", "--write"):
        sys.stderr.write(__doc__)
        return 2
    want = java(table())
    path = os.path.join(GSI, OUT)
    have = open(path, encoding="utf-8").read() if os.path.isfile(path) else None
    if argv[1] == "--write":
        if have != want:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w", encoding="utf-8").write(want)
        print("gen_vcontrol: %s written" % OUT)
        return 0
    if have != want:
        print("gen_vcontrol: %s is not the table's: python3 tools/control/gen_vcontrol.py --write" % OUT)
        return 1
    errs = wiring()
    for e in errs:
        print("gen_vcontrol: wiring: " + e)
    if errs:
        return 1
    print("gen_vcontrol: %s matches %s, every projector app with a stock phone remote starts it" % (OUT, TSV))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
