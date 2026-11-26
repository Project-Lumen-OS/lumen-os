#!/system/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen HDMI receiver bridge (/system/etc/lumen/lumen_hdmirx.sh 0755, started by lumen_hdmirx.rc as a root oneshot).
# The signal panel (apps/core/lumen-signal HdmiRxBridge, system uid) cannot open the MStar receiver's debug node
# /proc/utopia_mdb/HDMIRX (0600 root). It sets sys.lumen.hdmirx.req to a fresh token; this script asks the receiver
# once, keeps only its info lines and answers in sys.lumen.hdmirx.done = "<token> ok|busy|none|refused".
#
# Asking the receiver: the node prints its info dump into the kernel log (at EMERG level, nothing comes back to the
# reader). First the read the stock XGIMI log collector uses (vendor/bin/autolog/modules/bsp/hdmirx: cat of the node);
# only if no dump follows, the one command proven live on the Z9X on 2026-11-18: the exact bytes "help" (it prints the
# command list and the dump). Never anything else: every other command of the node (fixedeq, hdmiloglevel, hpddelay,
# rst, ptg, set_edid) and the conf node write the hardware (test/check_hdmirx.py holds this file to it).
# Cost of one dump (MUTEX_INFO lines in the captures): the Z9X holds the receiver's lock for 7-11 ms, the Z9X HB for
# 359 and 393 ms right after the read (the picture can stall about 0.4 s), and its dump takes 1.6 s to print. Both
# models run the same way (2026-11-18): the app asks only while its panel is on screen (on a change and every
# 5 s, or every 1 s when the user chose Fast); this script refuses requests closer than MIN_GAP_CS on its own.
# The node has no cheaper read: its help lists only write commands (fixedeq, hdmiloglevel, hpddelay, rst, ptg) and a
# read always prints every port and every receiver (MDBCMD_GETINFO), on the Z9X and on the HB.
# The X50 family (X50 Ultra G0111, Ultra Max G0100, RS30 Ultra Max G0155, Titan H0061 / H0062 / H0063) runs the Z9X's MT5877
# platform with the same receiver driver: its vendor utpa2k.ko carries the same node, dump lines and help text
# (gsi-stock analysis), so the same two steps run there. Its cost per dump is not measured yet.
#
# Output /dev/lumen_hdmirx/info.txt 0644 (tmpfs, gone at reboot): "token <t>", "trigger read|help", then only the
# whitelisted info lines without the time stamps (receiver blocks, colour space and depth, AVI / VS / GC / HDR / SPD
# packet bytes, the port on screen and its size, the receiver's own depth and link rate). No EDID bytes, no HDCP lines,
# nothing of other drivers.
# mksh + toybox only, no number above 2^31 in $(( )).

# R is empty on the device (init gives services a clean environment); the host test sets it.
R=${LUMEN_HDMIRX_ROOT:-}
NODE=$R/proc/utopia_mdb/HDMIRX
HELP=help
OUT=$R/dev/lumen_hdmirx
KMSG=$R/dev/kmsg
MIN_GAP_CS=90
# 0.1 s steps: the Z9X dump ends in about 0.3 s, the HB dump in 1.6 s
WAIT_STEPS=30
MODELS=" G0082 G0130 G0148 G0111 G0100 G0155 H0061 H0062 H0063 "
KEEP='^-+ +Mstar HDMI (IP|port)[0-9] Info|^Signal invalid|^Active HDMI Port [0-9]|^HDE: *[0-9]+ *$|^VDE: *[0-9]+ *$|^Interlace mode: *[0-9]|^Color space |^Color depth |^AVMute:[0-9]|^(AVI|VS|GC|HDR|SPD) packet Byte [0-9]+: 0x[0-9a-fA-F]+ *$|^u8FRLRate +[0-9]+ +is +[A-Z0-9_]+ *$|^-+ HDMI Utopia PollingInfo HDMI IP [0-9]|^ucHDMIInfoSource |^bYUV420Flag |^u8Colordepth |^HDE: *[0-9]+ +VDE: *[0-9]+ *$|^=+ eARC Start'

req=$(getprop sys.lumen.hdmirx.req)
case "$req" in ''|*[!0-9a-z-]*) exit 0 ;; esac
[ "${#req}" -le 24 ] || exit 0

answer() {
  setprop sys.lumen.hdmirx.done "$req $1"
  exit 0
}

case "$MODELS" in *" $(getprop ro.boot.xgimi.modelname) "*) ;; *) answer refused ;; esac
[ -e "$NODE" ] || answer none
[ -d "$OUT" ] || mkdir -p "$OUT" || answer none

# the floor between two dumps, in 1/100 s of uptime
now=$(sed -n 's/^\([0-9]*\)\.\([0-9][0-9]\).*/\1\2/p' "$R/proc/uptime" 2>/dev/null)
case "$now" in ''|*[!0-9]*) answer none ;; esac
last=$(cat "$OUT/last" 2>/dev/null)
case "$last" in ''|*[!0-9]*) last=0 ;; esac
gap=$((now - last))
[ "$last" -eq 0 ] || [ "$gap" -lt 0 ] || [ "$gap" -ge "$MIN_GAP_CS" ] || answer busy
echo "$now" > "$OUT/last"

mark="lumen_hdmirx $req"
echo "$mark" >> "$KMSG" || answer none

since() { dmesg 2>/dev/null | sed -n "/$mark\$/,\$p"; }

wait_dump() {
  n=0
  while [ "$n" -lt "$WAIT_STEPS" ]; do
    since | grep -q 'eARC End' && return 0
    sleep 0.1
    n=$((n + 1))
  done
  since | grep -qE 'Mstar HDMI IP[0-9] Info'
}

trigger=read
cat "$NODE" > /dev/null 2>&1
if ! wait_dump; then
  trigger=help
  printf '%s' "$HELP" > "$NODE" 2>/dev/null
  wait_dump || answer none
fi

{
  echo "token $req"
  echo "trigger $trigger"
  since | sed 's/^\[[ 0-9.]*\] //' | grep -E "$KEEP" | head -n 400
} > "$OUT/info.new" 2>/dev/null || answer none
chmod 0644 "$OUT/info.new"
mv -f "$OUT/info.new" "$OUT/info.txt" || answer none
answer ok
