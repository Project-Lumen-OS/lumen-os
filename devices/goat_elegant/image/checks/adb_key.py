"""--dsu-test: the build host's adb PUBLIC key is one key line (never a private key).
  adb_key.py ADBKEY_PUB
"""
import re
import sys
k = open(sys.argv[1], encoding="ascii").read()
if not re.fullmatch(r"[A-Za-z0-9+/]{500,}={0,2}( [!-~]+)?\n?", k):
    sys.exit("ERROR: dsu-test: %s is not one adb public key line" % sys.argv[1])
print("dsu-test: adb key of %s" % (k.split()[1] if len(k.split()) > 1 else "?"))
