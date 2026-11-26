<a name="x50"></a>

# Lumen OS on the XGIMI X50 Ultra, X50 Ultra Max, RS30 and TITAN

[Русская версия](ru.md). Other projectors: [Installing Lumen OS](../en.md).

This page is for these XGIMI projectors. You install Lumen OS from the projector itself, with the Lumen
OS Live app. Lumen OS downloads over the projector's own internet connection, Wi-Fi or Ethernet. You need no
computer and no USB cable, and there is no other way to install it.

| Projector | Model code | XGIMI firmware |
|---|---|---|
| X50 Ultra | G0111 | V1.2.64, V1.3.38 or V1.2.54 |
| X50 Ultra Max | G0100 | V1.2.64, V1.3.38 or V1.2.54 |
| RS30 Ultra Max | G0155 | V1.1.42 |
| RS30 Ultra | G0170 | V1.2.5 |
| TITAN Noir | H0061 | V1.2.99 or V1.2.54 |
| TITAN Noir Pro | H0062 | V1.2.99 or V1.2.54 |
| TITAN Noir Max | H0063 | V1.2.99 or V1.2.54 |

Lumen OS for these models is a beta. First it starts once as Lumen OS Live. Your XGIMI system and your files stay as
they are. Installing permanently is open on the X50 Ultra with V1.2.64 or V1.3.38. On the other models and versions only Lumen OS Live
works for now. Lumen OS Live tells you which applies before you start. Installing erases everything on
the projector once. Lumen OS replaces only the system: the XGIMI firmware and calibration stay as they are.

## What you need

- The projector on its XGIMI system, connected to the internet over Wi-Fi or Ethernet.
- A USB stick with FAT32 or exFAT. It only carries the Lumen OS Live app to the projector.
- Free space in the projector's memory. Lumen OS Live tells you how much it needs.
- The projector plugged in the whole time.

<a name="install"></a>

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

Chromecast built-in does not work, with or without Google: Google gives it only to the models it has certified.
Use AirPlay from an iPhone, iPad or Mac, or DLNA (send to TV) from a phone app.

<a name="updates"></a>

## Updates

Open Settings > Device Preferences > About > Lumen OS update. By default a new version downloads by itself and
installs only after your OK. Your apps, settings and files stay.

- Before the first update Lumen OS asks **Allow copying the XGIMI files?** Choose **Allow and install**.
- If a new version does not start, the projector goes back to the version it had before.
- An X50 Ultra with Lumen OS beta 5 or beta 6 cannot update by itself. Copy the Lumen OS Live file that
  ends in `.apk` to a USB stick and install it from Files in Lumen Home. Open Lumen OS Live and press
  **Update Lumen OS**. Confirm on the screen that opens. Then press **Open Lumen OS update** and
  **Download and install**. Your data stays.

<a name="problems"></a><a name="rescue"></a><a name="vbmeta"></a>

## Something went wrong

- Lumen OS Live stops with a message. Do what it says. It always tells you when nothing was changed.
- Your XGIMI firmware is not in the list. To only try Lumen OS on your version, tick
  **Start Lumen OS Live anyway**. Installing permanently from that version is not possible.
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

Ask in the Telegram channel [@projector_lumenos](https://t.me/projector_lumenos) or in
[GitHub issues](https://github.com/Project-Lumen-OS/lumen-os/issues). Say your model, the XGIMI firmware and
what you did. It helps if you also press **Send info** in Lumen OS Live and tell us the code it shows.

<a name="stock"></a>

## Back to the XGIMI system

In Lumen OS Live, just restart the projector. After an install, the XGIMI recovery USB stick brings the XGIMI
system back. It erases everything on the projector. Lumen OS shows the file name for the stick in
Settings > Device Preferences > About > Back to the XGIMI system.

1. Download the official XGIMI file for your model and the firmware it had before Lumen OS.

   | Projector | Your firmware was | File | Size in bytes |
   |---|---|---|---|
   | X50 Ultra | V1.2.64 | [XGIMI_HORSEPOPULAR_XR04E.bin](https://product-library.static.xgimi.com/firmware/release/MTK9681/diana/MTK9681_DOHKO_1.2.64_20260629-115816/XGIMI_HORSEPOPULAR_XR04E.bin) | 2,693,993,312 |
   | X50 Ultra | V1.2.54 | [XGIMI_HORSEPOPULAR_XR04E.bin](https://product-library.static.xgimi.com/firmware/release/MTK9681/diana/MTK9681_DOHKO_1.2.54_20260615-231328/XGIMI_HORSEPOPULAR_XR04E.bin) | 2,693,731,168 |
   | X50 Ultra Max | V1.2.64 | [XGIMI_HORSEPOPULAR_XR03E.bin](https://product-library.static.xgimi.com/firmware/release/MTK9681/diana/MTK9681_DOHKO_1.2.64_20260629-115816/XGIMI_HORSEPOPULAR_XR03E.bin) | 2,693,993,312 |
   | X50 Ultra Max | V1.2.54 | [XGIMI_HORSEPOPULAR_XR03E.bin](https://product-library.static.xgimi.com/firmware/release/MTK9681/diana/MTK9681_DOHKO_1.2.54_20260615-231328/XGIMI_HORSEPOPULAR_XR03E.bin) | 2,693,731,168 |

   Check that the size matches to the byte. The X50 Ultra and the X50 Ultra Max have different file names,
   never mix them up. For the RS30 Ultra Max and the TITAN we do not have the official file yet. On them only Lumen OS
   Live is open, so a restart is all you need.
2. Format a USB stick with an MBR partition table and one FAT32 partition. On a Mac: Disk Utility, View, Show
   All Devices, select the stick itself, Erase, format MS-DOS (FAT), scheme Master Boot Record.
3. Put only the `.bin` file on the stick, with exactly its name. On a Mac copy it in the Terminal, because
   Finder adds hidden files, for example:

   ```
   cp -X ~/Downloads/XGIMI_HORSEPOPULAR_XR04E.bin /Volumes/STICK/
   ```

4. Put the stick into the projector. Unplug the power cable.
5. Hold the power button on the projector itself, plug the power cable in and keep holding until the update
   screen shows, about 5 seconds.
6. Do not cut the power while it updates. Everything on the projector is erased, then the XGIMI system starts.

Lumen OS is an independent project. XGIMI, Google and LineageOS have nothing to do with it. You install it at
your own risk.
