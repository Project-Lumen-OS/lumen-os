#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Mutation check of the X50 family model table (apps/core/lumen-hal GmpfModel / GmpfHash) and its two users,
# X50EyeGuard (Device, Gmpf) and X50Projector (Gmpf, its remote pairing starts behind App.isX50 since lumen-remote):
# each edit below lets one model's image run on another
# model, gives a model another model's interface hash rule, or ignores ro.xgimi.modelname; the host suites of both apps
# (apps/X50EyeGuard/test/run.sh, apps/LumenProjector/host/activity/test/run.sh) must turn red on each, and pass unmutated.
#   sh tools/devices/test/gmpf_model_mutate.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
PASS=0
FAIL=0
H=apps/core/lumen-hal/src/org/lumen/hal
setup() {
  rm -rf "$W/t"; mkdir -p "$W/t/apps/core"
  for a in X50EyeGuard X50Projector X50TvInput LumenTvInput; do cp -R "$G/apps/$a" "$W/t/apps/$a"; rm -rf "$W/t/apps/$a/build"; done
  cp -R "$G/apps/core/." "$W/t/apps/core/"
  cp -R "$G/devices" "$W/t/devices"
  mkdir -p "$W/t/apps/common" && cp -R "$G/apps/common/projector" "$W/t/apps/common/"
  for a in Play6Projector LumenProjector/core; do mkdir -p "$W/t/apps/$a" && cp -R "$G/apps/$a/res" "$W/t/apps/$a/"; done
  ln -s "$G/tools" "$W/t/tools"
}
suites() {
  sh "$W/t/apps/X50EyeGuard/test/run.sh" > "$W/out" 2>&1 && sh "$W/t/apps/LumenProjector/host/activity/test/run.sh" >> "$W/out" 2>&1
}
# name file old new
mut() {
  setup
  name=$1; f=$2; old=$3; new=$4
  if ! python3 -B - "$W/t/$f" "$old" "$new" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
if s.count(old) != 1:
    sys.exit("edit: %r found %d times" % (old, s.count(old)))
open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
  then FAIL=$((FAIL + 1)); echo "FAIL $name (edit not applied)"; return; fi
  if suites; then FAIL=$((FAIL + 1)); echo "FAIL $name (suites still green)"; else PASS=$((PASS + 1)); echo "ok   $name"; fi
}
setup
if ! suites; then echo "FAIL unmutated suites are red"; tail -20 "$W/out"; exit 1; fi
mut any_codename "$H/GmpfModel.java" 'if (g.codename.equals(d) && g.model.equals(b)) return g;' 'if (g.model.equals(b)) return g;'
mut any_model "$H/GmpfModel.java" 'if (g.codename.equals(d) && g.model.equals(b)) return g;' 'if (g.codename.equals(d)) return g;'
mut modelname_ignored "$H/GmpfModel.java" 'if (!m.isEmpty() && !m.equals(b)) return null;' ''
mut rs30_x50_hash "$H/GmpfModel.java" 'RS30_ULTRA_MAX("g0155", "G0155", GmpfHash.RS30_RULE, "")' 'RS30_ULTRA_MAX("g0155", "G0155", GmpfHash.X50_RULE, "")'
mut rs30_codename_x50 "$H/GmpfModel.java" 'RS30_ULTRA_MAX("g0155", "G0155"' 'RS30_ULTRA_MAX("g0111", "G0155"'
mut unknown_gets_x50 "$H/GmpfModel.java" '        return HashRule.NONE;' '        return GmpfHash.X50_RULE;'
mut x50_v1254_rs30 "$H/GmpfHash.java" 'RS30_RULE = new HashRule(RS30);' 'RS30_RULE = new HashRule(RS30, X50_V1254_FIRMWARE, X50_V1254);'
mut rs30_hash_wrong "$H/GmpfHash.java" 'RS30 = "3a8aa5e6c42eb994e5922a154ac5a0bae146628f"' 'RS30 = "3a8aa5e6c42eb994e5922a154ac5a0bae1466280"'
mut eye_hash_fixed apps/X50EyeGuard/src/org/lumen/x50/eyeguard/Gmpf.java 'GmpfModel.ruleOf(App.bootModel())' 'GmpfModel.ruleOf("G0111")'
mut menu_hash_fixed apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector/Gmpf.java 'GmpfModel.ruleOf(App.bootModel())' 'GmpfModel.ruleOf("G0111")'
mut eye_any_device apps/X50EyeGuard/src/org/lumen/x50/eyeguard/Device.java 'if (GmpfModel.of(lumenDevice, bootModel, model) != null) return Fit.OWN;' 'if (GmpfModel.ruleOf(bootModel) != org.lumen.hal.HashRule.NONE) return Fit.OWN;'
mut family_none "$H/GmpfModel.java" 'if (g.codename.equals(d)) return true;' 'if (false) return true;'
mut family_any "$H/GmpfModel.java" 'if (g.codename.equals(d)) return true;' 'if (!d.isEmpty()) return true;'
mut titan_x50_rule "$H/GmpfModel.java" 'TITAN_NOIR_PRO("h0062", "H0062", GmpfHash.TITAN_RULE, "hd_server2")' 'TITAN_NOIR_PRO("h0062", "H0062", GmpfHash.X50_RULE, "hd_server2")'
mut titan_codename_x50 "$H/GmpfModel.java" 'TITAN_NOIR_PRO("h0062", "H0062"' 'TITAN_NOIR_PRO("g0111", "H0062"'
mut titan_max_x50_rule "$H/GmpfModel.java" 'TITAN_NOIR_MAX("h0063", "H0063", GmpfHash.TITAN_RULE' 'TITAN_NOIR_MAX("h0063", "H0063", GmpfHash.X50_RULE'
mut titan_max_codename_pro "$H/GmpfModel.java" 'TITAN_NOIR_MAX("h0063", "H0063"' 'TITAN_NOIR_MAX("h0062", "H0063"'
mut titan_v1299_everywhere "$H/GmpfHash.java" 'X50_RULE = new HashRule(X50, X50_V1254_FIRMWARE, X50_V1254, X50_V1338_FIRMWARE, X50_V1338);' 'X50_RULE = new HashRule(X50, X50_V1254_FIRMWARE, X50_V1254, X50_V1338_FIRMWARE, X50_V1338, "v1.2.99", "ad74d059b11cf5c5816cff11e285f8ec3bdc9270");'
mut titan_v1299_any_fw "$H/GmpfHash.java" 'TITAN_V1299_FIRMWARE = "v1.2.99";' 'TITAN_V1299_FIRMWARE = "v1.2.64";'
mut remote_any_device apps/LumenProjector/host/activity/src/org/lumen/x50/projector/App.java '        return GmpfModel.of(SystemProperties.get("ro.lumen.device", ""),' '        return GmpfModel.ruleOf(bootModel()) != org.lumen.hal.HashRule.NONE || GmpfModel.of(SystemProperties.get("ro.lumen.device", ""),'
echo "gmpf_model_mutate: $PASS killed, $FAIL survived"
[ "$FAIL" = 0 ]
