"""None of the HB OTA certificates is one of the base otacerts.zip (the AOSP test key).
  otacerts_base.py BASE_OTACERTS OURS
"""
import subprocess, sys, zipfile
def ders(p):
    z = zipfile.ZipFile(p)
    return {subprocess.run(["openssl", "x509", "-outform", "DER"], input=z.read(n), capture_output=True).stdout: n
            for n in z.namelist()}
base, ours = ders(sys.argv[1]), ders(sys.argv[2])
same = [ours[d] for d in ours if d in base]
if same or b"" in ours:
    sys.exit("ERROR: otacerts: %s is a base (AOSP) certificate" % same)
print("otacerts: replaces the base's %s; none of ours is a base certificate" % ", ".join(sorted(base.values())))
