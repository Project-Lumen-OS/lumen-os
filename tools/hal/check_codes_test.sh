#!/bin/sh
# check_codes_test.sh: tools/hal/check_codes.py and tools/hal/gen_codes.py java on a small synthetic tree (one HIDL app,
# one AIDL app, one table each) and on broken copies of it, each of which must be red: a literal code, a local
# constant, a forwarder fed a literal, a literal in an allow-list enum, an enum naming another method, a code-carrying
# enum with a literal, a row whose code is not the stock one, a row with a wrong name, a stale generated class; the
# shared gmpf client (rule 4): a literal or an unknown operation in a wire call, a code table row of another method.
# With --tree it also runs both on this tree (green once every client takes its codes from HalCodes).
#   sh tools/hal/check_codes_test.sh [--tree]
set -u
G=$(cd "$(dirname "$0")/../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/check_codes_test.XXXXXX")
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
expect() { # name want command...
  n=$1 want=$2; shift 2
  "$@" > "$W/out" 2>&1; got=$?
  if [ "$got" = "$want" ]; then PASS=$((PASS + 1)); echo "ok   $n: $(tail -1 "$W/out")"
  else FAIL=$((FAIL + 1)); echo "FAIL $n (exit $got, want $want)"; sed 's/^/     /' "$W/out"; fi
}
edit() { python3 -B - "$@" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
assert s.count(old) >= 1, (p, old)
open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}
T=$(printf '\t')
fake() { # a fresh synthetic root in $W/r
  rm -rf "$W/r"; mkdir -p "$W/r/devices/hidl/hal" "$W/r/devices/aidl/hal" "$W/r/research" \
    "$W/r/apps/Foo/src/org/x/hal" "$W/r/apps/Bar/src/org/y" "$W/r/apps/core/gm/src/org/z"
  printf 'BpHwGmpf 176 _hidl_setDlpLumensLevel(, unsigned char) W= writeUint8 R=\nBpHwGmpf 177 _hidl_getDlpLumensLevel() W= R= readUint8\n' \
    > "$W/r/research/codes.txt"
  printf '# service\tcode\tmethod\tsignature\nDisplayManager\t6\tsetDlpLumensLevel\tvoid(byte)\nDisplayManager\t7\tgetDlpLumensLevel\tbyte()\n' \
    > "$W/r/research/aidl.tsv"
  H="# interface${T}NAME${T}code${T}method${T}args${T}kind${T}apps${T}source"
  cat > "$W/r/devices/hidl/hal/codes.tsv" <<EOF
java${T}Foo${T}apps/Foo/src/org/x/hal/HalCodes.java
stock${T}IGmpf${T}research/codes.txt${T}BpHwGmpf
$H
IGmpf${T}GMPF_SET_DLP_LUMENS_LEVEL${T}176${T}setDlpLumensLevel${T}W=writeUint8 R=${T}set${T}Foo${T}stock codes.txt
IGmpf${T}GMPF_GET_DLP_LUMENS_LEVEL${T}177${T}getDlpLumensLevel${T}W= R=readUint8${T}get${T}Foo${T}stock codes.txt
EOF
  cat > "$W/r/devices/aidl/hal/codes.tsv" <<EOF
java${T}Bar${T}apps/Bar/src/org/y/HalCodes.java
stock${T}*${T}research/aidl.tsv
$H
-${T}META_GET_INTERFACE_VERSION${T}0x00FFFFFF${T}getInterfaceVersion${T}int()${T}meta${T}Bar${T}AIDL meta
DisplayManager${T}DM_SET_DLP_LUMENS_LEVEL${T}6${T}setDlpLumensLevel${T}void(byte)${T}set${T}Bar${T}stock aidl.tsv
EOF
  cat > "$W/r/apps/Foo/src/org/x/hal/HidlCaller.java" <<'EOF'
package org.x.hal;
abstract class HidlCaller {
    final int call(int code, Object q) { return b.transact(code, q, null, 0); }
    final void callU8(int code, int v) { call(code, null); }
    final int callRetU8(int code) { return call(code, null); }
}
EOF
  cat > "$W/r/apps/Foo/src/org/x/hal/Client.java" <<'EOF'
package org.x.hal;
final class Client extends HidlCaller {
    enum Item { LAMP(HalCodes.GMPF_GET_DLP_LUMENS_LEVEL, HalCodes.GMPF_SET_DLP_LUMENS_LEVEL); final int get, set; Item(int g, int s) { get = g; set = s; } }
    void setLamp(int v) { callU8(HalCodes.GMPF_SET_DLP_LUMENS_LEVEL, v); }
    int getLamp() { return tx(HalCodes.GMPF_GET_DLP_LUMENS_LEVEL); }
    int get(Item item) { return callRetU8(item.get); }
    private int tx(int code) { return callRetU8(code); }
}
EOF
  cat > "$W/r/apps/Bar/src/org/y/Gmpf.java" <<'EOF'
package org.y;
final class Gmpf {
    enum Iface { DISPLAY("xgimi.hardware.gmpf.IDisplayManager"); Iface(String d) {} }
    enum Call {
        GET_INTERFACE_VERSION(null, HalCodes.META_GET_INTERFACE_VERSION, "getInterfaceVersion"),
        SET_DLP_LUMENS_LEVEL(Iface.DISPLAY, HalCodes.DM_SET_DLP_LUMENS_LEVEL, "setDlpLumensLevel");
        final int code; Call(Iface i, int c, String m) { code = c; }
    }
    boolean send(Call c) { return b.transact(c.code, null, null, 0); }
    void lamp() { send(Call.SET_DLP_LUMENS_LEVEL); }
}
EOF
  cat > "$W/r/apps/core/gm/src/org/z/GmpfOp.java" <<'EOF'
package org.z;
public enum GmpfOp { GET_DLP_LUMENS_LEVEL("getDlpLumensLevel"), SET_DLP_LUMENS_LEVEL("setDlpLumensLevel"); GmpfOp(String m) {} }
EOF
  cat > "$W/r/apps/core/gm/src/org/z/Shared.java" <<'EOF'
package org.z;
abstract class Shared<K> extends GmpfCaller<K> {
    int lamp() { return wire.retU8(key(GET_DLP_LUMENS_LEVEL)); }
    void setLamp(int v) { K k = key(SET_DLP_LUMENS_LEVEL); wire.u8(k, v); }
}
EOF
  cat > "$W/r/apps/Foo/src/org/x/hal/FooCodes.java" <<'EOF'
package org.x.hal;
final class FooCodes {
    static final GmpfCodes<Integer> T = new GmpfCodes<Integer>()
            .put(GET_DLP_LUMENS_LEVEL, HalCodes.GMPF_GET_DLP_LUMENS_LEVEL)
            .put(SET_DLP_LUMENS_LEVEL, HalCodes.GMPF_SET_DLP_LUMENS_LEVEL);
}
EOF
  cat > "$W/r/apps/Bar/src/org/y/BarCodes.java" <<'EOF'
package org.y;
final class BarCodes {
    static final GmpfCodes<Gmpf.Call> T = new GmpfCodes<Gmpf.Call>()
            .put(SET_DLP_LUMENS_LEVEL, Call.SET_DLP_LUMENS_LEVEL);
}
EOF
  python3 -B "$G/tools/hal/gen_codes.py" java --write --root "$W/r" > /dev/null
}
C="$G/tools/hal/check_codes.py"
fake; expect synthetic_green 0 python3 -B "$C" --root "$W/r"
expect synthetic_gen_check 0 python3 -B "$G/tools/hal/gen_codes.py" java --check --root "$W/r"
fake; edit "$W/r/apps/Foo/src/org/x/hal/Client.java" "callU8(HalCodes.GMPF_SET_DLP_LUMENS_LEVEL, v)" "callU8(176, v)"
expect literal_code 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Foo/src/org/x/hal/Client.java" "callU8(HalCodes.GMPF_SET_DLP_LUMENS_LEVEL, v)" "callU8(SET, v)"
edit "$W/r/apps/Foo/src/org/x/hal/Client.java" "final class Client extends HidlCaller {" "final class Client extends HidlCaller { private static final int SET = 176;"
expect local_constant 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Foo/src/org/x/hal/Client.java" "tx(HalCodes.GMPF_GET_DLP_LUMENS_LEVEL)" "tx(177)"
expect forwarder_literal 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Foo/src/org/x/hal/Client.java" "LAMP(HalCodes.GMPF_GET_DLP_LUMENS_LEVEL," "LAMP(177,"
expect code_enum_literal 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Bar/src/org/y/Gmpf.java" "HalCodes.DM_SET_DLP_LUMENS_LEVEL, \"setDlpLumensLevel\"" "6, \"setDlpLumensLevel\""
expect allow_enum_literal 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Bar/src/org/y/Gmpf.java" "HalCodes.DM_SET_DLP_LUMENS_LEVEL, \"setDlpLumensLevel\"" "HalCodes.DM_SET_DLP_LUMENS_LEVEL, \"getDlpLumensLevel\""
expect allow_enum_other_method 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Foo/src/org/x/hal/Client.java" "HalCodes.GMPF_SET_DLP_LUMENS_LEVEL, v" "HalCodes.GMPF_SET_NOTHING, v"
expect unknown_constant 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/devices/hidl/hal/codes.tsv" "${T}176${T}" "${T}175${T}"
expect row_not_stock 1 python3 -B "$C" --root "$W/r" --tables
fake; edit "$W/r/devices/aidl/hal/codes.tsv" "DM_SET_DLP_LUMENS_LEVEL" "DM_LAMP"
expect row_bad_name 1 python3 -B "$C" --root "$W/r" --tables
fake; edit "$W/r/devices/aidl/hal/codes.tsv" "${T}void(byte)${T}" "${T}void(int)${T}"
expect row_bad_signature 1 python3 -B "$C" --root "$W/r" --tables
fake; edit "$W/r/apps/Bar/src/org/y/HalCodes.java" "= 6;" "= 7;"
expect stale_class 1 python3 -B "$C" --root "$W/r" --tables
expect stale_class_gen 1 python3 -B "$G/tools/hal/gen_codes.py" java --check --root "$W/r"
fake; edit "$W/r/apps/core/gm/src/org/z/Shared.java" "wire.retU8(key(GET_DLP_LUMENS_LEVEL))" "wire.retU8(177)"
expect gmpf_wire_literal 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/core/gm/src/org/z/Shared.java" "key(SET_DLP_LUMENS_LEVEL)" "key(SET_NOTHING)"
expect gmpf_unknown_op 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Foo/src/org/x/hal/FooCodes.java" ".put(GET_DLP_LUMENS_LEVEL, HalCodes.GMPF_GET_DLP_LUMENS_LEVEL)" \
  ".put(GET_DLP_LUMENS_LEVEL, HalCodes.GMPF_SET_DLP_LUMENS_LEVEL)"
expect gmpf_row_other_method 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Foo/src/org/x/hal/FooCodes.java" "HalCodes.GMPF_GET_DLP_LUMENS_LEVEL)" "177)"
expect gmpf_row_literal 1 python3 -B "$C" --root "$W/r" --sources
fake; edit "$W/r/apps/Bar/src/org/y/BarCodes.java" ".put(SET_DLP_LUMENS_LEVEL," ".put(GET_DLP_LUMENS_LEVEL,"
expect gmpf_aidl_row_other_method 1 python3 -B "$C" --root "$W/r" --sources
if [ "${1:-}" = "--tree" ]; then
  expect tree_check_codes 0 python3 -B "$C"
  expect tree_gen_codes 0 python3 -B "$G/tools/hal/gen_codes.py" --check
fi
echo "check_codes_test: $PASS passed, $FAIL failed"
[ $FAIL = 0 ]
