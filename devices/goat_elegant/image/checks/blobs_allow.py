"""The HB blobs allow-list names exactly the two files the test builds carry (the pinned stock
libstagefright_foundation.so, the staged audio policy file), with one from= per stock vendor file.
  blobs_allow.py BLOBS_ALLOW SF_SHA AUDIO_POLICY VENDOR_AUDIO_POLICY...
"""
import hashlib, re, sys
allow, sf_sha, ap = sys.argv[1:4]
vendors = sys.argv[4:]
def sha(b):
    return hashlib.sha256(b).hexdigest()
sets, rows = [], []
for i, line in enumerate(open(allow, encoding="utf-8"), 1):
    line = line.rstrip("\n")
    if not line.strip() or line.startswith("#"):
        continue
    if line.startswith("set="):
        sets.append(line[4:])
        continue
    rows.append(line.split(" "))
if len(sets) != 1 or not re.fullmatch(r"hb-[A-Za-z0-9.-]+", sets[0]):
    sys.exit("ERROR: blobs: want one set=hb-... line, got %s" % sets)
SF = "/apex/com.android.vndk.v33/lib/libstagefright_foundation.so"
AP = "/vendor/etc/audio_policy_configuration.xml"
ap_sha = sha(open(ap, "rb").read())
want = [[sf_sha, "lib/hb_vndk33/libstagefright_foundation.so", SF, "bind=" + SF],
        [ap_sha, "etc/xgimi/hb_audio_policy_configuration.xml", AP, "bind=" + AP]]
if [r[:4] for r in rows] != want or len(rows[0]) != 4 or len(rows[1]) != 4 + len(vendors):
    sys.exit("ERROR: blobs: rows %s, want %s (+ one from= per stock vendor file on the audio policy)" % (rows, want))
done = []
for opt, vendor in zip(rows[1][4:], vendors):
    m = re.fullmatch(r"from=([0-9a-f]{64}):((?:\d+,\d+d;)*\d+,\d+d)", opt)
    v = open(vendor, "rb").read()
    if not m or m.group(1) != sha(v):
        sys.exit("ERROR: blobs: %s is not the sha256 of %s with N,Md deletes" % (opt, vendor))
    vl, drop = v.split(b"\n"), set()
    for a, b in re.findall(r"(\d+),(\d+)d", m.group(2)):
        drop |= set(range(int(a), int(b) + 1))
    if sha(b"\n".join(x for i, x in enumerate(vl, 1) if i not in drop)) != ap_sha:
        sys.exit("ERROR: blobs: the from= deletes of %s do not give the audio policy file" % vendor)
    done.append("vendor %s... minus %s" % (m.group(1)[:8], m.group(2)))
print("blobs: allow-list set=%s: libstagefright_foundation %s..., audio policy %s... (= %s)"
      % (sets[0], sf_sha[:8], ap_sha[:8], "; = ".join(done)))
