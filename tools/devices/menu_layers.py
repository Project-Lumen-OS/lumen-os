#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""menu_layers.py: the ONE menu profile of every model, built from layers (devices/README.md, "Menu profile").

The menu tree (sections, rows, order, wording, icons) is the shared module's (apps/core/lumen-menu MenuSpec, strings,
lumen-icons). What a model shows of it is its effective profile, assets/lumen_menu/menu.json in the app. That file is
built here from layers, the widest first:

    devices/_menu/base.menu.json                every row every model shows the same way (General, developer, network,
                                                the tiles, the menu's own settings) with the shared values
    devices/_family/<family>/<family>.menu.json what a family adds or switches off (stock orders, game menu, rows)
    devices/<codename>/menu.json                the model: only what differs from its family

A layer other than the base names the layer it builds on in its first key: {"include": "devices/_family/x50/x50.menu.json", ...}.
Merge, layer by layer:
    features   a row the layer gives replaces the inherited value in place or is added (a capability of this model;
               where it shows is its one place in the shared tree, MenuSpec SECTIONS); "after": {"row": "anchor"}
               puts an added row right after the named anchor row in the file (inherited rows never move);
               "off": {"row": "why"} switches an inherited row off with the reason (and drops its hint)
    hints, keys, game_menu   key by key, null removes an inherited key
    sections   a stock order replaces the inherited order of that section as a whole (keystone and picture only)
    other keys (schema, device, model, adapter, note, pending)   replaced
Errors (each one fails the build): an include that is not the first key or is missing, a cycle, a layer repeating the
inherited value (a copy), "off" of a row that is not inherited or without a reason, a stock order of another section.
The guard of the whole tree (rows two models carry, shared rows outside the base) is tools/devices/test/menu_layers_test.py.

Every reader of a menu profile goes through this file:

    python3 menu_layers.py FILE              the effective profile of FILE (devices/<codename>/menu.json), JSON
    python3 menu_layers.py --files FILE      FILE and every layer it builds on, one path per line (what a build stages)
    python3 menu_layers.py --all ROOT OUT    OUT/<codename>/menu.json for every model of the tree ROOT
    python3 menu_layers.py --models ROOT     the codenames with a menu profile
    python3 menu_layers.py --guard ROOT      exit 1 when the tree keeps a copy of the menu (rules: guard())
    python3 menu_layers.py --view ROOT OUT   OUT: ROOT as links, every devices/<codename>/menu.json the effective one
                                             (test runners whose tests open the profile files themselves)

Python: menu_layers.effective(path) (dict), text(path), files(path), models(root), profile(root, codename).
An include is a path relative to the tree (gsi/); in a flat folder of staged inputs it is the file of the same name
next to FILE (tools/devices/layered.py locate).
"""
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import layered  # noqa: E402

INCLUDE = "include"
OFF = "off"
BASE = "devices/_menu/base.menu.json"
PROFILE = "menu.json"
# the keys of the effective profile, in this order (MenuProfile TOP)
ORDER = ("schema", "device", "model", "adapter", "note", "features", "hints", "keys", "game_menu", "sections", "pending")
KEYED = ("hints", "keys", "game_menu")        # merged key by key, null removes
KEY_ORDER = {"keys": ("gear_short", "gear_long", "eye_gear_short"),
             "game_menu": ("enabled", "gated", "items", "sound", "themes", "cec_detect", "vrr_top_speed", "note")}
REPLACED = ("schema", "device", "model", "adapter", "note", "pending")
# the sections whose row order a layer may give (its model's stock tree); every other order is the shared tree's
STOCK_ORDER = ("keystone", "picture")
SECTION_PARTS = ("stock", "groups")
MODEL_ONLY = ("device", "model")              # only in devices/<codename>/menu.json
# base only: the rows of the shared sections (General, For developers, Network) that stand on a model's own hardware,
# so a family or model may add them; every other row of those sections lives in the base (menu_layers_test.py)
MODEL_ROWS = "model_rows"
# {"new.row": "anchor.row"}: where a row a layer adds stands among the inherited rows (else at the end)
AFTER = "after"
VIEW_MARK = ".menu_view"                       # a --view tree: its profiles are merged already


class LayerError(Exception):
    pass


def read(path):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    try:
        return json.loads(text, object_pairs_hook=_pairs(path))
    except ValueError as e:
        raise LayerError("%s: %s" % (path, e))


def _pairs(path):
    def hook(pairs):
        out = {}
        for k, v in pairs:
            if k in out:
                raise LayerError("%s: key %s twice" % (path, k))
            out[k] = v
        return out
    return hook


def chain(path, _seen=None):
    """[(path, layer dict)] from the widest layer to PATH."""
    seen = _seen or []
    ap = os.path.abspath(path)
    if ap in seen:
        raise LayerError("%s: include cycle" % path)
    layer = read(path)
    keys = list(layer)
    if INCLUDE in layer:
        if keys[0] != INCLUDE:
            raise LayerError("%s: include must be the first key" % path)
        up = chain(layered.locate(path, layer[INCLUDE]), seen + [ap])
        return up + [(path, layer)]
    return [(path, layer)]


def files(path):
    return [p for p, _ in chain(path)]


def _same(a, b):
    return json.dumps(a, sort_keys=True) == json.dumps(b, sort_keys=True)


def merge(layers):
    """The effective profile of [(path, layer)] (widest first); LayerError on a copy or a bad key."""
    out = {}
    for n, (path, layer) in enumerate(layers):
        base = n == 0
        for k, v in layer.items():
            if k == INCLUDE:
                continue
            if k in MODEL_ONLY and n != len(layers) - 1:
                raise LayerError("%s: %s belongs in the model's own menu.json" % (path, k))
            if k == "features":
                feats = out.setdefault("features", {})
                after = layer.get(AFTER, {})
                for f in after:
                    if f not in v or f in feats:
                        raise LayerError("%s: after.%s: only a row this layer adds gets a place (no reordering)" % (path, f))
                for f, par in v.items():
                    if f in feats and _same(feats[f], par):
                        raise LayerError("%s: features.%s repeats the inherited value (drop it)" % (path, f))
                    if f in feats or f not in after:
                        feats[f] = par                       # a value in place, or a new row at the end
                        continue
                    if after[f] not in feats:
                        raise LayerError("%s: after.%s: anchor %s is no row here" % (path, f, after[f]))
                    rows = list(feats.items())
                    at = [r for r, _ in rows].index(after[f]) + 1
                    feats.clear()
                    feats.update(rows[:at] + [(f, par)] + rows[at:])
            elif k == AFTER:
                if "features" not in layer:
                    raise LayerError("%s: after without features" % path)
            elif k == OFF:
                feats = out.setdefault("features", {})
                for f, why in v.items():
                    if f not in feats:
                        raise LayerError("%s: off %s, a row no layer below gives" % (path, f))
                    if not isinstance(why, str) or not why.strip():
                        raise LayerError("%s: off %s needs its reason" % (path, f))
                    del feats[f]
                    out.get("hints", {}).pop(f, None)        # its hint picture goes with the row
            elif k == "sections":
                secs = out.setdefault("sections", {})
                for s, order in v.items():
                    if s not in STOCK_ORDER:
                        raise LayerError("%s: sections.%s: only the stock order of %s is model data, the rest is the "
                                         "shared tree" % (path, s, " and ".join(STOCK_ORDER)))
                    sec = secs.setdefault(s, {})
                    for part, pv in order.items():           # "groups" (the order) and "stock" (where it is from)
                        if part not in SECTION_PARTS:
                            raise LayerError("%s: sections.%s.%s: unknown" % (path, s, part))
                        if part in sec and _same(sec[part], pv):
                            raise LayerError("%s: sections.%s.%s repeats the inherited one (drop it)" % (path, s, part))
                        sec[part] = pv
            elif k == MODEL_ROWS:
                if not base:
                    raise LayerError("%s: %s is the base's list" % (path, k))
            elif k in KEYED:
                d = out.setdefault(k, {})
                for kk, vv in v.items():
                    if vv is None:
                        if kk not in d:
                            raise LayerError("%s: %s.%s: null removes an inherited key, there is none" % (path, k, kk))
                        del d[kk]
                    elif kk in d and _same(d[kk], vv):
                        raise LayerError("%s: %s.%s repeats the inherited value (drop it)" % (path, k, kk))
                    else:
                        d[kk] = vv
            elif k in REPLACED:
                if not base and k in out and _same(out[k], v):
                    raise LayerError("%s: %s repeats the inherited value (drop it)" % (path, k))
                out[k] = v
            else:
                raise LayerError("%s: unknown key %s" % (path, k))
    for k, order in KEY_ORDER.items():                     # one order of the small keyed objects, whoever gave them
        if k in out:
            out[k] = {kk: out[k][kk] for kk in sorted(out[k], key=lambda x: order.index(x) if x in order else len(order))}
    for s, sec in out.get("sections", {}).items():
        out["sections"][s] = {p: sec[p] for p in SECTION_PARTS if p in sec}
    return {k: out[k] for k in ORDER if k in out}


def effective(path):
    return merge(chain(path))


def dumps(profile):
    """A profile or a layer as text: one feature (and off, after) per line, one group of a stock order per line,
    every other key on one line, so a diff names the row."""
    def one(v):
        return json.dumps(v, ensure_ascii=False)

    parts = []
    for k, v in profile.items():
        if k == "sections" and v:
            secs = []
            for s, sec in v.items():
                inner = []
                for part, pv in sec.items():
                    if part == "groups":
                        inner.append('      "groups": [\n%s\n      ]' % ",\n".join("        " + one(g) for g in pv))
                    else:
                        inner.append("      %s: %s" % (one(part), one(pv)))
                secs.append("    %s: {\n%s\n    }" % (one(s), ",\n".join(inner)))
            parts.append('  "sections": {\n%s\n  }' % ",\n".join(secs))
        elif k in ("features", OFF, AFTER) and isinstance(v, dict) and v:
            parts.append("  %s: {\n%s\n  }" % (one(k), ",\n".join("    %s: %s" % (one(kk), one(vv)) for kk, vv in v.items())))
        else:
            parts.append("  %s: %s" % (one(k), one(v)))
    return "{\n" + ",\n".join(parts) + "\n}\n"


def text(path):
    return dumps(effective(path))


def models(root):
    d = os.path.join(root, "devices")
    return sorted(c for c in os.listdir(d)
                  if not c.startswith("_") and os.path.isfile(os.path.join(d, c, PROFILE)))


def is_profile(path):
    """True for devices/<codename>/menu.json (a model's layer, read only through this file)."""
    d = os.path.dirname(os.path.abspath(path))
    return (os.path.basename(path) == PROFILE and os.path.basename(os.path.dirname(d)) == "devices"
            and not os.path.basename(d).startswith("_"))


def read_text(path):
    """The text of PATH, the effective profile for devices/<codename>/menu.json and a strings file with its aliases
    resolved (tools/i18n/res_strings.py), as the build sees them (test helpers that read files)."""
    if is_profile(path):
        return text(path)
    if re.search(r"[/\\]res[/\\]values[^/\\]*[/\\][^/\\]+\.xml$", path):
        sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "i18n"))
        import res_strings      # a strings file as the APK resolves it (aliases of lumen-common-strings)
        return res_strings.text(path)
    with open(path, encoding="utf-8") as f:
        return f.read()


