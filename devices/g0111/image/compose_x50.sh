#!/bin/bash
# Compose a TEST system image for the XGIMI X50 Ultra (Lumen codename g0111: ro.product.device horse_popular like
# the Z9X, model G0111, mt9681 / mt5877, arm64, Android 14 vendor v1.2.64 with VNDK 34, AIDL xgimi.hardware.gmpf).
# Design and the reasons for every change: devices/g0111/README.md and the design notes.
#
# Base: the published Z9X 1.0.9 image (lumen-1.0.9-20261106anp, the asset lumen-os-1.0.9-nogms-system.img of the
# release z9x-1.0.9), pinned by sha256 in base.sha256. It is Lumen's own arm64 LineageOS 21 TV GSI with the Lumen
# apps, release-signed, without any MediaTek or XGIMI file (the Z9X blob set comes from the user's projector at
# install time). The X50 shares the Z9X partition layout byte for byte and its VINTF fragments, so the arm64 Z9X
# system is the closest system there is. Nothing is rebuilt or re-signed: every file the X50 keeps is the base's
# byte for byte, with the base's owner, mode, SELinux label and capability.
#
# What changes (verify_tree_x50.py proves exactly this and nothing else):
#   remove.txt          the Z9X updater (no OTA may reach an X50), the Z9X projector app (HIDL gmpf client with
#                       setters for light, motor, keystone; the X50 has no HIDL gmpf at all), the boot rescue,
#                       the Z9X wake-source and shutdown-watchdog writers, the bridges and default permissions of
#                       the removed apps, and the Z9X eye-protection fifo reader z9x_nui: it ACKs every
#                       native-UI command without drawing, while on the X50 the human-shape mask IS drawn by the
#                       stock /system/bin/nativeui (5.6): no reader at all
#                       until a real drawer exists (maintainer's decision; the eye protection is a release blocker);
#                       the Z9X blobs reader z9x_blobs.rc/.sh and its allow-list (the X50 reader below replaces it)
#   otacerts.zip        a zip without any certificate: update_engine verifies no payload on this image
#   build.prop (2)      x50_prop.txt (X50 identity ro.lumen.device=g0111, model G0111, ro.z9x.device=x50, build
#                       id lumen-x50-tN-<UTC minute>, no OTA props, gate and rescue off, network adb of a test
#                       image) and x50_model_prop.txt (the stock model props the XGIMI init loads from
#                       /vendor/etc/xgimimodelname/G0111/build.prop; LineageOS init does not), checked here
#                       against the stock file of the dump on this server
#   x50_netadb.rc       adbd at boot (network adb; the X50 vendor loads mtu3 with mtu3_dev_en=0 at every boot,
#                       so there is no USB device mode, as on stock)
#   x50_blobs.sh/.rc    the X50 blobs reader in place of the Z9X z9x_blobs.sh/.rc and blobs_allow.txt (remove.txt):
#   x50_blobs_allow.txt the nine Z9X codec / audio files (the X50 stock copies have the same sha256) plus the
#                       stock eye-protection drawer /system/bin/nativeui and its library
#                       xgimi.hardware.gmpf-V1-ndk.so (need=eye: all or none), from the user's own projector in a
#                       16 MiB z9x_blobs partition; 0-byte placeholders /system/bin/nativeui (root:shell 0755,
#                       nativeui_main_exec as in the stock vendor_file_contexts) and
#                       /system/lib64/xgimi.hardware.gmpf-V1-ndk.so (system_lib_file)
#   x50_nui.rc          the stock service nativeui (class main, oneshot, root, domain by the label), started only
#                       when x50_blobs.sh verified both eye-protection files (sys.lumen.nui=ok)
#   blobs_stock_dsu.sh  /system/etc/z9x/blobs_stock_dsu.sh (tools/devices/image, shared, sourced by x50_blobs.sh):
#                       in a DSU trial on stock (no z9x_blobs partition) the same allow-listed files read-only from
#                       the stock slot, checked and bound by x50_blobs.sh as from the partition
#   X50EyeGuard         /system/app/X50EyeGuard/X50EyeGuard.apk: the eye-follow fail-safe (org.lumen.x50.eyeguard,
#                       persistent): while the drawer is not verified and running it switches eye protection to the
#                       full-screen mode. Built and signed with the
#                       g0111 platform key on the Mac (tools/devices/build_apps.sh g0111 build/g0111/stage), staged
#                       by tools/image/compose.sh as IN/stage (stage_dir of image.conf); the one APK that may name
#                       the gmpf HAL, with exactly its four calls (verify_tree_x50.py HAL_APP, x50_apk_check.py)
#   power keys          x50_power_keys.py: the user power keys of the key layouts (IR, body keypad, BT remotes,
#                       Generic.kl KEY_SLEEP) back to KEYCODE_POWER WAKE as on stock (the Z9X base sends them as
#                       KEYCODE_STB_POWER to org.lumen.projector, absent here), edited in place
#   X50PowerOverlay     /system/product/overlay/X50PowerOverlay.apk: static framework RRO above the Z9X one (short
#                       POWER = Android sleep as stock's goToSleep, long POWER = power menu; gear and Source keys
#                       routed to what the image has), built and signed like X50EyeGuard
#   LumenDsuInstall     (t8) /system/priv-app/LumenDsuInstall/LumenDsuInstall.apk (built and signed like
#                       X50EyeGuard), /system/etc/init/lumen_dsu_install.rc and /system/etc/lumen/lumen_dsu_install.sh
#                       (apps/LumenDsuInstall/image): "Install Lumen OS on this projector" from a DSU try into slot
#                       b; every rc trigger needs ro.gsid.image_running=1 (checked below and by verify_tree_x50.py),
#                       prop ro.lumen.dsu_install=1; a real write on the X50 needs the maintainer switch on the unit
#                      ; the home-screen entry shows only during a DSU try
#   X50Projector        (t11) /system/priv-app/X50Projector/X50Projector.apk (built and signed like X50EyeGuard) and
#                       /system/product/etc/default-permissions/x50-projector.xml (apps/X50Projector/image): the gear-key
#                       menu (stock focus / keystone functions, its own HAL allow list, verify_tree_x50.py MENU_APP) and
#                       the remote pairing of t10 (the former X50Remote app: pairs the original XGIMI RC by itself and
#                       reports it to Setup's remote step)
#   platform set        (t12) framework-res and every other base APK on the Z9X release platform key, re-signed with the
#                       g0111 platform key on the Mac (devices/g0111/image/sign_x50_base.py; keys never leave the Mac),
#                       the four APEXes that hold such an APK (configinfrastructure, devicelock, ondevicepersonalization,
#                       permission) rebuilt with it and signed with the g0111 APEX keys, and the mac_permissions files
#                       naming the g0111 certificate (staged as IN/stage/base_signed, step 3b): the X50 apps signed with
#                       the g0111 platform key are platform apps (X50Projector runs as android.uid.system), a Z9X-signed
#                       app is not. APKs on the Z9X shared / media / networkstack / releasekey keys stay as in the base.
#   X50TvInput          (t17) the HDMI input app (org.lumen.tvinput) built from devices/g0111/app/X50TvInput.layers
#                       (apps/Z9xTvInput + the X50 source picker look), signed with the g0111 platform key on the Mac
#                       (tools/devices/build_apps.sh g0111), in place of the base's /system/app/Z9xTvInput/Z9xTvInput.apk;
#                       the base's oat files go (verify_tree_x50.py TVIN_APP)
# Kept from the Z9X image unchanged, among others: Setup, AirPlay, the other key layouts, the six
# 0-byte Codec2 placeholders, the Google add-on reader z9x_gapps, the OTA boot gate (off by prop, update_verifier as
# stock).
#
# Runs ON THE BUILD SERVER (xgimi-builder) as the normal user, with sudo for the loop mounts and the root copy
# (owners, modes, labels and capabilities must survive). Started by tools/image/compose.sh from the Mac, which
# stages OUT/in first:
#   bash ~/x50-out/in/compose_x50.sh VARIANT [--stage-only | --image-only] [--dry-run]
#   VARIANT       t1, t2, ...: ro.lumen.version=x50-VARIANT, TAG x50-VARIANT-net (test image, X50 Ultra only); or a
#                 release version (1.0.10-beta1, 2026-11-11 public beta): the PUBLIC variant, TAG <prefix>-VERSION-pub,
#                 ro.lumen.version=<prefix>-VERSION, no network adb (x50_prop_pub.txt / x50max_prop_pub.txt, no
#                 x50_netadb.rc). X50_CODENAME picks the model: g0111 X50 Ultra (prefix x50, the default) or g0100
#                 X50 Ultra Max (prefix x50max, its own keys, apps and platform set in IN/stage, model props
#                 x50max_model_prop.txt from the stock G0100 file).
#   --stage-only  steps 0-7: the staged tree OUT/TAG/root, checked against the base (verify_stage.log); no image
#   --image-only  steps 8-11 on the staged tree of an earlier --stage-only run with the same BUILD_ID
#   --dry-run     print TAG, build id and paths, then stop
# Environment: LUMEN_IMAGE_DIR = OUT (default ~/x50-out), BUILD_ID (the date part, default the UTC minute; letters,
# digits and . _ - only; --image-only needs the one of the staged tree), SOURCE_DATE_EPOCH (the time of every file,
# default 1230768000 as the Z9X image), X50_DUMP (default ~/devices/g0111_mt9681/work/fs, read only), X50_BOOT (the
# dumped boot_a.img and boot_b.img, default ~/devices/g0111_mt9681/dump, read only).
# Inputs are read only; everything is written under OUT/TAG. The base image goes in OUT/base (put there by hand,
# sha256 checked here).
set -euo pipefail
die() { echo "compose_x50: ERROR $*" >&2; exit 1; }
V=${1:-}
PUBLIC=0
case $V in
  t[0-9]|t[0-9][0-9]) ;;
  [0-9]*.[0-9]*.[0-9]*)
    printf '%s' "$V" | grep -Eqx '[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}(-(beta|rc)[0-9]{1,2})?' || die "bad release version $V"
    PUBLIC=1 ;;
  *) die "usage: [X50_CODENAME=g0111|g0100] compose_x50.sh tN|X.Y.Z[-betaN] [--stage-only|--image-only] [--dry-run]" ;;
