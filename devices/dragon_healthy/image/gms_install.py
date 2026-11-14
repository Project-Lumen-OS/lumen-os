"""--gms only, the Google test edition: the MindTheGapps files of IN/gms installed as
the zip's own installer does (dirs 0755, files 0644, 0755 for *.sh and files without an extension), each checked
against gms/members.txt first; nothing may already be at its path.
  gms_install.py GMS_DIR TOP
"""
import hashlib, os, re, shutil, sys
src, top = sys.argv[1:3]
def fail(m):
    sys.exit("ERROR: gms: " + m)
def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()
files = []
for line in open(os.path.join(src, "members.txt")):
    line = line.rstrip("\n")
    if not line or line.startswith("#") or line.startswith("zip="):
        continue
    m = re.fullmatch(r"([0-9a-f]{64}) (\d+) ([A-Za-z0-9_@+.-]+(?:/[A-Za-z0-9_@+.-]+)+)", line)
    if not m or ".." in m.group(3).split("/"):
        fail("bad members.txt line %r" % line)
    p = m.group(3)
    files.append((m.group(1), int(m.group(2)), p if p.startswith("system/") else "system/" + p))
staged = set()
for d, _, fs in os.walk(os.path.join(src, "system")):
    for n in fs:
        staged.add(os.path.relpath(os.path.join(d, n), src))
want = {ip for _, _, ip in files}
if staged != want:
    fail("staged files differ from members.txt: extra %s, missing %s" % (sorted(staged - want)[:5], sorted(want - staged)[:5]))
total = 0
for h, size, ip in files:
    f = os.path.join(src, ip)
    if os.path.islink(f) or os.path.getsize(f) != size or sha(f) != h:
        fail("%s is not the pinned file (size %d, sha256 %s)" % (ip, size, h))
    dst = os.path.join(top, ip)
    if os.path.lexists(dst):
        fail("%s is already in the composed tree" % ip)
    d = os.path.dirname(dst)
    missing = []
    while not os.path.isdir(d):
        missing.append(d)
        d = os.path.dirname(d)
    for d in reversed(missing):
        os.mkdir(d)
        os.chmod(d, 0o755)
    shutil.copyfile(f, dst)
    base = os.path.basename(ip)
    os.chmod(dst, 0o755 if base.endswith(".sh") or "." not in base else 0o644)
    total += size
print("gms: %d Google files installed (%d bytes), as shipped" % (len(files), total))
