"""The Google add-on targets: each overlaid tree is a real directory, /product and /system_ext link into /system,
unzip is there, and no allow-listed path is already in the image.
  gapps_targets.py ROOT GAPPS_ALLOW
"""
import os, sys
root, allow = sys.argv[1], sys.argv[2]
S = os.path.join(root, "system")
def fail(m):
    sys.exit("ERROR: gapps targets: " + m)
for d in ("product", "system_ext/priv-app", "system_ext/etc/permissions", "app", "etc/permissions", "etc/sysconfig"):
    p = os.path.join(S, d)
    if os.path.islink(p) or not os.path.isdir(p):
        fail("/system/%s is not a directory" % d)
for l, t in (("product", "/system/product"), ("system_ext", "/system/system_ext")):
    p = os.path.join(root, l)
    if not os.path.islink(p) or os.readlink(p) != t:
        fail("/%s is not a link to %s" % (l, t))
if not os.path.lexists(os.path.join(S, "bin", "unzip")):
    fail("no /system/bin/unzip")
n = 0
for line in open(allow, encoding="utf-8"):
    f = line.split()
    if len(f) != 3 or line.startswith("#") or f[0].startswith("zip="):
        continue
    rel = f[2] if f[2].startswith("system/") else "system/" + f[2]
    if os.path.lexists(os.path.join(root, rel)):
        fail("%s is already in the image" % rel)
    n += 1
print("gapps targets: 6 trees are directories, /product and /system_ext link into /system, unzip there, %d add-on paths not in the image" % n)
