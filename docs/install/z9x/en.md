<a name="z9x"></a>

# Lumen OS on the XGIMI Z9X

[Русская версия](ru.md). Other projectors: [Installing Lumen OS](../en.md).

This page is for the XGIMI Z9X, model code G0082. You install Lumen OS from the projector itself, with the
Lumen OS Live app. Lumen OS downloads over the projector's own internet connection, Wi-Fi or Ethernet. You need no
computer and no USB cable, and there is no other way to install it.

First Lumen OS starts once as Lumen OS Live. Your XGIMI system and your files stay as they are. If you like it,
you install it permanently from inside Lumen OS Live. Installing erases everything on the projector once. Lumen OS
replaces only the system: the XGIMI firmware, calibration, focus, keystone and lamp stay as they are.

<a name="choose"></a><a name="what-you-need"></a>

## What you need

- The Z9X on its XGIMI system, connected to the internet over Wi-Fi or Ethernet.
- XGIMI firmware V6.15.58 or V6.15.62. On V6.15.19 only Lumen OS Live works. To install, let the XGIMI system
  update itself over Wi-Fi first.
- A USB stick with FAT32 or exFAT. It only carries the Lumen OS Live app to the projector.
- Free space in the projector's memory. Lumen OS Live tells you how much it needs.
- The projector plugged in the whole time.

<a name="install"></a><a name="1-prepare-the-projector-10-min"></a>

## Install

1. On a computer or a phone, download the Lumen OS Live file that ends in `.apk1` from the
   [latest Lumen OS Live release](https://github.com/Project-Lumen-OS/lumen-installer/releases/latest).
   Copy it to the USB stick as it is.
2. Plug the stick into the projector. Open the file in the XGIMI file manager and confirm the install.
3. Open Lumen OS Live. At the top it shows your model and its XGIMI firmware. Below it says whether Lumen OS
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

<a name="google"></a>

## Google services

Lumen OS comes without Google. To get Google Play, YouTube and your Google account, choose **Yes** at step 8.
Lumen OS then downloads Google services from their official page, about 400 MB. Updates and a factory reset
keep them. You choose them only at the install. To add them later, install Lumen OS again from the
projector: first put back the XGIMI system with the recovery stick.

<a name="chromecast"></a>
Chromecast built-in does not work, with or without Google: Google gives it only to the models it has certified.
Use AirPlay from an iPhone, iPad or Mac, or DLNA (send to TV) from a phone app.

<a name="updates"></a>

## Updates

Open Settings > Device Preferences > About > Lumen OS update. By default a new version downloads by itself and
installs only after your OK. Your apps, settings and files stay.

- Before the first update Lumen OS asks **Allow copying the XGIMI files?** Choose **Allow and install**.
- If a new version does not start, the projector goes back to the version it had before.

<a name="problems"></a><a name="rescue"></a><a name="if-nothing-helped"></a><a name="advanced"></a><a name="vbmeta"></a><a name="flash"></a><a name="one-pass"></a><a name="verify"></a><a name="gsf"></a>

## Something went wrong

- Lumen OS Live stops with a message. Do what it says. It always tells you when nothing was changed.
- Your XGIMI firmware is not in the list. Update the XGIMI system over Wi-Fi first. To only try Lumen OS on
  your version, tick **Start Lumen OS Live anyway**. Installing permanently from that version is not possible.
- The download keeps failing. Check that the projector is online and press **Prepare Lumen OS Live** again.
  The download goes on from where it stopped.
- Lumen OS Live does not work right. Restart the projector. It goes back to the XGIMI system. To free the space,
  open the Lumen OS Live app and press **Remove Lumen OS Live**.
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
what you did. It helps if you also press **Send info** in Lumen OS Live and tell us the code it shows.

<a name="stock"></a>

## Back to the XGIMI system

In Lumen OS Live, just restart the projector. After an install, the XGIMI recovery USB stick brings the XGIMI
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

Lumen OS is an independent project. XGIMI, Google and LineageOS have nothing to do with it. You install it at
your own risk.
