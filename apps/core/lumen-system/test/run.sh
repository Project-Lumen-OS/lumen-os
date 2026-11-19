#!/bin/sh
# lumen-system host tests: sh apps/core/lumen-system/test/run.sh
#  1. SystemIntentTest: the ways of every SettingsPage in order, each chain ends on the settings root;
#  2. SystemPages.open walks the chain: a missing or refusing way goes on, true only when a way started;
#  3. ColourVisionTest: the stock colour vision tokens on Android's daltonizer settings (map-backed stub);
#  4. no Android settings action outside SystemIntent in the apps that use the module.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_system.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
bad=0
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$HERE/src/org/lumen/system/SettingsPage.java" \
  "$HERE/src/org/lumen/system/SystemIntent.java" "$HERE/test/SystemIntentTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.system.SystemIntentTest || bad=1
mkdir -p "$OUT/cv"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/cv" "$HERE/src/org/lumen/system/ColourVision.java" \
  "$HERE/test/stubs/android/content/Context.java" "$HERE/test/stubs/android/content/ContentResolver.java" \
  "$HERE/test/stubs/android/provider/Settings.java" "$HERE/test/stubs/android/util/Log.java" "$HERE/test/ColourVisionTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/cv" org.lumen.system.ColourVisionTest || bad=1
mkdir -p "$OUT/sd"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/sd" "$HERE/src/org/lumen/system/ScreensaverDelay.java" \
  "$HERE/test/stubs/android/content/Context.java" "$HERE/test/stubs/android/content/ContentResolver.java" \
  "$HERE/test/stubs/android/provider/Settings.java" "$HERE/test/stubs/android/util/Log.java" "$HERE/test/ScreensaverDelayTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/sd" org.lumen.system.ScreensaverDelayTest || bad=1
mkdir -p "$OUT/hs"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/hs" "$HERE/src/org/lumen/system/HotspotRules.java" \
  "$HERE/src/org/lumen/system/HotspotProblem.java" "$HERE/test/HotspotRulesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/hs" org.lumen.system.HotspotRulesTest || bad=1
# Hotspot is the Android glue of HotspotRules: its lines that decide behaviour are pinned (no Android on the host)
HS="$HERE/src/org/lumen/system/Hotspot.java"
for w in '            return w != null && t != null && w.isPortableHotspotSupported() && t.isTetheringSupported()' \
         '                    && HotspotRules.tetherable(t.getTetherableWifiRegexs(), iface);' \
         '            return w != null && HotspotRules.on(w.getWifiApState());' \
         '        if (HotspotRules.name(name) != null || HotspotRules.password(password) != null) return false;' \
         '        boolean running = on(c);' \
         '        if (running) restart(c, d);' \
         '                    .setPassphrase(password, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK).build());' \
         '        if (!Arrays.equals(f, cur) && !write(c, f[0], f[1])) return null;' \
         '        if (n != null && pw != null) return new String[]{n, pw};' \
         '                if (i != null && !HotspotRules.stopped(i.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, -1))) return;' \
         '        h.postDelayed(() -> r.onReceive(app, null), RESTART_WAIT_MS);' \
         '            TetheringManager.TetheringRequest r = new TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI)' \
         '            tethering(c).stopTethering(TetheringManager.TETHERING_WIFI);'; do
  grep -qxF -- "$w" "$HS" || { echo "FAIL hotspot: Hotspot.java lost: $w"; bad=1; }
done
n=$(grep -c '^        } catch (RuntimeException e) {$' "$HS")
[ "$n" -ge 8 ] && echo "ok   hotspot: every Android call of Hotspot catches what Android throws ($n)" \
  || { echo "FAIL hotspot: an Android call of Hotspot can crash the menu ($n catches)"; bad=1; }
cat > "$OUT/open.exp" <<'EXP'
    public static boolean open(Context ctx, SettingsPage page) {
        for (SystemIntent w : SystemIntent.chain(page)) {
            try {
                Intent i = new Intent(w.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (w.pkg != null) i.setPackage(w.pkg);
                if (w.extra != null) i.putExtra(w.extra, w.value);
                if (i.resolveActivity(ctx.getPackageManager()) == null) continue;
                ctx.startActivity(i);
                return true;
            } catch (Throwable t) {
                Log.w(TAG, page + " via " + w + ": " + t);
            }
        }
        return false;
    }
EXP
sed -n '/^    public static boolean open(Context ctx, SettingsPage page) {$/,/^    }$/p' "$HERE/src/org/lumen/system/SystemPages.java" > "$OUT/open.got"
if cmp -s "$OUT/open.exp" "$OUT/open.got"; then
  echo "ok   SystemPages: the first way of the chain some app handles starts, a missing or refusing way goes on"
else
  echo "FAIL SystemPages: open does not walk SystemIntent.chain"; diff "$OUT/open.exp" "$OUT/open.got" | head -20; bad=1
fi
# the users open their settings screens through this module only
for f in apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java apps/LumenSetup/src/org/lumen/setup/steps/RemoteStep.java; do
  [ -f "$G/$f" ] || continue
  if grep -nE 'Settings\.ACTION_(BLUETOOTH|WIFI)|"android\.settings\.' "$G/$f"; then
    echo "FAIL users: $f opens a settings screen of its own"; bad=1
  fi
done
[ $bad = 0 ] && echo "lumen-system: all green"
exit $bad
