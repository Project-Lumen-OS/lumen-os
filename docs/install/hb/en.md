<a name="hb"></a><a name="hb-changes"></a>

# Lumen OS on the XGIMI Z9X HB

[Русская версия](ru.md). Other projectors: [Installing Lumen OS](../en.md).

This page is for the XGIMI Z9X HB, model codes G0130 and G0148. You install Lumen OS from the projector itself,
with the Lumen Installer app. Lumen OS downloads over the projector's own internet connection, Wi-Fi or Ethernet. You need no
computer and no USB cable, and there is no other way to install it.

First Lumen OS starts once as Lumen OS Live. Your XGIMI system and your files stay as they are. If you like it,
you install it permanently from inside Lumen OS Live. Installing erases everything on the projector once. Lumen OS
replaces only the system: the XGIMI firmware, calibration, focus, keystone and lamp stay as they are.

<a name="hb-need"></a><a name="hb-prepare"></a>

## What you need

- The Z9X HB on its XGIMI system, connected to the internet over Wi-Fi or Ethernet.
- XGIMI firmware V6.16.24 or V6.17.12 on the G0130, V6.17.12 on the G0148. The G0118 is not supported yet.
- A USB stick with FAT32 or exFAT. It only carries the Lumen Installer app to the projector.
- Free space in the projector's memory. Lumen Installer tells you how much it needs.
- The projector plugged in the whole time.

Never put V6.16.24 on a G0148, or on any projector that came with V6.17.12. It is made for other models.

<a name="install"></a><a name="hb-install"></a><a name="hb-first-start"></a>

## Install

