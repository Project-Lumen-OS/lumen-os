"""The HB props appended to /system/product/etc/build.prop, which init reads last (it wins over the vendor):
hb_prop.txt (a release swaps its test lines for adb secured), the version and build id, hb_lumen_prop.txt (a public
release drops the gates of the two files it does not ship), the version code, the OTA lines, the About rows.
  hb_props.py IN OUT TAG VNAME BUILD_ID VCODE OTA_CHANNEL FLAGS     (FLAGS: the variant's flags, comma separated)
"""
import os
import re
import subprocess
import sys

indir, out, tag, vname, build_id, vcode, otach, flags = sys.argv[1:9]
flags = set(flags.split(","))
pub, rel, lumen = "pub" in flags, "rel" in flags, "lumen" in flags
TEST_LINES = re.compile(r"^(ro\.adb\.secure=0|ro\.logd\.auditd=false|persist\.logd\.size=.*)$")
GATES = re.compile(r"^ro\.lumen\.hb\.(c2foundation|audiopolicy)=1$")


def die(m):
    sys.exit("ERROR: props: " + m)


def rows(name, drop=None):
    """grep -v '^#' | sed '/^$/d' [| grep -v -E DROP]"""
    text = open(os.path.join(indir, name), encoding="utf-8").read().split("\n")
    return [l for l in text if not l.startswith("#") and l != "" and not (drop and drop.match(l))]


kind = "release" if pub else "test build" if lumen else "first-boot test"
p = ["", "# ---- Lumen OS HB %s (%s), devices/goat_elegant/image/compose_hb.sh" % (kind, tag)]
p += rows("hb_prop.txt", TEST_LINES) + ["ro.adb.secure=1", "persist.sys.usb.config=none"] if rel else rows("hb_prop.txt")
p += ["ro.lumen.version=" + vname, "ro.lumen.build_id=" + build_id]
ota = []
if lumen:
    p += rows("hb_lumen_prop.txt", GATES if pub else None)
    p.append("ro.lumen.version_code=" + vcode)
    if pub:
        tmp = out + ".ota"
        subprocess.run([sys.executable, "-I", os.path.join(indir, "ota_props.py"), os.path.join(indir, "hb_ota_props.txt"),
                        otach, tmp], check=True)
        ota = open(tmp).read().rstrip("\n").split("\n")
        os.unlink(tmp)
        p += ota
    p += ["ro.build.display.id=Lumen OS %s (%s)" % (vname, build_id), "ro.lineage.display.version=%s (kmuradoff)" % vname]
if "usbadb" in flags:
    p.append("persist.sys.usb.config=adb")
if rel:
    for l in ("ro.adb.secure=1", "persist.sys.usb.config=none", "service.adb.tcp.port=5555"):
        if l not in p:
            die("release lacks " + l)
    for l in ("ro.adb.secure=0", "persist.sys.usb.config=adb", "ro.logd.auditd=false"):
        if l in p:
            die("release still has the test line " + l)
if pub:
    for l in ("ro.lumen.version=" + vname, "ro.lumen.version_code=" + vcode, "ro.build.display.id=Lumen OS %s (%s)" % (vname, build_id)):
        if l not in p:
            die("release lacks " + l)
    if any(re.match(r"^ro\.lumen\.hb\.(c2foundation|audiopolicy)=", l) for l in p):
        die("release still has a gate of a file it does not ship")
    for l in ota:
        if sum(x.startswith(l.split("=", 1)[0] + "=") for x in p) != 1 or l not in p:
            die("release lacks the OTA line %s (once)" % l)
open(out, "w").write("\n".join(p) + "\n")
print("props: %d lines to append to /system/product/etc/build.prop" % sum("=" in l for l in p))
