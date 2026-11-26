#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the A2DP and About fixes and of base_patch.py: each one must turn test/run.sh red (run.sh green first).
  python3 tools/base_patch/test/mutate.py"""
import os
import shutil
import subprocess
import sys
import tempfile

T = os.path.dirname(os.path.abspath(__file__))
P = os.path.dirname(T)
FIX = "tools/base_patch/tvsettings_a2dp/BluetoothA2dpConnector.java"
AB = "tools/base_patch/tvsettings_about/AboutFragment.java"
BP = "tools/base_patch/base_patch.py"
SD = "tools/devices/sign_device.py"
COPY = ("tools/base_patch", "tools/devices", "tools/sign", "tools/play6", "devices")
EARLY = ("            if (isTargetConnected()) { // Already connected, no need to wait.\n"
         "                mA2dpProfile.setConnectionPolicy(mTarget,\n"
         "                        BluetoothProfile.CONNECTION_POLICY_ALLOWED);\n")
LATE = ("                    if (isTargetConnected()) { // False timeout\n"
        "                        mA2dpProfile.setConnectionPolicy(mTarget,\n"
        "                                BluetoothProfile.CONNECTION_POLICY_ALLOWED);\n")
MUTANTS = {
    # the fix
    "early_check_off": (FIX, "if (isTargetConnected()) { // Already connected", "if (false) { // Already connected"),
    "timeout_check_off": (FIX, "if (isTargetConnected()) { // False timeout", "if (false) { // False timeout"),
    "any_device_counts": (FIX, "return connectedDevices.contains(mTarget);", "return !connectedDevices.isEmpty();"),
    "early_keeps_waiting": (FIX, "                succeeded();\n                return;\n", "                succeeded();\n"),
    "early_no_policy": (FIX, EARLY, "            if (isTargetConnected()) { // Already connected, no need to wait.\n"),
    "timeout_no_policy": (FIX, LATE, "                    if (isTargetConnected()) { // False timeout\n"),
    "timeout_always_ok": (FIX, "Log.w(TAG, \"handleMessage(MSG_CONNECT_TIMEOUT)\");\n                        failed();",
                          "Log.w(TAG, \"handleMessage(MSG_CONNECT_TIMEOUT)\");\n                        succeeded();"),
    "connecting_only_kept": (FIX, "if (previousState == BluetoothA2dp.STATE_CONNECTING) {",
                             "if (previousState == BluetoothA2dp.STATE_CONNECTING && state != BluetoothA2dp.STATE_CONNECTED) {"),
    # base_patch.py
    "base_pin_off": (BP, "    if have != r[\"base\"]:\n", "    if False:\n"),
    "out_pin_off": (BP, "    if sha256(data) != r[\"out\"]:\n", "    if False:\n"),
    "dex_deflated": (BP, "if i.compress_type == zipfile.ZIP_STORED:\n                    payload = data",
                     "if False:\n                    payload = data"),
    "other_entries_changed": (BP, "payload = f.read(i.compress_size)",
                              "payload = f.read(i.compress_size)[:-1] + b\"\\x5a\""),
    "changed_class_passes": (BP, "    if diff:\n        raise", "    if False:\n        raise"),
    "lost_class_passes": (BP, "    if missing:\n        raise", "    if False:\n        raise"),
    "nested_not_swapped": (BP, "return name == cls or name.startswith(cls[:-1] + \"$\")", "return name == cls"),
    "opcode_normalised_away": (BP, "(re.compile(r\"^\\|[0-9a-f]{4}: \", re.M), \"|\"),",
                               "(re.compile(r\"^\\|[0-9a-f]{4}: [a-z-/0-9]+\", re.M), \"|\"),"),
    "other_file_patched": (BP, "return [r for r in rows() if r[\"path\"] == path]",
                           "return [r for r in rows() if path in (r[\"path\"], r[\"id\"])]"),
    "chain_last_only": (BP, "    for r in fixes:\n", "    for r in fixes[-1:]:\n"),
    "chain_first_only": (BP, "    for r in fixes:\n", "    for r in fixes[:1]:\n"),
    "build_skips_chain": (BP, "        dex = open(fixed_dex(prev, dex, out_dir), \"rb\").read()\n", "        pass\n"),
    "link_never_stops": (BP, "    if bad:\n        raise Stop(f\"{len(bad)} references", "    if False:\n        raise Stop(f\"{len(bad)} references"),
    "link_any_static": (BP, "            elif where != op.startswith(STATIC_OPS) and not holder.startswith(\"[\"):",
                        "            elif False:"),
    "link_no_platform_members": (BP, "        if key in mem:\n            return mem[key]\n        todo.extend",
                                 "        if key in mem or c not in classes:\n            return mem.get(key, False)\n        todo.extend"),
    "link_no_inheritance": (BP, "        todo.extend(([sup] if sup else []) + ifs)\n    return None",
                            "    return None"),
    "classfile_long_one_slot": (BP, "            pos += 9\n            i += 1\n", "            pos += 9\n"),
    "classfile_static_off": (BP, "mem[(utf[nm], utf[desc])] = bool(acc & 0x0008)", "mem[(utf[nm], utf[desc])] = False"),
    # the About fix
    "about_any_app": (AB, "\n                    || (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {", ") {"),
    "about_any_key": (AB, "key == null || !key.startsWith(\"lumen_\") || ", "key == null || "),
    "about_no_title_check": (AB, " || TextUtils.isEmpty(title)) continue;", ") continue;"),
    "about_failure_breaks_page": (AB, "} catch (RuntimeException e) {", "} catch (IllegalStateException e) {"),
    "about_saved": (AB, "p.setPersistent(false);", "p.setPersistent(true);"),
    "about_screen_of_any_app": (AB, "setClassName(pi.packageName, screen)", "setClassName(\"com.android.tv.settings\", screen)"),
    "about_screen_empty_opens": (AB, "if (!TextUtils.isEmpty(screen)) p.setIntent", "if (screen != null) p.setIntent"),
    "about_model_moves": (AB, "if (p.getOrder() > order) p.setOrder", "if (p.getOrder() >= order) p.setOrder"),
    "about_rows_on_model": (AB, "p.setOrder(++order);", "p.setOrder(order++);"),
    "about_rest_not_moved": (AB, "p.setOrder(p.getOrder() + rows.size());", "p.setOrder(p.getOrder());"),
    "about_not_called": (AB, "        LumenRows.insert(screen, findPreference(KEY_DEVICE_MODEL),\n                LumenRows.of(getPreferenceManager().getContext()));\n", ""),
    "about_lineage_line_dropped": (AB, "        updateTutorials();\n\n        LumenRows", "\n        LumenRows"),
    # the signer
    "signer_bypasses_fix": (SD, "resign_input(r, work), r[\"out_file\"]", "r[\"file\"], r[\"out_file\"]"),
    "signer_signs_base": (SD, "    return p or r[\"file\"]", "    return r[\"file\"]"),
}


def run(tree):
    p = subprocess.run(["sh", os.path.join(tree, "tools", "base_patch", "test", "run.sh")], stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, text=True, env=dict(os.environ, BASE_APK=""))
    return p.returncode, p.stdout


def main():
    work = tempfile.mkdtemp(prefix="base-patch-mut-")
    try:
        tree = os.path.join(work, "g")
        g = os.path.dirname(os.path.dirname(P))
        for d in COPY:
            shutil.copytree(os.path.join(g, d), os.path.join(tree, d), ignore=shutil.ignore_patterns("__pycache__"))
        rc, out = run(tree)
        if rc != 0:
            print(out)
            print("run.sh is red without a mutant: fix that first")
            return 1
        alive = []
        for name, (rel, old, new) in MUTANTS.items():
            f = os.path.join(tree, rel)
            src = open(f, encoding="utf-8").read()
            if src.count(old) != 1:
                print(f"BROKEN {name}: anchor found {src.count(old)} times in {rel}")
                alive.append(name)
                continue
            open(f, "w", encoding="utf-8").write(src.replace(old, new))
            try:
                rc, out = run(tree)
            finally:
                open(f, "w", encoding="utf-8").write(src)
            lines = out.splitlines()
            why = next((ln for ln in lines if ln.startswith("FAIL")), "") or \
                next((ln for ln in lines if "error" in ln.lower()), "")
            print(("killed  " if rc else "ALIVE   ") + name + ("   (" + why.strip()[:90] + ")" if rc else ""))
            if not rc:
                alive.append(name)
        print(f"mutants: {len(MUTANTS) - len(alive)}/{len(MUTANTS)} killed" + (f", alive {alive}" if alive else ""))
        return 1 if alive else 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
