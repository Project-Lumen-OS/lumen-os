#!/bin/bash
# sign_x50_test.sh: the signed SHA256SUMS of an X50 test image, made on the Mac (devices/g0111/README.md, "Signed
# test image"). The image stays where the composer wrote it (image.conf host, ~/<remote_dir>/x50-tN-net/); the ota
# key of the X50 never leaves the Mac and is never printed.
#   bash devices/g0111/image/sign_x50_test.sh tN [LOCAL_DIR]        LOCAL_DIR default ~/lumen-build-tmp/x50/tN
# 1. BUILD_INFO.txt of the build host: the image file, its size and sha256 (compose_x50.sh step 11).
# 2. LOCAL_DIR holds a copy of the image (fetched when missing or different: 32 MiB pieces over 8 ssh streams at a
#    time, each piece retried, because the jump host limits every single stream to a few hundred KiB/s); its size
#    and sha256 must be BUILD_INFO's.
# 3. LOCAL_DIR/SHA256SUMS = "<sha256>  lumen-os-x50-tN-test-system.img": the name under which the installer's x50
#    profile takes it (IMAGE_TOKEN x50). Not a release name (no -nogms-): only --unsigned-image (an own test build)
#    installs it, its signature still checked, and the Z9X installer refuses it by its name (lumen-os-x50-) as well as
#    by its signature and its build.prop. The image is linked under that name next to the list.
# 4. SHA256SUMS.sig: RSA PKCS#1 v1.5 SHA-256 with <keys_dir>/ota/ota.pk8 (as the Z9X and HB releases), whose
#    public key must be devices/g0111/certs/ota.x509.pem's (= installer/certs/x50/ota.x509.pem); verified with that
#    certificate, and refused by the Z9X release certificate (tools/sign/release_certs/ota.x509.pem).
# 5. SHA256SUMS and SHA256SUMS.sig go to the build host next to the image (sha256 compared after the copy), where the
#    image is hard-linked under the signed name and sha256sum -c checks the list.
# Exit 0 = signed and copied.
set -euo pipefail
G=$(cd "$(dirname "$0")/../../.." && pwd)
V=${1:?usage: sign_x50_test.sh tN [LOCAL_DIR]}
case $V in t[0-9]|t[0-9][0-9]) ;; *) echo "sign_x50_test: VARIANT must be tN" >&2; exit 1 ;; esac
L=${2:-$HOME/lumen-build-tmp/x50/$V}
die() { echo "sign_x50_test: $*" >&2; exit 1; }
reg() { python3 -B "$G/tools/devices/devices.py" get g0111 "$1"; }
img() { python3 -B "$G/tools/devices/devices.py" image g0111 "$1"; }
sha() { shasum -a 256 "$1" | cut -d' ' -f1; }
OPENSSL=$(command -v /opt/homebrew/bin/openssl || command -v openssl)
SRV=$(img host); RD=$(img remote_dir); TAG=x50-$V-net; RT=$RD/$TAG
NAME=lumen-os-x50-$V-test-system.img
KD=$(reg keys_dir); case $KD in "~/"*) KD=$HOME/${KD#\~/} ;; esac
KEY=$KD/ota/ota.pk8
CERT=$G/devices/g0111/certs/ota.x509.pem
Z9X_CERT=$G/tools/sign/release_certs/ota.x509.pem
[ -f "$KEY" ] || die "no X50 ota key $KEY (tools/devices/gen_device_keys.sh g0111, owner, on the Mac)"
retry() { local i; for i in 1 2 3 4 5 6; do "$@" && return 0; sleep 10; done; return 1; }

# 1. what the composer wrote
INFO=$(retry ssh "$SRV" "cat ~/$RT/BUILD_INFO.txt; exit 0") || die "cannot reach $SRV"
f=$(sed -n 's/^image=//p' <<< "$INFO"); size=$(sed -n 's/^size=//p' <<< "$INFO"); want=$(sed -n 's/^sha256=//p' <<< "$INFO")
[ "$f" = "system_$TAG.img" ] && [ -n "$size" ] && [[ $want =~ ^[0-9a-f]{64}$ ]] || die "no finished image in ~/$RT (BUILD_INFO.txt: '$f' '$size' '$want')"
echo "sign_x50_test: $SRV:$RT/$f $size bytes sha256 $want"

# 2. the copy on the Mac
mkdir -p "$L"
if [ ! -f "$L/$f" ] || [ "$(stat -f %z "$L/$f")" != "$size" ] || [ "$(sha "$L/$f")" != "$want" ]; then
  echo "sign_x50_test: fetching the image to $L (8 streams)"
  python3 -B - "$SRV" "$RT/$f" "$L/$f.part" "$size" <<'PY' || die "the copy of $f did not finish"
import concurrent.futures as cf, os, subprocess, sys, time
srv, rpath, out, size = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
MB = 1 << 20
piece = 32 * MB
n = (size + piece - 1) // piece
with open(out, "ab") as f:
    f.truncate(size)
def get(i):
    want = min(piece, size - i * piece)
    cmd = "dd if=$HOME/%s bs=1M skip=%d count=%d 2>/dev/null; exit 0" % (rpath, i * piece // MB, piece // MB)
    for _ in range(8):
        r = subprocess.run(["ssh", "-o", "ServerAliveInterval=15", srv, cmd], capture_output=True)
        if len(r.stdout) == want:
            with open(out, "r+b") as f:
                f.seek(i * piece)
                f.write(r.stdout)
            return i
        time.sleep(5)
    raise RuntimeError("piece %d: no complete copy after 8 tries" % i)
t0 = time.time()
with cf.ThreadPoolExecutor(8) as ex:
    for k, i in enumerate(ex.map(get, range(n)), 1):
        if k % 8 == 0 or k == n:
            print("sign_x50_test: %d/%d pieces, %.0f s" % (k, n, time.time() - t0), flush=True)
PY
  mv -f "$L/$f.part" "$L/$f"
  [ "$(stat -f %z "$L/$f")" = "$size" ] || die "$L/$f: size is not BUILD_INFO's"
  [ "$(sha "$L/$f")" = "$want" ] || die "$L/$f: sha256 is not BUILD_INFO's"
fi
echo "sign_x50_test: $L/$f sha256 ok"

# 3. the list, the image under the signed name
ln -f "$L/$f" "$L/$NAME"
printf '%s  %s\n' "$want" "$NAME" > "$L/SHA256SUMS"
(cd "$L" && shasum -a 256 -c SHA256SUMS) > /dev/null || die "SHA256SUMS does not check against $L/$NAME"

# 4. the signature, with the key that belongs to the X50 certificate
[ "$("$OPENSSL" pkey -inform DER -in "$KEY" -pubout 2>/dev/null)" = "$("$OPENSSL" x509 -in "$CERT" -pubkey -noout)" ] \
  || die "$KEY is not the key of $CERT"
"$OPENSSL" dgst -sha256 -keyform DER -sign "$KEY" -out "$L/SHA256SUMS.sig" "$L/SHA256SUMS"
P=$(mktemp); trap 'rm -f "$P"' EXIT
"$OPENSSL" x509 -in "$CERT" -pubkey -noout > "$P"
"$OPENSSL" dgst -sha256 -verify "$P" -signature "$L/SHA256SUMS.sig" "$L/SHA256SUMS" > /dev/null || die "the signature does not verify with $CERT"
"$OPENSSL" x509 -in "$Z9X_CERT" -pubkey -noout > "$P"
if "$OPENSSL" dgst -sha256 -verify "$P" -signature "$L/SHA256SUMS.sig" "$L/SHA256SUMS" > /dev/null 2>&1; then
  die "the signature verifies with the Z9X release certificate"
fi
echo "sign_x50_test: SHA256SUMS signed with the g0111 ota key, verified with $(basename "$CERT"), refused by the Z9X one"

# 5. next to the image on the build host
retry scp -q "$L/SHA256SUMS" "$L/SHA256SUMS.sig" "$SRV:$RT/" || die "cannot copy the list to $SRV"
R=$(retry ssh "$SRV" "cd ~/$RT && ln -f $f $NAME && sha256sum SHA256SUMS SHA256SUMS.sig && sha256sum -c SHA256SUMS; exit 0")
for x in SHA256SUMS SHA256SUMS.sig; do
  grep -q "^$(sha "$L/$x")  $x\$" <<< "$R" || die "$x on $SRV differs from $L/$x"
done
grep -qx "$NAME: OK" <<< "$R" || die "sha256sum -c on $SRV: $R"
echo "sign_x50_test: $SRV:$RT/{$NAME,SHA256SUMS,SHA256SUMS.sig} ok"
echo "sign_x50_test: SHA256SUMS $(sha "$L/SHA256SUMS") SHA256SUMS.sig $(sha "$L/SHA256SUMS.sig")"
