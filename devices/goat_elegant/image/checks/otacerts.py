"""otacerts.zip (make_otacerts.py on the Mac): exactly ota.x509.pem then ota_next.x509.pem, stored, each one
RSA-4096 certificate, the two different.
  otacerts.py OTACERTS_ZIP
"""
import subprocess, sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
names = [i.filename for i in z.infolist()]
if names != ["ota.x509.pem", "ota_next.x509.pem"]:
    sys.exit("ERROR: otacerts: entries %s, want ota.x509.pem, ota_next.x509.pem" % names)
der = []
for i in z.infolist():
    b = z.read(i)
    if i.compress_type != zipfile.ZIP_STORED or b.count(b"-----BEGIN ") != 1 or not b.startswith(b"-----BEGIN CERTIFICATE-----"):
        sys.exit("ERROR: otacerts: %s is not one stored PEM certificate" % i.filename)
    t = subprocess.run(["openssl", "x509", "-noout", "-text"], input=b, capture_output=True).stdout.decode()
    if "rsaEncryption" not in t or "Public-Key: (4096 bit)" not in t:
        sys.exit("ERROR: otacerts: %s is not an RSA-4096 certificate" % i.filename)
    der.append(subprocess.run(["openssl", "x509", "-outform", "DER"], input=b, capture_output=True).stdout)
if not der[0] or der[0] == der[1]:
    sys.exit("ERROR: otacerts: ota and ota_next are the same certificate")
print("otacerts: ota.x509.pem and ota_next.x509.pem, two RSA-4096 certificates, stored")
