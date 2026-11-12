# Installing Lumen OS

[Русская версия](ru.md)

There are two ways to install Lumen OS:

- [Easy install](#easy): from the projector itself, with the Lumen Installer app. No computer and no cable.
- [Advanced](#advanced): with a computer. For the Play 6 and for the cases the app does not cover.

| Projector | Easy install | Advanced |
|---|---|---|
| XGIMI Z9X (G0082) | try and install on XGIMI firmware V6.15.58 or V6.15.62, try only on V6.15.19 | [Z9X guide](z9x/en.md) |
| XGIMI Z9X HB (G0130, G0148) | try the stable release, try and install the beta. G0130 on V6.16.24 or V6.17.12, G0148 on V6.17.12 | [Z9X HB guide](hb/en.md) |
| XGIMI X50 Ultra (G0111), X50 Ultra Max (G0100) | try only, beta. XGIMI firmware V1.2.64 or V1.2.54 | no |
| XGIMI Play 6 (G0058) | no | [Play 6 guide](play6/en.md) |

Lumen Installer shows the model code and the XGIMI firmware of your projector under About (the round "i").

<a name="easy"></a>

## Easy install: from the projector

You need the projector on its XGIMI system with internet, a USB stick and free space in the projector's memory.
The app tells you how much space it needs.

1. Download the Lumen Installer `.apk` file from the [latest release](https://github.com/Project-Lumen-OS/lumen-installer/releases/latest).
2. Rename the file: change the ending `.apk` to `.apk1`. The XGIMI file manager may refuse a file that ends in
   `.apk`. Copy it to a USB stick (FAT32 or exFAT).
3. Plug the stick into the projector, open the file in the XGIMI file manager and confirm.
4. Open Lumen Installer. It reads the model, finds it in the signed list of supported projectors and downloads
   the Lumen OS release for exactly that model. Every file is checked against its signature before it is used.
   On the Z9X HB you choose Version: Stable or Version: Beta.
5. Try Lumen OS first: choose Get the trial ready, then Start Lumen OS. Lumen OS starts once, from files in the
   projector's memory. The XGIMI system and your files stay as they are. To go back, just restart the
   projector.
6. To keep Lumen OS, open Install Lumen OS inside the trial and choose whether you want Google services (Play
   Store, YouTube and your Google account, about 400 MB from their official page). Installing erases everything
   on the projector once: apps, accounts, settings and files. It takes about 10 minutes. Keep the projector
   plugged in.
7. Lumen OS then starts with its setup. New versions arrive over Wi-Fi. If Lumen OS ever fails to start, the
   projector goes back to the XGIMI system by itself.

Where installing for good is not open (the X50, the stable HB release, a Z9X on V6.15.19), the trial still works
and the app says so.

---

<a name="vbmeta"></a><a name="flash"></a><a name="verify"></a><a name="chromecast"></a>
<a name="play6"></a><a name="play6-need"></a><a name="play6-prepare"></a><a name="play6-files"></a><a name="play6-check"></a><a name="play6-install"></a><a name="play6-wifi"></a><a name="play6-verify"></a><a name="play6-google"></a><a name="play6-problems"></a><a name="play6-rescue"></a><a name="play6-stock"></a><a name="play6-changes"></a>
<a name="advanced"></a>

## Advanced: with a computer

The computer installer runs on macOS or Linux. A Windows version exists but has not been tested yet. Use it
for:

- the Play 6, which the app does not support yet.
- installing for good where the app offers only a trial: the stable HB release, a Z9X on V6.15.19.
- repairs, see Something went wrong in each guide.

Each guide has every step, Google services, updates, Something went wrong and Back to stock:
[XGIMI Z9X](z9x/en.md), [XGIMI Z9X HB](hb/en.md), [XGIMI Play 6](play6/en.md).

<a name="google"></a>

## Google services

Lumen OS comes without Google. With Easy install you choose Yes at Google services when you install. With a
computer, see the Google part of the guide for the [Z9X](z9x/en.md#google), the [Z9X HB](hb/en.md#hb-google)
or the [Play 6](play6/en.md#play6-google). Chromecast built-in is not part of it, see
[why](z9x/en.md#chromecast).

<a name="updates"></a>

## Updates

On the Z9X and the Z9X HB, Lumen OS updates itself over Wi-Fi and keeps your data, see
[Updates](z9x/en.md#updates). On the Play 6 a new version goes on with the computer installer and keeps your
data, see the [Play 6 guide](play6/en.md).

<a name="problems"></a><a name="rescue"></a>

## Something went wrong

- During a trial, restart the projector: it goes back to the XGIMI system. Remove the trial in Lumen Installer
  to free the space again.
- When you ask for help, add what Lumen Installer shows under About, Details for help.
- Google refuses the sign-in: see the [Z9X](z9x/en.md#gsf), [Z9X HB](hb/en.md#hb-gsf) or
  [Play 6](play6/en.md#play6-gsf) guide.
- With a computer: Something went wrong for the [Z9X](z9x/en.md#problems), the [Z9X HB](hb/en.md#hb-problems)
  and the [Play 6](play6/en.md#play6-problems).

Ask in [GitHub issues](https://github.com/Project-Lumen-OS/lumen-os/issues) or in the Telegram channel
[@projector_lumenos](https://t.me/projector_lumenos).

<a name="stock"></a>

## Back to stock

- During a trial: restart the projector.
- After an install from the projector: open the apps in Lumen OS and choose Go back to the XGIMI system. This
  erases everything in Lumen OS.
- Always: the official XGIMI firmware from a USB stick. This erases everything, see Back to stock for the
  [Z9X](z9x/en.md#stock), the [Z9X HB](hb/en.md#hb-stock) and the [Play 6](play6/en.md#play6-stock).
- X50 Ultra and X50 Ultra Max: a trial does not change the XGIMI system. The XGIMI firmware files are linked in
  the release notes for the [X50 Ultra](https://github.com/Project-Lumen-OS/lumen-os/releases/tag/x50-1.0.10-beta3)
  and the [X50 Ultra Max](https://github.com/Project-Lumen-OS/lumen-os/releases/tag/x50max-1.0.10-beta3).
