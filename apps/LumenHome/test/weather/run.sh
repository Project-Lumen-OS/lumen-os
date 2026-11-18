#!/bin/sh
# Lumen Home weather city search (CitySearch: Latin and Cyrillic names whatever the UI language, transliterations,
# no IP location involved). sh test/weather/run.sh (plain JDK, no R.java). LIVE=1 also asks the real Open-Meteo
# service the way Weather.search does (network). The python part pins Weather.search and the manual city's units.
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$( [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 21 2>/dev/null || echo /usr/lib/jvm/java-21-openjdk-amd64)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/home/weather/CitySearch.java" "$HERE/test/weather/CitySearchTest.java" \
  "$HERE/test/weather/CitySearchLive.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.home.weather.CitySearchTest
python3 - "$HERE" <<'EOF'
import re, sys
w = open(sys.argv[1] + "/src/org/lumen/home/weather/Weather.java", encoding="utf-8").read()
bad = 0
need = [
    r"for \(String\[\] s : CitySearch\.queries\(q, lang\)\) \{",
    r"String\[\] more = CitySearch\.needsMore\(people\) \? CitySearch\.transliterated\(q, lang\) : null;",
    r"if \(more != null\) found\.add\(searchOne\(more\[0\], more\[1\], count\)\);",
    r"return CitySearch\.merge\(found, c -> c\.id, c -> c\.population, count\);",
    r"ct\.population = r\.optLong\(\"population\", 0\);",
    r"ct\.cc = r\.optString\(\"country_code\"\);",
    r"\.put\(\"cc\", ct\.cc\)",
    r"String cc = manual != null \? manual\.optString\(\"cc\"\) : sp\(c\)\.getString\(\"cc\", \"\"\);",
]
for rx in need:
    if not re.search(rx, w):
        bad += 1
        print("FAIL weather wiring: " + rx)
print("weather wiring: %d checks, %d failed" % (len(need), bad))
sys.exit(1 if bad else 0)
EOF
if [ "${LIVE:-0}" = 1 ]; then
  "$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.home.weather.CitySearchLive \
    Moskva en Moscow en "Москва" en "Москва" ru Moskva ru Ekaterinburg en "Екатеринбург" en \
    Rostov-na-Donu en "Sankt-Peterburg" en "Санкт-Петербург" de "Київ" en Almaty ru "<city>" en Kazan en
fi
echo "home weather search: ok"
