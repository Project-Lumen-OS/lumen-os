"""The HB Google add-on allow-list has the shape of a 32-bit arm list (made and fully checked on the Mac by
hb_gapps_allow.py): zip= lines name only arm ATV zips, every file line has three fields under the five trees.
  gapps_shape.py GAPPS_ALLOW
"""
import re, sys
zips = files = 0
TREES = ("product/", "system_ext/priv-app/", "system_ext/etc/permissions/", "system/app/", "system/etc/permissions/")
for i, line in enumerate(open(sys.argv[1], encoding="utf-8"), 1):
    line = line.rstrip("\n")
    if not line.strip() or line.startswith("#"):
        continue
    if line.startswith("zip="):
        if not re.fullmatch(r"zip=[0-9a-f]{64} MindTheGapps-[0-9.]+-arm-ATV-[A-Za-z0-9_.-]+\.zip size=\d+ part=\d+ "
                            r"gmsversion=[0-9A-Za-z_.-]{1,32} url=https://github\.com/MindTheGapps/[A-Za-z0-9_./-]+\.zip", line):
            sys.exit("ERROR: gapps: line %d: not a zip= line of a 32-bit arm ATV zip" % i)
        zips += 1
        continue
    m = re.fullmatch(r"[0-9a-f]{64} \d+ ([A-Za-z0-9_@+.-]+(?:/[A-Za-z0-9_@+.-]+)+)", line)
    if not zips or not m or not m.group(1).startswith(TREES) or ".." in m.group(1).split("/"):
        sys.exit("ERROR: gapps: line %d: bad file line" % i)
    files += 1
if not zips or not files:
    sys.exit("ERROR: gapps: no zip block")
print("gapps: HB allow-list shape ok, %d arm zip(s), %d file lines" % (zips, files))
