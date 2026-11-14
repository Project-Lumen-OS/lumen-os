"""The Play 6 props, read last by init, and D's experiments (6.2):
product_prop.txt with the '#expN ' lines of the chosen experiments live, the version, version code and build id,
ro.lumen.p6.exp, adb over USB, the test-build log props; EXP-2 and EXP-3 also install play6_perf.rc.
  play6_props.py IN SYS VERSION VCODE BUILD_ID EXPS FLAGS     (EXPS: N[,N...] or -, FLAGS comma separated)
"""
import os
import re
import sys

indir, sysdir, version, vcode, build_id, exps, flags = sys.argv[1:8]
flags = set(flags.split(","))
exps = "" if exps == "-" else exps
if exps and not re.match(r"^(,[1-5])+,$", "," + exps + ","):
    sys.exit("ERROR: --exp=N[,N...] takes experiments 1 to 5, not '%s'" % exps)
live = re.compile(r"^#exp(%s) " % exps.replace(",", "|")) if exps else None


def exp_filter(name):
    text = open(os.path.join(indir, name), encoding="utf-8").read()
    return "".join(live.sub("", l) for l in text.splitlines(True)) if live else text


p = "\n" + exp_filter("product_prop.txt")
p += "ro.lumen.version=%s\nro.lumen.version_code=%s\nro.lumen.build_id=%s\n" % (version, vcode, build_id)
if exps:
    p += "ro.lumen.p6.exp=%s\n" % exps
if "usbadb" in flags:
    p += "persist.sys.usb.config=adb\n"
if "diag" in flags:
    p += "ro.logd.auditd=false\npersist.logd.size=2097152\n"
with open(os.path.join(sysdir, "product/etc/build.prop"), "a") as f:
    f.write(p)
nums = set(exps.split(",")) if exps else set()
if nums & {"2", "3"}:
    d = os.path.join(sysdir, "product/etc/init")
    os.makedirs(d, mode=0o755, exist_ok=True)
    with open(os.path.join(d, "play6_perf.rc"), "w") as f:
        f.write(exp_filter("play6_perf.rc"))
    os.chmod(os.path.join(d, "play6_perf.rc"), 0o644)
if "gms" in flags:
    for b in ("build.prop", "product/etc/build.prop", "system_ext/etc/build.prop"):
        f = os.path.join(sysdir, b)
        if os.path.exists(f) and re.search(r"^ro\.lumen\.gms=", open(f).read(), re.M):
            sys.exit("ERROR: gms: a build.prop sets ro.lumen.gms, the no-Google mark")
for e in sorted(nums):
    if not any(re.search(r"^#exp%s " % e, open(os.path.join(indir, n)).read(), re.M)
               for n in ("product_prop.txt", "play6_perf.rc") if os.path.exists(os.path.join(indir, n))):
        sys.exit("ERROR: --exp: EXP-%s has no lines" % e)
print("props: version %s, code %s, build id %s%s" % (version, vcode, build_id, ", experiments EXP-" + exps if exps else ""))
