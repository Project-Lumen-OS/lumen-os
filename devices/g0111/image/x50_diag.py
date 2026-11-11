#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""x50_diag.py: the X50 edit of the base's boot logger /system/etc/xgimi/z9x_diag.sh (compose_x50.sh step 6b).

  x50_diag.py apply FILE          edit the base script in place (inode, owner, mode and label kept)
  x50_diag.py check BASE NEW      NEW is exactly BASE with the edit (verify_tree_x50.py runs check())

Why: the Z9X logger watches the vendor service gmpf_main, which the X50 vendor defines but never runs (its
init.svc.gmpf_main stays empty: t6 / t7 DSU events.txt "gmpf_main not running ('') for 12 s", a false alarm every boot);
the X50 gmpf HAL is the service xgimi_middle (/vendor/bin/hw/xgimi.hardware.gmpf-service, class hal, started on
post-fs-data; X50 dump vendor/etc/init/xgimi.hardware.gmpf-service.rc). And the X50 eye-protection detector logs
~30000 FaceCutoutBin lines per boot, which push the first ~24 s of every boot out of the rotating logcat
, so the capture silences that tag next to Motion_det_ctl.
The edit: every gmpf_main becomes xgimi_middle (service state, pid and boot time props, messages), the pidof fallback
names the HAL's process, and the logcat filter gains FaceCutoutBin:S. The Z9X file in overlay/v1 is not touched.
Exit 0 = OK, 1 = the base is not the expected script or NEW is not the edit, 2 = usage.
"""
import sys

sys.dont_write_bytecode = True
PIDOF_OLD = "pidof gmpf_main"
PIDOF_NEW = "pidof xgimi.hardware.gmpf-service"
LOG_OLD = "'Motion_det_ctl:S' 'XGIMI_PERCEPTION_DEBUG:S'"
LOG_NEW = "'Motion_det_ctl:S' 'XGIMI_PERCEPTION_DEBUG:S' 'FaceCutoutBin:S'"
# what the base must hold before the edit (the Z9X 1.0.9 z9x_diag.sh)
NEED = {PIDOF_OLD: 1, LOG_OLD: 1, "init.svc.gmpf_main": 2, "init.svc_debug_pid.gmpf_main": 3,
        "ro.boottime.gmpf_main": 3}


def edit(text):
    """the X50 text of the base script; raises ValueError when the base is not the expected script"""
    if not text.startswith("#!/system/bin/sh\n"):
        raise ValueError("not the base z9x_diag.sh (first line)")
    for s, n in NEED.items():
        if text.count(s) != n:
            raise ValueError("base z9x_diag.sh has %r %d times, expected %d" % (s, text.count(s), n))
    if "xgimi_middle" in text or "FaceCutoutBin" in text:
        raise ValueError("already edited")
    out = text.replace(PIDOF_OLD, PIDOF_NEW).replace(LOG_OLD, LOG_NEW).replace("gmpf_main", "xgimi_middle")
    if "gmpf_main" in out or out.count("FaceCutoutBin:S") != 1:
        raise ValueError("edit incomplete")
    return out


def check(base_path, new_path):
    """[] or the reasons NEW is not exactly the edit of BASE"""
    try:
        want = edit(open(base_path, encoding="utf-8").read())
    except (OSError, ValueError) as e:
        return ["z9x_diag.sh base: %s" % e]
    if open(new_path, encoding="utf-8").read() != want:
        return ["%s is not the X50 edit of the base z9x_diag.sh (x50_diag.py)" % new_path]
    return []


def main(a):
    if len(a) == 2 and a[0] == "apply":
        try:
            new = edit(open(a[1], encoding="utf-8").read())
        except ValueError as e:
            print("x50_diag: ERROR %s" % e, file=sys.stderr)
            return 1
        with open(a[1], "r+", encoding="utf-8") as f:   # in place: inode, owner, mode, xattrs stay
            f.seek(0)
            f.write(new)
            f.truncate()
        print("x50_diag: %s watches xgimi_middle, logcat silences FaceCutoutBin" % a[1])
        return 0
    if len(a) == 3 and a[0] == "check":
        bad = check(a[1], a[2])
        for b in bad:
            print("x50_diag: ERROR " + b)
        print("x50_diag: " + ("OK" if not bad else "FAILED"))
        return 1 if bad else 0
    print((__doc__ or "").split("\n\n")[1])
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
