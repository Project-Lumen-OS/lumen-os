#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# cec_defaults_test.sh: the stock HDMI-CEC defaults of every image (tools/devices/cec_defaults.py, the table
# devices/_image/cec_defaults.tsv, the evidence devices/_image/cec_stock.tsv): green on the tree, and red, naming the
# cause, for every way the defaults can drift on a scratch copy (a table value, a stock column, a framework RRO file,
# a device's own copy of a value, a recipe without the asset, an image without its RRO, two devices of one RRO with
# different stock). write restores a lost RRO file.
#   bash tools/devices/test/cec_defaults_test.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/cec_defaults.XXXXXX") || exit 2
trap 'rm -rf "$W"' EXIT
python3 -B - "$G" "$W" <<'EOF'
import glob, os, shutil, subprocess, sys
G, W = sys.argv[1], sys.argv[2]
T = os.path.join(W, "t")
bad = []


def ok(name, cond, detail=""):
    print(("ok   " if cond else "FAIL ") + name + ("" if cond else "  | " + str(detail)[-600:]))
    if not cond:
        bad.append(name)


def run(root, *a):
    r = subprocess.run([sys.executable, "-B", os.path.join(root, "tools/devices/cec_defaults.py"), "--root", root] + list(a),
                       capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def copy(rel):
    for src in glob.glob(os.path.join(G, rel), recursive=True):
        if os.path.isfile(src):
            dst = os.path.join(T, os.path.relpath(src, G))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)


for rel in ("tools/devices/cec_defaults.py", "tools/devices/devices.py", "tools/devices/assemble_app.py", "tools/devices/layered.py", "tools/devices/patch_file.py",
            "tools/devices/menu_layers.py", "devices/_menu/*.json", "devices/_family/*/*.menu.json", "devices/*/menu.json",
            "tools/lumen/apkinfo.py", "devices/*/device.conf", "devices/_family/*/*.conf", "devices/_vendor/*/*.conf", "devices/_family/*/app/*.layers",
            "devices/*/build.conf", "devices/*/app/*.layers", "devices/_image/cec_*.tsv", "apps/*/AndroidManifest.xml",
            "apps/**/res/values*/*.xml"):
    copy(rel)


def edit(rel, old, new):
    p = os.path.join(T, rel)
    s = open(p, encoding="utf-8").read()
    assert old in s, (rel, old)
    open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))


def red(name, want, mutate):
    """Applies mutate to a fresh copy of the scratch tree; check must be red and name want."""
    keep = os.path.join(W, "keep")
    shutil.copytree(T, keep)
    try:
        mutate()
        rc, out = run(T, "check")
        ok(name, rc == 1 and want in out, out)
    finally:
        shutil.rmtree(T)
        shutil.move(keep, T)


TABLE, STOCK = "devices/_image/cec_defaults.tsv", "devices/_image/cec_stock.tsv"
rc, out = run(G, "check")
ok("the tree: green", rc == 0 and "4 framework RROs" in out and " app recipes" in out, out)   # one recipe per model, no count to keep
rc, out = run(T, "check")
ok("the scratch copy: green", rc == 0, out)
rc, out = run(T, "nonsense")
ok("usage: exit 2", rc == 2, out)

red("table: send standby on, against every stock dump", "G0082-v6.15.62 config_cecTvSendStandbyOnSleepEnabled_default = false",
    lambda: edit(TABLE, "default  tv_send_standby_on_sleep                  0", "default  tv_send_standby_on_sleep                  1"))
red("table: Play 6 without its wake line, against its Android 11 stock", "G0058-v1.8.127 tv_wake_on_one_touch_play = 1",
    lambda: edit(TABLE, "device   dragon_healthy  tv_wake_on_one_touch_play  1\n", ""))
red("table: a reset on an unknown device", "reset on z9y: no such supported device",
    lambda: edit(TABLE, "reset    goat_elegant", "reset    z9y"))
red("table: a reset of a setting the app never writes", "reset of power_control_mode",
    lambda: edit(TABLE, "reset    goat_elegant    tv_wake_on_one_touch_play", "reset    goat_elegant    power_control_mode"))
red("table: a device line equal to the default", "equals the default",
    lambda: edit(TABLE, "device   dragon_healthy  tv_wake_on_one_touch_play  1", "device   dragon_healthy  tv_wake_on_one_touch_play  0"))
red("table: a value the setting does not have", "power_control_mode has no value tv",
    lambda: edit(TABLE, "to_tv_and_audio_system", "tv"))