def profile(root, codename):
    return effective(os.path.join(root, "devices", codename, PROFILE))


def write_all(root, out):
    for c in models(root):
        os.makedirs(os.path.join(out, c), exist_ok=True)
        with open(os.path.join(out, c, PROFILE), "w", encoding="utf-8") as f:
            f.write(text(os.path.join(root, "devices", c, PROFILE)))
    return models(root)


def copy_profile(src, dst):
    """Like shutil.copy (DST a file or a folder) for devices/<codename>/menu.json: writes its effective profile, flat
    (no include), so a scratch copy of a few files reads the same profile and an edit of it applies whatever layer
    the text comes from (mutators)."""
    if os.path.isdir(dst):
        dst = os.path.join(dst, PROFILE)
    with open(dst, "w", encoding="utf-8") as f:
        f.write(text(src))
    return dst


def materialize(path):
    """A file with the effective profile of PATH (for an app recipe's menu line), cached by its content."""
    body = text(path).encode("utf-8")
    d = os.path.join(tempfile.gettempdir(), "lumen-menu-profiles")
    os.makedirs(d, exist_ok=True)
    out = os.path.join(d, hashlib.sha256(body).hexdigest() + ".json")
    if not os.path.isfile(out):
        tmp = out + ".%d" % os.getpid()
        with open(tmp, "wb") as f:
            f.write(body)
        os.replace(tmp, out)
    return out


