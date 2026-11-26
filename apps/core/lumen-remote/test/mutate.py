#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Targeted mutants of lumen-remote: each one change to the module code must turn test/run.sh red.
  python3 apps/core/lumen-remote/test/mutate.py [NAME...]      (runs on a scratch copy, JDK 17+, python3)
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
M = os.path.dirname(HERE)
G = os.path.dirname(os.path.dirname(os.path.dirname(M)))
S = "src/org/lumen/remote/"
MUTANTS = {
    "match_any_flags": (S + "RemoteAdvert.java", "return flags >= 0 && (flags & (RemoteMatch.FLAG_LE_LIMITED | RemoteMatch.FLAG_LE_GENERAL)) != 0;", "return flags >= 0;"),
    "match_no_general": (S + "RemoteAdvert.java", "(flags & (RemoteMatch.FLAG_LE_LIMITED | RemoteMatch.FLAG_LE_GENERAL)) != 0;", "(flags & RemoteMatch.FLAG_LE_LIMITED) != 0;"),
    "match_noflags_bonded": (S + "RemoteMatch.java", "if (a.hid && !anyRemoteBonded && (a.flags < 0 || firstRun)) return true;", "if (a.hid && (a.flags < 0 || firstRun)) return true;"),
    "match_first_run_always": (S + "RemoteMatch.java", "(a.flags < 0 || firstRun)) return true;", "(a.flags < 0 || true)) return true;"),
    "match_first_run_never": (S + "RemoteMatch.java", "(a.flags < 0 || firstRun)) return true;", "(a.flags < 0)) return true;"),
    "match_connectable": (S + "RemoteMatch.java", "if (!a.connectable || ownName(a, ownName)) return false;\n        if (a.stockPairingMark())", "if (ownName(a, ownName)) return false;\n        if (a.stockPairingMark())"),
    "match_own_name": (S + "RemoteMatch.java", "if (!a.connectable || ownName(a, ownName)) return false;\n        if (a.stockPairingMark())", "if (!a.connectable) return false;\n        if (a.stockPairingMark())"),
    "match_case": (S + "RemoteMatch.java", "if (up.contains(n.toUpperCase(Locale.ROOT))) return r;", "if (name.contains(n)) return r;"),
    "match_kind": (S + "RemoteMatch.java", "if (r.kind != RemoteKind.BLE_REMOTE) continue;\n            for (String n", "for (String n"),
    "match_vendor_ir": (S + "RemoteMatch.java", "(r.kind == RemoteKind.BLE_REMOTE || r.kind == RemoteKind.USB_DONGLE) && ", ""),
    "match_manual_hid": (S + "RemoteMatch.java", "return a.hid && pairingMode(a, false, false);", "return false;"),
    "match_prefilter_manual": (S + "RemoteMatch.java", "return hasXgimi(a.name) || isRemoteName(a.name) || (manual && a.hid);", "return hasXgimi(a.name) || isRemoteName(a.name);"),
    "match_prefilter_company": (S + "RemoteMatch.java", "if (a.xgimiData != null || a.hid) return true;", "if (a.hid) return true;"),
    "match_auto_generic": (S + "RemoteMatch.java", "if (!isRemoteName(a.name)) return false;\n        return pairingMode", "return pairingMode"),
    "match_mark_off": (S + "RemoteMatch.java", "        if (a.stockPairingMark()) return a.name == null || isRemoteName(a.name);\n", ""),
    "match_mark_any_name": (S + "RemoteMatch.java", "if (a.stockPairingMark()) return a.name == null || isRemoteName(a.name);", "if (a.stockPairingMark()) return true;"),
    "match_mark_candidate": (S + "RemoteMatch.java", "        if (a.stockPairingMark()) return true;\n        if (a.name == null) return false;", "        if (a.name == null) return false;"),
    "match_mark_no_hid": (S + "RemoteAdvert.java", "return hid && startsWith(xgimiData, RemoteMatch.REMOTE_PAIRING_MARK);", "return startsWith(xgimiData, RemoteMatch.REMOTE_PAIRING_MARK);"),
    "match_mark_music": (S + "RemoteMatch.java", "REMOTE_PAIRING_MARK = {0x38, 0x38, 0x02, 0x00, 0x00};", "REMOTE_PAIRING_MARK = {0x39, 0x38, 0x02, 0x00, 0x00};"),
    "match_mark_short": (S + "RemoteMatch.java", "REMOTE_PAIRING_MARK = {0x38, 0x38, 0x02, 0x00, 0x00};", "REMOTE_PAIRING_MARK = {0x38, 0x38, 0x02};"),
    "match_mark_prefix": (S + "RemoteAdvert.java", "        for (int i = 0; i < mark.length; i++) if (data[i] != mark[i]) return false;\n", ""),
    "pair_mark_no_name": (S + "RemotePairing.java", "        RemoteAdvert a = advert(r, name);\n        boolean candidate", "        if (name == null) return;\n        RemoteAdvert a = advert(r, name);\n        boolean candidate"),
    "pair_mark_company": (S + "RemotePairing.java", "rec.getManufacturerSpecificData(RemoteMatch.ADV_COMPANY)", "rec.getManufacturerSpecificData(0x00FF)"),
    "pair_mark_repair": (S + "RemotePairing.java", "boolean pairing = a.limited() || a.stockPairingMark();", "boolean pairing = a.limited();"),
    "pair_first_run": (S + "RemotePairing.java", "RemoteMatch.automatic(a, ownName(), anyRemoteBonded(), !setupComplete());", "RemoteMatch.automatic(a, ownName(), anyRemoteBonded(), false);"),
    "pair_transport": (S + "RemotePairing.java", "ok = d.createBond(BluetoothDevice.TRANSPORT_LE);", "ok = d.createBond(BluetoothDevice.TRANSPORT_AUTO);"),
    "pair_no_abort": (S + "RemotePairing.java", "if (ok && isOrderedBroadcast()) abortBroadcast();", "if (ok && isOrderedBroadcast() && false) abortBroadcast();"),
    "pair_pin_confirm": (S + "RemotePairing.java", "&& v != 3 /* PAIRING_VARIANT_CONSENT */) {", "&& v != 3 && v != 0) {"),
    "pair_hid_after_bond": (S + "RemotePairing.java", "            connect(d, \"bonded\");\n", ""),
    "pair_hid_proxy_ready": (S + "RemotePairing.java", "                        connectBonded(\"HID host ready\");\n", ""),
    "pair_hid_recover": (S + "RemotePairing.java", "if (now - sConnectAskedAt >= CONNECT_RETRY_MS) connectBonded(\"recover window\");", ""),
    "pair_hid_recover_tick": (S + "RemotePairing.java", "            next = Math.min(next, CONNECT_RETRY_MS);\n", ""),
    "pair_repair_fast": (S + "RemotePairing.java", "REPAIR_CONFIRM_MS = 3_000", "REPAIR_CONFIRM_MS = 300"),
    "pair_recover_bonds_new": (S + "RemotePairing.java", "if (sMode == MODE_RECOVER && !manualNow && setupComplete() && !knownRemote(addr)) {", "if (false) {"),
    "pair_unbond_grace": (S + "RemotePairing.java", "UNBOND_GRACE_MS = 5_000", "UNBOND_GRACE_MS = 9_000"),
    "pair_audio_cap": (S + "RemotePairing.java", "if (audio) want = ScanSettings.SCAN_MODE_LOW_POWER;", ""),
    "pair_self_grant": (S + "RemotePairing.java", "sApp.getPackageManager().grantRuntimePermission(sApp.getPackageName(), p, Process.myUserHandle());", ""),
    "pair_new_budget": (S + "RemotePairing.java", "NEW_MAX_MS = 60 * 60_000", "NEW_MAX_MS = 90 * 60_000"),
    "pair_manual_window": (S + "RemotePairing.java", "MANUAL_MS = 120_000", "MANUAL_MS = 60_000"),
    "pair_bond_timeout": (S + "RemotePairing.java", "BOND_TIMEOUT_MS = 45_000", "BOND_TIMEOUT_MS = 30_000"),
    "pair_known_remote": (S + "RemotePairing.java", "if (RemoteMatch.isRemoteName(d.getName())) return true;", ""),
}


def run(name, f, old, new):
    tmp = tempfile.mkdtemp()
    try:
        root = os.path.join(tmp, "g")
        for rel in ("apps/core/lumen-remote", "tools/remotes", "devices/_remotes"):
            shutil.copytree(os.path.join(G, rel), os.path.join(root, rel))
        path = os.path.join(root, "apps/core/lumen-remote", f)
        text = open(path, encoding="utf-8").read()
        if text.count(old) != 1:
            return "ERROR (anchor found %d times)" % text.count(old)
        open(path, "w", encoding="utf-8").write(text.replace(old, new))
        r = subprocess.run(["sh", os.path.join(root, "apps/core/lumen-remote/test/run.sh")], capture_output=True, text=True, timeout=600)
        return "killed" if r.returncode != 0 else "ALIVE"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main(names):
    todo = names or sorted(MUTANTS)
    bad = 0
    for n in todo:
        res = run(n, *MUTANTS[n])
        print("%-26s %s" % (n, res))
        bad += res != "killed"
    print("mutants: %d, killed %d, not killed %d" % (len(todo), len(todo) - bad, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
