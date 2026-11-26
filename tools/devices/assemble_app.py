#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""assemble_app.py: build the full source tree of a device app from its recipe (the design notes
and 3). A device app stops being a full copy: the recipe says "start from this base, drop these files, put my
files on top", and this script writes the full tree into a scratch folder for the unchanged apps/build_apk.sh.

  assemble_app.py [--root DIR] RECIPE [--out DIR] [--compare DIR] [--list FILE] [--release] [--quiet]
  assemble_app.py [--root DIR] --device CODENAME --app NAME [same options]   (finds the device's recipe: recipes())
  assemble_app.py [--root DIR] --device CODENAME --app NAME --show   the recipe with {codename} and {device}/ resolved
  assemble_app.py [--root DIR] --recipes CODENAME   APP<TAB>recipe path of every app recipe the device sees

  --out DIR      write the tree there (the folder is emptied first; it may not lie inside a layer, except in a
                 skipped folder of one, such as build/). Without --out nothing is written: the rules are checked
                 and the listing is computed.
  --compare DIR  the assembled tree must equal DIR file by file (sorted path and sha256; DIR is read with the
                 recipe's skip patterns, so build/ and docs do not count). Any difference is red.
  --list FILE    write the listing "sha256  path" (sorted by path, LC_ALL=C order) of the assembled tree.
  --release      an override whose origin changed since it was recorded is red (default: a warning).

Recipe (devices/<codename>/app/<App>.layers, or a family recipe devices/_family/<f>/app/<App>.layers that serves every
model including that family (devices/README.md); one directive per line, '#' starts a comment, paths relative to
gsi/ unless said otherwise; in a family recipe {codename} is the model's codename and {device}/NAME is the model's
devices/<c>/NAME, else the nearest family folder's NAME):
  app NAME              the app name (apps/NAME is normally the top layer)
  base DIR [LIST]       a base layer: every file of DIR, or only the paths named in LIST (one path relative to
                        DIR per line, '#' comments; apps/common/projector/FILES has that format). Bases are
                        applied in order; two bases that both give one path are an error.
  file SRC DEST         a base layer of one file: SRC (relative to gsi/) at DEST (relative to the app).
  menu SRC DEST         like file, with the effective menu profile of SRC (devices/<codename>/menu.json and the
                        layers it builds on, tools/devices/menu_layers.py) at DEST.
  drop PATTERN          remove base files: an exact path, a folder ending in '/', or an fnmatch pattern. Each
                        drop must remove at least one base file (a renamed Z9X file is noticed).
  module NAME|PATH      a shared source module (tools/arch/README.md): apps/core/NAME (or PATH, relative to gsi/,
                        when the word has a '/'), a base layer read with its module.conf (name=, package=,
                        depends=). Modules are closed: see rules 5 to 8.
  top DIR               the device layer: its files win over the bases. Optional: a recipe whose model adds no
                        own files (every model of the one projector app once its forks are folded) has none.
  feature NAME...       the optional parts of a layer this app takes (rule 9): a layer folder DIR may have a table
                        DIR.features beside it (apps/LumenProjector/core.features), one 'FEATURE PATTERN...' row per
                        line (PATTERN as in drop); a file a row names is part of the tree only when the recipe names
                        its feature. A recipe declares what its model has, never which core files it drops.
  skip PATTERN          never part of the tree, in any layer and in --compare: a folder ending in '/' (at the
                        top of a layer), a pattern with '/' (fnmatch on the path) or a file name pattern
                        (fnmatch on the last path element, at any depth).
  patch DIFF            a model's few changes to one shared file of the assembled bases, as a unified diff (relative
                        to gsi/; its '+++ b/PATH' names the path in the app): applied after the bases and drops, every
                        hunk exactly (rule 10). The HB manifest is the core manifest plus
                        devices/goat_elegant/app/HbProjector.manifest.diff.
  include FILE          the lines of a recipe fragment (relative to gsi/), read in place: the module set of the one
                        projector app is apps/LumenProjector/app.modules; a fragment has no app, top or identical line.
  identical allow|error a top file byte-identical to the base file it shadows: 'error' (default) = a pointless
                        copy, delete it; 'allow' only while a device still keeps copies (compare mode).

Rules (fail-closed, exit 1):
  1. every drop removes something; no path is given by two bases; every listed or single file exists;
  2. a top file that shadows a base file and differs from it has a row in the device's fork table (the forks key
     of devices/<codename>/device.conf, one format for every device: copy, origin, origin sha256, note) whose
     copy is that top file and whose origin is that base file; a shadow without a row is an error (no untracked
     fork);
  3. a top file byte-identical to its base file is an error unless 'identical allow';
  4. the origin sha256 of each such row equals the base file now: a warning that names the file to review, red
     with --release (so Z9X work never blocks a development build of another device, and nothing reaches a
     release unreviewed).
  5. a module's module.conf names the module (name= equals its folder name), its Java package (package=) and any
     further packages it adds classes to (packages=, space separated; lumen-menu adds to the projector app's panel
     and ui packages so the moved classes keep their package-private access); each
     module it depends on (depends=) is named by a module line of the same recipe;
  6. a module file is never shadowed (a top file at a module path is an error: change the module, or keep the
     device code under another name) and never dropped;
  7. module Java sources live under src/<package path>/; module resources are prefixed: every file under res/
     other than res/values*/ is named lumen_*, every resource name in res/values*/*.xml starts with lumen_;
     7b. a body module (module.conf body=yes: the whole app is this module plus the device's data, lumen-updater)
     keeps the app's own resource names; its resources are still res/<type>/<file>, and a device layer that gives
     the same resource file is an error by rule 6;
  8. module.conf, README*.md, the design notes, *.tsv (module data), test/, tools/ and image/ (init files an image recipe
     ships, e.g. lumen-signal's root bridge) of a module are never part of an app tree.
  9. features (DIR.features): every row matches a file of its folder, a file belongs to one feature at most, every
     feature a recipe names is a row of a table of its layers, a LIST of a base names no file of a feature the
     recipe does not name, and a drop never removes a file of a feature table (leave its feature out instead).
 10. a patch names one base file and every hunk applies at its line with its context and removed lines equal to the
     base text (no fuzz, no offset): a core change under a patched line is red until the diff is made again from the
     reviewed result (diff -u BASE NEW); the top may not shadow a patched file.
Output: one summary line, warnings and errors; exit 0 green, 1 red, 2 usage. Never writes outside --out and
--list.
"""
import fnmatch
import hashlib
import os
import re
import shutil
import sys
import tempfile

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import devices  # noqa: E402
import layered  # noqa: E402
import menu_layers  # noqa: E402
import patch_file  # noqa: E402


GUARDED = {}  # tree -> the errors of menu_layers.guard, once per run


class RecipeError(Exception):
    pass


FEATURE = re.compile(r"^[a-z][a-z0-9-]*$")


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def skipped(rel, patterns):
    name = rel.rsplit("/", 1)[-1]
    for p in patterns:
        if p.endswith("/"):
            if rel.startswith(p):
                return True
        elif "/" in p:
            if fnmatch.fnmatchcase(rel, p):
                return True
        elif fnmatch.fnmatchcase(name, p):
            return True
    return False


def walk(folder, patterns):
    """{relative path: absolute path} of every file under folder, without skipped paths."""
    out = {}
    for d, dirs, names in os.walk(folder):
        dirs.sort()
        rd = os.path.relpath(d, folder).replace(os.sep, "/")
        rd = "" if rd == "." else rd + "/"
        dirs[:] = [x for x in dirs if not skipped(rd + x + "/", patterns)]
        for n in sorted(names):
            rel = rd + n
            if not skipped(rel, patterns):
                out[rel] = os.path.join(d, n)
    return out


def device_dirs(root, codename):
    """devices/<codename> and the family folders its device.conf includes (devices/_family/<f>), nearest first,
    relative to root: where the device's app recipes and data files are looked up (tools/devices/layered.py)."""
    conf = os.path.join(root, "devices", codename, "device.conf")
    if not os.path.isfile(conf):
        return [os.path.join("devices", codename)]
    return [os.path.relpath(d, root) for d in layered.dirs(conf)]


def data_path(root, codename, name, where):
    """{device}/NAME of a recipe (a file, or a folder such as a top layer): the device's own devices/<c>/NAME, else the
    nearest family folder's NAME."""
    for d in device_dirs(root, codename):
        if os.path.exists(os.path.join(root, d, name)):
            return os.path.join(d, name)
    raise RecipeError("%s: %s: no %s in %s" % (where, codename, name, " ".join(device_dirs(root, codename))))


def tokens(root, words, codename, where, strict=True):
    """A recipe line's words with {codename} and {device}/NAME resolved for CODENAME (a family recipe, devices/_family/
    <f>/app/*.layers, serves every model of the family: its data paths name no model)."""
    out = []
    for w in words:
        if "{codename}" in w or w.startswith("{device}/"):
            if not codename:
                raise RecipeError("%s: %s needs a device (assemble_app.py --device CODENAME --app NAME)" % (where, w))
            w = w.replace("{codename}", codename)
            if w.startswith("{device}/"):
                try:
                    w = data_path(root, codename, w[len("{device}/"):], where)
                except RecipeError:
                    if strict:
                        raise
                    w = os.path.join("devices", codename, w[len("{device}/"):])
        out.append(w)
    return out


def parse(root, recipe, codename=None):
    r = {"app": None, "bases": [], "drops": [], "top": None, "skips": [], "identical": "error",
         "path": recipe, "codename": codename or codename_of(root, recipe), "includes": [], "features": [], "patches": []}
    read_lines(root, recipe, r, [])
    if not r["app"] or not r["bases"]:
        raise RecipeError("%s: needs app and at least one base or module" % os.path.relpath(recipe, root))
    return r


def read_lines(root, path, r, chain):
    """The directives of a recipe, or of a fragment it includes (include FILE: module, base, file, menu, drop and skip
    lines shared by several recipes, read in place; no app, top or identical, no cycle)."""
    frag = bool(chain)
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        w = line.split("#", 1)[0].split()
        if not w:
            continue
        where = "%s:%d" % (os.path.relpath(path, root), n)
        w = tokens(root, w, r["codename"], where)
        k, a = w[0], w[1:]
        if k == "include" and len(a) == 1:
            inc = os.path.join(root, a[0])
            if not os.path.isfile(inc):
                raise RecipeError("%s: include %s not found" % (where, a[0]))
            if os.path.abspath(inc) in chain + [os.path.abspath(path)]:
                raise RecipeError("%s: include cycle through %s" % (where, a[0]))
            r["includes"].append(a[0])
            read_lines(root, inc, r, chain + [os.path.abspath(path)])
        elif frag and k in ("app", "top", "identical"):
            raise RecipeError("%s: a recipe fragment gives no %s line" % (where, k))
        elif k == "app" and len(a) == 1:
            r["app"] = a[0]
        elif k == "base" and len(a) in (1, 2):
            r["bases"].append(("dir", a[0], a[1] if len(a) == 2 else None, where))
        elif k in ("file", "menu") and len(a) == 2:
            r["bases"].append((k, a[0], a[1], where))
        elif k == "module" and len(a) == 1:
            path_ = a[0] if "/" in a[0] else "apps/core/" + a[0]
            r["bases"].append(("module", path_.rstrip("/"), None, where))
        elif k == "patch" and len(a) == 1:
            r["patches"].append((a[0], where))
        elif k == "drop" and len(a) == 1:
            r["drops"].append((a[0], where))
        elif k == "top" and len(a) == 1 and r["top"] is None:
            r["top"] = a[0]
        elif k == "skip" and a:
            r["skips"].extend(a)
        elif k == "feature" and a and all(FEATURE.match(x) for x in a):
            r["features"].extend(a)
        elif k == "identical" and len(a) == 1 and a[0] in ("allow", "error"):
            r["identical"] = a[0]
        else:
            raise RecipeError("%s: cannot read %r" % (where, line.strip()))


def drop_matches(rel, pattern):
    if pattern.endswith("/"):
        return rel.startswith(pattern)
    if any(c in pattern for c in "*?["):
        return fnmatch.fnmatchcase(rel, pattern)
    return rel == pattern


def feature_rows(root, folder):
    """The optional parts of a layer folder: ([(feature, pattern, ref)], table path) of DIR.features beside it (rule
    9), ([], None) when the layer has no table."""
    table = folder.rstrip("/") + ".features"
    if not os.path.isfile(os.path.join(root, table)):
        return [], None
    rows = []
    for n, line in enumerate(open(os.path.join(root, table), encoding="utf-8"), 1):
        w = line.split("#", 1)[0].split()
        if not w:
            continue
        if len(w) < 2 or not FEATURE.match(w[0]):
            raise RecipeError("%s:%d: cannot read %r (FEATURE PATTERN...)" % (table, n, line.strip()))
        rows += [(w[0], p, "%s:%d" % (table, n)) for p in w[1:]]
    return rows, table


def select(root, folder, got, listed, r, known, optional, errors):
    """got without the files of the features the recipe does not name (rule 9); optional collects every file of a
    feature table, known every feature a table names."""
    rows, table = feature_rows(root, folder)
    if not rows:
        return got
    every = walk(os.path.join(root, folder), [])
    for f, p, ref in rows:
        known.add(f)
        if not any(drop_matches(x, p) for x in every):
            errors.append("%s: %s of feature %s matches no file of %s" % (ref, p, f, folder))
    out = {}
    for rel, src in got.items():
        hit = sorted({f for f, p, _ in rows if drop_matches(rel, p)})
        if len(hit) > 1:
            errors.append("%s: %s belongs to the features %s (one at most)" % (table, rel, " ".join(hit)))
        if hit:
            optional.add(rel)
        if hit and hit[0] not in r["features"]:
            if listed:
                errors.append("%s: the list %s names %s of feature %s, which the recipe does not name"
                              % (folder, listed, rel, hit[0]))
            continue
        out[rel] = src
    return out


def fork_rows(root, codename):
    """{copy path: (origin, sha256, line ref)} of the device's fork table, or {} without one."""
    if not codename:
        return {}, None
    d = devices.load(root).get(codename)
    if not d or d.get("forks", "none") == "none":
        return {}, None
    table = os.path.join(root, d["forks"])
    rows = {}
    for n, line in enumerate(open(table, encoding="utf-8"), 1):
        if not line.strip() or line.startswith("#"):
            continue
        p = line.rstrip("\n").split("\t")
        if len(p) >= 3 and len(p[2]) == 64:
            rows[p[0]] = (p[1], p[2], "%s:%d" % (d["forks"], n))
    return rows, d["forks"]


def codename_of(root, recipe):
    rel = os.path.relpath(os.path.abspath(recipe), root).replace(os.sep, "/").split("/")
    if len(rel) >= 4 and rel[0] == "devices" and rel[2] == "app":
        return rel[1]
    return None


MODULE_SKIPS = ["module.conf", "README*.md", "CONTRACT.md", "test/", "tools/", "image/", "*.tsv"]
RES_NAME = re.compile(r'<(?:string|string-array|plurals|color|dimen|integer|bool|style|array|integer-array|item|attr|'
                      r'declare-styleable|fraction|drawable|id)\b[^>]*\bname="([^"]+)"')


def module_conf(root, path, where):
    """{key: value} of apps/core/<m>/module.conf (same strict key=value format as device.conf)."""
    conf = os.path.join(root, path, "module.conf")
    if not os.path.isfile(conf):
        raise RecipeError("%s: module %s has no module.conf" % (where, path))
    out = {}
    for n, line in enumerate(open(conf, encoding="utf-8"), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        k, eq, v = line.partition("=")
        if not eq or k not in ("name", "package", "packages", "depends", "body"):
            raise RecipeError("%s/module.conf:%d: cannot read %r" % (path, n, line))
        out[k] = v.strip()
    if out.get("name") != os.path.basename(path):
        raise RecipeError("%s/module.conf: name=%s, the folder is %s" % (path, out.get("name"), os.path.basename(path)))
    if not re.match(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$", out.get("package", "")):
        raise RecipeError("%s/module.conf: package= is not a Java package" % path)
    if out.get("body", "no") not in ("yes", "no"):
        raise RecipeError("%s/module.conf: body= is yes or no" % path)
    return out


def module_errors(path, files, conf):
    """Rule 7 for one module: source places and prefixed resources (not for a body module, rule 7b)."""
    errs = []
    body = conf.get("body") == "yes"
    pkgs = tuple("src/" + p.replace(".", "/") + "/" for p in [conf["package"]] + conf.get("packages", "").split())
    for rel, src in sorted(files.items()):
        if rel.startswith("src/") and not (rel.startswith(pkgs) and "/" not in rel[len(max((p for p in pkgs if rel.startswith(p)), key=len)):]):
            errs.append("module %s: %s is outside its package folders %s" % (path, rel, " ".join(pkgs)))
        elif rel.startswith("res/"):
            parts = rel.split("/")
            if len(parts) != 3:
                errs.append("module %s: %s is not res/<type>/<file>" % (path, rel))
            elif body:
                pass
            elif parts[1] == "values" or parts[1].startswith("values-"):
                for name in RES_NAME.findall(open(src, encoding="utf-8").read()):
                    if not name.startswith("lumen_"):
                        errs.append("module %s: resource %s in %s is not prefixed lumen_" % (path, name, rel))
            elif not parts[2].startswith("lumen_"):
                errs.append("module %s: resource file %s is not named lumen_*" % (path, rel))
        elif not rel.startswith(("src/", "res/", "assets/")):
            errs.append("module %s: %s is neither src/, res/ nor assets/" % (path, rel))
    return errs


def assemble(root, recipe, release=False, codename=None):
    """Returns (files {rel: source abs path}, stats, warnings, errors). CODENAME: the device a family recipe is
    assembled for (default: the devices/<c>/app/ folder of the recipe)."""
    r = parse(root, recipe, codename)
    errors, warnings = [], []
    base, origin_of = {}, {}
    module_of = {}                      # path in the app -> module folder (rules 6, 7)
    modules = {os.path.basename(m[1]) for m in r["bases"] if m[0] == "module"}
    known, optional = set(), set()
    for kind, a, b, where in r["bases"]:
        if kind == "module":
            folder = os.path.join(root, a)
            if not os.path.isdir(folder):
                errors.append("%s: no module folder %s" % (where, a))
                continue
            try:
                conf = module_conf(root, a, where)
            except RecipeError as e:
                errors.append(str(e))
                continue
            for dep in conf.get("depends", "").split():
                if dep not in modules:
                    errors.append("%s: module %s depends on %s, which this recipe does not name" % (where, a, dep))
            got = walk(folder, r["skips"] + MODULE_SKIPS)
            errors.extend(module_errors(a, got, conf))
            for rel, src in got.items():
                if rel in base:
                    errors.append("%s: %s is given by two bases (%s and %s)" % (where, rel, origin_of[rel], a))
                base[rel] = src
                origin_of[rel] = a + "/" + rel
                module_of[rel] = a
            continue
        if kind == "dir":
            folder = os.path.join(root, a)
            if not os.path.isdir(folder):
                errors.append("%s: no folder %s" % (where, a))
                continue
            if b:
                lst = os.path.join(root, b)
                if not os.path.isfile(lst):
                    errors.append("%s: no list %s" % (where, b))
                    continue
                got = {}
                for line in open(lst, encoding="utf-8"):
                    p = line.split("#", 1)[0].strip()
                    if not p:
                        continue
                    if p.startswith("/") or ".." in p.split("/"):
                        errors.append("%s: %s: bad path %s" % (where, b, p))
                        continue
                    if not os.path.isfile(os.path.join(folder, p)):
                        errors.append("%s: %s lists %s, missing in %s" % (where, b, p, a))
                        continue
                    if not skipped(p, r["skips"]):
                        got[p] = os.path.join(folder, p)
            else:
                got = walk(folder, r["skips"])
            got = select(root, a, got, b, r, known, optional, errors)
            for rel, src in got.items():
                if rel in base:
                    errors.append("%s: %s is given by two bases (%s and %s)" % (where, rel, origin_of[rel], a))
                base[rel] = src
                origin_of[rel] = a.rstrip("/") + "/" + rel
        else:
            src = os.path.join(root, a)
            if not os.path.isfile(src):
                errors.append("%s: no file %s" % (where, a))
                continue
            if kind == "menu":                  # the model's effective menu profile (tools/devices/menu_layers.py)
                if root not in GUARDED:         # and no copy of the menu anywhere in the tree
                    GUARDED[root] = menu_layers.guard(root)
                errors.extend("%s: %s" % (where, e) for e in GUARDED[root])
                try:
                    src = menu_layers.materialize(src)
                except (menu_layers.LayerError, layered.LayerError) as e:
                    errors.append("%s: %s" % (where, e))
                    continue
            if b in base:
                errors.append("%s: %s is given by two bases" % (where, b))
            base[b] = src
            origin_of[b] = a
    nbase = len(base)
    dropped = 0
    for pattern, where in r["drops"]:
        hit = [p for p in base if drop_matches(p, pattern)]
        if not hit:
            errors.append("%s: drop %s removes nothing from the bases" % (where, pattern))
        for p in hit:
            if p in module_of:
                errors.append("%s: drop %s removes %s of module %s (modules are closed)" % (where, pattern, p, module_of[p]))
                continue
            if p in optional:
                errors.append("%s: drop %s removes %s of a feature table (leave its feature out instead)"
                              % (where, pattern, p))
                continue
            del base[p]
            dropped += 1
    patched = set()
    for diff, where in r["patches"]:
        try:
            rel, body = apply_patch(root, diff, base)
        except RecipeError as e:
            errors.append("%s: %s" % (where, e))
            continue
        base[rel] = cached(body, os.path.splitext(rel)[1])
        origin_of[rel] = diff
        patched.add(rel)
    top_dir = os.path.join(root, r["top"]) if r["top"] else None
    if top_dir is None:
        top = {}
    elif not os.path.isdir(top_dir):
        errors.append("%s: no top folder %s" % (os.path.relpath(recipe, root), r["top"]))
        top = {}
    else:
        top = select(root, r["top"], walk(top_dir, r["skips"]), None, r, known, optional, errors)
    for f in sorted(set(r["features"]) - known):
        errors.append("%s: feature %s is named by no feature table of its layers" % (os.path.relpath(recipe, root), f))
    rows, table = fork_rows(root, r["codename"])
    forks = copies = own = 0
    for rel, src in top.items():
        if rel not in base:
            own += 1
            continue
        if rel in patched:
            errors.append("%s shadows %s, a patched file (rule 10): put the change into the patch" % (
                r["top"].rstrip("/") + "/" + rel, rel))
            continue
        copy = r["top"].rstrip("/") + "/" + rel
        if rel in module_of:
            errors.append("%s shadows %s of module %s: modules are closed (change the module, or give the device "
                          "code another name)" % (copy, rel, module_of[rel]))
            continue
        if sha(src) == sha(base[rel]):
            copies += 1
            if r["identical"] != "allow":
                errors.append("%s is byte-identical to %s: a pointless copy, delete it" % (copy, origin_of[rel]))
            continue
        forks += 1
        row = rows.get(copy)
        if row is None:
            errors.append("%s shadows %s and differs, but %s has no row for it (untracked fork)"
                          % (copy, origin_of[rel], table or "the device (no fork table)"))
            continue
        if row[0] != origin_of[rel]:
            errors.append("%s: the row of %s names origin %s, but the base file is %s"
                          % (row[2], copy, row[0], origin_of[rel]))
            continue
        now = sha(base[rel])
        if now != row[1]:
            msg = ("%s: origin %s changed since the fork (sha256 %s, recorded %s): review it, port what applies to "
                   "%s, then record the new sha256" % (row[2], origin_of[rel], now, row[1], copy))
            (errors if release else warnings).append(msg)
    files = dict(base)
    files.update(top)
    stats = {"app": r["app"], "files": len(files), "base": nbase, "dropped": dropped, "top": len(top),
             "forks": forks, "copies": copies, "own": own, "skips": r["skips"]}
    return files, stats, warnings, errors


def apply_patch(root, diff, base):
    """(path in the app, patched text) of one unified diff applied exactly to the assembled base file (rule 10)."""
    src = os.path.join(root, diff)
    if not os.path.isfile(src):
        raise RecipeError("no patch %s" % diff)
    text = open(src, encoding="utf-8").read()
    rel = patch_file.target(text)
    if not rel or rel not in base:
        raise RecipeError("patch %s names %s, no file of the bases" % (diff, rel))
    try:
        return rel, patch_file.apply(open(base[rel], encoding="utf-8").read(), text, rel)
    except patch_file.PatchError as e:
        raise RecipeError("patch %s %s" % (diff, e))


def cached(body, ext):
    """A file with BODY (a patched base file), cached by its content like the menu profiles."""
    data = body.encode("utf-8")
    d = os.path.join(tempfile.gettempdir(), "lumen-patched")
    os.makedirs(d, exist_ok=True)
    out = os.path.join(d, hashlib.sha256(data).hexdigest() + ext)
    if not os.path.isfile(out):
        tmp = out + ".%d" % os.getpid()
        with open(tmp, "wb") as f:
            f.write(data)
        os.replace(tmp, out)
    return out


def listing(files):
    return "".join("%s  %s\n" % (sha(files[p]), p) for p in sorted(files))


def safe_out(root, out, recipe, codename=None):
    r = parse(root, recipe, codename)
    real = os.path.realpath(out)
    layers = [x for x in [r["top"]] if x] + [b[1] for b in r["bases"] if b[0] in ("dir", "module")]
    for x in layers:
        lx = os.path.realpath(os.path.join(root, x))
        if real.startswith(lx + os.sep):
            rel = os.path.relpath(real, lx).replace(os.sep, "/") + "/"
            if skipped(rel, r["skips"]):
                continue          # a skipped folder of the layer (build/): never read as part of it
        if real == lx or real.startswith(lx + os.sep) or lx.startswith(real + os.sep):
            raise RecipeError("--out %s overlaps the layer %s" % (out, x))
    if real in (os.path.realpath(root), "/", os.path.realpath(os.path.expanduser("~"))):
        raise RecipeError("--out %s is not a scratch folder" % out)


def write(files, out):
    if os.path.isdir(out):
        shutil.rmtree(out)
    os.makedirs(out)
    for rel in sorted(files):
        dst = os.path.join(out, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(files[rel], dst)


def app_dir(root, app):
    """The source folder of the app APP (its APK name), relative to root: apps/<app> when that folder holds an app (an
    older tree, before a rename), else the app's row of apps/app_dirs.tsv, else apps/<app>."""
    own = os.path.join("apps", app)
    if any(os.path.isfile(os.path.join(root, own, f)) for f in ("AndroidManifest.xml", "app.layers")):
        return own
    for table in (os.path.join(root, "apps", "app_dirs.tsv"), os.path.join(devices.ROOT, "apps", "app_dirs.tsv")):
        if os.path.isfile(table):
            for line in open(table, encoding="utf-8"):
                p = line.rstrip("\n").split("\t")
                if not line.startswith("#") and len(p) >= 2 and p[0] == app:
                    return p[1]
            break
    return own


def recipes(root, codename):
    """{app: recipe path} of the device: its devices/<c>/app/*.layers, then those of its family folders
    (devices/_family/<f>/app/*.layers, nearest first; the device's own recipe of an app wins)."""
    out = {}
    for d in device_dirs(root, codename):
        folder = os.path.join(root, d, "app")
        for n in sorted(os.listdir(folder)) if os.path.isdir(folder) else []:
            if n.endswith(".layers"):
                p = os.path.join(folder, n)
                out.setdefault(app_name(p), p)
    return out


def app_name(recipe):
    """The app a recipe builds (its app directive; a recipe without one is named after its file), read without
    resolving the rest, so a broken recipe is reported by the assembly, not skipped here."""
    for line in open(recipe, encoding="utf-8"):
        w = line.split("#", 1)[0].split()
        if len(w) == 2 and w[0] == "app":
            return w[1]
    return os.path.basename(recipe)[:-len(".layers")]


def recipe_text(root, codename, recipe, strict=False):
    """The recipe's directive lines with {codename} and {device}/ resolved for CODENAME (comments dropped), one
    "directive args" line each, the directive padded to the recipes' column ("module    lumen-ui"). A data file no
    folder has reads devices/<c>/NAME unless strict (a partial tree of a test); assembling stays strict."""
    out = []
    for n, line in enumerate(open(recipe, encoding="utf-8"), 1):
        w = line.split("#", 1)[0].split()
        if w:
            out.append("%-9s %s\n" % (w[0], " ".join(tokens(root, w[1:], codename, "%s:%d" % (recipe, n), strict))))
    return "".join(out)


def all_recipes(root):
    """[(codename, app, recipe path)] of every device of devices/ (a device.conf) and every app recipe it sees."""
    out = []
    d = os.path.join(root, "devices")
    for c in sorted(os.listdir(d)):
        if not c.startswith((".", "_")) and os.path.isfile(os.path.join(d, c, "device.conf")):
            out += [(c, a, p) for a, p in sorted(recipes(root, c).items())]
    return out


def find_recipe(root, codename, app):
    """The device's recipe of the app (recipes()), else the app's own apps/<app>/app.layers (a stand-alone app that
    uses apps/core modules, the same on every device), else None (the app builds from its folder as it is).
    apps/<app> is the folder of app_dir."""
    p = recipes(root, codename).get(app)
    if p:
        return p
    own = os.path.join(root, app_dir(root, app), "app.layers")
    return own if os.path.isfile(own) else None


def main(argv):
    root = devices.ROOT
    if argv[:1] == ["--root"] and len(argv) > 1:
        root, argv = os.path.abspath(argv[1]), argv[2:]
    if argv[:1] == ["--app-dir"] and len(argv) == 2:
        print(app_dir(root, argv[1]))
        return 0
    if argv[:1] == ["--recipes"] and len(argv) == 2:
        for app, path in sorted(recipes(root, argv[1]).items()):
            print("%s\t%s" % (app, os.path.relpath(path, root)))
        return 0
    opts, rest, i = {}, [], 0
    while i < len(argv):
        a = argv[i]
        if a in ("--out", "--compare", "--list", "--device", "--app"):
            if i + 1 >= len(argv):
                print(__doc__)
                return 2
            opts[a] = argv[i + 1]
            i += 2
        elif a in ("--release", "--quiet", "--show"):
            opts[a] = True
            i += 1
        else:
            rest.append(a)
            i += 1
    if "--device" in opts or "--app" in opts:
        if rest or "--device" not in opts or "--app" not in opts:
            print(__doc__)
            return 2
        recipe = find_recipe(root, opts["--device"], opts["--app"])
        if not recipe:
            print("assemble_app: no recipe for %s in %s" % (opts["--app"], " ".join(
                os.path.join(d, "app/") for d in device_dirs(root, opts["--device"]))))
            return 1
        if opts.get("--show"):
            print("# %s for %s" % (os.path.relpath(recipe, root), opts["--device"]))
            sys.stdout.write(recipe_text(root, opts["--device"], recipe))
            return 0
    elif len(rest) == 1:
        recipe = os.path.abspath(rest[0])
    else:
        print(__doc__)
        return 2
    try:
        files, st, warnings, errors = assemble(root, recipe, release=bool(opts.get("--release")),
                                               codename=opts.get("--device"))
        if "--out" in opts and not errors:
            safe_out(root, opts["--out"], recipe, opts.get("--device"))
    except (RecipeError, devices.RegistryError, OSError) as e:
        print("assemble_app: ERROR %s" % e)
        return 1
    status = 1 if errors else 0
    text = listing(files)
    tree = hashlib.sha256(text.encode()).hexdigest()
    if not opts.get("--quiet") or errors:
        print("assemble_app: %s: %d files (bases %d, dropped %d; top %d = %d fork(s), %d copy(ies), %d own); "
              "tree sha256 %s" % (st["app"], st["files"], st["base"], st["dropped"], st["top"], st["forks"],
                                  st["copies"], st["own"], tree))
    for w in warnings:
        print("assemble_app: WARNING " + w)
    for e in errors:
        print("assemble_app: ERROR " + e)
    if errors:
        return 1
    if "--list" in opts:
        with open(opts["--list"], "w") as f:
            f.write(text)
    if "--compare" in opts:
        cdir = os.path.abspath(opts["--compare"])
        other = walk(cdir, st["skips"])
        a = {p: sha(files[p]) for p in files}
        b = {p: sha(other[p]) for p in other}
        diff = sorted(set(a) | set(b))
        bad = [p for p in diff if a.get(p) != b.get(p)]
        for p in bad[:50]:
            what = "only assembled" if p not in b else ("only in %s" % opts["--compare"] if p not in a else "differs")
            print("assemble_app: COMPARE %s: %s" % (p, what))
        if bad:
            print("assemble_app: COMPARE FAILED: %d path(s) differ from %s" % (len(bad), opts["--compare"]))
            status = 1
        else:
            print("assemble_app: COMPARE OK: %d files equal to %s (path and sha256)" % (len(a), opts["--compare"]))
    if "--out" in opts:
        write(files, opts["--out"])
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