def view(root, out):
    """OUT: the tree ROOT as the readers of a profile see it, every entry a link into ROOT except
    devices/<codename>/menu.json, which is that model's effective profile. Test runners hand OUT to tests that open
    devices/<codename>/menu.json themselves."""
    root = os.path.abspath(root)
    if os.path.isdir(out):
        shutil.rmtree(out)
    os.makedirs(os.path.join(out, "devices"))
    for e in os.listdir(root):
        if e != "devices":
            os.symlink(os.path.join(root, e), os.path.join(out, e))
    have = set(models(root))
    with open(os.path.join(out, VIEW_MARK), "w", encoding="utf-8") as f:
        f.write(root + "\n")                   # the tree it was merged from (guard ran there, assemble_app may run here)
    for e in os.listdir(os.path.join(root, "devices")):
        src = os.path.join(root, "devices", e)
        if e not in have:
            os.symlink(src, os.path.join(out, "devices", e))
            continue
        os.makedirs(os.path.join(out, "devices", e))
        for f in os.listdir(src):
            if f != PROFILE:
                os.symlink(os.path.join(src, f), os.path.join(out, "devices", e, f))
        with open(os.path.join(out, "devices", e, PROFILE), "w", encoding="utf-8") as f:
            f.write(text(os.path.join(src, PROFILE)))
    return out


