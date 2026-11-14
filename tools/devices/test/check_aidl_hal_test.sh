#!/bin/bash
# check_aidl_hal_test.sh: tests of tools/devices/check_aidl_hal.py on the HB apps.
#
#   bash tools/devices/test/check_aidl_hal_test.sh
#
# The app named by hal_app of the Z9X HB (apps/HbProjector, assembled from devices/goat_elegant/app/HbProjector.layers
# when that recipe exists), checked with the registry's allowlist. The section header and the
# "p2_" test names date from when a second app (apps/HbBoot, since removed) was tested as part 1.
# The clean HbProjector copy must pass ('OK: N calls on M interfaces', N and M counted from the registry's
# allowed.tsv: the rows and their distinct interfaces, never a fixed number) and each broken copy must fail: a Call of
# DataBaseManager 2 updateDbInt (BANNED), a Call of a REVIEW row (DisplayManager 56 generateLogoByTrapezoid, even with an
# allowlist row; newAutoKst left REVIEW on 2026-10-29, 9.1), a wrong code (getBootAnimState 34), a second '.transact(' in GmpfClient.java, Parcel used in
# KstPoint.java, a raw integer transact code, a reflection bypass (Class.forName + getMethod + invoke on ServiceManager
# outside the transport), an integer code passed to a typed helper of the gmpf transport (AidlWire), a Call that no allowlist row names (DisplayManager
# 10 isVideoPlaying), an allowlist row that is no Call, and a run without --allowed (fail-closed: usage, exit 2).
# Dex level (rule 7): the clean copy is compiled in the scratch dir with a throwaway key
# (apps/build_apk.sh) and must pass; a fake dexdump then adds one forbidden instruction to the real dump each time
# (Method.invoke, a second IBinder.transact, a const-string "ServiceManager" outside the transport class) and
# finally fails itself: each must fail.
# The tree is only read.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
HAL_APP=$G/$(python3 -B "$G/tools/devices/devices.py" get goat_elegant hal_app) || exit 2
HAL_ALLOWED=$G/$(python3 -B "$G/tools/devices/devices.py" get goat_elegant hal_allowed) || exit 2
R=$G/devices/goat_elegant/research
W=$(mktemp -d "${TMPDIR:-/tmp}/aidl_test.XXXXXX")
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
ANDROID_SDK=${ANDROID_SDK:-$HOME/Library/Android/sdk}
REAL_DEXDUMP=${BUILD_TOOLS:-$ANDROID_SDK/build-tools/36.0.0}/dexdump

