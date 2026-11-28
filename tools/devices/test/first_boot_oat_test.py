#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""first_boot_oat_test.py: no image recipe removes a base dexpreopt file (tools/build/build.py removes_dexpreopt).

Replays the rule on the recipes that shipped: the Z9X 1.1.0-beta2 recipe (6dc95fbe9, fresh installs from Lumen OS Live
came up) has no such row; the beta4/beta5 recipes (cf57d60eb, 547b44d45: the Wi-Fi APEX swap deleted the service-wifi.jar
odex and vdex, no fresh install from Live came up) and the X50 family recipe of that time have two each; the tree's
recipes have none. Commits missing from a shallow clone are skipped and named.
  python3 tools/devices/test/first_boot_oat_test.py
"""
import os, subprocess, sys, tempfile
G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.insert(0, os.path.join(G, "tools", "build"))
import build    # noqa: E402
import recipe   # noqa: E402

CASES = [("6dc95fbe9", "devices/horse_popular/image/steps.tsv", 0),
         ("cf57d60eb", "devices/horse_popular/image/steps.tsv", 2),
         ("547b44d45", "devices/horse_popular/image/steps.tsv", 2),
         ("547b44d45", "devices/g0111/image/steps.tsv", 2),
         ("066ef742b", "devices/_family/rs20/image/steps.tsv", 2)]
fails = 0


def check(name, path, want):
    global fails
    got = build.removes_dexpreopt(recipe.read_steps(path))
    ok = len(got) == want
    fails += not ok
    print("%s %s: %d removal(s), want %d %s" % ("ok  " if ok else "FAIL", name, len(got), want,
                                                  " ".join(x.split("/")[-1] for _, _, x in got)))


for rev, rel, want in CASES:
    r = subprocess.run(["git", "-C", G, "show", "%s:%s" % (rev, rel)], capture_output=True, text=True)
    if r.returncode:
        print("skip %s:%s (not in this clone)" % (rev, rel))
        continue
    with tempfile.NamedTemporaryFile("w", suffix=".tsv", delete=False) as f:
        f.write(r.stdout)
    try:
        check("%s %s" % (rev, rel), f.name, want)
    finally:
        os.unlink(f.name)
for top in ("devices", os.path.join("devices", "_family")):
    for c in sorted(os.listdir(os.path.join(G, top))):
        p = os.path.join(G, top, c, "image", "steps.tsv")
        if os.path.isfile(p):
            check("tree " + os.path.join(top, c), p, 0)
# the vendor PWM driver at post-fs (overlay/v1/z9x_pwm_early.rc): before gmpf_main's 12 s wait for pwmchip8 (started at
# the vendor's 'on post-fs') ends, whatever early-boot's delay. HELD out of the Z9X recipe until a live cold start plus
# sleep/wake on a Z9X (2026-11-28): the file stays as it is, placed 0 times; 1 once the live gate passed.
PWM_RC_PLACED = 0
rc = open(os.path.join(G, "overlay", "v1", "z9x_pwm_early.rc")).read().split("\n")
body = [ln for ln in rc if ln.strip() and not ln.startswith("#")]
want = ["on post-fs", "    exec u:r:vendor_modprobe:s0 -- /vendor/bin/modprobe -d /vendor/lib/modules pwm-mt5896.ko"]
z = [a for _, _, op, a in recipe.read_steps(os.path.join(G, "devices", "horse_popular", "image", "steps.tsv"))
     if op == "tar_add" and a[:2] == ["system/etc/init/z9x_pwm_early.rc", "z9x_pwm_early.rc"]]
ok = body == want and len(z) == PWM_RC_PLACED
fails += not ok
print("%s z9x_pwm_early.rc: %s, placed by the Z9X recipe %d time(s)" % ("ok  " if ok else "FAIL", body, len(z)))
print("first_boot_oat: %s" % ("FAILED %d" % fails if fails else "OK"))
sys.exit(1 if fails else 0)
