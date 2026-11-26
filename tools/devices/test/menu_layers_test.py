#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""menu_layers_test.py [TREE]: the menu of every model has ONE source (devices/README.md, "Menu profile").

Runs tools/devices/menu_layers.py guard() (assemble_app.py runs it too for every recipe with a menu line, so an app
build fails on it), which is red when
  1. a model's devices/<codename>/menu.json is no layer (no include: its own full copy of the menu),
  2. a layer does not merge (tools/devices/menu_layers.py: a repeated inherited value, off without a reason, a moved
     inherited row, an order of a section that is the shared tree's),
  3. a row of the shared sections (General, For developers, Network) or a module row is added below the base
     (a model or family keeping its own copy of a shared row, the way Remote buttons reached three models only),
  4. two layers on the same parent (two models of a family, two families, Z9X and HB on the base) add the same row,
     hint, key or stock order with the same value (a copy: it belongs in the parent),
  5. a model's menu chain leaves its family chain (device.conf includes),
  6. an app recipe ships menu.json with "file" (the raw layer) instead of "menu" (the merged profile),
  7. a device app handles a module row itself (FeatureId.<ROW> or case <ROW>: in apps/<App>/src, apps/common/projector/src).
Then every mutant of MUTANTS must turn it red. Exit 0 green, 1 red.
"""
import copy
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import layered  # noqa: E402
import menu_layers  # noqa: E402

# ---------------------------------------------------------------- mutants: each one must turn the guard red
def _json_edit(rel, fn):
    def apply(root):
        p = os.path.join(root, rel)
        d = json.load(open(p, encoding="utf-8"))
        fn(d)
        with open(p, "w", encoding="utf-8") as f:
            f.write(menu_layers.dumps(d))
    return apply


def _text_edit(rel, old, new):
    def apply(root):
        p = os.path.join(root, rel)
        t = open(p, encoding="utf-8").read()
        if old not in t:
            raise AssertionError("mutant pattern not found in %s: %r" % (rel, old))
        open(p, "w", encoding="utf-8").write(t.replace(old, new, 1))
    return apply


def _full_copy(c):
    def apply(root):
        p = os.path.join(root, "devices", c, "menu.json")
        t = menu_layers.text(p)
        open(p, "w", encoding="utf-8").write(t)
    return apply


def _move_to_models(row, cs):
    def apply(root):
        _json_edit(menu_layers.BASE, lambda d: d["features"].pop(row))(root)
        for c in cs:
            _json_edit("devices/%s/menu.json" % c, lambda d: d.setdefault("features", {}).update({row: {}}))(root)
    return apply


MUTANTS = [
    ("a model repeats a base row", "repeats the inherited value",
     _json_edit("devices/g0111/menu.json", lambda d: d.setdefault("features", {}).update({"settings.remote": {}}))),
    ("a model carries its own full copy", "has no include", _full_copy("h0062")),
    ("Remote buttons only on Z9X, HB and Play 6 (the 04eece0c drift)", "lives in the base",
     _move_to_models("settings.remote_keys", ("horse_popular", "goat_elegant", "dragon_healthy"))),
    ("two RS30 models carry the same row", "a copy",
     lambda root: [_json_edit("devices/%s/menu.json" % c, lambda d: d.setdefault("features", {}).update(
         {"zoom.digital": {}}))(root) for c in ("g0155", "g0170")]),
    ("a model moves an inherited row", "no reordering",
     _json_edit("devices/g0100/menu.json", lambda d: d.update({"after": {"lamp.off": "settings.remote"}}))),
    ("a model orders the General section itself", "the rest is the shared tree",
     _json_edit("devices/h0061/menu.json", lambda d: d.setdefault("sections", {}).update(
         {"general": {"groups": [{"rows": ["settings.remote_keys", "settings.remote"]}]}}))),
    ("a model switches a row off without a reason", "needs its reason",
     _json_edit("devices/g0170/menu.json", lambda d: d.setdefault("off", {}).update({"settings.remote": " "}))),
    ("a model gives the family's stock order again", "repeats the inherited one",
     lambda root: _json_edit("devices/g0100/menu.json", lambda d: d["sections"]["keystone"].update(
         {"groups": copy.deepcopy(menu_layers.profile(root, "g0111")["sections"]["keystone"]["groups"])}))(root)),
    ("TITAN builds on the X50 Ultra layer", "outside its family chain",
     _json_edit("devices/h0062/menu.json", lambda d: d.update({"include": "devices/_family/x50ultra/x50ultra.menu.json"}))),
    ("a recipe ships the raw layer", "use 'menu SRC DEST'",
     _text_edit("devices/_family/mt9681/app/X50Projector.layers", "menu      {device}/menu.json", "file      {device}/menu.json")),
    ("the X50 pages handle Pair remote again", "handles the module row SETTINGS_REMOTE",
     _text_edit("apps/LumenProjector/host/activity/src/org/lumen/x50/projector/X50MenuPages.java",
                "            case SETTINGS_SLEEP_TIMER: return sleep();",
                "            case SETTINGS_REMOTE: return null;\n            case SETTINGS_SLEEP_TIMER: return sleep();")),
    ("SettingsRows opens Remote buttons again", "handles the module row SETTINGS_REMOTE_KEYS",
     _text_edit("apps/common/projector/src/org/lumen/projector/panel/SettingsRows.java",
                "            case VOICE_KEY: return VoiceKeyActivity.open(app);",
                "            case VOICE_KEY: return VoiceKeyActivity.open(app);\n            case SETTINGS_REMOTE_KEYS: return true;")),
]


def scratch_tree(root):
    """The parts check() reads, copied: devices/, the menu module's tables, the device app sources."""
    t = tempfile.mkdtemp(prefix="menu_layers_test.")
    shutil.copytree(os.path.join(root, "devices"), os.path.join(t, "devices"), symlinks=True,
                    ignore=shutil.ignore_patterns("research", "probe", "test", "certs", "image", "telegram", "*.apk"))
    for rel in (menu_layers.SPEC, menu_layers.FID):
        os.makedirs(os.path.dirname(os.path.join(t, rel)), exist_ok=True)
        shutil.copy(os.path.join(root, rel), os.path.join(t, rel))
    for src in menu_layers.DEVICE_SRC:
        if os.path.isdir(os.path.join(root, src)):
            shutil.copytree(os.path.join(root, src), os.path.join(t, src))
    return t


def main(argv):
    root = os.path.abspath(argv[0] if argv else os.path.join(HERE, "..", "..", ".."))
    errs = menu_layers.guard(root)
    for e in errs:
        print("FAIL " + e)
    if errs:
        return 1
    print("ok   menu layers: %d models, every one a layer on the base, no copies, shared rows in the base"
          % len(menu_layers.models(root)))
    bad = 0
    for name, want, apply in MUTANTS:
        t = scratch_tree(root)
        try:
            apply(t)
            got = menu_layers.guard(t)
        except (AssertionError, KeyError) as e:
            got, bad = [], bad + 1
            print("FAIL mutant %s: cannot apply (%s)" % (name, e))
            continue
        finally:
            shutil.rmtree(t, ignore_errors=True)
        if any(want in g for g in got):
            print("ok   mutant killed: %s" % name)
        else:
            bad += 1
            print("FAIL mutant survived: %s (wanted %r, got %s)" % (name, want, got[:3]))
    print("menu_layers_test: %d of %d mutants killed" % (len(MUTANTS) - bad, len(MUTANTS)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