# ---------------------------------------------------------------- the guard of the whole tree (menu_layers_test.py)
SPEC = "apps/core/lumen-menu/src/org/lumen/menu/MenuSpec.java"
FID = "apps/core/lumen-menu/src/org/lumen/menu/FeatureId.java"
SHARED_SECTIONS = ("GENERAL", "DEVELOPER", "NETWORK", "SOURCE", "BLUETOOTH")
MODULE_SETS = ("MODULE_SCREENS", "MODULE_SETTINGS", "HOTSPOT_ROWS", "MODULE_INFO", "MODULE_PAGES")
# the source folders of the device apps whose adapters sit behind the shared menu
DEVICE_SRC = ("apps/LumenProjector/host/activity/src", "apps/LumenProjector/vendor/xgimi/mt9681-aidl/src", "apps/LumenProjector/vendor/xgimi/play6-hidl/src", "apps/LumenProjector/core/src",
              "apps/common/projector/src")
ADDED = ("features", "hints", "keys", "game_menu")


def _src(root, rel):
    with open(os.path.join(root, rel), encoding="utf-8") as f:
        return f.read()


def ids(root):
    return dict(re.findall(r'^\s+([A-Z][A-Z0-9_]*)\("([a-z0-9_.]+)", FeatureKind\.[A-Z]+\)', _src(root, FID), re.M))


def shared_rows(root):
    """{row: why it is shared}: the rows of the shared sections and the module rows (the menu's own code)."""
    spec, enum = _src(root, SPEC), ids(root)
    out = {}
    for sec in SHARED_SECTIONS:
        i = spec.index("{%s, new Object[][]{" % sec)
        for c in re.findall(r"FeatureId\.([A-Z0-9_]+)", spec[i:spec.index("}}}", i)]):
            out[enum[c]] = "a row of the shared %s section" % sec.lower()
    for name in MODULE_SETS:
        i = spec.index(" %s = " % name)
        for c in re.findall(r"FeatureId\.([A-Z0-9_]+)", spec[i:spec.index(";", i)]):
            out.setdefault(enum[c], "a module row")
    return out


def module_consts(root):
    spec = _src(root, SPEC)
    out = set()
    for name in MODULE_SETS:
        i = spec.index(" %s = " % name)
        out.update(re.findall(r"FeatureId\.([A-Z0-9_]+)", spec[i:spec.index(";", i)]))
    return out


def layers_of(root):
    """{layer path relative to root: layer dict} of every model's chain, and {codename: [chain paths]}."""
    lay, chains = {}, {}
    for c in models(root):
        ch = chain(os.path.join(root, "devices", c, PROFILE))
        chains[c] = [os.path.relpath(p, root) for p, _ in ch]
        for p, d in ch:
            lay[os.path.relpath(p, root)] = d
    return lay, chains


def added(lay, chains):
    """{(layer, key, item): value} of what each layer adds or changes (features, hints, keys, game_menu, stock orders)."""
    out = {}
    for path, d in lay.items():
        for key in ADDED:
            for k, v in (d.get(key) or {}).items():
                if v is not None:
                    out[(path, key, k)] = v
        for s, sec in (d.get("sections") or {}).items():
            if "groups" in sec:
                out[(path, "sections", s + ".groups")] = sec["groups"]
    return out


