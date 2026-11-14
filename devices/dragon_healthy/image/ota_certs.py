"""--ota only: /system/etc/security/otacerts.zip = the Play 6 OTA certificate only (the one the patched recovery
adds), stored with a fixed time, mode 0644.
  ota_certs.py CERT OTACERTS_ZIP
"""
import os
import sys, zipfile
with zipfile.ZipFile(sys.argv[2], "w") as z:
    zi = zipfile.ZipInfo("lumen_play6_ota.x509.pem", (2009, 1, 1, 0, 0, 0))
    zi.external_attr = 0o100644 << 16
    z.writestr(zi, open(sys.argv[1], "rb").read())
os.chmod(sys.argv[2], 0o644)
