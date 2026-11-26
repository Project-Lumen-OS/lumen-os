#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""mutate.py: targeted mutants of lineage/patches/wifi/0001 (V3 P2P calls by interface hash). Each mutant changes one
line of a copy of the patch (same line count, so it still applies), runs test/run.sh on that copy and must make it
fail. Run from anywhere:

  python3 lineage/patches/wifi/test/mutate.py [NAME...]

Prints KILLED / ALIVE per mutant, exits 1 when one stays alive or a mutation no longer applies.
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
PATCH = os.path.join(HERE, "..", "0001-p2p-supplicant-v3-snapshot.patch")

# name, old line text, new line text (both inside one "+" line of the patch)
MUTANTS = [
    # SupplicantP2pV3Calls
    ("hash_other", 'FROZEN_V3_HASH = "55b58c9bd6d40c1459073b5d03f4ede5cfc9a212";',
     'FROZEN_V3_HASH = "4652398bc565e185ec0799d2a9ea9db1774710bc";'),
    ("hash_ignored", "boolean frozen = FROZEN_V3_HASH.equals(hash);", "boolean frozen = true;"),
    ("hash_any_known", "boolean frozen = FROZEN_V3_HASH.equals(hash);", "boolean frozen = hash != null;"),
    ("hash_case", "boolean frozen = FROZEN_V3_HASH.equals(hash);",
     "boolean frozen = FROZEN_V3_HASH.equalsIgnoreCase(hash);"),
    ("version_gate_2", "if (version < 3) {", "if (version < 2) {"),
    ("version_gate_4", "if (version < 3) {", "if (version < 4) {"),
    ("frozen_no_connect", "            mCalls.add(CONNECT);", "            mCalls.add(FIND);"),
    ("frozen_no_find", "            mCalls.add(FIND);", "            mCalls.add(CONNECT);"),
    ("frozen_no_listen", "            mCalls.add(EXT_LISTEN);", "            mCalls.add(CONNECT);"),
    ("none_has_calls", 'return new SupplicantP2pV3Calls(false, "no interface");',
     'return new SupplicantP2pV3Calls(true, "no interface");'),
    ("has_always", "return mCalls.contains(call);", "return !mCalls.isEmpty();"),
    ("drop_keeps_call", "        mCalls.remove(call);", "        mCalls.clear();"),
    ("drop_not_removed", "        mCalls.remove(call);", "        mCalls.contains(call);"),
    ("drop_any_message", "if (!isUnimplemented(call, exceptionMessage)) {", "if (exceptionMessage == null) {"),
    ("unimpl_prefix_only", "return (UNIMPLEMENTED_PREFIX + call + UNIMPLEMENTED_SUFFIX).equals(exceptionMessage);",
     "return exceptionMessage != null && exceptionMessage.startsWith(UNIMPLEMENTED_PREFIX);"),
    ("unimpl_text", 'UNIMPLEMENTED_SUFFIX = " is unimplemented.";', 'UNIMPLEMENTED_SUFFIX = " is unimplemented";'),
    ("log_no_hash", '"V" + version + " hash " + hash + (frozen', '"V" + version + " hash " + "" + (frozen'),
    # SupplicantP2pIfaceHalAidlImpl
    ("setup_no_calls", "            mV3Calls = v3CallsOf(iface);", "            mV3Calls.toString();"),
    ("start_with_all", "private SupplicantP2pV3Calls mV3Calls = SupplicantP2pV3Calls.none();",
     "private SupplicantP2pV3Calls mV3Calls = SupplicantP2pV3Calls.of(3, SupplicantP2pV3Calls.FROZEN_V3_HASH);"),
    ("hash_read_v2", "        if (version >= 3) {", "        if (version >= 2) {"),
    ("hash_unread", "                hash = iface.getInterfaceHash();", "                hash = iface.toString();"),
    ("find_gate_version", "            if (!mV3Calls.has(SupplicantP2pV3Calls.FIND)) {",
     "            if (getCachedServiceVersion() < 3) {"),
    ("find_gate_inverted", "            if (!mV3Calls.has(SupplicantP2pV3Calls.FIND)) {",
     "            if (mV3Calls.has(SupplicantP2pV3Calls.FIND)) {"),
    ("find_unimpl_drops_iface", "if (mV3Calls.dropIfUnimplemented(SupplicantP2pV3Calls.FIND, e.getMessage())) {",
     "if (false) {"),
    ("connect_gate_version", "            if (mV3Calls.has(SupplicantP2pV3Calls.CONNECT)) {",
     "            if (getCachedServiceVersion() >= 3) {"),
    ("connect_no_fallback", "                if (pin != null || mV3Calls.has(SupplicantP2pV3Calls.CONNECT)) {",
     "                if (true) {"),
    ("connect_unimpl_drops_iface",
     "if (mV3Calls.dropIfUnimplemented(SupplicantP2pV3Calls.CONNECT, e.getMessage())) {", "if (false) {"),
    ("connect_wrong_call", "if (mV3Calls.dropIfUnimplemented(SupplicantP2pV3Calls.CONNECT, e.getMessage())) {",
     "if (mV3Calls.dropIfUnimplemented(SupplicantP2pV3Calls.FIND, e.getMessage())) {"),
    ("listen_gate_version", "            if (mV3Calls.has(SupplicantP2pV3Calls.EXT_LISTEN)) {",
     "            if (getCachedServiceVersion() >= 3) {"),
    ("listen_no_fallback", "                if (done || mV3Calls.has(SupplicantP2pV3Calls.EXT_LISTEN)) {",
     "                if (true) {"),
    ("listen_unimpl_drops_iface",
     "if (mV3Calls.dropIfUnimplemented(SupplicantP2pV3Calls.EXT_LISTEN, e.getMessage())) {", "if (false) {"),
]


def main(names):
    base = open(PATCH).read()
    todo = [m for m in MUTANTS if not names or m[0] in names]
    alive, broken = [], []
    env = dict(os.environ)
    with tempfile.TemporaryDirectory() as d:
        # the unmutated patch must pass first, else every mutant would count as killed
        p = os.path.join(d, "0001.patch")
        open(p, "w").write(base)
        env["WIFI_PATCH"] = p
        if subprocess.run(["sh", os.path.join(HERE, "run.sh")], env=env, capture_output=True).returncode:
            print("BROKEN the unmutated patch fails test/run.sh")
            return 1
        for name, old, new in todo:
            lines = [ln for ln in base.split("\n") if ln.startswith("+") and old in ln]
            if len(lines) != 1:
                broken.append(name)
                print(f"BROKEN {name}: {len(lines)} patch lines hold the text")
                continue
            open(p, "w").write(base.replace(lines[0], lines[0].replace(old, new), 1))
            r = subprocess.run(["sh", os.path.join(HERE, "run.sh")], env=env, capture_output=True, text=True)
            if r.returncode == 0:
                alive.append(name)
                print(f"ALIVE  {name}")
            else:
                last = [x for x in (r.stdout + r.stderr).strip().split("\n") if x][-1:]
                print(f"KILLED {name}: {last[0][:110] if last else ''}")
    print(f"{len(todo) - len(alive) - len(broken)}/{len(todo)} killed, alive {alive}, broken {broken}")
    return 1 if alive or broken else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
