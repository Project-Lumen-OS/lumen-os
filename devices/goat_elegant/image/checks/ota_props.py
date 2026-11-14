"""The HB OTA props: exactly the four lines of hb_ota_props.txt in order (ota.conf list, design decision: no mboot,
no vbmeta), the manifest on the tag z9x-hb; CHANNEL hb-test reads update-hb-test.json there instead. OUT gets
the four lines for CHANNEL.
  ota_props.py HB_OTA_PROPS CHANNEL [OUT]
"""
import re, sys
src, ch = sys.argv[1:3]
out = sys.argv[3] if len(sys.argv) > 3 else None
rows = [l.rstrip("\n") for l in open(src, encoding="utf-8") if l.strip() and not l.startswith("#")]
keys = [r.split("=", 1)[0] for r in rows]
want = ["ro.product.ab_ota_partitions", "ro.lumen.ota.gate", "ro.lumen.ota.device", "ro.lumen.ota.manifest_url"]
if keys != want:
    sys.exit("ERROR: ota props: keys %s, want %s" % (keys, want))
v = dict(r.split("=", 1) for r in rows)
LIST = "armfw,boot,bootdata,dtbo,optee,tvconfig,tvservice,vbmeta_system,xgimiconfig"
BASE = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/z9x-hb/"
if v[want[0]] != LIST:
    sys.exit("ERROR: ota props: ab_ota_partitions=%s, want %s (ota.conf, decision O3)" % (v[want[0]], LIST))
if v[want[1]] != "1" or v[want[2]] != "hb" or v[want[3]] != BASE + "update-hb-nogms.json":
    sys.exit("ERROR: ota props: gate=%s device=%s url=%s" % (v[want[1]], v[want[2]], v[want[3]]))
if ch not in ("hb-nogms", "hb-test"):
    sys.exit("ERROR: ota props: channel %s" % ch)
v[want[3]] = BASE + "update-%s.json" % ch
lines = ["%s=%s" % (k, v[k]) for k in want]
if out:
    open(out, "w").write("\n".join(lines) + "\n")
print("ota props: %s, channel %s" % (", ".join("%s=%s" % (k, v[k]) for k in want[:3]), ch), file=sys.stderr)