esac
shift
C=${X50_CODENAME:-g0111}
case $C in
  g0111) MODEL=G0111; PREFIX=x50; STOCK_MODEL_SHA=16d66b7c2b99831c29c006d1cb6a04f0af555502a98fa40bacafc65ac3ff1525
         MPROPF=x50_model_prop.txt; [ $PUBLIC = 1 ] && PROPF=x50_prop_pub.txt || PROPF=x50_prop.txt ;;
  g0100) MODEL=G0100; PREFIX=x50max; STOCK_MODEL_SHA=8d9e70ce15505f85ae6492f1f96637e53806dc314f3363a3211f6527678289d2
         MPROPF=x50max_model_prop.txt; PROPF=x50max_prop_pub.txt
         [ $PUBLIC = 1 ] || die "the X50 Ultra Max (g0100) has no test variant: give a release version" ;;
  *) die "X50_CODENAME $C is not an X50 model (g0111, g0100)" ;;
esac
MODE=all DRY=0
for a in "$@"; do
  case $a in
    --stage-only) MODE=stage ;;
    --image-only) MODE=image ;;
    --dry-run) DRY=1 ;;
    *) die "unknown option $a" ;;
  esac
done
OUT=${LUMEN_IMAGE_DIR:-$HOME/x50-out}
IN=$OUT/in
if [ $PUBLIC = 1 ]; then TAG=$PREFIX-$V-pub; else TAG=x50-$V-net; fi
T=$OUT/$TAG
R=$T/root
MB=$T/mnt_base
MN=$T/mnt_new
IMG=$T/system_$TAG.img
BASE=$OUT/base/z9x-1.0.9-anp-system.img
DUMP=${X50_DUMP:-$HOME/devices/g0111_mt9681/work/fs}
# the stock model props of this model (the dump holds every model's file: one XGIMI build for the whole line)
STOCK_MODEL=$DUMP/vendor/etc/xgimimodelname/$MODEL/build.prop
# the X50's own kernel (boot_a = boot_b in the v1.2.64 dump): the image's ART boot image needs its userfaultfd
BOOT=${X50_BOOT:-$HOME/devices/g0111_mt9681/dump}
BOOT_SHA=bf169bef493291a8cada10fa688ff53a62b22043e7e82fcfba9c4db76fc7d2f5
BASE_BUILD_ID=lumen-1.0.9-20261106anp
HOST=$HOME/lumen-builder-backup/lineage-out/host/linux-x86/bin   # LineageOS erofs-utils 1.7.1, as the Z9X image
TS=${SOURCE_DATE_EPOCH:-1230768000}
case $TS in ''|*[!0-9]*) die "SOURCE_DATE_EPOCH must be digits" ;; esac
if [ "$MODE" = image ] && [ -z "${BUILD_ID:-}" ]; then
  [ -f "$T/STAGE_INFO.txt" ] && BUILD_ID=$(sed -n 's/^bid=//p' "$T/STAGE_INFO.txt")
