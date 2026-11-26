<a name="play6"></a><a name="play6-changes"></a>

# Lumen OS on the XGIMI Play 6

[Русская версия](ru.md). Other projectors: [Installing Lumen OS](../en.md).

This page is for the XGIMI Play 6, model code G0058. The Play 6 installs with a computer. Lumen OS Live on the
projector does not support it. Every other projector installs only from the projector itself, over the
network.

The install takes about 30 minutes and erases everything on the projector. Lumen OS replaces only the system:
XGIMI's firmware, calibration and factory data stay as they are. Lumen OS for the Play 6 comes without Google.
You can add Google services from your own download.

<a name="play6-need"></a><a name="play6-choose"></a>

## What you need

- A Play 6 on XGIMI firmware V1.7.116 or V1.8.127. If yours is lower, let the XGIMI system update itself over
  Wi-Fi first. Other versions are refused.
- Newer Play 6 units have the AIC Wi-Fi chip and need V1.8.127. Never put V1.7.116 on them, Wi-Fi and
  Bluetooth then stop working. The installer checks the chip and tells you.
- The power adapter connected the whole time and the battery at 50 % or more.
- One Wi-Fi network for the projector and the computer. A guest network that keeps devices apart does not work.
- A Mac or a Linux computer with internet for the first run. The Windows version `lumen-install.cmd` is not
  tested yet.
- A USB A-to-A cable and a FAT32 USB stick. The Play 6 starts reliably with that stick in its USB port. With the
  cable, a hub or a network adapter in the port at power on, it may not start.
- 4 GB of free space on the computer.

<a name="play6-prepare"></a>

## 1. Prepare the projector

1. On the XGIMI system open the settings, About, and check the firmware version.
2. Connect the projector to your Wi-Fi. If the settings have automatic system updates, turn them off.
3. Turn on debugging with SystemUI Tuner:
   1. Download SystemUI Tuner and copy it to the USB stick. XGIMI's file manager does not open `.apk` files,
      so rename the file to end in `.apk1`, for example `SystemUITuner.apk1`. On Windows turn on file name
      extensions in Explorer first.
   2. Plug the stick into the projector. Open the file in XGIMI's file manager and install it.
   3. Open SystemUI Tuner. In its first screens it offers to turn on USB debugging. Confirm with OK. On the
      Play 6 this turns on debugging over the network at once, without a restart.
4. Note the projector's address. The XGIMI network settings show it on the page of your Wi-Fi network. Below
   it is `192.168.x.y`. If you cannot find it, start the installer without `--ip`. It looks for the projector
   and asks you.

<a name="play6-files"></a>

## 2. Download the files

