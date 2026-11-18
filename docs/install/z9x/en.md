<a name="z9x"></a>

# Lumen OS on the XGIMI Z9X

[Русская версия](ru.md). Other projectors: [Installing Lumen OS](../en.md).

This page is for the XGIMI Z9X, model code G0082. You install Lumen OS from the projector itself, with the
Lumen Installer app. You do not need a computer.

First Lumen OS starts once as a trial. Your XGIMI system and your files stay as they are. If you like it,
you install it for good from inside the trial. Installing erases everything on the projector once. Lumen OS
replaces only the system: the XGIMI firmware, calibration, focus, keystone and lamp stay as they are.

<a name="choose"></a><a name="what-you-need"></a>

## What you need

- The Z9X on its XGIMI system, connected to Wi-Fi with internet.
- XGIMI firmware V6.15.58 or V6.15.62. On V6.15.19 only the trial works. To install, let the XGIMI system
  update itself over Wi-Fi first.
- A USB stick with FAT32 or exFAT.
- Free space in the projector's memory. Lumen Installer tells you how much it needs.
- The projector plugged in the whole time.

<a name="install"></a><a name="1-prepare-the-projector-10-min"></a>

## Install

1. On a computer or a phone, download the Lumen Installer file that ends in `.apk1` from the
   [latest Lumen Installer release](https://github.com/Project-Lumen-OS/lumen-installer/releases/latest).
   Copy it to the USB stick as it is.
2. Plug the stick into the projector. Open the file in the XGIMI file manager and confirm the install.
3. Open Lumen Installer. At the top it shows your model and its XGIMI firmware. Below it says whether Lumen OS
   works on this projector.
4. Press **Get the trial ready**. The app checks the projector, downloads Lumen OS, checks the download and
   prepares the trial. This takes about 10 minutes. Most of it is the download. You can press **Pause** and
   continue later.
5. Press **Start Lumen OS** and confirm. Then press **Restart now**. Lumen OS starts once as a trial.
6. Go through the setup on the screen and try Lumen OS. To go back, just restart the projector.
7. To keep Lumen OS, open **Install Lumen OS**. It is the top line of the language step in the setup, and it is
   on the home screen of the trial too.
8. Choose Google services: press OK on that line to switch between **Yes** and **No**. Then press
   **Continue**. The app checks the projector. Nothing changes yet.
9. When it says **All set**, press **Install**. If the app asks **Allow copying the XGIMI files?**, choose
   **Allow and continue**. Then confirm with **Erase and install**.
10. Wait about 10 minutes and do not switch the projector off. When it says **Lumen OS is installed**, press
    **Restart**. The projector clears its memory once, then Lumen OS starts with its setup.

The app asks **Allow copying the XGIMI files?** when the projector's two copies of the XGIMI firmware differ,
for example after an XGIMI update. Lumen OS then copies the firmware in use over the other copy, unchanged,
as XGIMI's own updates do.

<a name="google"></a>

## Google services

Lumen OS comes without Google. To get Google Play, YouTube and your Google account, choose **Yes** at step 8.
Lumen OS then downloads Google services from their official page, about 400 MB. Updates and a factory reset
keep them.

<a name="chromecast"></a>
Chromecast built-in does not work, with or without Google: Google gives it only to the models it has certified.
Use AirPlay from an iPhone, iPad or Mac, or DLNA (send to TV) from a phone app.

<a name="updates"></a>

## Updates

Open Settings > Device Preferences > About > Lumen OS update. By default a new version downloads by itself and
installs only after your OK. Your apps, settings and files stay.

- Before the first update Lumen OS asks **Allow copying the XGIMI files?** Choose **Allow and install**.
- If a new version does not start, the projector goes back to the version it had before.

<a name="problems"></a><a name="rescue"></a><a name="if-nothing-helped"></a>

## Something went wrong

- Lumen Installer stops with a message. Do what it says. It always tells you when nothing was changed.
- Your XGIMI firmware is not in the list. Update the XGIMI system over Wi-Fi first. To only try Lumen OS on
  your version, tick **Start the trial anyway**. Installing for good from that version is not possible.
- The download keeps failing. Lumen Installer names the version under About (the round "i"), in the line
  Lumen OS to try. On a computer, download the image, `SHA256SUMS` and `SHA256SUMS.sig` of that version from
  the [Releases page](https://github.com/Project-Lumen-OS/lumen-os/releases). Put them into a folder `LumenOS`
  at the top of a USB stick. In Lumen Installer switch to **Get Lumen OS: USB stick or LumenOS folder**.
- The trial does not work right. Restart the projector. It goes back to the XGIMI system. To free the space,
  open Lumen Installer and press **Remove the trial**.
- The install stopped with **Installing stopped**. Your XGIMI system has not changed. You can try again.
- The app says **Something went wrong after the switch to Lumen OS**. Do not restart. Take a photo of the
  screen with the help code and ask us.
- After the install the picture stays dark for more than 15 minutes. Unplug the projector, plug it in again
  and switch it on with a short press. If Lumen OS does not start, the projector goes back to the XGIMI system
  by itself after a few tries.
- Google refuses the sign-in. Usually it just works. If not, see [Google refuses the sign-in](#gsf).

Ask in the Telegram channel [@projector_lumenos](https://t.me/projector_lumenos) or in
[GitHub issues](https://github.com/Project-Lumen-OS/lumen-os/issues). Say your model, the XGIMI firmware and
what you did. It helps if you also press **Send info** in Lumen Installer and tell us the code it shows.

<a name="stock"></a>

## Back to the XGIMI system

During the trial, just restart the projector. After an install, the XGIMI recovery USB stick brings the XGIMI
system back. It erases everything on the projector. Lumen OS shows the file name for the stick in
Settings > Device Preferences > About > Back to the XGIMI system.

1. Download the official XGIMI file for the firmware your projector had before Lumen OS. Both files have the
   same name, `XGIMI_HORSEPOPULAR_XR03D.bin`.

   | Your firmware was | File | Size in bytes |
   |---|---|---|
   | V6.15.58 or V6.15.62 | [V6.15.62](https://product-library.static.xgimi.com/firmware/release/MTK9681/dapengend/MTK9681_LEO_6.15.62_20260325-154151/XGIMI_HORSEPOPULAR_XR03D.bin) | 2,656,047,920 |
   | V6.15.19 | [V6.15.19](https://product-library.static.xgimi.com/firmware/release/MTK9681/dapengend/MTK9681_LEO_6.15.19_20251014-012201/XGIMI_HORSEPOPULAR_XR03D.bin) | 2,589,266,736 |

   Check that the size matches to the byte. Never use a file for another model.
2. Format a USB stick with an MBR partition table and one FAT32 partition. On a Mac: Disk Utility, View, Show
   All Devices, select the stick itself, Erase, format MS-DOS (FAT), scheme Master Boot Record.
3. Put only two files on the stick: `XGIMI_HORSEPOPULAR_XR03D.bin` with exactly this name and an empty file
   `factory.txt`. On a Mac copy in the Terminal, because Finder adds hidden files:

   ```
   cp -X ~/Downloads/XGIMI_HORSEPOPULAR_XR03D.bin /Volumes/STICK/
   ```

   ```
   touch /Volumes/STICK/factory.txt
   ```

4. Put the stick into the projector's USB 2.0 port. Unplug the power cable.
5. Hold the power button on the projector, plug the power cable in and keep holding for 6 to 7 seconds.
6. The restore takes about 4 minutes. Do not cut the power. If the power does go off, leave the stick in and
   switch the projector on again. Then the XGIMI system starts as it came from the factory.
7. Take the stick out and delete `factory.txt` from it, so it does not restore again by accident.

<a name="advanced"></a><a name="vbmeta"></a><a name="flash"></a><a name="one-pass"></a>

## With a computer

Most people never need this. You need a computer only to:

- add Google services to a Lumen OS that is already installed without them.
- get past the screen **One-time step needed for updates**. Only a Z9X installed from a computer long ago shows it.
- register the projector with Google when Google refuses the sign-in.
- install Lumen OS from a computer instead of Lumen Installer.

You need a Mac or a Linux computer (the Windows version `lumen-install.cmd` is not tested yet) and a USB
A-to-A cable in the projector's USB 2.0 port. From the newest Z9X release on the
[Releases page](https://github.com/Project-Lumen-OS/lumen-os/releases) download the image
`lumen-os-…-nogms-system.img`, `SHA256SUMS`, `SHA256SUMS.sig` and `lumen-os-…-installer.zip`. Unpack the zip
and put the other files next to `lumen-install.sh`. The installer checks the signature itself and writes
nothing that does not match.

Never run `fastboot -w`, `fastboot erase` or `adb reboot bootloader` on a Z9X. Use only the commands on this
page.

**1. Turn on USB debugging and connect the cable.** On the XGIMI system, install SystemUI Tuner from a USB
stick and turn debugging on there. The [4PDA thread](https://4pda.to/forum/index.php?showtopic=1121837) shows
it in Russian. If the file manager does not open the `.apk`, rename it to end in `.apk1` (on Windows turn on
file name extensions in Explorer first). On Lumen OS, open
Settings > Device Preferences > About, press OK on Build 7 times, then turn on Developer options > USB
debugging. Restart the projector, then connect the cable.

**2. The one step you do by hand.** The projector must be allowed once to start a system that XGIMI did not
sign. The installer never does this itself. Your data stays. Run `bash lumen-install.sh check` once: it
downloads the tools the commands below use. Then take `vbmeta.img` out of any arm64 archive on
[Google's page](https://developer.android.com/topic/generic-system-image/releases), for example `aosp_arm64`,
put it into the installer folder and run these commands right after each other. The Z9X leaves fastboot mode
after about 2 minutes.

```
tools/platform-tools/adb reboot fastboot
```

```
tools/platform-tools/fastboot --disable-verity --disable-verification --slot=all flash vbmeta vbmeta.img
```

```
tools/platform-tools/fastboot reboot
```

If the projector asks **Allow USB debugging?**, tick Always allow, press OK and run the first command again.
Both write lines must end in `OKAY`. On Windows write `tools\platform-tools\adb.exe` and
`tools\platform-tools\fastboot.exe`. On an installed Lumen OS, open Lumen OS update again afterwards.

**3. Install.** Run `bash lumen-install.sh`. It checks everything, copies the sound and video files from your
projector, asks you to type `ERASE` and writes Lumen OS. When it prints Done, unplug the cable. The setup
continues on the projector. To get Google services in the same run, add
`--gapps-zip MindTheGapps-14.0.0-arm64-ATV-full-20240523_192016.zip` (Mac and Linux only).

**Adding Google services later.** On the installed Lumen OS turn on USB debugging and, in the same Developer options,
Rooted debugging. Download exactly
[MindTheGapps-14.0.0-arm64-ATV-full-20240523_192016.zip](https://github.com/MindTheGapps/14.0.0-arm64-ATV/releases/download/MindTheGapps-14.0.0-arm64-ATV-20240523_192151/MindTheGapps-14.0.0-arm64-ATV-full-20240523_192016.zip)
and run `bash lumen-install.sh gapps --zip MindTheGapps-14.0.0-arm64-ATV-full-20240523_192016.zip`. This erases
everything on the projector once more.

<a name="verify"></a>
**Check the install.** Turn on USB debugging as in item 1, connect the cable and run
`bash lumen-install.sh verify`. Lines with `✓` are fine. Send the lines with `!` with your question.

<a name="gsf"></a>
**Google refuses the sign-in.** Turn on USB debugging and Rooted debugging as above and connect the cable. Run
`bash lumen-install.sh gsf` and register the ID it shows at
[google.com/android/uncertified](https://www.google.com/android/uncertified) with the same Google account. Wait
10 to 30 minutes, then sign in again.

If the installer stops, fix what it says and run the same command again. It goes on where it stopped. While
the cable is connected the projector does not really sleep, so unplug it when you are done. Every command is
in the [installer description](https://github.com/Project-Lumen-OS/lumen-os/blob/main/installer/README.md).

Lumen OS is an independent project. XGIMI, Google and LineageOS have nothing to do with it. You install it at
your own risk.