1. On a computer or a phone, download the Lumen Installer file that ends in `.apk1` from the
   [latest Lumen Installer release](https://github.com/Project-Lumen-OS/lumen-installer/releases/latest).
   Copy it to the USB stick as it is.
2. Plug the stick into the projector. Open the file in the XGIMI file manager and confirm the install.
3. Open Lumen Installer. At the top it shows your model and its XGIMI firmware. Below it says whether Lumen OS
   works on this projector.
4. Press **Prepare Lumen OS Live**. The app checks the projector, downloads Lumen OS, checks the download and
   prepares Lumen OS Live. This takes about 10 minutes. Most of it is the download. You can press **Pause** and
   continue later.
5. Press **Start Lumen OS Live** and confirm. Then press **Restart now**. Lumen OS starts once as Lumen OS Live.
6. Go through the setup on the screen and try Lumen OS. To go back, just restart the projector.
7. To keep Lumen OS, open **Install Lumen OS permanently**. It is the top line of the language step in the setup, and it is
   on the home screen of Lumen OS Live too.
8. Choose Google services: press OK on that line to switch between **Yes** and **No**. Then press
   **Continue**. The app checks the projector. Nothing changes yet.
9. When it says **All set**, press **Install**. If the app asks **Allow copying the XGIMI files?**, choose
   **Allow and continue**. Then confirm with **Erase and install**.
10. Wait about 10 minutes and do not switch the projector off. When it says **Lumen OS is installed**, press
    **Restart**. The projector clears its memory once, then Lumen OS starts with its setup.

The app asks **Allow copying the XGIMI files?** when the projector's two copies of the XGIMI firmware differ,
for example after an XGIMI update. Lumen OS then copies the firmware in use over the other copy, unchanged,
as XGIMI's own updates do.

<a name="google"></a><a name="hb-google"></a>

## Google services

Lumen OS comes without Google. To get Google Play, YouTube and your Google account, choose **Yes** at step 8.
Lumen OS then downloads Google services from their official page, about 400 MB. Updates and a factory reset
keep them. You choose them only at the install. To add them later, install Lumen OS again from the
projector: first put back the XGIMI system with the recovery stick.

Chromecast built-in does not work, with or without Google: Google gives it only to the models it has certified.
Use AirPlay from an iPhone, iPad or Mac, or DLNA (send to TV) from a phone app.

<a name="updates"></a>

## Updates

Open Settings > Device Preferences > About > Lumen OS update. By default a new version downloads by itself and
installs only after your OK. Your apps, settings and files stay.

- Before the first update Lumen OS asks **Allow copying the XGIMI files?** Choose **Allow and install**.
- If a new version does not start, the projector goes back to the version it had before.
- While an update installs, until the projector asks you to restart, do not change the picture settings or
  the keystone. A change made during that time is lost.

<a name="problems"></a><a name="hb-problems"></a><a name="rescue"></a><a name="hb-rescue"></a><a name="advanced"></a><a name="vbmeta"></a><a name="hb-vbmeta"></a><a name="hb-files"></a><a name="hb-check"></a><a name="hb-blobs"></a><a name="hb-verify"></a><a name="hb-gsf"></a>

## Something went wrong

- Lumen Installer stops with a message. Do what it says. It always tells you when nothing was changed.
- Your XGIMI firmware is not in the list. To only try Lumen OS on your version, tick
  **Start Lumen OS Live anyway**. Installing permanently from that version is not possible.
- The download keeps failing. Check that the projector is online and press **Prepare Lumen OS Live** again.
  The download goes on from where it stopped.
- Lumen OS Live does not work right. Restart the projector. It goes back to the XGIMI system. To free the space,
  open Lumen Installer and press **Remove Lumen OS Live**.
- The install stopped with **Installing stopped**. Your XGIMI system has not changed. You can try again.
- The app says **Something went wrong after the switch to Lumen OS**. Do not restart. Take a photo of the
  screen with the help code and ask us.
- After the install the picture stays dark for more than 15 minutes. Unplug the projector, plug it in again
  and switch it on with a short press. If Lumen OS does not start, the projector goes back to the XGIMI system
  by itself after a few tries.
- Lumen OS shows **One-time step needed for updates**. Only a projector that got Lumen OS from a computer
  long ago shows it. Put back the XGIMI system with the recovery stick, see Back to the XGIMI system below.
  Then install Lumen OS again from the projector.
- Google refuses the sign-in. Usually it just works. If not, write to us (below) and name your Lumen OS version.

Ask in the Telegram channel [@projector_lumenos](https://t.me/projector_lumenos) or in
[GitHub issues](https://github.com/Project-Lumen-OS/lumen-os/issues). Say your model, the XGIMI firmware and
what you did. It helps if you also press **Send info** in Lumen Installer and tell us the code it shows.

<a name="stock"></a><a name="hb-stock"></a>

## Back to the XGIMI system

In Lumen OS Live, just restart the projector. After an install, the XGIMI recovery USB stick brings the XGIMI
system back. It erases everything on the projector. Lumen OS shows the file name for the stick in
Settings > Device Preferences > About > Back to the XGIMI system.

1. Download the official XGIMI file for the firmware your projector had before Lumen OS. Both files have the
   same name, `XGIMI_GOATELEGANT_XR03D.bin`.

   | Your firmware was | File | Size in bytes |
   |---|---|---|
   | V6.17.12, and the G0148 always | [V6.17.12](https://product-library.static.xgimi.com/firmware/release/MTK9660_AOSP/dapeng/MTK9660_AOSP_GEMINI_6.17.12_20260728-200706/XGIMI_GOATELEGANT_XR03D.bin) | 2,178,814,576 |
   | V6.16.24, the G0130 only | [V6.16.24](https://product-library.static.xgimi.com/firmware/release/MTK9660_AOSP/dapeng/MTK9660_AOSP_GEMINI_6.16.24_20260428-152516/XGIMI_GOATELEGANT_XR03D.bin) | 2,160,464,496 |

   Check that the size matches to the byte. Never use a file for another model.
2. Format a USB stick with an MBR partition table and one FAT32 partition. On a Mac: Disk Utility, View, Show
   All Devices, select the stick itself, Erase, format MS-DOS (FAT), scheme Master Boot Record.
3. Put only two files on the stick: `XGIMI_GOATELEGANT_XR03D.bin` with exactly this name and an empty file
   `factory.txt`. On a Mac copy in the Terminal, because Finder adds hidden files:

   ```
   cp -X ~/Downloads/XGIMI_GOATELEGANT_XR03D.bin /Volumes/STICK/
   ```

   ```
   touch /Volumes/STICK/factory.txt
   ```

4. Put the stick into the projector's USB port. Unplug the power cable.
5. Hold the power button on the projector, plug the power cable in and keep holding for 6 to 7 seconds. If
   nothing happens, try the other USB port.
6. The restore takes a few minutes. Do not cut the power. If the power does go off, leave the stick in and
   switch the projector on again. Then the XGIMI system starts as it came from the factory.
7. Take the stick out and delete `factory.txt` from it, so it does not restore again by accident.

Lumen OS is an independent project. XGIMI, Google and LineageOS have nothing to do with it. You install it at
your own risk.
