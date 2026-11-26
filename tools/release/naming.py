#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""The one place that names a GitHub release of Project-Lumen-OS/lumen-os (rules: tools/release/README.md "Naming").

    python3 tools/release/naming.py CODENAME VERSION      TITLE='...' and TAG='...' (shell: eval "$(...)")
    python3 tools/release/naming.py --channel CODENAME..  TITLE of an update channel release
    python3 tools/release/naming.py --slug "NAME" [CODE]  the tag slug a NEW model gets from its name

Title:  Lumen OS <version> · <Model> (<codes>)[ · Live]    (Live: the model has no install or update path)
Tag:    <slug>-<version>, the slug from devices/<c>/catalog.conf tag=, the pattern the Installer catalog matches.
Every input is registry data (devices/<c>/device.conf name, models, ota_engine and catalog.conf): no model is named here.
"""
import os
import re
import sys

G = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path[:0] = [os.path.join(G, "tools", "devices")]
import devices  # noqa: E402

DOT = " \u00b7 "
VERSION = r"\d+\.\d+\.\d+(?:-(?:beta|rc)\d+)?"


def model_name(d):
    """registry name without the vendor and without a trailing model code: 'XGIMI RS30 Ultra G0114' -> 'RS30 Ultra'"""
    n = re.sub(r"^XGIMI\s+", "", d["name"])
    return re.sub(r"\s*\(?\b[A-Z]\d{4}\)?$", "", n).strip()


def label(d):
    return "%s (%s)" % (model_name(d), ", ".join(d["models"]))


def live_only(d):
    return d.get("ota_engine", "none") == "none"


def tag_pattern(codename):
    """devices/<c>/catalog.conf tag=, e.g. 'z9x-hb-{version}'"""
    p = os.path.join(G, "devices", codename, "catalog.conf")
    for line in open(p, encoding="utf-8"):
        k, _, v = line.partition("=")
        if k.strip() == "tag":
            return v.strip()
    raise SystemExit("%s: no tag= line" % p)


def tag(codename, version):
    if not re.fullmatch(VERSION, version):
        raise SystemExit("version %r is not x.y.z or x.y.z-betaN" % version)
    return tag_pattern(codename).replace("{version}", version)


def title(codename, version):
    d = devices.get(G, codename)
    return "Lumen OS %s%s%s%s" % (version, DOT, label(d), DOT + "Live" if live_only(d) else "")


def channel_title(codenames):
    return "Lumen OS updates" + DOT + ", ".join(label(devices.get(G, c)) for c in codenames)


def slug(name, code=None, taken=()):
    """the tag slug of a NEW model: 'XGIMI RS 20 Pro' -> 'rs20pro'; a slug another model already has gets -<code>"""
    s = re.sub(r"[^a-z0-9]", "", re.sub(r"^xgimi\s+", "", name.lower()))
    if s in taken:
        if not code:
            raise SystemExit("slug %s is taken: give the model code" % s)
        s += "-" + code.lower()
    return s


def taken_slugs():
    out = set()
    for c in devices.load(G):
        if os.path.exists(os.path.join(G, "devices", c, "catalog.conf")):
            out.add(tag_pattern(c).split("-{version}")[0])
    return out


def of_tag(t):
    """(codename, version) of a published tag, the longest slug first (rs30ultra before rs30); None if no model's"""
    best = None
    for c in devices.load(G):
        if not os.path.exists(os.path.join(G, "devices", c, "catalog.conf")):
            continue
        m = re.fullmatch(re.escape(tag_pattern(c)).replace(re.escape("{version}"), "(%s)" % VERSION), t)
        if m and (best is None or len(tag_pattern(c)) > len(tag_pattern(best[0]))):
            best = (c, m.group(1))
    return best


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__, file=sys.stderr)
        return 2
    if argv[0] == "--channel":
        print(channel_title(argv[1:]))
    elif argv[0] == "--slug":
        print(slug(argv[1], argv[2] if len(argv) > 2 else None, taken_slugs()))
    else:
        print("TITLE='%s'\nTAG='%s'" % (title(argv[0], argv[1]).replace("'", "'\\''"), tag(argv[0], argv[1])))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