red("table: a setting without a default", "no default for set_menu_language",
    lambda: edit(TABLE, "default  set_menu_language                         1\n", ""))
red("stock: one HB dump differs", "G0130-v6.16.24 config_cecTvWakeOnOneTouchPlayEnabled_default = true",
    lambda: edit(STOCK, "config_cecTvWakeOnOneTouchPlayEnabled_default                   true   false  false  false  false",
                 "config_cecTvWakeOnOneTouchPlayEnabled_default                   true   false  false  false  true "))
red("stock: a device without a dump of its own stock", "no stock dump of h0063",
    lambda: edit(STOCK, "h0062,h0063", "h0062"))
red("stock: a column without a dump line", "column G0155-v1.1.42 has no dump line",
    lambda: edit(STOCK, "dump  G0155-v1.1.42   g0155           ", "dump  G0155-v1.1.43   g0155           "))
red("stock: Play 6 without its wake set line", "has no set line for tv_wake_on_one_touch_play",
    lambda: edit(STOCK, "set  G0058-v1.8.127  tv_wake_on_one_touch_play  1\n", ""))
red("RRO: the Z9X file edited by hand", "apps/Z9xFrameworkKeysOverlay/res/values/lumen_cec.xml differs",
    lambda: edit("apps/Z9xFrameworkKeysOverlay/res/values/lumen_cec.xml",
                 '<bool name="config_cecTvSendStandbyOnSleepEnabled_default">false</bool>',
                 '<bool name="config_cecTvSendStandbyOnSleepEnabled_default">true</bool>'))
red("RRO: the X50 file missing", "apps/X50PowerOverlay/res/values/lumen_cec.xml missing",
    lambda: os.remove(os.path.join(T, "apps/X50PowerOverlay/res/values/lumen_cec.xml")))
red("own copy: a CEC value back in the Z9X config.xml", "apps/Z9xFrameworkKeysOverlay/res/values/config.xml sets "
    "config_cecTvSendStandbyOnSleepEnabled_default",
    lambda: edit("apps/Z9xFrameworkKeysOverlay/res/values/config.xml", "<resources>",
                 '<resources>\n    <bool name="config_cecTvSendStandbyOnSleepEnabled_default">true</bool>'))
red("own copy: a CEC value in another app's resources", "apps/LumenProjector/core/res/values/zz_cec.xml sets",
    lambda: open(os.path.join(T, "apps/LumenProjector/core/res/values/zz_cec.xml"), "w").write(
        '<resources><bool name="config_cecTvWakeOnOneTouchPlayEnabled_default">true</bool></resources>\n'))
red("recipe: the HB app without the table asset", "devices/goat_elegant/app/HbProjector.layers: has the lumen-cec module",
    lambda: edit("devices/goat_elegant/app/HbProjector.layers",
                 "file      devices/_image/cec_defaults.tsv assets/lumen_cec/defaults.tsv\n", ""))
red("image: the RS30 without its framework RRO", "devices/g0155/build.conf: 0 framework RROs",
    lambda: edit("devices/_family/rs30/rs30.build.conf", "X50PowerOverlay ", ""))
red("RRO: two devices of one overlay with different stock", "whose stock CEC values differ: one RRO cannot carry both",
    lambda: edit(TABLE, "reset    goat_elegant", "device   g0155  set_menu_language  0\nreset    goat_elegant"))

# write restores a lost file byte for byte
want = open(os.path.join(T, "apps/HbFrameworkOverlay/res/values/lumen_cec.xml"), encoding="utf-8").read()
os.remove(os.path.join(T, "apps/HbFrameworkOverlay/res/values/lumen_cec.xml"))
rc, out = run(T, "write")
got = open(os.path.join(T, "apps/HbFrameworkOverlay/res/values/lumen_cec.xml"), encoding="utf-8").read()
ok("write: restores the HB file byte for byte", rc == 0 and got == want and "wrote apps/HbFrameworkOverlay" in out, out)
rc, out = run(T, "write")
ok("write: nothing to write on a clean tree", rc == 0 and "wrote" not in out, out)
ok("the rendered file: every _default bool of every setting, one true each (21 settings)",
   got.count("<bool name=") == 44 and got.count(">true</bool>") == 21, got)

print("cec_defaults_test: %d failed" % len(bad) if bad else "cec_defaults_test: all green")
sys.exit(1 if bad else 0)
EOF
