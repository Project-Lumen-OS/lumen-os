#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host test of the HDMI receiver bridge (image/lumen_hdmirx.sh) on a fake root: the real Z9X and Z9X HB captures as
# the dump, stub getprop / setprop / dmesg / cat in PATH.
# bash apps/core/lumen-signal/test/hdmirx_bridge_test.sh [OUT_FILE [HB_OUT_FILE]]
# Proves: the read path never writes the node; the node gets exactly "help" only when the read gave nothing; the floor
# between two dumps; model, token and node checks; the output keeps only the info lines.
set -u
HERE=$(cd "$(dirname "$0")/.." && pwd)
SCRIPT="$HERE/image/lumen_hdmirx.sh"
DUMP="$HERE/test/fixtures/z9x_hdmirx_ps5_4k60.txt"
HB_DUMP="$HERE/test/fixtures/hb_hdmirx_yuv420_4k60.txt"
KEEP_OUT=${1:-}
HB_OUT=${2:-}
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_hdmirx.XXXXXX")
trap 'rm -rf "$W"' EXIT
fails=0 oks=0
check() { if eval "$2"; then oks=$((oks + 1)); else fails=$((fails + 1)); echo "FAIL $1"; fi; }

mkdir -p "$W/bin"
cat > "$W/bin/getprop" <<'EOF'
#!/bin/sh
cat "$LUMEN_HDMIRX_ROOT/props/$1" 2>/dev/null
EOF
cat > "$W/bin/setprop" <<'EOF'
#!/bin/sh
printf '%s' "$2" > "$LUMEN_HDMIRX_ROOT/props/$1"
EOF
# the kernel log: the ring, our marker lines, then the dump when the receiver was asked the way this run allows
cat > "$W/bin/dmesg" <<'EOF'
#!/bin/sh
R=$LUMEN_HDMIRX_ROOT
cat "$R/ring" "$R/dev/kmsg" 2>/dev/null
mode=$(cat "$R/mode")
if [ "$mode" = read ] && [ -f "$R/was_read" ]; then cat "$R/dump"; fi
if [ "$mode" = help ] && [ "$(head -c 64 "$R/proc/utopia_mdb/HDMIRX")" = help ]; then cat "$R/dump"; fi
EOF
REALCAT=$(command -v cat)
cat > "$W/bin/cat" <<EOF
#!/bin/sh
if [ "\$1" = "\$LUMEN_HDMIRX_ROOT/proc/utopia_mdb/HDMIRX" ]; then : > "\$LUMEN_HDMIRX_ROOT/was_read"; exit 0; fi
exec $REALCAT "\$@"
EOF
chmod +x "$W/bin/"*

setup() {   # setup NAME MODE [MODEL [DUMP]]
  R="$W/$1"
  mkdir -p "$R/props" "$R/proc/utopia_mdb" "$R/dev/lumen_hdmirx"
  : > "$R/proc/utopia_mdb/HDMIRX"
  printf '[ 9000.000000] [PQ] PQ_DBG_ERROR old noise\n' > "$R/ring"
  cp "${4:-$DUMP}" "$R/dump"
  printf '%s' "$2" > "$R/mode"
  printf '%s' "${3:-G0082}" > "$R/props/ro.boot.xgimi.modelname"
  printf '9001.50 100.00\n' > "$R/proc/uptime"
}
run() { LUMEN_HDMIRX_ROOT="$R" PATH="$W/bin:$PATH" sh "$SCRIPT"; }
ask() { printf '%s' "$1" > "$R/props/sys.lumen.hdmirx.req"; run; }
done_() { cat "$R/props/sys.lumen.hdmirx.done" 2>/dev/null; }
node() { cat "$R/proc/utopia_mdb/HDMIRX"; }

setup read read
ask abc-1
check "read path: answered ok" '[ "$(done_)" = "abc-1 ok" ]'
check "read path: the node was never written" '[ -z "$(node)" ]'
check "read path: trigger read" 'grep -qx "trigger read" "$R/dev/lumen_hdmirx/info.txt"'
check "token line first" '[ "$(head -n1 "$R/dev/lumen_hdmirx/info.txt")" = "token abc-1" ]'
check "info kept" 'grep -qx "Color space RGB444" "$R/dev/lumen_hdmirx/info.txt" && grep -qx "Color depth 12bit" "$R/dev/lumen_hdmirx/info.txt" && grep -q "^AVI packet Byte 3: 0x61" "$R/dev/lumen_hdmirx/info.txt"'
check "no EDID rows, no HDCP, no other drivers, no time stamps" '! grep -qE "^[0-9A-F]{2}: |HDCP|PQ_DBG|avc:|^\[" "$R/dev/lumen_hdmirx/info.txt"'
check "file mode 644" '[ "$(stat -c %a "$R/dev/lumen_hdmirx/info.txt" 2>/dev/null || stat -f %Lp "$R/dev/lumen_hdmirx/info.txt")" = 644 ]'
[ -n "$KEEP_OUT" ] && cp "$R/dev/lumen_hdmirx/info.txt" "$KEEP_OUT"
printf '9002.00 100.00\n' > "$R/proc/uptime"
before=$(md5sum "$R/dev/lumen_hdmirx/info.txt" 2>/dev/null || md5 -q "$R/dev/lumen_hdmirx/info.txt")
ask abc-2
check "0.5 s later: busy, nothing asked" '[ "$(done_)" = "abc-2 busy" ] && [ -z "$(node)" ]'
check "busy leaves the last file" '[ "$(md5sum "$R/dev/lumen_hdmirx/info.txt" 2>/dev/null || md5 -q "$R/dev/lumen_hdmirx/info.txt")" = "$before" ]'
printf '9002.45 100.00\n' > "$R/proc/uptime"
ask abc-3
check "0.95 s later: allowed (Fast pace is 1 s)" '[ "$(done_)" = "abc-3 ok" ]'