fi
BID=${BUILD_ID:-$(date -u +%Y%m%d%H%M)}
case $BID in *[!A-Za-z0-9._-]*|'') die "BUILD_ID may hold only letters, digits and . _ -" ;; esac
echo "compose_x50: $C ($MODEL) TAG=$TAG build_id=lumen-$PREFIX-$V-$BID mode=$MODE out=$T props=$PROPF + $MPROPF"
[ $DRY = 1 ] && exit 0

case $T in "$HOME"/*/x50-t*-net|"$HOME"/*/x50-[0-9]*-pub|"$HOME"/*/x50max-[0-9]*-pub) ;; *) die "refusing the output folder $T" ;; esac
for f in base.sha256 remove.txt "$PROPF" "$MPROPF" x50_props.py x50_netadb.rc verify_tree_x50.py \
         erofs_kcheck.py x50_blobs.sh x50_blobs.rc x50_blobs_allow.txt x50_nui.rc x50_apk_check.py x50_kconfig.py \
         x50_power_keys.py x50_perm_pkgs.py apkinfo.py x50_diag.py blobs_stock_dsu.sh lumen_dsu_install.rc lumen_dsu_install.sh \
         x50-projector.xml; do
  [ -f "$IN/$f" ] || die "missing input $IN/$f (tools/image/compose.sh g0111 stages it)"
done
APP_APK=stage/X50EyeGuard.apk
[ -s "$IN/$APP_APK" ] || die "missing $IN/$APP_APK (build and sign it on the Mac: tools/devices/build_apps.sh g0111 build/g0111/stage)"
OVL_APK=stage/X50PowerOverlay.apk
[ -s "$IN/$OVL_APK" ] || die "missing $IN/$OVL_APK (build and sign it on the Mac: tools/devices/build_apps.sh g0111 build/g0111/stage)"
DSU_APK=stage/LumenDsuInstall.apk
[ -s "$IN/$DSU_APK" ] || die "missing $IN/$DSU_APK (build and sign it on the Mac: tools/devices/build_apps.sh g0111 build/g0111/stage)"
RC_APK=stage/X50Projector.apk
[ -s "$IN/$RC_APK" ] || die "missing $IN/$RC_APK (build and sign it on the Mac: tools/devices/build_apps.sh g0111 build/g0111/stage)"
HOME_APK=stage/Z9xHome.apk
[ -s "$IN/$HOME_APK" ] || die "missing $IN/$HOME_APK (build and sign it on the Mac: devices/g0111/image/build_home_x50.sh)"
TVIN_APK=stage/X50TvInput.apk
[ -s "$IN/$TVIN_APK" ] || die "missing $IN/$TVIN_APK (build and sign it on the Mac: tools/devices/build_apps.sh g0111 build/g0111/stage)"
[ -s "$IN/stage/base_signed/base_signed.tsv" ] \
  || die "missing $IN/stage/base_signed (the platform set on the g0111 keys: devices/g0111/image/sign_x50_base.py all on the Mac)"
# the install-from-DSU rc: every trigger needs ro.gsid.image_running=1 (a normal boot of an installed Lumen OS starts
# none of its services); nothing else but its three disabled oneshot services
bad_on=$(grep -E '^on ' "$IN/lumen_dsu_install.rc" | grep -v -E '^on property:sys\.lumen\.dsu\.request=(check|install|reboot) && property:ro\.gsid\.image_running=1$' || true)
[ -z "$bad_on" ] || die "lumen_dsu_install.rc has a trigger without ro.gsid.image_running=1: $bad_on"
[ "$(grep -c '^service ' "$IN/lumen_dsu_install.rc")" = 3 ] && [ "$(grep -c '^    disabled$' "$IN/lumen_dsu_install.rc")" = 3 ] \
  || die "lumen_dsu_install.rc is not the three disabled services of apps/LumenDsuInstall/image"
# what the APK may do with the HAL, before anything is built from it (verify_tree_x50.py checks it again in the tree)
mkdir -p "$T"
# the model's own platform certificate (verify_tree_x50.py PLATFORM_CERTS)
PCERT=$(python3 -I -c 'import re, sys; s = open(sys.argv[1]).read(); m = re.search(r"PLATFORM_CERTS = \{[^}]*\"%s\": \"([0-9a-f]{64})\"" % sys.argv[2], s); print(m.group(1) if m else "")' "$IN/verify_tree_x50.py" "$C")
[ -n "$PCERT" ] || die "no platform certificate of $C in verify_tree_x50.py"
python3 -I "$IN/x50_apk_check.py" "$IN/$APP_APK" --expect "$IN/verify_tree_x50.py" --cert "$PCERT" > "$T/apk_check.log" 2>&1 \
  || { cat "$T/apk_check.log"; die "$APP_APK is not the X50 HAL app verify_tree_x50.py allows"; }
tail -n 1 "$T/apk_check.log"

cleanup() {
  for m in "$MN" "$MB"; do mountpoint -q "$m" 2>/dev/null && sudo umount "$m"; done
  return 0
}
trap cleanup EXIT

# 0. the base is the pinned one; the model props are the stock file's (the dump on this server, read only)
want=$(awk '{print $1}' "$IN/base.sha256")
echo "base: checking sha256 of $BASE"
[ -s "$BASE" ] || die "no base image $BASE (copy lumen-os-1.0.9-nogms-system.img of z9x-1.0.9 there)"
got=$(sha256sum "$BASE" | awk '{print $1}')
[ "$want" = "$got" ] || die "base sha256 $got is not the pinned $want (base.sha256)"
echo "base: ok $got"
[ -f "$STOCK_MODEL" ] || die "no stock model props $STOCK_MODEL (X50_DUMP)"
[ "$(sha256sum "$STOCK_MODEL" | awk '{print $1}')" = "$STOCK_MODEL_SHA" ] || die "$STOCK_MODEL is not the dumped v1.2.64 file"
python3 -I "$IN/x50_props.py" model "$IN/$MPROPF" "$STOCK_MODEL" || die "$MPROPF is not the stock subset of $MODEL"
# the ART boot image of the base is compiled for the userfaultfd GC (verify_tree_x50.py checks boot.oat against
# ro.dalvik.vm.enable_uffd_gc=true): the X50 kernel must have it, else odrefresh compiles the boot classpath before
# zygote at the first boot (x50_kconfig.py); both dumped boot images, pinned
for s in a b; do
  [ "$(sha256sum "$BOOT/boot_$s.img" 2>/dev/null | awk '{print $1}')" = "$BOOT_SHA" ] \
    || die "$BOOT/boot_$s.img is not the dumped v1.2.64 boot image (X50_BOOT)"
done
mkdir -p "$T"
python3 -I "$IN/x50_kconfig.py" "$BOOT/boot_a.img" "$BOOT/boot_b.img" --need CONFIG_USERFAULTFD=y > "$T/kconfig.log" 2>&1 \
  || { cat "$T/kconfig.log"; die "the X50 kernel lacks what the image's ART boot image needs"; }
tail -n 1 "$T/kconfig.log"

mkdir -p "$T" "$MB" "$MN"
sudo mount -t erofs -o loop,ro "$BASE" "$MB"
sudo grep -qx "ro.z9x.build_id=$BASE_BUILD_ID" "$MB/system/product/etc/build.prop" || die "the base is not $BASE_BUILD_ID"
sudo grep -q "^ro.build.display.id=Lumen OS 1.0.9 " "$MB/system/build.prop" || die "the base is not Lumen OS 1.0.9"

if [ "$MODE" != image ]; then
  # 1-2. root copy with hard links, ACLs and every xattr (security.selinux, security.capability)
  sudo rm -rf "$R"
  sudo rsync -aHAX --numeric-ids "$MB/" "$R/"
  echo "stage: copied $(sudo find "$R" | wc -l) entries"

  # 3. removals (each path must exist)
  n=0
  while IFS=$'\t' read -r p why; do
    case $p in ''|'#'*) continue ;; esac
    case $p in /*|*..*) die "remove.txt: bad path $p" ;; esac
    sudo test -e "$R/$p" || die "remove.txt: $p is not in the base"
    sudo rm -rf "$R/$p"
    n=$((n + 1))
  done < "$IN/remove.txt"
  echo "stage: removed $n paths"

  # 3b. t12: the platform set on the g0111 keys (devices/g0111/image/sign_x50_base.py base, signed on the Mac, staged
  #     as IN/stage/base_signed): framework-res and every other base APK on the Z9X release platform key re-signed with
  #     the g0111 platform key, the four APEXes holding such an APK rebuilt and signed with the g0111 APEX keys, the
  #     mac_permissions files naming the g0111 certificate. Every row of base_signed.tsv is installed over its base copy
  #     only when the base file is the one the Mac read (base sha256) and the staged file the one it wrote (signed
  #     sha256); owner, mode and label stay the base path's. A row whose base file is a .capex installs the signed
  #     uncompressed .apex in its place (same owner, mode, label) and removes the .capex (as the HB composer does).
  sudo python3 -I - "$IN/stage/base_signed" "$MB" "$R" <<'PY'
import hashlib, os, shutil, sys
sig, base, top = sys.argv[1:4]
def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()
def fail(m):
    sys.exit("ERROR: base_signed: " + m)
rows, kinds = [], {}
for n, line in enumerate(open(os.path.join(sig, "base_signed.tsv")), 1):
    if line.startswith("#") or not line.strip():
        continue
    p = line.rstrip("\n").split("\t")
    if len(p) != 5:
        fail("base_signed.tsv:%d: expected 5 columns" % n)
    path, kind, old, new, rep = p
    for x in (path, rep):
        if not x.startswith("system/") or ".." in x.split("/") or "//" in x:
            fail("base_signed.tsv:%d: bad path %r" % (n, x))
    if kind not in ("apk", "apex", "mac_permissions"):
        fail("base_signed.tsv:%d: unknown kind %r" % (n, kind))
    if rep != path and not (kind == "apex" and rep.endswith(".capex") and path == rep[:-len(".capex")] + ".apex"):
        fail("%s replaces %s: only a .capex may be replaced by its .apex" % (path, rep))
    src, bf, dst, old_dst = os.path.join(sig, path), os.path.join(base, rep), os.path.join(top, path), os.path.join(top, rep)
    if not os.path.isfile(src) or sha(src) != new:
        fail("%s: not the file sign_x50_base.py wrote (signed sha256 %s)" % (path, new))
    if not os.path.isfile(bf) or os.path.islink(bf) or sha(bf) != old:
        fail("%s: the base file is not the one the Mac signed (base sha256 %s)" % (rep, old))
    if not os.path.isfile(old_dst) or os.path.islink(old_dst):
        fail("%s: not in the composed tree" % rep)
    if rep != path and os.path.lexists(dst):
        fail("%s: already in the composed tree" % path)
    rows.append((path, rep, src, dst, old_dst))
    kinds[kind] = kinds.get(kind, 0) + 1
paths = {r[0] for r in rows}
for must in ("system/framework/framework-res.apk", "system/etc/selinux/plat_mac_permissions.xml",
             "system/apex/com.android.permission.apex"):
    if must not in paths:
        fail("%s is not in the signed set: not a complete sign_x50_base.py base output" % must)
for path, rep, src, dst, old_dst in rows:
    st = os.lstat(old_dst)
    attrs = {k: os.getxattr(old_dst, k, follow_symlinks=False) for k in os.listxattr(old_dst, follow_symlinks=False)}
    shutil.copyfile(src, dst)
    os.chown(dst, st.st_uid, st.st_gid)
    os.chmod(dst, st.st_mode & 0o7777)
    for k, v in attrs.items():
        os.setxattr(dst, k, v, follow_symlinks=False)
    if rep != path:
        os.unlink(old_dst)
print("base_signed: %d files installed over the base (%s)" % (len(rows), ", ".join("%s %d" % kv for kv in sorted(kinds.items()))))
PY

  # 4. otacerts.zip without a certificate, written into the base file (inode, owner, mode, label kept)
  sudo python3 -I - "$R/system/etc/security/otacerts.zip" <<'PY'
import io, sys, zipfile
b = io.BytesIO()
zipfile.ZipFile(b, "w").close()
with open(sys.argv[1], "r+b") as f:
    f.truncate(0)
    f.write(b.getvalue())
print("stage: otacerts.zip now holds no certificate (%d bytes)" % len(b.getvalue()))
PY

  # 5. the two build.prop files, edited in place
  sudo python3 -I "$IN/x50_props.py" apply "$IN/$PROPF" "$IN/$MPROPF" \
    "$R/system/build.prop" "$R/system/product/etc/build.prop" "$V" "$BID"

  # 6. network adb of the test image; the X50 blobs reader with the eye-protection drawer and its placeholders
  put() {  # SRC (a file of IN, or - for a 0-byte placeholder) DST MODE UID GID LABEL
    if [ "$1" = - ]; then sudo sh -c ': > "$1"' put "$R/$2"; else sudo cp "$IN/$1" "$R/$2"; fi
    sudo chown "$4:$5" "$R/$2"; sudo chmod "$3" "$R/$2"
    sudo python3 -I -c 'import os, sys; os.setxattr(sys.argv[1], "security.selinux", sys.argv[2].encode() + b"\0")' \
      "$R/$2" "$6"
  }
  # the public variant has no network adb (verify_tree_x50.py refuses the file there)
  [ $PUBLIC = 1 ] || put x50_netadb.rc system/etc/init/x50_netadb.rc 0644 0 0 u:object_r:system_file:s0
  put x50_blobs.rc system/etc/init/x50_blobs.rc 0644 0 0 u:object_r:system_file:s0
  put x50_nui.rc system/etc/init/x50_nui.rc 0644 0 0 u:object_r:system_file:s0
  put x50_blobs.sh system/etc/z9x/x50_blobs.sh 0755 0 0 u:object_r:system_file:s0
  put x50_blobs_allow.txt system/etc/z9x/x50_blobs_allow.txt 0644 0 0 u:object_r:system_file:s0
  put blobs_stock_dsu.sh system/etc/z9x/blobs_stock_dsu.sh 0644 0 0 u:object_r:system_file:s0
  put - system/bin/nativeui 0755 0 2000 u:object_r:nativeui_main_exec:s0
  put - system/lib64/xgimi.hardware.gmpf-V1-ndk.so 0644 0 0 u:object_r:system_lib_file:s0
  # the eye-follow fail-safe, a persistent system app (the system partition: /system/app)
  sudo install -d -o 0 -g 0 -m 0755 "$R/system/app/X50EyeGuard"
  sudo python3 -I -c 'import os, sys; os.setxattr(sys.argv[1], "security.selinux", sys.argv[2].encode() + b"\0")' \
    "$R/system/app/X50EyeGuard" u:object_r:system_file:s0
  put "$APP_APK" system/app/X50EyeGuard/X50EyeGuard.apk 0644 0 0 u:object_r:system_file:s0
  # the power keys: the user power keys back to the system POWER (in place), and the framework RRO that makes a short
  # press sleep and a long press open the power menu
  sudo python3 -I "$IN/x50_power_keys.py" apply "$R/system/product/usr/keylayout"
  put "$OVL_APK" system/product/overlay/X50PowerOverlay.apk 0644 0 0 u:object_r:system_file:s0
  # t12 (t11 review P0.3): Lumen Home of this tree (talks to org.lumen.x50.projector on G0111), signed with the g0111
  # platform key on the Mac, over the base's (and its re-signed copy of step 3b); the base's oat files were compiled
  # for the base APK and go (verify_tree_x50.py HOME_APP)
  put "$HOME_APK" system/priv-app/Z9xHome/Z9xHome.apk 0644 0 0 u:object_r:system_file:s0
  sudo rm -rf "$R/system/priv-app/Z9xHome/oat"
  # t17 (t16 live BUG C): the HDMI input app of this tree with the X50 source picker look (devices/g0111/app/
  # X50TvInput.layers), same package org.lumen.tvinput, over the base's (and its re-signed copy of step 3b); the base's
  # oat files were compiled for the base APK and go (verify_tree_x50.py TVIN_APP)
  put "$TVIN_APK" system/app/Z9xTvInput/Z9xTvInput.apk 0644 0 0 u:object_r:system_file:s0
  sudo rm -rf "$R/system/app/Z9xTvInput/oat"
  # t12: the boot logger watches the X50 gmpf HAL service (xgimi_middle, not gmpf_main) and silences the FaceCutoutBin
  # flood in its logcat (in place, x50_diag.py; verify_tree_x50.py checks the exact edit)
  sudo python3 -I "$IN/x50_diag.py" apply "$R/system/etc/xgimi/z9x_diag.sh"
  # install from a DSU try (t8): the app, its init rc and its root script
  for d in system/priv-app/LumenDsuInstall system/etc/lumen; do
    sudo install -d -o 0 -g 0 -m 0755 "$R/$d"
    sudo python3 -I -c 'import os, sys; os.setxattr(sys.argv[1], "security.selinux", sys.argv[2].encode() + b"\0")' \
      "$R/$d" u:object_r:system_file:s0
  done
  put "$DSU_APK" system/priv-app/LumenDsuInstall/LumenDsuInstall.apk 0644 0 0 u:object_r:system_file:s0
  put lumen_dsu_install.rc system/etc/init/lumen_dsu_install.rc 0644 0 0 u:object_r:system_file:s0
  put lumen_dsu_install.sh system/etc/lumen/lumen_dsu_install.sh 0755 0 0 u:object_r:system_file:s0
  # the projector menu with the remote pairing (t11; in t10 the pairing alone was the X50Remote app): the priv-app
  # and its runtime Bluetooth permissions
  sudo install -d -o 0 -g 0 -m 0755 "$R/system/priv-app/X50Projector"
  sudo python3 -I -c 'import os, sys; os.setxattr(sys.argv[1], "security.selinux", sys.argv[2].encode() + b"\0")' \
    "$R/system/priv-app/X50Projector" u:object_r:system_file:s0
  put "$RC_APK" system/priv-app/X50Projector/X50Projector.apk 0644 0 0 u:object_r:system_file:s0
  put x50-projector.xml system/product/etc/default-permissions/x50-projector.xml 0644 0 0 u:object_r:system_file:s0

  # 7. the staged tree against the base
  sudo python3 -I "$IN/verify_tree_x50.py" "$MB" "$R" "$IN" "$V" "$BID" "$C" "$PROPF" "$MPROPF" > "$T/verify_stage.log" 2>&1 \
    || { cat "$T/verify_stage.log"; die "the staged tree does not verify"; }
  tail -n 3 "$T/verify_stage.log"
  { echo "tag=$TAG"; echo "bid=$BID"; echo "build_id=lumen-$PREFIX-$V-$BID"; echo "codename=$C"; echo "model=$MODEL"
    echo "public=$PUBLIC"; echo "base_sha256=$got"
    echo "base_build_id=$BASE_BUILD_ID"; echo "stock_model_sha256=$STOCK_MODEL_SHA"
    echo "inputs_sha256=$(cd "$IN" && cat base.sha256 remove.txt "$PROPF" "$MPROPF" x50_props.py \
      x50_netadb.rc verify_tree_x50.py x50_blobs.sh x50_blobs.rc x50_blobs_allow.txt x50_nui.rc x50_apk_check.py \
      x50_kconfig.py x50_power_keys.py x50_perm_pkgs.py apkinfo.py x50_diag.py blobs_stock_dsu.sh lumen_dsu_install.rc \
      lumen_dsu_install.sh x50-projector.xml "$APP_APK" "$OVL_APK" "$DSU_APK" "$RC_APK" "$HOME_APK" "$TVIN_APK" \
      stage/base_signed/base_signed.tsv | sha256sum | awk '{print $1}')"
    echo "kernel=boot_a,boot_b $BOOT_SHA CONFIG_USERFAULTFD=y"
    echo "art_gc=$(sed -n 's/^verify_tree_x50: ART //p' "$T/verify_stage.log")"
    echo "blobs_set=$(sed -n 's/^set=//p' "$IN/x50_blobs_allow.txt")"
    echo "app=system/app/X50EyeGuard/X50EyeGuard.apk $(sha256sum "$IN/$APP_APK" | awk '{print $1}')"
    echo "overlay=system/product/overlay/X50PowerOverlay.apk $(sha256sum "$IN/$OVL_APK" | awk '{print $1}')"
    echo "dsu_install=system/priv-app/LumenDsuInstall/LumenDsuInstall.apk $(sha256sum "$IN/$DSU_APK" | awk '{print $1}')"
    echo "menu=system/priv-app/X50Projector/X50Projector.apk $(sha256sum "$IN/$RC_APK" | awk '{print $1}')"
    echo "home=system/priv-app/Z9xHome/Z9xHome.apk $(sha256sum "$IN/$HOME_APK" | awk '{print $1}')"
    echo "tvinput=system/app/Z9xTvInput/Z9xTvInput.apk $(sha256sum "$IN/$TVIN_APK" | awk '{print $1}')"
    echo "platform_set=$C $(sha256sum "$IN/stage/base_signed/base_signed.tsv" | awk '{print $1}')"; } > "$T/STAGE_INFO.txt"
  echo "stage: OK ($T/STAGE_INFO.txt)"
  [ "$MODE" = stage ] && exit 0
fi

# 8. the image: the Z9X image's erofs flags (4 KiB blocks, lz4hc, no inline data: the kernel rule of
#    erofs_kcheck.py), one time for every file, a UUID from TAG and the base sha256 (uuid5), never random
[ -f "$T/STAGE_INFO.txt" ] && grep -qx "bid=$BID" "$T/STAGE_INFO.txt" || die "no staged tree for build id $BID (--stage-only first)"
for t in mkfs.erofs fsck.erofs; do [ -x "$HOST/$t" ] || die "no host tool $HOST/$t"; done
UUID=$(python3 -I -c 'import sys, uuid; print(uuid.uuid5(uuid.NAMESPACE_OID, sys.argv[1]))' "lumen-x50:$TAG:$BID:$got")
rm -f "$IMG" "$IMG.sha256"
sudo "$HOST/mkfs.erofs" -b4096 -zlz4hc -E noinline_data -T"$TS" -U"$UUID" "$IMG" "$R" > "$T/mkfs.log" 2>&1 \
  || { tail -n 40 "$T/mkfs.log"; die "mkfs.erofs failed"; }
sudo chown "$(id -u):$(id -g)" "$IMG"
"$HOST/fsck.erofs" "$IMG" > "$T/fsck.log" 2>&1 || { cat "$T/fsck.log"; die "fsck.erofs failed"; }
echo "fsck.erofs: ok"
python3 -I "$IN/erofs_kcheck.py" "$IMG" > "$T/kcheck.log" 2>&1 || { cat "$T/kcheck.log"; die "erofs_kcheck failed"; }
echo "erofs_kcheck: $(tail -n 1 "$T/kcheck.log")"

# 9. the image, mounted, against the base
sudo mount -t erofs -o loop,ro "$IMG" "$MN"
sudo python3 -I "$IN/verify_tree_x50.py" "$MB" "$MN" "$IN" "$V" "$BID" "$C" "$PROPF" "$MPROPF" > "$T/verify.log" 2>&1 \
  || { cat "$T/verify.log"; die "the image does not verify"; }
tail -n 3 "$T/verify.log"

# 10. size budget: group_oem_a holds at most 4613734400 bytes (info/lpdump.txt); the installer deletes product_a and
#     system_ext_a as on the Z9X, vendor_a stays (1.47 GB on v1.2.64): keep the system under 2.5 GB with room for
#     z9x_blobs (16 MiB on the X50: the codec files and the eye-protection drawer) and z9x_gapps (512 MiB)
size=$(stat -c %s "$IMG")
BUDGET=2500000000
[ "$size" -le "$BUDGET" ] || die "image is $size bytes, over the budget $BUDGET"

# 11. sha256 and build info
sha256sum "$IMG" | sed "s|  .*/|  |" > "$IMG.sha256"
{ cat "$T/STAGE_INFO.txt"; echo "image=$(basename "$IMG")"; echo "size=$size"
  echo "sha256=$(awk '{print $1}' "$IMG.sha256")"; echo "budget=$BUDGET"; echo "source_date_epoch=$TS"
  echo "uuid=$UUID"; echo "mkfs=$HOST/mkfs.erofs -b4096 -zlz4hc -E noinline_data"; } > "$T/BUILD_INFO.txt"
cat "$T/BUILD_INFO.txt"
echo "compose_x50: OK $IMG"
