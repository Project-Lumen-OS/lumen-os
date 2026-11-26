#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host checks of the shared media route rules (apps/core/lumen-audio): MediaPath (the path a route plays on, the format
# again on a path switch), AutoPick (the stock Auto output), the Android glue compiles against the platform headers, and
# every projector's path picker, output choice and recipe use the module. sh apps/core/lumen-audio/test/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
S=$HERE/src/org/lumen/audio
mkdir -p "$OUT/glue" "$OUT/rules"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/glue" -cp "$HJ:$AJ" "$S/MediaPath.java" "$S/AutoPick.java" "$S/AutoOutput.java" "$S/JackOutput.java" \
  "$S/SinkOrder.java" "$S/HalSink.java" "$S/A2dpPick.java" "$S/BtA2dp.java" "$S/DspOwned.java" "$S/SoundbarMode.java" \
  "$S/ChipKey.java" "$S/ChipTable.java" "$S/ChipLevels.java" "$S/StockVolume.java" "$S/VendorOutput.java" "$S/VendorCall.java" \
  "$S/VendorAudio.java" "$S/PathSwitch.java" "$S/OutputDevice.java" "$S/OutputScan.java" "$S/BootSoundRules.java" \
  "$S/BootSound.java"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/rules" "$S/MediaPath.java" "$S/AutoPick.java" \
  "$G/apps/core/lumen-testkit/src/org/lumen/testkit/Check.java" "$HERE/test/org/lumen/audio/MediaPathTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/rules" org.lumen.audio.MediaPathTest "$G"
# the HAL sink order (SinkOrder, HalSink over a recording AudioManager stub) and the Bluetooth device of a choice
mkdir -p "$OUT/sink"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/sink" "$S/SinkOrder.java" "$S/HalSink.java" "$S/A2dpPick.java" \
  "$HERE/test/stubs/android/media/AudioManager.java" "$HERE/test/stubs/android/util/Log.java" \
  "$G/apps/core/lumen-testkit/src/org/lumen/testkit/Check.java" "$HERE/test/org/lumen/audio/HalSinkTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/sink" org.lumen.audio.HalSinkTest "$G"
# the stock volume split (ChipTable, ChipLevels, every model's table, DspOwned over a recording AudioDeviceVolumeManager
# stub) and its wiring per model and image
mkdir -p "$OUT/dsp"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/dsp" "$S/ChipKey.java" "$S/ChipTable.java" "$S/ChipLevels.java" \
  "$S/DspOwned.java" "$S/MediaPath.java" \
  "$HERE/test/stubs/android/content/Context.java" "$HERE/test/stubs/android/media/AudioManager.java" \
  "$HERE/test/stubs/android/media/AudioDeviceAttributes.java" "$HERE/test/stubs/android/media/AudioDeviceVolumeManager.java" \
  "$HERE/test/stubs/android/media/VolumeInfo.java" "$HERE/test/stubs/android/media/AudioSystem.java" \
  "$HERE/test/stubs/android/util/Log.java" \
  "$G/apps/core/lumen-testkit/src/org/lumen/testkit/Check.java" "$HERE/test/org/lumen/audio/ChipVolumeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/dsp" org.lumen.audio.ChipVolumeTest "$G"
# CEC System Audio and CEC volume control of an output choice (SoundbarMode) and its wiring in the HB and X50 apps
mkdir -p "$OUT/soundbar"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/soundbar" "$S/SoundbarMode.java" \
  "$G/apps/core/lumen-testkit/src/org/lumen/testkit/Check.java" "$HERE/test/org/lumen/audio/SoundbarModeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/soundbar" org.lumen.audio.SoundbarModeTest "$G"
# the power-on sound (BootSoundRules on the shipped asset) and its wiring: the module row of every profile, the stock
# switch off in the Z9X / HB / Play 6 adapters, BootSound installed with PathSwitch, the rc in every image recipe
mkdir -p "$OUT/boot"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/boot" "$S/BootSoundRules.java" \
  "$G/apps/core/lumen-testkit/src/org/lumen/testkit/Check.java" "$HERE/test/org/lumen/audio/BootSoundTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/boot" org.lumen.audio.BootSoundTest "$G"