setup help help
ask def-1
check "help path: ok" '[ "$(done_)" = "def-1 ok" ]'
check "help path: the node got exactly help, no newline" '[ "$(od -An -c "$R/proc/utopia_mdb/HDMIRX" | tr -d " \n")" = help ]'
check "help path: trigger help" 'grep -qx "trigger help" "$R/dev/lumen_hdmirx/info.txt"'

setup none none
ask ghi-1
check "no dump at all: none" '[ "$(done_)" = "ghi-1 none" ]'
check "no dump: help written once, nothing else" '[ "$(node)" = help ]'

setup model read G0058
ask jkl-1
check "another model (Play 6, no bridge): refused" '[ "$(done_)" = "jkl-1 refused" ] && [ -z "$(node)" ] && [ ! -f "$R/was_read" ]'

setup token read
ask 'x;reboot'
check "bad token: no answer, nothing touched" '[ -z "$(done_)" ] && [ ! -f "$R/was_read" ] && [ -z "$(node)" ]'
ask 'abcdefghijklmnopqrstuvwxyz'
check "too long token: no answer" '[ -z "$(done_)" ]'

setup nonode read
rm -f "$R/proc/utopia_mdb/HDMIRX"
ask mno-1
check "no node: none" '[ "$(done_)" = "mno-1 none" ]'

# the Z9X HB (G0130 and G0148): the same read path on its own capture, the HB info lines kept
for model in G0130 G0148; do
  setup "hb_$model" read "$model" "$HB_DUMP"
  tok="hb-$(echo "$model" | tr A-Z a-z)"
  ask "$tok"
  check "HB $model: answered ok by the read" '[ "$(done_)" = "$tok ok" ] && grep -qx "trigger read" "$R/dev/lumen_hdmirx/info.txt"'
  check "HB $model: the node was never written" '[ -z "$(node)" ]'
done
check "HB: colour space, both depth lines, the port size and the receiver's depth kept" 'grep -qx "Color space YUV420" "$R/dev/lumen_hdmirx/info.txt" && grep -qx "Color depth not indicated or default as 8bit" "$R/dev/lumen_hdmirx/info.txt" && grep -qE "^u8Colordepth +10-bit$" "$R/dev/lumen_hdmirx/info.txt" && grep -qx "HDE: 1920  VDE: 2160" "$R/dev/lumen_hdmirx/info.txt"'
check "HB: the HDR packet and the link state kept" 'grep -qE "^HDR packet Byte 3: 0xd *$" "$R/dev/lumen_hdmirx/info.txt" && grep -qE "^HDR packet Byte 27: 0x0 *$" "$R/dev/lumen_hdmirx/info.txt" && grep -qE "^u8FRLRate +9 +is +LEGACY_20 *$" "$R/dev/lumen_hdmirx/info.txt"'
check "HB: no EDID rows, no HDCP, no driver noise, no time stamps" '! grep -qE "^[0-9A-F]{2}: |HDCP|UTPA_INFO|MUTEX_INFO|MI3_ERR|^\[" "$R/dev/lumen_hdmirx/info.txt"'
[ -n "$HB_OUT" ] && cp "$R/dev/lumen_hdmirx/info.txt" "$HB_OUT"
# the X50 family (the Z9X's MT5877 platform, the same receiver driver): the same read path on the Z9X capture
for model in G0111 G0100 G0155 H0061 H0062 H0063; do
  setup "x50_$model" read "$model"
  tok="x50-$(echo "$model" | tr A-Z a-z)"
  ask "$tok"
  check "X50 family $model: answered ok by the read" '[ "$(done_)" = "$tok ok" ] && grep -qx "trigger read" "$R/dev/lumen_hdmirx/info.txt"'
  check "X50 family $model: the node was never written" '[ -z "$(node)" ]'
done
check "Z9X capture: the SPD packet and the link state kept" 'grep -qE "^SPD packet Byte 0: 0x53 *$" "$R/dev/lumen_hdmirx/info.txt" && grep -qE "^SPD packet Byte 24: 0x8 *$" "$R/dev/lumen_hdmirx/info.txt" && grep -qE "^u8FRLRate +4 +is +FRL_8G_4L *$" "$R/dev/lumen_hdmirx/info.txt"'
setup hb_g0118 read G0118 "$HB_DUMP"
ask hb-g0118
check "HB G0118 (another amplifier, no dump yet): refused" '[ "$(done_)" = "hb-g0118 refused" ] && [ ! -f "$R/was_read" ]'

echo "$([ $fails = 0 ] && echo 'ok  ' || echo FAIL) hdmirx_bridge_test: $oks ok, $fails failed"
[ $fails = 0 ]
