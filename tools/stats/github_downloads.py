#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Download counts of the public Lumen OS releases (GitHub API through `gh api`: the token stays in gh, never in
a file). Counts are totals since each asset was uploaded; GitHub keeps no history, so a curve over time needs
snapshots (--json rows carry the snapshot date, the dashboard stores one set per run).

    python3 tools/stats/github_downloads.py              table per release and asset
    python3 tools/stats/github_downloads.py --summary    one line per release: installs, updates, checks
    python3 tools/stats/github_downloads.py --json       rows (date, tag, device, version, kind, downloads) for
                                                         the dashboard, summed per release and kind
Kinds: image (system.img), installer (installer zip), ota_full / ota_delta (OTA zips), metadata (payload metadata
read before an OTA), manifest (update-*.json: every update check of a projector on that release's version fetches
it while the release is the latest, so it counts checks, not projectors), sig, checksums, source, other.
"""
import argparse
import datetime
import json
import re
import subprocess
import sys

REPO = "Project-Lumen-OS/lumen-os"
# the model-agnostic Lumen Installer (its own repository): APK downloads are installer downloads, the signed device
# index (devices.json on the tag index) is fetched at every start of the installer; its tags get this prefix
INSTALLER_REPO, INSTALLER_PREFIX = "Project-Lumen-OS/lumen-installer", "lumen-installer-"
KINDS = [
    ("sig", re.compile(r"\.sig$")),
    ("manifest", re.compile(r"^update-.*\.json$")),
    ("image", re.compile(r"system\.img(\.\w+)?$")),
    ("installer", re.compile(r"installer\.zip$")),
    ("installer", re.compile(r"^lumen-installer-.*\.apk$")),
    ("manifest", re.compile(r"^devices\.json$")),
    ("ota_full", re.compile(r"full-ota\.zip$")),
    ("ota_delta", re.compile(r"-from-.*-ota\.zip$")),
    ("metadata", re.compile(r"payload_metadata\.bin$")),
    ("checksums", re.compile(r"^SHA256SUMS$")),
    ("source", re.compile(r"source\.zip$")),
]
# release tag prefix -> device codename, longest first (the design notes: <slug>-<version> from 1.0.8,
# the Z9X up to 1.0.7 as v<version>, the HB until its 1.0.8 as hb-<version>)
DEVICES = [("lumen-installer", "installer"), ("z9x-hb", "goat_elegant"), ("hb", "goat_elegant"),
           ("play6", "dragon_healthy"), ("x50max", "g0100"), ("x50", "g0111"), ("rs30", "g0155"), ("z9x", "horse_popular"),
           ("v", "horse_popular")]
# update channel tags: fixed pre-releases holding only the signed manifests; their downloads are update checks (the
# installer's index: installer starts)
CHANNELS = {"z9x", "z9x-hb", "hb", "play6", "x50", "x50max", "rs30", "lumen-installer-index"}


def kind(name):
    for k, rx in KINDS:
        if rx.search(name):
            return k
    return "other"


def device(tag):
    for prefix, dev in DEVICES:
        if tag == prefix or tag.startswith(prefix + "-") or (prefix == "v" and re.match(r"v\d", tag)):
            return dev
    return "other"


def version(tag):
    m = re.search(r"\d+\.\d+(\.\d+)?(-[0-9A-Za-z]+)?", tag)
    return m.group(0) if m else tag


def utc_day():
    """The snapshot date in UTC (the VPS runs on EST, the Mac on MSK: local dates would split one snapshot)."""
    return datetime.datetime.now(datetime.timezone.utc).date().isoformat()


def installer_tags(rels):
    """Releases of the installer repository under the prefixed tag (v1.0.1 -> lumen-installer-v1.0.1)."""
    return [dict(r, tag_name=INSTALLER_PREFIX + r["tag_name"]) for r in rels]


def releases(repo=REPO):
    out = subprocess.run(["gh", "api", "--paginate", "repos/%s/releases?per_page=100" % repo],
                         check=True, capture_output=True, text=True).stdout
    # --paginate concatenates one JSON array per page
    data, dec, i = [], json.JSONDecoder(), 0
    while i < len(out):
        while i < len(out) and out[i].isspace():
            i += 1
        if i >= len(out):
            break
        page, i = dec.raw_decode(out, i)
        data.extend(page)
    return [r for r in data if not r.get("draft")]


def rows(rels, day):
    out = []
    for r in rels:
        sums = {}
        for a in r.get("assets", []):
            k = kind(a["name"])
            sums[k] = sums.get(k, 0) + a["download_count"]
        for k, n in sorted(sums.items()):
            out.append({"date": day, "tag": r["tag_name"], "device": device(r["tag_name"]),
                        "version": version(r["tag_name"]), "channel": r["tag_name"] in CHANNELS,
                        "prerelease": bool(r.get("prerelease")) and r["tag_name"] not in CHANNELS,
                        "published": (r.get("published_at") or "")[:10], "kind": k, "downloads": n})
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--json", action="store_true")
    g.add_argument("--summary", action="store_true")
    a = ap.parse_args()
    rels = sorted(releases() + installer_tags(releases(INSTALLER_REPO)), key=lambda r: r.get("published_at") or "",
                  reverse=True)
    day = utc_day()
    if a.json:
        json.dump(rows(rels, day), sys.stdout, indent=1)
        print()
        return
    if a.summary:
        print("%-24s %-15s %-10s %8s %8s %8s" % ("release", "device", "published", "installs", "updates", "checks"))
        for r in rels:
            s = {}
            for x in rows([r], day):
                s[x["kind"]] = x["downloads"]
            print("%-24s %-15s %-10s %8d %8d %8d" % (
                r["tag_name"] + (" (pre)" if r.get("prerelease") else ""), device(r["tag_name"]),
                (r.get("published_at") or "")[:10], max(s.get("image", 0), s.get("installer", 0)),
                s.get("ota_full", 0) + s.get("ota_delta", 0), s.get("manifest", 0)))
        print("installs = max(system.img, installer zip) downloads; updates = OTA zip downloads; checks = "
              "manifest fetches (update checks while the release was the latest)")
        return
    for r in rels:
        total = sum(x["download_count"] for x in r.get("assets", []))
        print("%s%s  %s  %s  total %d" % (r["tag_name"], " (pre-release)" if r.get("prerelease") else "",
                                         device(r["tag_name"]), (r.get("published_at") or "")[:10], total))
        for x in sorted(r.get("assets", []), key=lambda x: -x["download_count"]):
            print("    %6d  %-10s %s" % (x["download_count"], kind(x["name"]), x["name"]))


if __name__ == "__main__":
    main()
