#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""installer_catalog_mutate.py: mutants of the two catalog files (catalog.json for Lumen Installer 1.0.3, G codes only,
catalog-v2.json for 1.0.4 and later), their serial rules, the trial from the newest beta and the vendor file reader
guard in tools/devices/installer_catalog.py. Every one must turn installer_catalog_test.py red.

    python3 tools/devices/test/installer_catalog_mutate.py [-j N] [NAME ...]

A mutant is (name, old, new) on installer_catalog.py or (name, old, new, path) on another file of the tree (the model
code readers installer_index.py and dsu_revisions.py, the TITAN catalog.conf data). Each runs in a scratch tree: every
folder on the way to the changed file linked entry by entry, the file itself copied and changed. A mutant whose text is
not found is an ERROR. Exit 0 only when every mutant is killed.
"""
import os
import shutil
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor

G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
SRC = "tools/devices/installer_catalog.py"
IDX = "tools/devices/installer_index.py"
REV = "tools/devices/test/dsu_revisions.py"

MUTANTS = [
    ("v1_any_letter", 'CODES = {1: r"G[0-9]{4}", 2: r"[A-Z][0-9]{4}"}', 'CODES = {1: r"[A-Z][0-9]{4}", 2: r"[A-Z][0-9]{4}"}'),
    ("v2_g_only", 'CODES = {1: r"G[0-9]{4}", 2: r"[A-Z][0-9]{4}"}', 'CODES = {1: r"G[0-9]{4}", 2: r"G[0-9]{4}"}'),
    ("v2_any_case", 'CODES = {1: r"G[0-9]{4}", 2: r"[A-Z][0-9]{4}"}', 'CODES = {1: r"G[0-9]{4}", 2: r"[A-Za-z][0-9]{4}"}'),
    ("v1_keeps_all", "    if v == 2:\n        return c\n    keep =", "    if True:\n        return c\n    keep ="),
    ("v2_filtered", "    if v == 2:\n        return c\n    keep =", "    if False:\n        return c\n    keep ="),
    ("file_names_swapped", 'FILES = {1: "catalog.json", 2: "catalog-v2.json"}', 'FILES = {1: "catalog-v2.json", 2: "catalog.json"}'),
    ("strict_code_unchecked", "need(re.fullmatch(CODES[v], code) and code not in seen", "need(code not in seen"),
    ("strict_always_v2", "    need(c.get(\"schema\") == 1 and c.get(\"kind\") == KIND", "    v = 2\n    need(c.get(\"schema\") == 1 and c.get(\"kind\") == KIND"),
    ("version_of_name_ignored", 'return 2 if "--v2" in args or Path(path).name == FILES[2] else 1', 'return 2 if "--v2" in args else 1'),
    ("version_of_flag_ignored", 'return 2 if "--v2" in args or Path(path).name == FILES[2] else 1', 'return 2 if Path(path).name == FILES[2] else 1'),
    ("live_older_taken", 'if c["serial"] < live["serial"]:', "if False:"),
    ("live_changed_same_serial", 'if (changed or rest) and c["serial"] <= live["serial"]:', "if False:"),
    ("live_one_file", 'p = Path(live_dir) / FILES[v]', 'p = Path(live_dir) / FILES[1]'),
    ("live_skipped", '            if (v in outs or not outs and v == 1) and "--live" in args:', '            if False:'),
    ("check_v1_only", 'want = for_file(build(got.get("serial", 1), net()), version_of(args[0], args))', 'want = for_file(build(got.get("serial", 1), net()), 1)'),
    # model codes of any capital letter in installer.conf and in the in-trial table
    ("idx_refused_g_only", 'REFUSED_KEY = re.compile(r"^refused_([A-Z][0-9]{4})_', 'REFUSED_KEY = re.compile(r"^refused_(G[0-9]{4})_', IDX),
    ("idx_refused_any_case", 'REFUSED_KEY = re.compile(r"^refused_([A-Z][0-9]{4})_', 'REFUSED_KEY = re.compile(r"^refused_([A-Za-z][0-9]{4})_', IDX),
    ("idx_products_g_only", "prods = re.findall(r'row\\(\"([a-z0-9]+)\", \"([A-Z][0-9]{4})\"", "prods = re.findall(r'row\\(\"([a-z0-9]+)\", \"(G[0-9]{4})\"", IDX),
    ("rev_rows_g_only", "r'row\\(\"([a-z0-9]+)\", \"([A-Z][0-9]{4})\"", "r'row\\(\"([a-z0-9]+)\", \"(G[0-9]{4})\"", REV),
    # the TITAN data: each model's own certificates and release tag
    ("titanpro_max_certs", "certs=devices/h0062/certs/ota.x509.pem devices/h0062/certs/ota_next.x509.pem",
     "certs=devices/h0063/certs/ota.x509.pem devices/h0063/certs/ota_next.x509.pem", "devices/h0062/catalog.conf"),
    ("titanmax_pro_tag", "tag=titanmax-{version}", "tag=titanpro-{version}", "devices/h0063/catalog.conf"),
    # the way back (Settings > About > Back to the XGIMI system)
    ("restore_dropped", "        if m in ways:\n            e[\"restore\"] = ways[m]", "        pass"),
    ("restore_partial_ok", "    if len(have) != len(RESTORE_KEYS):", "    if False:"),
    ("restore_any_name", "    if not re.fullmatch(STICK, name):", "    if False:"),
    ("restore_any_host", "return (isinstance(url, str) and url.startswith(XGIMI_FIRMWARE) and url.endswith(\"/\" + name)",
     "return (isinstance(url, str) and url.endswith(\"/\" + name)"),
    ("restore_other_name", " and url.endswith(\"/\" + name) and len(url) <= 300", " and len(url) <= 300"),
    ("restore_dots_ok", "            and \"..\" not in url and re.fullmatch", "            and re.fullmatch"),
    ("restore_size_zero", "not 0 < size <= MAX_STOCK_FILE", "not 0 <= size <= MAX_STOCK_FILE"),
    ("restore_any_stock", "            if stock not in d[\"vendor_firmware\"] or v not in files", "            if v not in files"),
    ("restore_other_model", "        if model not in d[\"models\"] or model in out or not pairs:", "        if model in out or not pairs:"),
    ("restore_stock_twice", " or stock in [r[\"stock\"] for r in rows]:", ":"),
    ("restore_file_twice", "        if v in files or not 0 < size", "        if not 0 < size"),
    ("strict_restore_off", "        if \"restore\" in e:\n            r = e[\"restore\"]", "        if False:\n            r = e[\"restore\"]"),
    # never an older file: the stick may refuse a file older than the projector's firmware
    ("restore_older_ok", "            if stock_key(v) < stock_key(stock):", "            if False:"),
    ("restore_text_order", "            if stock_key(v) < stock_key(stock):", "            if v < stock:"),
    ("restore_newer_over_own", "            if v != stock and stock in files:", "            if False:"),
    ("restore_any_folder", 'built = re.search(r"_%s_([0-9]{8}-[0-9]{6})/[^/]+$" % re.escape(v[1:]), url)',
     'built = re.search(r"_([0-9]{8}-[0-9]{6})/[^/]+$", url)'),
    ("restore_build_time_optional", 'r"_%s_([0-9]{8}-[0-9]{6})/[^/]+$"', 'r"_%s(?:_([0-9]{8}-[0-9]{6}))?/[^/]+$"'),
    ("restore_build_order_off", "    if order != sorted(order):", "    if False:"),
    ("strict_older_ok", '                need(stock_key(f["version"]) >= stock_key(f["stock"]), code + " restore: a file older than its stock")\n',
     ""),
    ("z9x_v58_on_v19", "v6.15.58>v6.15.62", "v6.15.58>v6.15.19", "devices/horse_popular/catalog.conf"),
    ("z9x_v19_on_v62", "G0082:v6.15.19>v6.15.19,", "G0082:v6.15.19>v6.15.62,", "devices/horse_popular/catalog.conf"),
    ("g0111_v54_on_v64", "v1.2.54>v1.2.54", "v1.2.54>v1.2.64", "devices/g0111/catalog.conf"),
    ("g0100_v64_on_v54", "v1.2.64>v1.2.64", "v1.2.64>v1.2.54", "devices/g0100/catalog.conf"),
    ("g0100_v64_dropped", ",v1.2.64>v1.2.64", "", "devices/g0100/catalog.conf"),
    ("g0148_on_v61624", "G0148:v6.17.12>v6.17.12", "G0148:v6.17.12>v6.17.12,v6.16.24>v6.16.24", "devices/goat_elegant/catalog.conf"),
    ("play6_wrong_size", "v1.8.127:2115834528:", "v1.8.127:2115834529:", "devices/dragon_healthy/catalog.conf"),
    # a trial starts from the newest beta
    ("tfb_off", 'kept = trial_from_beta(e["builds"])', 'kept = list(e["builds"])'),
    ("tfb_whole_stable", '            if not b["stock"]:\n                continue\n        out.append(b)',
     '            if beta:\n                continue\n        out.append(b)'),
    ("tfb_stock_kept", 'b = dict(b, stock=[v for v in b["stock"] if v not in beta])', 'b = dict(b, stock=list(b["stock"]))'),
    ("tfb_stable_only", '        if b["channel"] == "stable":\n            b = dict', '        if True:\n            b = dict'),
    ("tfb_computer_beta", 'beta = {v for b in builds if b["way"] == "trial" and b["channel"] == "beta" for v in b["stock"]}',
     'beta = {v for b in builds if b["channel"] == "beta" for v in b["stock"]}'),
    # every trial build carries the reader of the projector's own vendor files
    ("guard_off", '            if trial_rules:\n                check_readers(e, net)\n', ''),
    ("guard_before_rule", '            e["builds"] = kept\n            if trial_rules:\n                check_readers(e, net)\n',
     '            if trial_rules:\n                check_readers(e, net)\n            e["builds"] = kept\n'),
    ("rules_default_off", 'def build(serial=None, net=None, trial_rules=True):', 'def build(serial=None, net=None, trial_rules=False):'),
    ("rules_flag_tfb_ignored", 'kept = trial_from_beta(e["builds"]) if trial_rules else e["builds"]', 'kept = trial_from_beta(e["builds"])'),
    ("rules_flag_guard_ignored", '            if trial_rules:\n                check_readers(e, net)\n', '            if True:\n                check_readers(e, net)\n'),
    ("guard_beta_only", '        if b["way"] != "trial":\n            continue\n        tag, name', '        if b["channel"] != "beta":\n            continue\n        tag, name'),
    ("guard_computer_too", '        if b["way"] != "trial":\n            continue\n        tag, name', '        if False:\n            continue\n        tag, name'),
    ("guard_none_only", 'if not net.reader(tag, name, b["sha256"]):', 'if net.reader(tag, name, b["sha256"]) is None:'),
    ("reader_unrecorded_ok", 'die("no recorded vendor file reader lookup for %s/%s (%s)" % (tag, name, sha))', 'return "shared reader"'),
    ("reader_unreadable_ok", '            if how is None:\n                die(', '            if False:\n                die('),
    ("reader_url", 'url = "https://github.com/%s/releases/download/%s/%s" % (REPO, tag, name)\n            how',
     'url = "https://github.com/%s/releases/download/%s" % (REPO, name)\n            how'),
    ("reader_cache_unwritten", '            self.rcache.write_text(json.dumps(self.readers, indent=1, sort_keys=True) + "\\n")', '            pass'),
    ("reader_not_recorded", '            self.rkept[sha] = self.readers[sha]\n', ''),
    ("rcache_handover", 'self.rcache = self.cache.with_name(READERS)', 'self.rcache = self.cache'),
    ("buildprop_unchecked", '        if not e.exists("/system/build.prop"):\n            return None\n        return reader_of(e)', '        return reader_of(e)'),
    ("reader_first_only", '            return how\n    return ""', '            return how\n        break\n    return ""'),
    ("reader_x50_dropped", '    ("system/etc/z9x/blobs_stock_dsu.sh", "x50 reader"),\n', ''),
    ("reader_shared_path", '"system/etc/z9x/blobs.conf"', '"system/etc/z9x/blobs_allow.txt"'),
    ("reader_order", '    ("system/etc/z9x/blobs.conf", "shared reader"),\n    ("system/etc/z9x/blobs_stock_dsu.sh", "x50 reader"),',
     '    ("system/etc/z9x/blobs_stock_dsu.sh", "x50 reader"),\n    ("system/etc/z9x/blobs.conf", "shared reader"),'),
    # app updates of an installed Lumen OS (Lumen Installer 1.0.6): python3 tools/devices/test/installer_catalog_mutate.py $(grep -o '"app_[a-z_]*"' tools/devices/test/installer_catalog_mutate.py | tr -d '"')
    ("app_signer_unchecked", 'if signer != apkinfo.der_sha256_of_pem(str(cert)):', "if False:"),
    ("app_oldest_release", "for _, v, r in sorted(tags, key=lambda x: x[0], reverse=True):", "for _, v, r in sorted(tags, key=lambda x: x[0]):"),
    ("app_size_unchecked", "if apk is None or len(apk) != size:", "if apk is None:"),
    ("app_vc_unchecked", "or not isinstance(vc, int) or vc <= 0:", ":"),
    ("app_pkg_unchecked", "if not isinstance(pkg, str) or not re.fullmatch(PKG, pkg) or", "if"),
    ("app_rows_dropped", "            if apps:\n                e[\"app_updates\"] = apps\n", ""),
    ("app_conf_unchecked", "            die(\"%s: app_update needs FILE.apk", "            pass  # die(\"%s: app_update needs FILE.apk"),
    ("app_strict_skipped", "        for a in e.get(\"app_updates\", []):\n            https(a.get(\"apk\"))", "        for a in []:\n            https(a.get(\"apk\"))"),
    ("app_strict_lumen_any", 'and a.get("lumen") and all(re.fullmatch(VERSION, v) for v in a["lumen"])', 'and a.get("lumen")'),
    ("app_x50_row_missing", "app_update=lumen-updater-x50-{version}.apk 1.0.10-beta5 1.0.10-beta6", "", "devices/g0111/catalog.conf"),
]


def tree(base, rel):
    """a scratch repo: every entry linked except the folders on the way to rel, which are made and linked entry by
    entry; rel itself is left for the caller to write"""
    r = os.path.join(base, "r")
    parts = rel.split("/")
    for i in range(len(parts)):
        src, dst = os.path.join(G, *parts[:i]), os.path.join(r, *parts[:i])
        os.makedirs(dst, exist_ok=True)
        for n in os.listdir(src):
            if n == parts[i] or n in (".git", "__pycache__"):
                continue
            os.symlink(os.path.join(src, n), os.path.join(dst, n))
    return r


def run_one(m):
    name, old, new, rel = (m + (SRC,))[:4]
    base = tempfile.mkdtemp(prefix="ic_mut_", dir=os.environ.get("TMPDIR"))
    try:
        r = tree(base, rel)
        src = open(os.path.join(G, rel), encoding="utf-8").read()
        if src.count(old) != 1:
            return name, "ERROR", "text found %d times" % src.count(old)
        open(os.path.join(r, rel), "w", encoding="utf-8").write(src.replace(old, new))
        p = subprocess.run([sys.executable, "-B", os.path.join(r, "tools", "devices", "test", "installer_catalog_test.py")],
                           capture_output=True, text=True, env=dict(os.environ, PYTHONDONTWRITEBYTECODE="1"), timeout=600)
        fails = [l[5:] for l in p.stdout.splitlines() if l.startswith("FAIL ")]
        if p.returncode != 0:
            return name, "KILLED", "; ".join(fails[:2]) or (p.stdout + p.stderr).strip().splitlines()[-1][:200]
        return name, "SURVIVED", p.stdout.strip().splitlines()[-1]
    finally:
        shutil.rmtree(base, ignore_errors=True)


def main(argv):
    jobs = 4
    if "-j" in argv:
        jobs = int(argv[argv.index("-j") + 1])
        del argv[argv.index("-j"):argv.index("-j") + 2]
    todo = [m for m in MUTANTS if len(argv) < 2 or m[0] in argv[1:]]
    with ThreadPoolExecutor(jobs) as ex:
        res = list(ex.map(run_one, todo))
    bad = 0
    for name, verdict, why in res:
        print("%-28s %-8s %s" % (name, verdict, why[:240]))
        bad += verdict != "KILLED"
    print("%d mutants, %d killed, %d not" % (len(res), len(res) - bad, bad))
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
