#!/bin/bash
# Host test of overlay/play6/play6_aic.sh on a fake root: the USB bus, /proc/modules and /sys are files in a
# temp folder, insmod / setprop / log / sleep are stubs. Cases: MediaTek unit, AIC unit, no chip, AIC whose
# vendor never loads cfg80211, AIC on a vendor that loads the AIC driver itself (V1.8.127 dongle.cfg).
# Run: bash tools/play6/aic/test_play6_aic.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
SCRIPT=$G/overlay/play6/play6_aic.sh
fails=0; n=0

# the vendor's wireless module map: V1.7.116 knows MediaTek only, V1.8.127 adds the three AIC modules
DONGLE_17116='mt7663:0e8d:7663:/vendor/lib/modules/btmtk_usb.ko
mt7663:0e8d:7663:/vendor/lib/modules/wlan_mt7663.ko
mt7961:0e8d:7961:/vendor/lib/modules/wlan_mt7961.ko'
DONGLE_18127="$DONGLE_17116
aic8d90:368b:8d90:/vendor/lib/modules/aic_load_fw.ko
aic8d90:368b:8d90:/vendor/lib/modules/aic_btusb.ko
aic8d90:368b:8d90:/vendor/lib/modules/aic8800_fdrv.ko"

setup() {   # $1 = usb id of the internal chip ("" = none), $2 = 1 when the vendor modules are in,
            # $3 = the vendor's dongle.cfg (default: the V1.7.116 one; "none" = no file)
  R=$(mktemp -d)
  mkdir -p "$R/sys/bus/usb/devices" "$R/sys/module" "$R/sys/class/net" "$R/proc" "$R/dev" "$R/bin" \
    "$R/system/etc/aic8800/modules" "$R/system/lib" "$R/vendor/lib/firmware"
  [ "${3:-}" = none ] || printf '# Config file for wireless module mapping\n%s\n' "${3:-$DONGLE_17116}" > "$R/vendor/lib/firmware/dongle.cfg"
  : > "$R/proc/modules"; : > "$R/proc/mounts"; : > "$R/props"; : > "$R/log"
  : > "$R/system/lib/libbt-vendor-aic.so"; : > "$R/vendor/lib/libbt-vendor.so"
  mkdir -p "$R/sys/bus/usb/devices/usb1"; echo 1d6b > "$R/sys/bus/usb/devices/usb1/idVendor"
  echo 0002 > "$R/sys/bus/usb/devices/usb1/idProduct"
  if [ -n "$1" ]; then
    mkdir -p "$R/sys/bus/usb/devices/3-1"
    echo "${1%:*}" > "$R/sys/bus/usb/devices/3-1/idVendor"; echo "${1#*:}" > "$R/sys/bus/usb/devices/3-1/idProduct"
  fi
  [ "$2" = 1 ] && mkdir -p "$R/sys/module/cfg80211" "$R/sys/module/firmware_class"
  cat > "$R/bin/insmod" <<EOF
#!/bin/bash
m=\$(basename "\$1" .ko); echo "\$m 1 0 - Live 0x0" >> "$R/proc/modules"; echo "insmod \$*" >> "$R/log"
case \$m in
  aic_load_fw) echo 8d91 > "$R/sys/bus/usb/devices/3-1/idProduct" ;;
  aic_btusb) echo late > "$R/aicbt_pending" ;;
  aic8800_fdrv) mkdir -p "$R/sys/class/net/wlan0" "$R/sys/class/net/p2p0" ;;
esac
EOF
  printf '#!/bin/bash\necho "$1=$2" >> "%s/props"\n' "$R" > "$R/bin/setprop"
  printf '#!/bin/bash\nshift 2; echo "log $*" >> "%s/log"\n' "$R" > "$R/bin/log"
  cat > "$R/bin/sleep" <<EOF
#!/bin/bash
# ueventd creates /dev/aicbt_dev a few seconds after aic_btusb loads
if [ -f "$R/aicbt_pending" ]; then c=\$(cat "$R/aicbt_n" 2>/dev/null || echo 0); c=\$((c + 1)); echo \$c > "$R/aicbt_n"
  [ \$c -ge 3 ] && { touch "$R/dev/aicbt_dev"; rm -f "$R/aicbt_pending"; }; fi
exit 0
EOF
  printf '#!/bin/bash\necho "mount $*" >> "%s/log"; echo "x /vendor/lib/libbt-vendor.so x" >> "%s/proc/mounts"\n' "$R" "$R" > "$R/bin/mount"
  printf '#!/bin/bash\necho "chown $*" >> "%s/log"\n' "$R" > "$R/bin/chown"
  printf '#!/bin/bash\necho "chmod $*" >> "%s/log"\n' "$R" > "$R/bin/chmod"
  for c in svc cmd stop start; do printf '#!/bin/bash\necho "%s $*" >> "%s/log"\n' "$c" "$R" > "$R/bin/$c"; done
  printf '#!/bin/bash\necho 1\n' > "$R/bin/settings"
  chmod 755 "$R/bin/"*
}
run() { R=$R PATH="$R/bin:$PATH" sh "$SCRIPT"; }
prop() { grep "^$1=" "$R/props" | tail -1 | cut -d= -f2-; }
eq() {
  n=$((n + 1))
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: want '$2', got '$3'"; fails=$((fails + 1)); fi
}

