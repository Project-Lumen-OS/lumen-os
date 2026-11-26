#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""The supported projectors table of the READMEs and install guides, generated, never edited by hand.

    python3 tools/release/models_table.py [--catalog FILE] [--releases FILE] [--check] [FILE...]

Data: the signed Installer catalog (catalog-v2.json of the lumen-installer `index` release, the list Lumen OS Live,
the About screen and the site read) says which models exist and what a unit can do (Lumen OS Live, install, computer);
the GitHub release list says whether a model has a published build and a stable one yet (matched with its
catalog.conf tag through naming.py). Default FILEs: the README sources and the install overview, EN and RU.
Each file holds `<!-- models:KIND:begin -->` ... `<!-- models:end -->`, KIND install (READMEs and the install overview)
or installer (the Installer README).
--check changes nothing and exits 1 when a table is out of date.
"""
import json
import os
import re
import subprocess
import sys
import urllib.request

H = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, H)
import naming  # noqa: E402

G = naming.G
devices = naming.devices
CATALOG = "https://github.com/Project-Lumen-OS/lumen-installer/releases/download/index/catalog-v2.json"
DOCS = "https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install/"
FILES = ["docs/repo/ROOT_README.md", "docs/repo/README.ru.md", "docs/install/en.md", "docs/install/ru.md",
         "apps/LumenInstaller/public/README.md", "apps/LumenInstaller/public/README.ru.md"]
WORDS = {
    "en": {"install": "Lumen OS Live, Install Lumen OS permanently", "live": "Lumen OS Live only",
           "computer": "Install with a computer", "beta": "beta",
           "head_install": "| Projector | Model code | What you can do | Guide |",
           "head_installer": "| Projector | Model code | What Lumen OS Live offers |",
           "pc": "With a computer, not with Lumen OS Live", "guide": "guide"},
    "ru": {"install": "Lumen OS Live, Установить Lumen OS насовсем", "live": "только Lumen OS Live",
           "computer": "Установка с компьютера", "beta": "бета",
           "head_install": "| Проектор | Код модели | Что можно | Инструкция |",
           "head_installer": "| Проектор | Код модели | Что предлагает Lumen OS Live |",
           "pc": "С компьютера, не через Lumen OS Live", "guide": "инструкция"},
}


def fetch(src, gh=False):
    if src and os.path.exists(src):
        return json.load(open(src, encoding="utf-8"))
    if gh:
        out = subprocess.run(["gh", "api", "--paginate", "repos/Project-Lumen-OS/lumen-os/releases?per_page=100"],
                             capture_output=True, text=True, check=True).stdout
        return json.loads(out.replace("]\n[", ",").replace("][", ","))
    with urllib.request.urlopen(src or CATALOG, timeout=60) as r:
        return json.load(r)


def guide(c):
    """the install guide page: the model's own update wire, its slug or a family folder, else the guide of a model
    sharing its widest family (registry data only, docs/install/<page>/ must exist)"""
    def has(p):
        return p and os.path.isdir(os.path.join(G, "docs", "install", p))

    def wire(x):
        p = os.path.join(G, "devices", x, "ota.conf")
        for line in open(p, encoding="utf-8") if os.path.exists(p) else ():
            if line.startswith("wire_id="):
                return line.split("=", 1)[1].strip()
    fam = [os.path.basename(os.path.dirname(f)) for f in naming_layers(c)[1:] if "/_family/" in f]
    for p in [wire(c), naming.tag_pattern(c).split("-{version}")[0]] + fam:
        if has(p):
            return p
    for f in reversed(fam):
        ws = sorted(w for w in (wire(x) for x in devices.load(G) if f in [os.path.basename(os.path.dirname(y))
                                                                          for y in naming_layers(x)]) if has(w))
        if ws:
            return ws[0]
    return None


def naming_layers(c):
    import layered
    return layered.files(os.path.join(G, "devices", c, "device.conf"))


def rows(catalog, releases):
    by_code = {}
    for c in devices.load(G):
        if os.path.exists(os.path.join(G, "devices", c, "catalog.conf")):
            for code in devices.get(G, c)["models"]:
                by_code[code] = c
    seen, out = set(), []
    for m in catalog["models"]:
        c = by_code.get(m["model"])
        if not c or c in seen:
            continue
        seen.add(c)
        cms = [x for x in catalog["models"] if by_code.get(x["model"]) == c]
        ways = {b.get("way") for x in cms for b in x.get("builds", [])}
        if any(s.get("install") for x in cms for s in x["stock"]):
            what = "install"
        elif "computer" in ways:
            what = "computer"
        else:
            what = "live"
        newest = {}
        for r in releases:
            t = naming.of_tag(r["tag_name"])
            if not t or t[0] != c or r.get("draft"):
                continue
            ch = "beta" if r["prerelease"] else "stable"
            if ch not in newest or vkey(t[1]) > vkey(newest[ch]):
                newest[ch] = t[1]
        if not newest:
            continue  # a model with no published build yet gets its row with its first release
        if "stable" in newest and "beta" in newest and vkey(newest["beta"]) < vkey(newest["stable"]):
            del newest["beta"]
        d = devices.get(G, c)
        out.append({"c": c, "name": "XGIMI " + naming.model_name(d), "codes": ", ".join(d["models"]), "what": what,
                    "stable": newest.get("stable"), "beta": newest.get("beta"), "guide": guide(c)})
    rank = {"install": 0, "computer": 1, "live": 2}
    out.sort(key=lambda r: (rank[r["what"]], r["stable"] is None, r["name"], r["codes"]))
    return out


def vkey(v):
    a, b, c, s, n = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(?:-(beta|rc)(\d+))?", v).groups()
    return int(a), int(b), int(c), {"beta": 0, "rc": 1, None: 2}[s], int(n or 0)


def table(kind, lang, rs, rel_docs):
    w = WORDS[lang]
    lines = [w["head_" + kind], "|" + "---|" * w["head_" + kind].count(" | ") + "---|"]
    for r in rs:
        what = w[r["what"]] + ("" if r["stable"] else ", " + w["beta"])
        g = "%s%s/%s.md" % (rel_docs, r["guide"], lang) if r["guide"] else ""
        gl = "[%s](%s)" % (w["guide"], g) if g else ""
        if kind == "install":
            cells = [r["name"], r["codes"], what, gl]
        else:
            cells = [r["name"], r["codes"], w["pc"] if r["what"] == "computer" else w[r["what"]]]
        lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines)


def render(path, rs):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    lang = "ru" if re.search(r"(^|[./])ru\.md$", path) else "en"
    rel_docs = "docs/install/" if "README" in os.path.basename(path) and "LumenInstaller" not in path else ""
    if "LumenInstaller" in path:
        rel_docs = DOCS

    def sub(m):
        return "%s\n%s\n%s" % (m.group(1), table(m.group(2), lang, rs, rel_docs), m.group(3))
    new, n = re.subn(r"(<!-- models:(install|installer):begin -->)\n.*?\n(<!-- models:end -->)", sub, text,
                     flags=re.S)
    if n != 1:
        raise SystemExit("%s: needs exactly one <!-- models:KIND:begin --> ... <!-- models:end --> block" % path)
    return text, new


def main(argv):
    args, opt = [], {}
    it = iter(argv)
    for a in it:
        if a in ("--catalog", "--releases"):
            opt[a] = next(it)
        elif a == "--check":
            opt[a] = True
        else:
            args.append(a)
    rs = rows(fetch(opt.get("--catalog")), fetch(opt.get("--releases"), gh=True))
    stale = []
    for f in args or FILES:
        p = f if os.path.isabs(f) else os.path.join(G, f)
        old, new = render(p, rs)
        if old != new:
            stale.append(f)
            if "--check" not in opt:
                with open(p, "w", encoding="utf-8") as f:
                    f.write(new)
    print(("OUT OF DATE: " if "--check" in opt else "updated: ") + (" ".join(stale) or "nothing"), "(%d models)" % len(rs))
    return 1 if stale and "--check" in opt else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
