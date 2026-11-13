# Lumen OS

English | [Русский](README.ru.md)

<p align="center">
  <a href="#-support-the-project"><img src="docs/repo/support/banner-en.svg" width="100%" alt="Support Lumen OS: every donation goes to a faster build computer"></a>
</p>
<p align="center">
  <a href="https://send.leobank.az/moneybox/W8IoNRjea6"><img src="https://img.shields.io/badge/💳_Any_card-Donate-ff3d8b?style=for-the-badge" alt="Donate by any card"></a>
  <a href="https://pay.cloudtips.ru/p/723aaba1"><img src="https://img.shields.io/badge/🇷🇺_Russian_cards_·_SBP-Donate-0f9d58?style=for-the-badge" alt="Donate with a Russian card or SBP"></a>
  <a href="#-support-the-project"><img src="https://img.shields.io/badge/🪙_USDT_·_TON-Crypto-6d4dff?style=for-the-badge" alt="Donate in crypto"></a>
</p>

Android TV 14 for XGIMI projectors, built on LineageOS 21.

News, new versions and answers to questions: the Telegram channel [@projector_lumenos](https://t.me/projector_lumenos).

## Supported projectors

| Projector | Status | Latest release | Install |
|---|---|---|---|
| XGIMI Z9X | stable | [Lumen OS 1.0.10](https://github.com/Project-Lumen-OS/lumen-os/releases/tag/z9x-1.0.10) | [from the projector](docs/install/en.md#easy) or [with a computer](docs/install/z9x/en.md) |
| XGIMI Play 6 | stable | [Lumen OS 1.0.7 for the Play 6](https://github.com/Project-Lumen-OS/lumen-os/releases/tag/play6-1.0.7) | [with a computer](docs/install/play6/en.md) |
| XGIMI Z9X HB | stable | [Lumen OS 1.0.9 for the Z9X HB](https://github.com/Project-Lumen-OS/lumen-os/releases/tag/z9x-hb-1.0.9) | [from the projector](docs/install/en.md#easy) or [with a computer](docs/install/hb/en.md) |
| XGIMI X50 Ultra, X50 Ultra Max | beta, try only | [X50 Ultra](https://github.com/Project-Lumen-OS/lumen-os/releases/tag/x50-1.0.10-beta3), [X50 Ultra Max](https://github.com/Project-Lumen-OS/lumen-os/releases/tag/x50max-1.0.10-beta3) | [from the projector](docs/install/en.md#easy) |

Each projector has its own release files, signed with its own key. The installer refuses an image for
another projector and any XGIMI firmware it does not know.

- XGIMI Z9X (model code G0082): XGIMI firmware V6.15.58, V6.15.62 or V6.15.19. Updates come over Wi-Fi.
- XGIMI Play 6 (model code G0058): XGIMI firmware V1.7.116, no other version. The newer V1.8.x (newer units with
  another Wi-Fi chip) is not supported yet: do not replace it with V1.7.116, Wi-Fi and Bluetooth then stop
  working. A new version goes on with the installer over the USB cable and
  keeps your data. Updates over Wi-Fi come later.
- XGIMI Play 6: Google services are not in the image and cannot be added yet. As on the Z9X, they are
  planned as an add-on that you make from your own MindTheGapps download.
- XGIMI Z9X HB (model code G0130): XGIMI firmware V6.16.24 with a computer. From the projector, the HB beta
  also installs on V6.17.12 and on the G0148 with V6.17.12. Updates come over Wi-Fi. Two XGIMI files are not
  in the image: the installer copies them from your own projector. Google services can be added with the
  installer from your own MindTheGapps download.
- XGIMI X50 Ultra (model code G0111) and X50 Ultra Max (model code G0100): XGIMI firmware V1.2.64 or V1.2.54.
  A beta that you try from the projector: Lumen OS starts once and the XGIMI system stays as it is.

More projectors will come later.

> [!TIP]
> **Lumen OS is free: no ads, no tracking, nothing to buy.** It is built in spare time on a slow computer, where one
> image takes hours. If it gave your projector a second life, please [💖 support the project](#-support-the-project):
> every donation goes to a faster build computer, so updates and new projectors come sooner.

> [!WARNING]
> **Lumen OS is never sold.** We do not sell the system, projectors with it, licences or "activation" of any kind.
> If someone charged you for Lumen OS or for a projector "with Lumen OS", that was not us, and we take no
> responsibility for such a sale. The only official source is the Releases page of this repository, free of charge.

<p>
<img src="docs/screenshots/en_01_projector.jpg" width="49%" alt="Projector settings in Lumen Home">
<img src="docs/screenshots/en_03_pro.jpg" width="49%" alt="Professional picture settings">
<img src="docs/screenshots/en_04_keystone.jpg" width="49%" alt="Keystone, optical zoom and picture rotation">
<img src="docs/screenshots/en_05_sound.jpg" width="49%" alt="Sound settings with the sound output choice">
<img src="docs/screenshots/en_06_files.jpg" width="49%" alt="The Files app">
<img src="docs/screenshots/en_07_update.jpg" width="49%" alt="Lumen OS update screen">
</p>

## What it is

Lumen OS replaces only the system partition of the projector. XGIMI's vendor firmware, boot, calibration,
lamp, focus and keystone stay as they are, and our apps drive them through XGIMI's own hardware service.
On the Z9X the interface runs at 1080p.

- Lumen Home: a clock over a live sky with the real sun and moon for your city (also the screensaver),
  weather, Continue watching from your installed apps, and all apps with favourites and A to Z.
- Projector app: autofocus, manual and auto keystone, picture and sound modes, eye protection, sleep,
  and a quick panel.
- Real sleep: the lamp goes off at once and the projector suspends. The remote wakes it in the same app.
- AirPlay receiver.
- HDMI-CEC: the HDMI input shows the name of the player or console, and the remote controls it.
- 17 languages. The XGIMI remote pairs in the first setup.
- Updates over Wi-Fi, signed with the project's key. If a new version fails to start, the projector
  goes back to the previous one.
- Files: the projector's storage and USB sticks, a built-in player, and any `.apk` installs from there.
- No ads and no Google services running in the background. At most 10 apps are kept cached.

This list is for the Z9X. The Play 6 has Lumen Home, its own projector app with every stock setting, the HDMI
input, the voice assistant and the first setup, but no AirPlay receiver, no updates over Wi-Fi and no Google
services yet.

Nothing about you is collected, apart from the anonymous usage counter you can turn off (see [Privacy](#privacy)).
The system goes online only for the time zone and city (ip-api.com over plain HTTP, ipapi.co or ipwho.is), the
weather (Open-Meteo), updates (GitHub) and that counter.

## Without Google

The published edition has no Google apps: no Google Play, no Chromecast built-in, no Google voice
search. Apps that need Google Play services will not run. Apps install from a USB stick or from any store
you choose. You can add Google services yourself. Installing from the projector, you choose Yes at Google
services and the installer downloads them from their official page. With a computer, they come from your own
MindTheGapps download, see the Google part of the guide for the [Z9X](docs/install/z9x/en.md#google), the
[Z9X HB](docs/install/hb/en.md#hb-google) or the [Play 6](docs/install/play6/en.md#play6-google).

## Install

One guide covers every projector: [Installing Lumen OS](docs/install/en.md)
([по-русски](docs/install/ru.md)). Installing Lumen OS for good erases all data on the projector, once.

Download only from this repository's [Releases](https://github.com/Project-Lumen-OS/lumen-os/releases) and the
[Lumen Installer releases](https://github.com/Project-Lumen-OS/lumen-installer/releases). The release marked
Latest is for the Z9X. Every Lumen OS release has `SHA256SUMS` and `SHA256SUMS.sig`. Both installers check them
and refuse anything else. Copies on forums and file hosts are not ours.

### Easy install: from the projector

No computer and no cable. For the Z9X, the Z9X HB, and as a trial the X50 Ultra and X50 Ultra Max.

1. Download the Lumen Installer file that ends in `.apk1` from the [latest release](https://github.com/Project-Lumen-OS/lumen-installer/releases/latest)
   and copy it to a USB stick (FAT32 or exFAT).
2. Plug the stick into the projector, open the file in the XGIMI file manager and confirm.
3. Open Lumen Installer. It finds your model and downloads the right Lumen OS release.
4. Try Lumen OS first. It starts once, and the XGIMI system and your files stay as they are. A restart brings
   the XGIMI system back.
5. To keep it, open Install Lumen OS inside the trial and choose whether you want Google services.

---

### Advanced: with a computer

For the Play 6, which the app does not support yet, and for the cases the app does not cover, see
[Advanced](docs/install/en.md#advanced).

## Updates and going back

On the Z9X, Lumen OS updates itself over Wi-Fi from Settings > Device Preferences > About > Lumen OS
update. An update installs next to the running system, keeps your data and waits for your OK. See
[Updates](docs/install/z9x/en.md#updates) and [CHANGELOG.md](CHANGELOG.md).

On the Play 6 there are no updates over Wi-Fi yet. Download the new Play 6 release and run
`bash lumen-install.sh --ip ADDRESS --keep-data flash`. It writes the new system over the USB cable and
keeps your data. Debugging must be on, see [step 6 of the Play 6 guide](docs/install/play6/en.md#play6-verify).

To go back during a trial, just restart the projector. After an install from the projector, open the apps in
Lumen OS and choose Go back to the XGIMI system. This erases everything in Lumen OS. The official XGIMI firmware
from a USB stick always works too and also erases everything, see Back to stock for the
[Z9X](docs/install/z9x/en.md#stock), the [Z9X HB](docs/install/hb/en.md#hb-stock) and the
[Play 6](docs/install/play6/en.md#play6-stock).

## Privacy

No ads, no tracking: only an anonymous usage counter that you can turn off. At every start and once a day it
sends the model, the version and the serial number (the server keeps only its hash), and from your connection the
server works out the country. Your IP address is not stored. In detail: the projector model (codename
and XGIMI model), the Lumen OS version, its version code and build id, the update channel, a random install id,
and after an update the previous version and how it was installed. The serial number and the install id are kept
only as a keyed hash, rows are deleted after 400 days, and nothing else is sent: no MAC address, no account, no
list of apps, nothing about what you watch. It only counts how many projectors run each version and how many
have updated. Country: IP Geolocation by [DB-IP](https://db-ip.com).

To turn it off: the switch "Send anonymous statistics" in the projector settings (Diagnostics). Turning it off
also deletes the install id. Turning it on again starts with a new one.

## Known issues

- The Windows installer has not been tested yet.
- On the Z9X, while a USB cable to a computer is connected, the projector does not really sleep. Unplug
  it after the install.
- Play 6: no updates over Wi-Fi, no AirPlay receiver and no Google services yet.

## Help and bug reports

Open an [issue](https://github.com/Project-Lumen-OS/lumen-os/issues) and include the projector model, the XGIMI
firmware version, the Lumen OS version (Settings > Device Preferences > About), what you did right before
the problem and the full installer output (in Lumen Installer: About, Details for help). Russian-speaking users
also talk about Lumen OS in the [XGIMI Z9X thread on 4PDA](https://4pda.to/forum/index.php?showtopic=1121837)
and in the Telegram channel [@projector_lumenos](https://t.me/projector_lumenos).

## Lumen Play

The video player made for Lumen OS lives in [its own repository](https://github.com/kmuradoff/kino-player).

## Build

Lumen OS is put together from a LineageOS 21 TV GSI, our apps (`apps/`), system files (`system/`) and
the image tools (`tools/`). You can build all of it yourself, from the pinned LineageOS source to the
signed image, with your own keys: [docs/selfbuild](docs/selfbuild/en.md). That guide covers the Z9X. The
Play 6 build scripts are in `tools/play6` and `lineage/patches/play6`, without a step-by-step guide yet.

```
git clone --recurse-submodules https://github.com/Project-Lumen-OS/lumen-os.git
```

## Thanks

Special thanks to [@Avalone-s](https://github.com/Avalone-s) and [@SAAndrevv](https://github.com/SAAndrevv),
who helped make this project happen.

To the LineageOS, UxPlay, android-airplay-server and MindTheGapps projects, and to everyone who tests
Lumen OS and reports back.

## 💖 Support the project

<div align="center">

<img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Smiling%20Face%20with%20Hearts.png" alt="Love" width="80" />

**If Lumen OS gave your projector a second life, please consider supporting it!**

Every donation goes to one goal: **a build computer (Beelink HX series), about $1,300.**<br>
Today one image takes hours to build. A powerful mini PC means faster updates and more projectors.

<br>

### 💳 Donate by card

[![Any bank card](https://img.shields.io/badge/💳_Any_card-Moneybox-ff69b4?style=for-the-badge)](https://send.leobank.az/moneybox/W8IoNRjea6)
[![Russian cards and SBP](https://img.shields.io/badge/🇷🇺_Russian_cards_·_SBP-CloudTips-0f9d58?style=for-the-badge)](https://pay.cloudtips.ru/p/723aaba1)

<br>

### 🪙 Or send crypto

| Currency | Network | Address | QR |
|:--------:|:-------:|:--------|:--:|
| **USDT** | TRC20 (TRON only) | `TRjBAWuPa7WjdsATNkgwy955imhobH6sQV` | <img src="docs/repo/support/usdt-trc20.png" width="110" alt="USDT TRC20 QR"> |
| **TON**, **USDT** | TON | `UQA2yAApJyyZ_vOYwrM6E0K2hwmn_35Y7m68UbCN6Y_A1L6x` | <img src="docs/repo/support/ton.png" width="110" alt="TON QR"> |

Check the network before you send: USDT sent to the TRON address over ERC20 or BEP20 is lost.

**Thank you!** 🙏

</div>

## License

Apache License 2.0, except `apps/Z9xAirPlay` (GPL-3.0-only) and the third-party submodules under
`apps/third_party`, which keep their own licenses. See [LICENSE](LICENSE) and [NOTICE](NOTICE). This
repository holds no Google apps, no MediaTek or XGIMI files and no signing keys.

Lumen OS is an independent project by kmuradoff. It is not affiliated with XGIMI, Google, MediaTek or
the LineageOS project. "XGIMI", "Android", "Google" and "LineageOS" are trademarks of their owners.
