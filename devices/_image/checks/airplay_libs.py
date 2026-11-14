"""The 32-bit userspace loads the AirPlay receiver's native code from the APK: its armeabi-v7a libraries must be
stored uncompressed and page-aligned (4096).
  airplay_libs.py APK
"""
import struct, sys, zipfile
apk = open(sys.argv[1], "rb").read()
libs = [i for i in zipfile.ZipFile(sys.argv[1]).infolist()
        if i.filename.startswith("lib/armeabi-v7a/") and i.filename.endswith(".so")]
if not libs or any(i.compress_type != zipfile.ZIP_STORED for i in libs):
    sys.exit("ERROR: airplay: armeabi-v7a libraries %s" % [(i.filename, i.compress_type) for i in libs])
def start(i):   # data offset: local header (30 bytes) + name + local extra (zipalign's padding)
    h = apk[i.header_offset:i.header_offset + 30]
    if h[:4] != b"PK\x03\x04":
        return -1
    n, e = struct.unpack("<HH", h[26:30])
    return i.header_offset + 30 + n + e
bad = [i.filename for i in libs if start(i) < 0 or start(i) % 4096]
if bad:
    sys.exit("ERROR: airplay: armeabi-v7a libraries not page-aligned (4096): %s" % ", ".join(bad))
print("airplay: %s stored uncompressed, page-aligned" % ", ".join(i.filename for i in libs))