copy() { rm -rf "$W/app"; mkdir -p "$W/app"; cp -R "$APP/src" "$W/app/src"; cp "$ALLOWED" "$W/allowed.tsv"; }
CODES_ALSO=""   # part 3: the code tables of the other stock firmware (hal_codes_also, relative to G), one --codes each
run() { # extra args...
  local more=() c
  for c in $CODES_ALSO; do more+=(--codes "$G/$c"); done
  python3 -B "$G/tools/devices/check_aidl_hal.py" --app "$W/app" --codes "$R/aidl_codes.tsv" ${more[@]+"${more[@]}"} \
    --banned "$R/banned.tsv" "$@" > "$W/out" 2>&1
}
result() { # name want got
  if [ "$3" = "$2" ]; then PASS=$((PASS + 1)); echo "ok   $1: $(tail -1 "$W/out")"
  else FAIL=$((FAIL + 1)); echo "FAIL $1 (exit $3, want $2)"; sed 's/^/     /' "$W/out"; fi
}
expect() { # name want(0|1|2) [extra args...]
  n=$1 want=$2; shift 2
  run --allowed "$W/allowed.tsv" "$@"; result "$n" "$want" $?
}
reported() { # name pattern: the last run printed a line matching pattern
  grep -q -- "$2" "$W/out" || { FAIL=$((FAIL + 1)); echo "FAIL $1: no line matches '$2'"; }
}
edit() { # file python-replace old new (exactly one occurrence)
  python3 - "$W/app/$1" "$2" "$3" <<'PYEOF' || { echo "FAIL edit $1"; exit 1; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
assert s.count(old) == 1, (p, old)
open(p, "w").write(s.replace(old, new))
PYEOF
}
key() { # a throwaway key pair for the dex builds, made once
  [ -f "$W/key/k.pk8" ] && return 0
  mkdir -p "$W/key"
  (umask 077
   openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=check_aidl_hal_test throwaway" \
     -keyout "$W/key/k.pem" -out "$W/key/k.x509.pem" 2>/dev/null &&
   openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -in "$W/key/k.pem" -out "$W/key/k.pk8")
}
fake() { # name want pattern line: a dexdump that adds LINE after the first method header matching pattern
  cat > "$W/dexdump" <<DEXEOF
#!/bin/sh
"$REAL_DEXDUMP" "\$@" | awk -v pat='$3' -v add='$4' '{ print } !done && index(\$0, pat) { print add; done = 1 }'
DEXEOF
  chmod +x "$W/dexdump"
  DEXDUMP=$W/dexdump run --allowed "$W/allowed.tsv" --dex "$W/app.apk"; result "$1" "$2" $?
}
dex_suite() { # prefix build-dir method-of-a-client app-oncreate: the four rule 7 cases on $W/app.apk
  fake "$1dex_reflect_invoke" 1 "] $3:(" \
    '00aaaa: 6e30 0000 0000 |0000: invoke-virtual {v0, v1, v2}, Ljava/lang/reflect/Method;.invoke:(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object; // method@0000'
  reported "$1dex_reflect_invoke" "rule 7: $3 invokes java.lang.reflect.Method.invoke"
  fake "$1dex_second_binder_transact" 1 "] $3:(" \
    '00aaaa: 7256 0000 0000 |0000: invoke-interface {v1, v4, v2, v3, v6}, Landroid/os/IBinder;.transact:(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z // method@000c'
  reported "$1dex_second_binder_transact" "rule 7: $3 invokes Landroid/os/IBinder;.transact"
  fake "$1dex_service_manager_string" 1 "] $4:(" \
    '00aaaa: 1a00 0000 |0000: const-string v0, "android.os.ServiceManager" // string@0000'
  reported "$1dex_service_manager_string" "rule 7: $4: const-string 'android.os.ServiceManager'"
  sed -i.bak 's#^"[^"]*" "\$@"#exit 3; &#' "$W/dexdump"
  DEXDUMP=$W/dexdump run --allowed "$W/allowed.tsv" --dex "$W/app.apk"; result "$1dex_tool_fails" 1 $?
  reported "$1dex_tool_fails" 'rule 7: dexdump failed'
}
build() { # build-dir: compile $W/app with the throwaway key into $W/app.apk
  key
  KEY_PK8=$W/key/k.pk8 KEY_PEM=$W/key/k.x509.pem BUILD_DIR=$W/$1 sh "$G/apps/build_apk.sh" "$W/app" "$W/app.apk" \
    > "$W/build.log" 2>&1
}

# ------------------------------------------------------------------ part 2: the hal_app (apps/HbProjector)
APP=$HAL_APP
# an app built from a recipe: the test runs on its assembled tree, as the build
if [ -n "$(ls "$G"/devices/goat_elegant/app/*.layers 2>/dev/null)" ]; then
  python3 -B "$G/tools/devices/assemble_app.py" --device goat_elegant --app "$(basename "$HAL_APP")" --out "$W/hal_app" --quiet \
    || { echo "FAIL p2: the recipe of ${HAL_APP#$G/} does not assemble"; exit 1; }
  APP=$W/hal_app
fi
ALLOWED=$HAL_ALLOWED
H=src/org/lumen/projector/hal
LAST='GET_SYSTEM_TEMPERATURE(GmpfIface.TEMPERATURE, HalCodes.TM_GET_SYSTEM_TEMPERATURE, "getSystemTemperature");'
LAST_OPEN='GET_SYSTEM_TEMPERATURE(GmpfIface.TEMPERATURE, HalCodes.TM_GET_SYSTEM_TEMPERATURE, "getSystemTemperature"),'
echo "part 2: ${HAL_APP#$G/}"
# the expected summary, from the allowlist itself: rows (comments and blank lines skipped) and distinct interfaces
# ('-' marks a meta call, no interface of its own)
N_CALLS=$(grep -v '^#' "$ALLOWED" | grep -c .)
N_IFACES=$(grep -v '^#' "$ALLOWED" | grep . | cut -f1 | grep -vx -- '-' | sort -u | grep -c .)
WANT_OK="OK: $N_CALLS calls on $N_IFACES interfaces"

copy
expect p2_clean 0
reported p2_clean "$WANT_OK"

copy
edit $H/GmpfIface.java 'FACTORY("xgimi.hardware.gmpf.IGmFactoryManager");' \
  'FACTORY("xgimi.hardware.gmpf.IGmFactoryManager"), DB("xgimi.hardware.gmpf.IDataBaseManager");'
edit $H/GmpfCall.java "$LAST" "$LAST_OPEN UPDATE_DB_INT(GmpfIface.DB, 2, \"updateDbInt\");"
expect p2_banned_call 1
reported p2_banned_call 'rule 4: Call.UPDATE_DB_INT: DataBaseManager 2 updateDbInt is banned'

copy
edit $H/GmpfCall.java "$LAST" "$LAST_OPEN GENERATE_LOGO_BY_TRAPEZOID(GmpfIface.DISPLAY, 56, \"generateLogoByTrapezoid\");"
printf 'DisplayManager\t56\tgenerateLogoByTrapezoid\tapproved by nobody\n' >> "$W/allowed.tsv"
expect p2_review_row_call 1
reported p2_review_row_call 'rule 4: Call.GENERATE_LOGO_BY_TRAPEZOID: DisplayManager 56 generateLogoByTrapezoid is under review'

copy
edit $H/GmpfCall.java 'GET_BOOT_ANIM_STATE(GmpfIface.SYSTEM, HalCodes.SM_GET_BOOT_ANIM_STATE, "getBootAnimState")' 'GET_BOOT_ANIM_STATE(GmpfIface.SYSTEM, 34, "getBootAnimState")'
expect p2_wrong_code 1
reported p2_wrong_code 'rule 3: Call.GET_BOOT_ANIM_STATE'

copy
edit $H/GmpfClient.java 'public final class GmpfClient extends GmpfBase<GmpfCall> {' \
  'public final class GmpfClient extends GmpfBase<GmpfCall> {
    static void poke(android.os.IBinder b) throws android.os.RemoteException { b.transact(1, null, null, 0); }'
expect p2_second_transact 1
reported p2_second_transact "rule 1: '.transact(' must be in exactly one file"

copy
edit $H/KstPoint.java 'public final class KstPoint {' \
  'public final class KstPoint {
    static void poke(android.os.Parcel p) { p.writeInt(1); }'
expect p2_parcel_in_kstpoint 1
reported p2_parcel_in_kstpoint 'rule 1: src/org/lumen/projector/hal/KstPoint.java:.*: Parcel outside the transport file'

copy
edit $H/AidlCaller.java 'b.transact(c.code, data, reply, 0)' 'b.transact(33, data, reply, 0)'
expect p2_raw_integer_code 1
reported p2_raw_integer_code 'rule 1: .*found .33.'

copy
edit $H/GmpfClient.java 'public final class GmpfClient extends GmpfBase<GmpfCall> {' \
  'public final class GmpfClient extends GmpfBase<GmpfCall> {
    static Object peek() throws Exception {
        return Class.forName("android.os.Service" + "Manager").getMethod("check" + "Service", String.class)
                .invoke(null, "x");
    }'
expect p2_reflection_bypass 1
reported p2_reflection_bypass 'rule 1: .*GmpfClient.java:.*Class.forName( outside'
reported p2_reflection_bypass 'rule 1: .*GmpfClient.java:.*getMethod( outside'
reported p2_reflection_bypass 'rule 1: .*GmpfClient.java:.*\.invoke( outside'

copy
edit $H/AidlWire.java '@Override public void i32(GmpfCall k, int v) throws RemoteException { callI32(k, v); }' \
  '@Override public void i32(GmpfCall k, int v) throws RemoteException { callI32(36, v); }'
expect p2_helper_integer_code 1
reported p2_helper_integer_code 'rule 5: src/org/lumen/projector/hal/AidlWire.java:.*callI32(36, v) passes no Call'

# rule 5 names: a class outside the transport's hierarchy with its own method of a transport name calls its own method
# (the shared menu's QpdMirror: call(method, arg) on a content provider); a transport subclass is checked even through
# an overload of its own
copy
mkdir -p "$W/app/src/org/lumen/projector/panel"
cat > "$W/app/src/org/lumen/projector/panel/LocalCall.java" <<'JEOF'
package org.lumen.projector.panel;
final class LocalCall {
    private static String call(String method, String arg) { return method + arg; }
    static String read() { return call("state", null); }
}
JEOF
expect p2_local_method_named_like_transport 0

copy
edit $H/AidlWire.java 'final class AidlWire extends AidlCaller implements GmpfWire<GmpfCall> {' \
  'final class AidlWire extends AidlCaller implements GmpfWire<GmpfCall> {
    private int callI32(int code, int v) throws android.os.RemoteException { return code + v; }
    int poke() throws android.os.RemoteException { return callI32(36, 1); }'
expect p2_subclass_own_overload 1
reported p2_subclass_own_overload 'rule 5: src/org/lumen/projector/hal/AidlWire.java:.*callI32(36, 1) passes no Call'

copy
edit $H/GmpfCall.java "$LAST" "$LAST_OPEN IS_VIDEO_PLAYING(GmpfIface.DISPLAY, 10, \"isVideoPlaying\");"
expect p2_not_allowlisted 1
reported p2_not_allowlisted 'rule 6: Call.IS_VIDEO_PLAYING: DisplayManager 10 isVideoPlaying is not a row'
if grep -q 'rule [1-5]:' "$W/out"; then FAIL=$((FAIL + 1)); echo "FAIL p2_not_allowlisted: refused by another rule too"; fi

copy
printf 'DisplayManager\t10\tisVideoPlaying\tnot a call\n' >> "$W/allowed.tsv"
expect p2_stale_allowlist_row 1
reported p2_stale_allowlist_row 'rule 6: .*DisplayManager 10 isVideoPlaying is no Call'

copy
run; result p2_no_allowed_option 2 $?

copy
cp "$APP/AndroidManifest.xml" "$W/app/"
cp -R "$APP/res" "$W/app/res"
if build build2; then
  expect p2_dex_clean 0 --dex "$W/app.apk"
  reported p2_dex_clean "$WANT_OK"
  dex_suite p2_ build2 org.lumen.projector.hal.GmpfBase.getHdrType org.lumen.projector.App.onCreate
else
  FAIL=$((FAIL + 1)); echo "FAIL p2_dex_build: the scratch copy did not compile"; sed 's/^/     /' "$W/build.log" | tail -20
fi

# ------------------------------------------------------------------ part 3: several stock firmware (hal_codes_also)
# HB V6.16.24 and V6.17.12: XGIMI changed the frozen gmpf V1 in place. With both
# tables every Call must be the same row in each: the clean app passes; FOCUS 102 doProImgFusionOverlap (its NDK
# argument changed: KstPoint const& / KstPoint*) is refused even with an allowlist row, while one table alone lets it
# through (only the second table catches it); a method only V6.17.12 has (DisplayManager 207 isKeystoneAdjusted) is
# refused; the registry's allowlist has no FOCUS 102 row and the app no doProImgFusionOverlap (it stays unsent).
CODES_ALSO=$(python3 -B "$G/tools/devices/devices.py" get goat_elegant hal_codes_also 2>/dev/null)
echo "part 3: ${HAL_APP#$G/} with hal_codes_also"
if [ -z "${CODES_ALSO// /}" ]; then
  FAIL=$((FAIL + 1)); echo "FAIL p3: goat_elegant has no hal_codes_also"
else
  copy
  expect p3_clean 0
  reported p3_clean "$WANT_OK"

  copy
  edit $H/GmpfCall.java "$LAST" "$LAST_OPEN DO_PRO_IMG_FUSION_OVERLAP(GmpfIface.FOCUS, 102, \"doProImgFusionOverlap\");"
  printf 'ProjectorFocusManager\t102\tdoProImgFusionOverlap\tapproved by nobody\n' >> "$W/allowed.tsv"
  expect p3_focus102_changed 1
  reported p3_focus102_changed 'rule 3: Call.DO_PRO_IMG_FUSION_OVERLAP: ProjectorFocusManager 102 doProImgFusionOverlap differs between aidl_codes.tsv and aidl_codes_v61712.tsv'
  KEEP_ALSO=$CODES_ALSO; CODES_ALSO=""
  expect p3_focus102_one_table 0
  CODES_ALSO=$KEEP_ALSO

  copy
  edit $H/GmpfCall.java "$LAST" "$LAST_OPEN IS_KEYSTONE_ADJUSTED(GmpfIface.DISPLAY, 207, \"isKeystoneAdjusted\");"
  printf 'DisplayManager\t207\tisKeystoneAdjusted\tapproved by nobody\n' >> "$W/allowed.tsv"
  expect p3_only_in_61712 1
  reported p3_only_in_61712 'rule 3: Call.IS_KEYSTONE_ADJUSTED: DisplayManager 207 isKeystoneAdjusted is not a row of aidl_codes.tsv'

  if grep -v '^#' "$ALLOWED" | awk -F '\t' '$1 == "ProjectorFocusManager" && ($2 == "102" || $3 == "doProImgFusionOverlap")' | grep -q .; then
    FAIL=$((FAIL + 1)); echo "FAIL p3_focus102_unsent: the allowlist names FOCUS 102"
  elif grep -rqE '"doProImgFusionOverlap"|Iface\.FOCUS, *102[,)]' "$APP/src"; then
    FAIL=$((FAIL + 1)); echo "FAIL p3_focus102_unsent: the app names doProImgFusionOverlap"
  else
    PASS=$((PASS + 1)); echo "ok   p3_focus102_unsent: no allowlist row, no Call"
  fi
fi

echo "check_aidl_hal_test: $PASS passed, $FAIL failed"
[ $FAIL = 0 ]