setup 0e8d:7663 1; run
eq "MediaTek unit: chip" mediatek "$(prop sys.lumen.wifi_chip)"
eq "MediaTek unit: aic off" off "$(prop sys.lumen.aic)"
eq "MediaTek unit: nothing loaded" 0 "$(grep -c insmod "$R/log")"
eq "MediaTek unit: MediaTek Bluetooth library kept" 0 "$(grep -c '^mount' "$R/log")"
eq "MediaTek unit: Wi-Fi and Bluetooth left alone" 0 "$(grep -cE '^(svc|cmd|stop|start) ' "$R/log")"
rm -rf "$R"

setup 368b:8d90 1; run
eq "AIC unit: chip" aic "$(prop sys.lumen.wifi_chip)"
eq "AIC unit: ready" ready "$(prop sys.lumen.aic)"
eq "AIC unit: load order" "aic_load_fw aic_btusb aic8800_fdrv" "$(awk '{print $1}' "$R/proc/modules" | xargs)"
eq "AIC unit: firmware path" 1 "$(grep -c 'aic_load_fw.ko aic_fw_path=/system/etc/aic8800/firmware' "$R/log")"
eq "AIC unit: wlan0" yes "$([ -d "$R/sys/class/net/wlan0" ] && echo yes)"
eq "AIC unit: Bluetooth library bound once" 1 "$(grep -c "^mount -o bind $R/system/lib/libbt-vendor-aic.so $R/vendor/lib/libbt-vendor.so" "$R/log")"
eq "AIC unit: aicbt_dev for user bluetooth (1002)" 1 "$(grep -c "^chown 1002:1002 $R/dev/aicbt_dev" "$R/log")"
eq "AIC unit: aicbt_dev mode" 1 "$(grep -c "^chmod 0660 $R/dev/aicbt_dev" "$R/log")"
eq "AIC unit: Wi-Fi restarted after the driver" 1 "$(grep -c '^svc wifi enable' "$R/log")"
eq "AIC unit: Bluetooth HAL restarted" "stop vendor.bluetooth-1-0 start vendor.bluetooth-1-0" "$(grep -E '^(stop|start) vendor' "$R/log" | xargs)"
eq "AIC unit: Bluetooth switched on again" 1 "$(grep -c '^cmd bluetooth_manager enable' "$R/log")"
run   # a second start loads nothing twice
eq "AIC unit: second run loads nothing again" 3 "$(grep -c insmod "$R/log")"
eq "AIC unit: second run binds nothing again" 1 "$(grep -c '^mount' "$R/log")"
rm -rf "$R"

setup 368b:8d91 1; run
eq "AIC already with firmware: ready" ready "$(prop sys.lumen.aic)"
rm -rf "$R"

setup "" 1; run
eq "no chip: none" none "$(prop sys.lumen.wifi_chip)"
eq "no chip: nothing loaded" 0 "$(grep -c insmod "$R/log")"
eq "no chip: no bind mount" 0 "$(grep -c '^mount' "$R/log")"
rm -rf "$R"

setup 368b:8d90 0; run
eq "AIC, vendor without cfg80211: fails cleanly" failed:cfg80211 "$(prop sys.lumen.aic)"
eq "AIC, vendor without cfg80211: no Wi-Fi module" 0 "$(grep -c 'insmod .*aic8800_fdrv' "$R/log")"
rm -rf "$R"

setup 368b:8d90 1 none; run
eq "AIC, vendor without dongle.cfg: our driver" ready "$(prop sys.lumen.aic)"
rm -rf "$R"

# V1.8.127: the vendor loads its own AIC modules and Bluetooth library; we must not touch anything
for id in 368b:8d90 368b:8d91; do
  setup $id 1 "$DONGLE_18127"; run
  eq "AIC $id, vendor V1.8.127: chip" aic "$(prop sys.lumen.wifi_chip)"
  eq "AIC $id, vendor V1.8.127: left to the vendor" vendor "$(prop sys.lumen.aic)"
  eq "AIC $id, vendor V1.8.127: nothing loaded" 0 "$(grep -c insmod "$R/log")"
  eq "AIC $id, vendor V1.8.127: vendor Bluetooth library kept" 0 "$(grep -c '^mount' "$R/log")"
  eq "AIC $id, vendor V1.8.127: Wi-Fi and Bluetooth not restarted" 0 "$(grep -cE '^(svc|cmd|stop|start|chown|chmod) ' "$R/log")"
  rm -rf "$R"
done

# a comment line that names the AIC ids is not a mapping
setup 368b:8d90 1 "$DONGLE_17116
# aic8d90:368b:8d90:/vendor/lib/modules/aic_load_fw.ko"; run
eq "AIC, only a commented AIC line: our driver" ready "$(prop sys.lumen.aic)"
rm -rf "$R"

setup 0e8d:7663 1 "$DONGLE_18127"; run
eq "MediaTek unit, vendor V1.8.127: aic off" off "$(prop sys.lumen.aic)"
eq "MediaTek unit, vendor V1.8.127: nothing touched" 0 "$(grep -cE '^(insmod|mount|svc|cmd|stop|start) ' "$R/log")"
rm -rf "$R"

echo; [ $fails = 0 ] && echo "OK: $n passed" || { echo "FAILED: $fails of $n"; exit 1; }