def guard(root):
    """The copies of the menu in the tree ROOT, one line each (empty: none). A tree without the menu module has none."""
    if not os.path.isfile(os.path.join(root, SPEC)) or os.path.isfile(os.path.join(root, VIEW_MARK)):
        return []
    errs = []
    base = BASE
    try:
        lay, chains = layers_of(root)
    except (LayerError, layered.LayerError, OSError, ValueError) as e:
        return ["layers: %s" % e]
    # 1 + 2
    for c in models(root):
        own = lay.get("devices/%s/menu.json" % c, {})
        if INCLUDE not in own:
            errs.append("%s: devices/%s/menu.json has no include, a full copy of the menu (build on the base)" % (c, c))
        elif chains[c][0] != base:
            errs.append("%s: the menu chain starts at %s, not at the base %s" % (c, chains[c][0], base))
        try:
            profile(root, c)
        except (LayerError, layered.LayerError) as e:
            errs.append("%s: %s" % (c, e))
    # 3
    shared = shared_rows(root)
    model_rows = set((lay.get(base) or {}).get(MODEL_ROWS) or [])
    for path, d in sorted(lay.items()):
        if path == base:
            continue
        inh = {}
        for c, ch in chains.items():
            if path in ch:
                inh = merge([(os.path.join(root, p), lay[p]) for p in ch[:ch.index(path)]]).get("features", {})
                break
        for f in d.get("features") or {}:
            if f in shared and f not in model_rows and f not in inh:
                errs.append("%s: adds %s, %s: it lives in the base, a model without it says off with the reason"
                            % (path, f, shared[f]))
    # 4
    add = added(lay, chains)
    by = {}
    for (path, key, k), v in add.items():
        by.setdefault((key, k, json.dumps(v, sort_keys=True)), []).append(path)
    parent = {p: d.get(INCLUDE) for p, d in lay.items()}
    for (key, k, _), paths in sorted(by.items()):
        for a in paths:
            for b in paths:
                if a < b and parent[a] == parent[b]:
                    errs.append("%s.%s: the same value in %s and %s, a copy (move it to their common family or the base)"
                                % (key, k, a, b))
    # 5
    for c, ch in chains.items():
        fam = [os.path.relpath(d, root) for d in layered.dirs(os.path.join(root, "devices", c, "device.conf"))[1:]]
        for p in ch[1:-1]:
            if os.path.dirname(p) not in fam:
                errs.append("%s: its menu builds on %s, outside its family chain %s" % (c, p, " ".join(fam) or "(none)"))
    # 6
    for d, _, fs in os.walk(os.path.join(root, "devices")):
        for f in fs:
            if f.endswith(".layers"):
                for n, line in enumerate(open(os.path.join(d, f), encoding="utf-8"), 1):
                    if re.match(r"^file\s+\S*menu\.json\s", line):
                        errs.append("%s:%d: ships the raw menu layer, use 'menu SRC DEST' (the merged profile)"
                                    % (os.path.relpath(os.path.join(d, f), root), n))
    # 7
    consts = module_consts(root)
    for src in DEVICE_SRC:
        for d, _, fs in os.walk(os.path.join(root, src)):
            for f in fs:
                if not f.endswith(".java"):
                    continue
                t = open(os.path.join(d, f), encoding="utf-8").read()
                if "org.lumen.menu.FeatureId" not in t and "package org.lumen.menu" not in t:
                    continue
                for c in sorted(consts):
                    if re.search(r"FeatureId\.%s\b|case %s:" % (c, c), t):
                        errs.append("%s: handles the module row %s itself (lumen-menu ModuleScreens owns it)"
                                    % (os.path.relpath(os.path.join(d, f), root), c))
    return errs


def main(argv):
    try:
        if len(argv) == 2 and argv[0] == "--files":
            print("\n".join(files(argv[1])))
            return 0
        if len(argv) == 3 and argv[0] == "--all":
            write_all(argv[1], argv[2])
            return 0
        if len(argv) == 3 and argv[0] == "--view":
            print(view(argv[1], argv[2]))
            return 0
        if len(argv) == 2 and argv[0] == "--guard":
            errs = guard(argv[1])
            for e in errs:
                sys.stderr.write("menu_layers: %s\n" % e)
            return 1 if errs else 0
        if len(argv) == 2 and argv[0] == "--models":
            print("\n".join(models(argv[1])))
            return 0
        if len(argv) == 1:
            sys.stdout.write(text(argv[0]))
            return 0
    except (LayerError, layered.LayerError, OSError) as e:
        sys.stderr.write("menu_layers: %s\n" % e)
        return 1
    sys.stderr.write(__doc__ or "")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
