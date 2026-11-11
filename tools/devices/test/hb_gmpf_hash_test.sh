#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# hb_gmpf_hash_test.sh: the gmpf V1 hash rule of the Z9X HB apps (GmpfHash.java in apps/HbProjector, apps/HbBoot and
# apps/LumenInfo). On the Mac or Linux, javac only:
#   bash tools/devices/test/hb_gmpf_hash_test.sh
# 1. The three copies are one file: equal apart from the package line and the one doc line naming the app's transport.
# 2. The rule of each copy, compiled and run: 908de964 (V6.16.24) on any firmware; 715affd0 (V6.17.12) only on exactly
#    v6.17.12 (never on v6.16.24, an empty or a null firmware, V6.17.12 in upper case, a value with a space); any other
#    hash, a null or empty one, the hashes in upper case: refused. answer() gives back the accepted hash.
# 3. The hashes and the firmware are pinned (the strings of the stock gmpf-V1-ndk.so of each dump).
# 4. Each transport (AidlCaller, HbBoot HbGmpf, LumenInfo HbGmpf) asks GmpfHash.accepted exactly once, with the
#    firmware read from GmpfHash.FIRMWARE_PROP, and no app source but GmpfHash.java has a hash literal.
# The tree is only read.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/gmpf_hash.XXXXXX")
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
ok() { PASS=$((PASS + 1)); echo "ok   $1"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL $1"; }
COPIES="apps/HbProjector/src/org/lumen/projector/hal apps/HbBoot/src/org/lumen/hb/boot apps/LumenInfo/src/org/lumen/info/hal"
TRANSPORTS="apps/HbProjector/src/org/lumen/projector/hal/AidlCaller.java apps/HbBoot/src/org/lumen/hb/boot/HbGmpf.java apps/LumenInfo/src/org/lumen/info/hal/HbGmpf.java"

# 1. one file
first=""
for d in $COPIES; do
  f=$G/$d/GmpfHash.java
  [ -f "$f" ] || { bad "copy: $d/GmpfHash.java missing"; continue; }
  sed -E -e '1d' -e 's/\((HbProjector: AidlCaller|HbBoot: HbGmpf|LumenInfo: HbGmpf)\)/(APP)/' "$f" > "$W/norm.$(echo "$d" | cut -d/ -f2)"
  if [ -z "$first" ]; then first=$W/norm.$(echo "$d" | cut -d/ -f2)
  elif cmp -s "$first" "$W/norm.$(echo "$d" | cut -d/ -f2)"; then ok "copy: $d/GmpfHash.java = the HbProjector copy"
  else bad "copy: $d/GmpfHash.java differs from the HbProjector copy: $(diff "$first" "$W/norm.$(echo "$d" | cut -d/ -f2)" | head -4 | tr '\n' ' ')"; fi
done

# 2. + 3. the rule of each copy
mkdir -p "$W/src" "$W/out"
for d in $COPIES; do cp "$G/$d/GmpfHash.java" "$W/src/$(echo "$d" | cut -d/ -f2).java.in"; done
cat > "$W/src/HashRule.java" <<'EOF'
import java.lang.reflect.Method;

public class HashRule {
    static final String A = "908de964b7da427ad21df846e629b80ffdc183ab", B = "715affd032170b4d256c28c9bbc3c6cbaf5de492";
    static int fails;

    static void want(String cls, Method m, String h, String fw, boolean exp) throws Exception {
        boolean got = (Boolean) m.invoke(null, h, fw);
        if (got != exp) { fails++; System.out.println("FAIL rule: " + cls + ".accepted(" + h + ", " + fw + ") = " + got); }
    }

    public static void main(String[] a) throws Exception {
        for (String cls : a) {
            Class<?> c = Class.forName(cls);
            Method m = c.getMethod("accepted", String.class, String.class), ans = c.getMethod("answer", String.class);
            String v1 = (String) c.getField("V61624").get(null), v2 = (String) c.getField("V61712").get(null);
            String fw = (String) c.getField("V61712_FIRMWARE").get(null), prop = (String) c.getField("FIRMWARE_PROP").get(null);
            if (!A.equals(v1) || !B.equals(v2) || !"v6.17.12".equals(fw) || !"ro.vendor.build.version.incremental".equals(prop)) {
                fails++; System.out.println("FAIL pin: " + cls + " " + v1 + " " + v2 + " " + fw + " " + prop);
            }
            for (String f : new String[] {"v6.16.24", "v6.17.12", "v6.18.3", "", null}) want(cls, m, A, f, true);
            want(cls, m, B, "v6.17.12", true);
            for (String f : new String[] {"v6.16.24", "v6.18.3", "V6.17.12", "v6.17.12 ", " v6.17.12", "6.17.12", "", null}) want(cls, m, B, f, false);
            for (String h : new String[] {null, "", A.toUpperCase(), B.toUpperCase(), A.substring(0, 39), B + "0",
                    "0000000000000000000000000000000000000000", "notfrozen"}) {
                for (String f : new String[] {"v6.16.24", "v6.17.12", "", null}) want(cls, m, h, f, false);
            }
            if (!B.equals(ans.invoke(null, B)) || !A.equals(ans.invoke(null, A))) { fails++; System.out.println("FAIL answer: " + cls); }
        }
        System.out.println(fails == 0 ? "rule: OK" : "rule: " + fails + " failures");
        System.exit(fails == 0 ? 0 : 1);
    }
}
EOF
classes=""
for d in $COPIES; do
  pkg=$(echo "$d" | sed 's#^apps/[^/]*/src/##; s#/#.#g')
  mkdir -p "$W/src/$(echo "$pkg" | tr . /)"
  cp "$G/$d/GmpfHash.java" "$W/src/$(echo "$pkg" | tr . /)/GmpfHash.java"
  classes="$classes $pkg.GmpfHash"
done
if javac -d "$W/out" $(find "$W/src" -name '*.java') > "$W/javac.log" 2>&1; then
  if java -cp "$W/out" HashRule $classes > "$W/rule.log" 2>&1; then ok "rule of the three copies ($(tail -1 "$W/rule.log"))"
  else bad "rule: $(grep FAIL "$W/rule.log" | head -5 | tr '\n' ' ')"; fi
else
  bad "javac: $(head -5 "$W/javac.log" | tr '\n' ' ')"
fi

# 4. the transports
for t in $TRANSPORTS; do
  f=$G/$t
  n=$(grep -c 'GmpfHash\.accepted(' "$f")
  var=$(sed -n 's/.*!GmpfHash\.accepted([A-Za-z_][A-Za-z0-9_]*, *\([A-Za-z_][A-Za-z0-9_]*\)).*/\1/p' "$f" | head -1)
  if [ "$n" != 1 ]; then bad "transport: $t asks GmpfHash.accepted $n times, want 1"
  elif [ -z "$var" ]; then bad "transport: $t: GmpfHash.accepted(hash, <a variable>) not found"
  elif ! grep -qE "String $var = SystemProperties\.get\(GmpfHash\.FIRMWARE_PROP, \"\"\);" "$f"; then
    bad "transport: $t: the firmware '$var' is not read from GmpfHash.FIRMWARE_PROP"
  else ok "transport: $t asks GmpfHash.accepted once, firmware from GmpfHash.FIRMWARE_PROP"; fi
done
lit=$(cd "$G" && grep -rlE '"(908de964|715affd0)' apps/HbProjector/src apps/HbBoot/src apps/LumenInfo/src | grep -v '/GmpfHash\.java$')
if [ -z "$lit" ]; then ok "no app source but GmpfHash.java has a gmpf hash literal"; else bad "a hash literal outside GmpfHash.java: $lit"; fi

echo "hb_gmpf_hash_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