From the newest Play 6 release on the [Releases page](https://github.com/Project-Lumen-OS/lumen-os/releases)
(its name starts with `play6-`) download the image `lumen-os-play6-…-nogms-system.img`, `SHA256SUMS`,
`SHA256SUMS.sig` and `lumen-os-play6-…-installer.zip`. Unpack the zip and put the other files next to
`lumen-install.sh`. Open a terminal in that folder. The installer checks the signature itself and refuses the
files of another projector.

<a name="play6-check"></a><a name="play6-install"></a>

## 3. Install

1. Run the check. It writes nothing.

   ```
   bash lumen-install.sh --ip 192.168.x.y check
   ```

   It checks the model, the firmware, the Wi-Fi chip, the battery, what is in the USB port and the files. If it
   prints `STOP`, do what it says.
2. Run the install.

   ```
   bash lumen-install.sh --ip 192.168.x.y
   ```

3. It offers to save your apps and files to the computer. Then it shows what will be erased. Type `ERASE`.
4. When it asks, take the stick out of the projector's USB port, connect that port to the computer with the
   A-to-A cable and press Enter. Keep the power adapter connected.
5. The installer sends the projector into fastboot mode over Wi-Fi and writes Lumen OS. Do not pull the cable
   or the power now.
6. When it says to unplug the A-to-A cable, pull the cable, put only the FAT32 stick into the port and press
   Enter. If the picture does not come back within a minute, press the power button.
7. XGIMI's recovery erases the data, then Lumen OS starts for the first time. This can take up to 15 minutes.
   The Wi-Fi network is erased too, so the computer loses the projector here. That is expected.
8. When you see Lumen OS, answer `yes` to **Do you see Lumen OS?**. The installer finishes.

If the installer stops, fix what it says and run the same command again. It goes on where it stopped.

On Windows run `lumen-install.cmd` and put the options after the command, for example
`lumen-install.cmd check -Ip 192.168.x.y`. Keep the folder on the computer's own disk, for example `C:\lumen`.
For fastboot mode Windows may need the [Google USB Driver](https://developer.android.com/studio/run/win-usb).

<a name="play6-wifi"></a>

## 4. First start

Lumen OS starts clean. If it shows a setup, follow it. Otherwise open Settings > Network & Internet and join
your Wi-Fi. A few XGIMI demo files may show up in the Music folder. You can delete them.

<a name="play6-verify"></a>

## Check the install

Turning on debugging on Lumen OS is also needed for Google services and updates below:

1. Join Wi-Fi.
2. Open Settings > Device Preferences > About and press OK on Build 7 times.
3. Open Settings > Device Preferences > Developer options and turn on USB debugging. On the Play 6 this is
   debugging over the network.
4. Look up the projector's address in Settings > Network & Internet, on the page of your network.

To check the install, run `bash lumen-install.sh --ip 192.168.x.y verify` and allow the question on the
projector. Lines with `✓` are fine. Send the lines with `!` with your question.

<a name="play6-google"></a><a name="play6-one-pass"></a>

## Google services

You download Google services yourself and the installer adds them. You get Google Play, Google Play services,
the Google sign-in and the remote in the Google TV phone app. Chromecast built-in does not work: Google gives it
only to the models it has certified. Download exactly
[MindTheGapps-14.0.0-arm-ATV-full-20240523_191930.zip](https://github.com/MindTheGapps/14.0.0-arm-ATV/releases/download/MindTheGapps-14.0.0-arm-ATV-20240523_192108/MindTheGapps-14.0.0-arm-ATV-full-20240523_191930.zip)
and put it next to `lumen-install.sh`.

- **With the install.** Add `--gapps-zip MindTheGapps-14.0.0-arm-ATV-full-20240523_191930.zip` to the install
  command. After the first start the installer asks you to turn on debugging again, and in the same Developer
  options Rooted debugging. Then it adds the Google apps and resets the clean system once more. This way has
  not been tried on a projector yet. If something goes wrong, the installer tells you how to go on.
- **Later, on an installed Lumen OS.** Turn on debugging as in [Check the install](#play6-verify) and Rooted debugging, then run
  `bash lumen-install.sh --ip 192.168.x.y gapps --zip MindTheGapps-14.0.0-arm-ATV-full-20240523_191930.zip`.
  Type `ERASE` and connect the cable when it asks. This erases everything on the projector once more.

Then join Wi-Fi and sign in: Settings > Accounts > Add account.

<a name="updates"></a>

## Updates

The Play 6 does not update over Wi-Fi yet. Download the new Play 6 release as in step 2, turn on debugging and
run `bash lumen-install.sh --ip 192.168.x.y --keep-data flash`. It writes the new system over the cable and
keeps your data.

<a name="play6-problems"></a><a name="play6-noboot"></a><a name="play6-gsf"></a><a name="problems"></a>

## Something went wrong

- The installer prints `STOP` or stops. Do what it says, then run the same command again.
- The projector does not come back after the write, so you answered `no`:
  1. Leave only the FAT32 stick in the USB port. No cable, no network adapter, no hub.
  2. Pull the power adapter, wait 30 seconds, plug it in again and press the power button once. Do not hold
     the button while you plug it in, that starts the XGIMI restore.
  3. The first start can take up to 15 minutes. If it switches off after the XGIMI logo, repeat item 2 three to
     five times.
  4. If it still does not start, write to us and send the installer log `~/.lumen-installer/play6/install.log`.
- The projector does not power on at all. Pull the A-to-A cable, put the FAT32 stick into the port and press
  the power button.
- Google refuses the sign-in. Turn on debugging, run `bash lumen-install.sh --ip 192.168.x.y gsf` and register
  the ID it shows at [google.com/android/uncertified](https://www.google.com/android/uncertified). Wait 10 to 30
  minutes, then sign in again.

Ask in the Telegram channel [@projector_lumenos](https://t.me/projector_lumenos) or in
[GitHub issues](https://github.com/Project-Lumen-OS/lumen-os/issues). Attach the full terminal text and the
output of `bash lumen-install.sh status`.

<a name="play6-stock"></a><a name="play6-rescue"></a><a name="stock"></a>

## Back to the XGIMI system

The XGIMI recovery USB stick brings the XGIMI system back. It erases everything on the projector.

1. Download the official XGIMI file for your projector. Both files have the same name,
   `XGIMI_DRAGONHEALTHY_XN03C.bin`.

   | Firmware | For | File | Size in bytes, MD5 |
   |---|---|---|---|
   | V1.8.127 | units with the AIC Wi-Fi chip or serial XHF, and every Play 6 that ran V1.8.x | [V1.8.127](https://product-library.static.xgimi.com/firmware/release/MTK9270/dayao/MTK9270_APHRODIT_1.8.127_20260410-175145/XGIMI_DRAGONHEALTHY_XN03C.bin) | 2,115,834,528, `a14277a6c73676487a7de5ede36dc017` |
   | V1.7.116 | only units with the MediaTek Wi-Fi chip that came with V1.7.116 or lower | [V1.7.116](https://product-library.static.xgimi.com/firmware/release/MTK9270/dayao/MTK9270_APHRODIT_1.7.116_20251125-162039/XGIMI_DRAGONHEALTHY_XN03C.bin) | 2,051,543,712, `55ae66ed7e14a20759ea4b9d5686d591` |

   Check the size and the MD5 (Mac `md5 FILE`, Linux `md5sum FILE`, Windows `certutil -hashfile FILE MD5`).
   Never put V1.7.116 on a unit with the AIC chip or on one that ran V1.8.x. The check in step 3 shows which
   chip your projector has.
2. Format a USB stick with an MBR partition table and one FAT32 partition. A stick of 32 GB or less is the
   easiest. On a Mac: Disk Utility, View, Show All Devices, select the stick itself, Erase, format MS-DOS (FAT),
   scheme Master Boot Record.
3. Put only two files on the stick: `XGIMI_DRAGONHEALTHY_XN03C.bin` with exactly this name and an empty file
   `factory.txt`. On a Mac copy in the Terminal, because Finder adds hidden files:

   ```
   cp -X ~/Downloads/XGIMI_DRAGONHEALTHY_XN03C.bin /Volumes/STICK/
   ```

   ```
   touch /Volumes/STICK/factory.txt
   ```

   Then let the installer check the stick. It only reads it:

   ```
   bash lumen-install.sh stick /Volumes/STICK
   ```

4. Pull the A-to-A cable. Put the stick into the projector's USB port. Switch the projector off and unplug
   the power cable.
5. Hold the power button on the projector, plug the power cable in and keep holding for about 5 seconds. The
   screen shows 正在检测加载更新包 (looking for the update), then 系统更新中，请勿断开电源，预计4分钟 (updating,
   about 4 minutes).
6. Do not cut the power. If it does go off, do steps 4 and 5 again with the same stick. Then the XGIMI system
   starts as it came from the factory.
7. Take the stick out and delete `factory.txt` from it, so it does not restore again by accident.

To install Lumen OS again later, first run `bash lumen-install.sh --device play6 reset`. It makes the installer
forget the last install.

Lumen OS is an independent project. XGIMI, Google and LineageOS have nothing to do with it. You install it at
your own risk.
