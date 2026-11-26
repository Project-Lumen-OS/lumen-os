#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen Home showcase and search (plain JDK, no android.jar): the shipped "where to watch" table (assets/watch/apps.tsv),
# released and watchable titles only, the hero order, which app Смотреть opens, the "Искать в" order, the region, the
# relevance of search results, the hero rebinding on a new button. sh apps/LumenHome/test/watch/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17); fi
OUT=$(mktemp -d "${TMPDIR:-/tmp}/watch.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/home"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$S"/watch/WatchApps.java "$S"/watch/WatchPick.java \
  "$S"/watch/Showcase.java "$S"/watch/Region.java "$S"/search/Relevance.java "$S"/model/HeroBind.java "$S"/data/Card.java \
  "$HERE/test/watch/WatchTest.java"
"$JAVA_HOME/bin/java" -Dhome.dir="$HERE" -cp "$OUT" org.lumen.home.watch.WatchTest
