#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen OS: copy rule for the shared sources of the apps built without a recipe (XGIMI HAL transport, projector app
# code and resources, framework key RRO files). An app with a recipe takes them with a file or base line instead
# (the colour tokens apps/common/tokens.xml and apps/common/GeoIp.java: Lumen Home and Setup), never a copy.
#
#   sh apps/common/sync.sh APP_DIR ITEM...            copy, ITEM = hal | projector | keys | power
#   sh apps/common/sync.sh --check APP_DIR ITEM...    exit 1 if a copy differs
#
# Copies:
#   common/hal/HidlCaller.java -> APP_DIR/src/org/lumen/projector/hal/HidlCaller.java (package kept, so the
#                      device clients next to it need no change): every device projector app
#   projector: every PATH listed in common/projector/FILES -> APP_DIR/PATH, from common/projector/PATH
#                      (one path per line, relative to the app dir, no spaces; # starts a comment): every
#                      device projector app. Refused: a path under src/org/lumen/projector/hal/ (the device HAL
#                      clients are per device; HidlCaller is the `hal` item). Rules: common/projector/README.txt
#   keys:  common/overlay/xml/global_keys.xml -> APP_DIR/res/xml/global_keys.xml: every framework RRO whose
#                      family keeps the shared key routing (Z9X, HB, Play 6; the X50 family has its own)
#   power: common/overlay/values/lumen_power_menu.xml -> APP_DIR/res/values/lumen_power_menu.xml (the Android power
#          menu's entries) and common/overlay/layout/shutdown_dialog.xml -> APP_DIR/res/layout/shutdown_dialog.xml
#          (the power off / restart screen): every framework RRO, one per family
# Never edit the copies; edit apps/common/* and re-run this with `keys power` (`power` for the X50 family) for every
# framework RRO; apps/common/test/power_menu_test.py lists them.
# No projector app keeps copies of `hal` or `projector` any more: their recipes take
# apps/common/projector and apps/common/hal as base layers.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
CHECK=0
if [ "${1:-}" = "--check" ]; then CHECK=1; shift; fi
[ $# -ge 2 ] || { echo "usage: sync.sh [--check] APP_DIR hal|projector|keys|power..." >&2; exit 2; }
APP=$(cd "$1" && pwd); shift
WHAT=$*
RC=0
# projector: every line of FILES is checked before the first copy, so a bad FILES copies nothing
PFILES=
case " $WHAT " in
  *" projector "*)
    F=$HERE/projector/FILES
    [ -f "$F" ] || { echo "sync: no $F" >&2; exit 2; }
    set -f   # FILES holds literal paths: no pathname expansion of its words
    for p in $(sed 's/#.*//' "$F"); do
      case $p in
        /*|..|../*|*/..|*/../*|*[*?[]*) echo "sync: $F: bad path $p" >&2; exit 2 ;;
        src/org/lumen/projector/hal/*) echo "sync: $F: $p is a device HAL file (never shared)" >&2; exit 2 ;;
      esac
      [ -f "$HERE/projector/$p" ] || { echo "sync: $F lists $p, but $HERE/projector/$p is missing" >&2; exit 2; }
      PFILES="$PFILES $p"
    done
    set +f ;;
esac
one() { # src dst
  if [ $CHECK = 1 ]; then
    if ! cmp -s "$1" "$2"; then echo "sync: $2 differs from $1" >&2; RC=1; fi
  else
    mkdir -p "$(dirname "$2")"
    cp "$1" "$2"
    echo "sync: $2"
  fi
}
for w in $WHAT; do
  case $w in
    hal)    one "$HERE/hal/HidlCaller.java" "$APP/src/org/lumen/projector/hal/HidlCaller.java" ;;
    projector)
      set -f
      for p in $PFILES; do one "$HERE/projector/$p" "$APP/$p"; done
      set +f ;;
    keys)   one "$HERE/overlay/xml/global_keys.xml" "$APP/res/xml/global_keys.xml" ;;
    power)
      one "$HERE/overlay/values/lumen_power_menu.xml" "$APP/res/values/lumen_power_menu.xml"
      one "$HERE/overlay/layout/shutdown_dialog.xml" "$APP/res/layout/shutdown_dialog.xml" ;;
    *) echo "sync: unknown item $w" >&2; exit 2 ;;
  esac
done
exit $RC
