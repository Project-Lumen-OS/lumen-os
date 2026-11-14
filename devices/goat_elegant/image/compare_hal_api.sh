#!/bin/sh
# compare_hal_api.sh: the public members of the Z9X and HB hal clients must be equal,
# except the device calls listed below. Builds apps/Z9xProjector and apps/HbProjector into a scratch dir with the
# test key of apps/sdk (nothing is written into the tree), then compares javap -public of GmpfClient, Gmpf2Client
# and KstPoint with their public nested types.
#
#   sh devices/goat_elegant/image/compare_hal_api.sh
#
# Since 2026-11-12 both GmpfClient / Gmpf2Client extend the shared GmpfBase / Gmpf2Base (apps/core/lumen-gmpf): one
# body, so their shared members are equal by construction; the script checks that both apps extend them and compares
# what each client declares itself. The value enums and value classes are top-level types of the hal package: one
# file for both apps, except the four per-device value lists the HB forks (AutoKstMode, ColorTemp, AspectRatio,
# PictureReset).
#
# Allowed differences (anything else is red), one row per member: client <TAB> side (z = Z9X only, h = HB only)
# <TAB> javap line <TAB> why.
ALLOWED='
GmpfClient	z	public boolean connect(android.os.IHwBinder$DeathRecipient, long);	HIDL connect (PROJECTOR_DESIGN.md 4.3)
GmpfClient	h	public boolean connect(org.lumen.projector.hal.DeathListener);	AIDL handshake connect (4.3)
GmpfClient	h	public void setBootAnimRunning() throws android.os.RemoteException;	HbBoot row A (7.1)
GmpfClient	h	public int[] getZoomLimitStep() throws android.os.RemoteException;	DISPLAY 190 (the Z9X: IGmpf2 13, Gmpf2Client)
GmpfClient	h	public int setOpticalZoomLimitStep(int) throws android.os.RemoteException;	DISPLAY 189 (the Z9X: IGmpf2 14, Gmpf2Client)
GmpfClient	h	public boolean resetRealtimeAkTemporary() throws android.os.RemoteException;	the stock fit hint window (hb-t7)
GmpfClient	z	public void resetKeystoneCorners() throws android.os.RemoteException;	the Z9X stock keystone reset (the HB: resetKeystoneStock)
Gmpf2Client	z	public boolean resetKeystoneRatio() throws android.os.RemoteException;	the Z9X stock keystone reset
Gmpf2Client	z	public int[] getZoomLimitStep() throws android.os.RemoteException;	IGmpf2 13 (the HB: GmpfClient)
Gmpf2Client	z	public int setOpticalZoomLimitStep(int) throws android.os.RemoteException;	IGmpf2 14 (the HB: GmpfClient)
Gmpf2Client	z	public int getVrrState(int) throws android.os.RemoteException;	Z9xGameIo name of VRR (the HB: getVrr)
Gmpf2Client	z	public int setVrrState(boolean, int) throws android.os.RemoteException;	Z9xGameIo name (the HB: setVrr)
Gmpf2Client	z	public boolean getDarkScenes(int, int) throws android.os.RemoteException;	Z9xGameIo name (the HB: getDarkBoost)
Gmpf2Client	z	public int setDarkScenes(boolean, int, int) throws android.os.RemoteException;	Z9xGameIo name (the HB: setDarkBoost)
Gmpf2Client	z	public int getGameSound() throws android.os.RemoteException;	the Z9X game sound (no HB call)
Gmpf2Client	z	public boolean setGameSound(int) throws android.os.RemoteException;	the Z9X game sound (no HB call)
Gmpf2Client	h	public boolean getVrr(int) throws android.os.RemoteException;	HbGameIo name of VRR
Gmpf2Client	h	public int setVrr(boolean, int) throws android.os.RemoteException;	HbGameIo name
Gmpf2Client	h	public boolean getDarkBoost(int, int) throws android.os.RemoteException;	HbGameIo name
Gmpf2Client	h	public int setDarkBoost(boolean, int, int) throws android.os.RemoteException;	HbGameIo name
Gmpf2Client	h	public int getOutputFps() throws android.os.RemoteException;	DISPLAY 179, the FPS overlay (no Z9X call)
'
set -eu
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/hal_api.XXXXXX")
trap 'rm -rf "$W"' EXIT
JH=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
# both apps are assembled from their recipes first (the Z9X app takes lumen-menu)
python3 -B "$G/tools/devices/assemble_app.py" --device goat_elegant --app HbProjector --out "$W/tree/HbProjector" --quiet \
  || { echo "compare_hal_api: the HbProjector recipe does not assemble" >&2; exit 1; }
python3 -B "$G/tools/devices/assemble_app.py" --device horse_popular --app Z9xProjector --out "$W/tree/Z9xProjector" --quiet \
  || { echo "compare_hal_api: the Z9xProjector recipe does not assemble" >&2; exit 1; }
for a in Z9xProjector HbProjector; do
  BUILD_DIR=$W/$a sh "$G/apps/build_apk.sh" "$W/tree/$a" "$W/$a.apk" > "$W/$a.log" 2>&1 \
    || { cat "$W/$a.log" >&2; echo "compare_hal_api: $a does not build" >&2; exit 1; }
done
api() { # classes dir, class: its declared public members; the superclass without the device's type argument
  ( cd "$1/org/lumen/projector/hal" && for f in "$2.class" "$2"\$*.class; do
      [ -f "$f" ] && "$JH/bin/javap" -public "$f"
    done ) | grep -v -e '^Compiled from' -e '^}$' | sed -E 's/ extends (org\.lumen\.projector\.hal\.Gmpf2?Base)<[^>]*>/ extends \1/' | sort
}
RC=0
for c in GmpfClient Gmpf2Client KstPoint; do
  api "$W/Z9xProjector/classes" "$c" > "$W/z.$c"
  api "$W/HbProjector/classes" "$c" > "$W/h.$c"
  base=
  case $c in GmpfClient) base=GmpfBase ;; Gmpf2Client) base=Gmpf2Base ;; esac
  for s in z h; do
    [ -z "$base" ] || grep -q -x "public final class org\.lumen\.projector\.hal\.$c extends org\.lumen\.projector\.hal\.$base {" "$W/$s.$c" \
      || { echo "compare_hal_api: $c of $s does not extend the shared $base"; RC=1; }
  done
  printf '%s\n' "$ALLOWED" | awk -F'\t' -v c="$c" '$1 == c { print ($2 == "z" ? "< " : "> ") "  " $3 }' > "$W/a.$c"
  diff "$W/z.$c" "$W/h.$c" | grep '^[<>]' | grep -v -x -F -f "$W/a.$c" > "$W/d.$c" || true
  if [ -s "$W/d.$c" ]; then
    echo "compare_hal_api: $c differs beyond the allowed changes:"; cat "$W/d.$c"; RC=1
  else
    echo "compare_hal_api: $c: public members equal (allowed device calls only)"
  fi
done
exit $RC
