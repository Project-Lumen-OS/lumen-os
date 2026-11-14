#!/bin/bash
# check_aidl_hal_module_test.sh: tools/devices/check_aidl_hal.py on an app whose transport is the shared source module
# apps/core/lumen-hal (org.lumen.hal.AidlTransport; the X50 eye guard, devices/g0111/app/X50EyeGuard.layers).
#
#   bash tools/devices/test/check_aidl_hal_module_test.sh
#
# The clean layer with --module and the assembled tree (module layout found by itself) must pass with the 8 calls of
# devices/g0111/research/allowed_x50eyeguard.tsv; each broken copy must fail: a second enum implementing
# AidlTransport.Method outside the allow list, IBinder or ServiceManager named in the allow-list file, the allow list
# not extending AidlTransport<...Call>, a transport call without a Call constant, a second transact site in the module,
# the vendor eye switch 61 added to the Call list, and the module missing (the layer alone has no transport).
# The tree is only read.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
R=$G/devices/g0111/research
W=$(mktemp -d "${TMPDIR:-/tmp}/aidl_module_test.XXXXXX")
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
GM=src/org/lumen/x50/eyeguard/Gmpf.java
MT=src/org/lumen/hal/AidlTransport.java

copy() {
  rm -rf "$W/app" "$W/mod"; mkdir -p "$W/app" "$W/mod"
  cp -R "$G/apps/X50EyeGuard/src" "$W/app/src"; cp -R "$G/apps/core/lumen-hal/src" "$W/mod/src"
}
run() { # dir [extra args...]
  d=$1; shift
  python3 -B "$G/tools/devices/check_aidl_hal.py" --app "$d" --codes "$R/aidl_codes.tsv" \
    --banned "$G/devices/goat_elegant/research/banned.tsv" --allowed "$R/allowed_x50eyeguard.tsv" "$@" > "$W/out" 2>&1
}
result() { # name want got pattern
  if [ "$3" = "$2" ] && grep -q -- "$4" "$W/out"; then PASS=$((PASS + 1)); echo "ok   $1: $(grep -m1 -- "$4" "$W/out")"
  else FAIL=$((FAIL + 1)); echo "FAIL $1 (exit $3, want $2, pattern '$4')"; sed 's/^/     /' "$W/out"; fi
}
edit() { # file(relative to $W) old new (exactly one occurrence)
  python3 - "$W/$1" "$2" "$3" <<'PYEOF' || { echo "FAIL edit $1"; exit 1; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
assert s.count(old) == 1, (p, old)
open(p, "w").write(s.replace(old, new))
PYEOF
}
broken() { # name pattern: run the edited copy with --module, it must fail with a line matching pattern
  run "$W/app" --module "$W/mod"; result "$1" 1 $? "$2"
}

copy; run "$W/app" --module "$W/mod"; result layer_with_module 0 $? "OK: 8 calls on 3 interfaces"
python3 -B "$G/tools/devices/assemble_app.py" --device g0111 --app X50EyeGuard --out "$W/tree" --quiet \
  || { echo "FAIL the X50EyeGuard recipe does not assemble"; exit 1; }
run "$W/tree"; result assembled_tree_found_by_itself 0 $? "OK: 8 calls on 3 interfaces"
copy; run "$W/app"; result layer_without_module 1 $? "must be in exactly one file, found in none"

copy; cat > "$W/app/src/org/lumen/x50/eyeguard/Sneak.java" <<'EOF'
package org.lumen.x50.eyeguard;

enum Sneak implements org.lumen.hal.AidlTransport.Method {
    X;
    public org.lumen.hal.AidlTransport.Service iface() { return GmpfIface.DISPLAY; }
    public int code() { return 61; }
    public String method() { return "setHumanDetectOnOff"; }
}
EOF
broken second_method_enum "implements AidlTransport.Method outside the allow list"
copy; edit "app/$GM" '    boolean connect() {' '    static android.os.IBinder raw() { return null; }

    boolean connect() {'
broken ibinder_in_allow_list "IBinder outside the transport file"
copy; edit "app/$GM" '    boolean connect() {' '    static Object sm() { return android.os.ServiceManager.checkService("x"); }

    boolean connect() {'
broken service_manager_in_allow_list "ServiceManager outside the transport file"
copy; edit "app/$GM" 'final class Gmpf extends AidlTransport<GmpfCall> {' 'final class Gmpf extends AidlTransport<Call> {'
broken not_extending_the_call_list "does not extend AidlTransport"
copy; edit "app/$GM" 'call(GmpfCall.GET_BOOT_ANIM_STATE, null, Parcel::readInt)' 'call(null, null, Parcel::readInt)'
broken call_without_a_constant "passes no Call constant or Call parameter"
copy; edit "mod/$MT" '    private static void drop(Service i, IBinder b) {' '    static boolean raw(IBinder b) throws RemoteException { return b.transact(61, Parcel.obtain(), null, 0); }

    private static void drop(Service i, IBinder b) {'
broken second_transact_in_module "exactly one '.transact(' site expected"
copy; edit "app/${GM%Gmpf.java}GmpfCall.java" '    GO_TO_BOOT_COMPLETED(GmpfIface.POWER, HalCodes.PM_GO_TO_BOOT_COMPLETED, "goToBootCompleted");' '    GO_TO_BOOT_COMPLETED(GmpfIface.POWER, HalCodes.PM_GO_TO_BOOT_COMPLETED, "goToBootCompleted"),
    SET_ON_OFF(GmpfIface.DISPLAY, 61, "setHumanDetectOnOff");'
broken eye_switch_61_added "is not a row of allowed_x50eyeguard.tsv"

echo "check_aidl_hal_module_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
