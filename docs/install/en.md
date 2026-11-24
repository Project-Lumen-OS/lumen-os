# Installing Lumen OS

[Русская версия](ru.md)

Lumen OS is Android TV 14 for XGIMI projectors. Find your projector below and open its page. Each page has
every step, Google services, updates, what to do if something goes wrong and the way back to the XGIMI system.

| Projector | Model code | How you install | Guide |
|---|---|---|---|
| XGIMI Z9X | G0082 | from the projector | [Z9X](z9x/en.md) |
| XGIMI Z9X HB | G0130, G0148 | from the projector | [Z9X HB](hb/en.md) |
| XGIMI X50 Ultra, X50 Ultra Max | G0111, G0100 | from the projector, beta | [X50, RS30 and TITAN](x50/en.md) |
| XGIMI RS30 Ultra Max | G0155 | from the projector, beta, Lumen OS Live only | [X50, RS30 and TITAN](x50/en.md) |
| XGIMI TITAN Noir, TITAN Noir Pro, TITAN Noir Max | H0061, H0062, H0063 | from the projector, beta, Lumen OS Live only | [X50, RS30 and TITAN](x50/en.md) |
| XGIMI Play 6 | G0058 | with a computer | [Play 6](play6/en.md) |

Lumen Installer shows the model code and the XGIMI firmware of your projector at the top of its screen, and on the
right, in the Firmware versions card, the version of every firmware part the projector names.

<a name="easy"></a>

## From the projector

On every projector in the list except the Play 6 there is one way to install Lumen OS: the Lumen Installer app
on the projector. It downloads Lumen OS over the projector's own internet connection, Wi-Fi or Ethernet. You
need no computer and no USB cable. Download the Lumen Installer file that ends in `.apk1` from the
[latest Lumen Installer release](https://github.com/Project-Lumen-OS/lumen-installer/releases/latest) and copy
it to a USB stick. The stick only carries the app. Lumen Installer first starts Lumen OS once as Lumen OS Live. Your
XGIMI system and your files stay as they are. To keep Lumen OS, you install it from inside Lumen OS Live. That
erases everything on the projector once. The steps are on your projector's page.

<a name="advanced"></a><a name="flash"></a><a name="verify"></a><a name="play6"></a><a name="play6-need"></a><a name="play6-prepare"></a><a name="play6-files"></a><a name="play6-check"></a><a name="play6-install"></a><a name="play6-wifi"></a><a name="play6-verify"></a><a name="play6-google"></a><a name="play6-problems"></a><a name="play6-rescue"></a><a name="play6-stock"></a><a name="play6-changes"></a>

## Play 6

Only the Play 6 installs with a computer, see the [Play 6 page](play6/en.md). Every other projector
installs only from the projector itself, as above.

<a name="new-model"></a>

## Your projector or firmware is not in the list

Lumen Installer tells you so. If the model is known and only the XGIMI firmware is not in the list, you can
tick **Start Lumen OS Live anyway**. Then only Lumen OS Live is possible, not the install.

You can also help us make Lumen OS for your projector. The button works only on the XGIMI system and changes
nothing on the projector.

- **Send info** sends us the projector's system information, about 100 MB: model, firmware version, the XGIMI
  settings app, app list, how the memory is divided, the system logs and settings. Passwords, Wi-Fi networks,
  accounts, keys and the serial number are not sent. If the sending stops, press the button again and it goes on
  from where it stopped. You get a code. Tell it to us when you write.

When we need the whole firmware of your projector, we tell you how to copy it.

<a name="google"></a><a name="chromecast"></a>

## Google services

Lumen OS comes without Google. When you install from the projector, choose **Yes** at Google services. On the
Play 6 the installer adds them from your own download, see [Google services](play6/en.md#play6-google).
Chromecast built-in does not work, with or without Google: Google gives it only to the models it has
certified. AirPlay and DLNA work.

<a name="updates"></a>

## Updates

Lumen OS updates itself over the internet: Settings > Device Preferences > About > Lumen OS update. Your apps,
settings and files stay. The Play 6 does not update over Wi-Fi yet, see [Updates](play6/en.md#updates) on its
page.

<a name="problems"></a><a name="rescue"></a><a name="vbmeta"></a>

## Something went wrong

- In Lumen OS Live, restart the projector. It goes back to the XGIMI system.
- What to do in each case is on your projector's page: [Z9X](z9x/en.md#problems),
  [Z9X HB](hb/en.md#problems), [X50, RS30 and TITAN](x50/en.md#problems), [Play 6](play6/en.md#play6-problems).
- Ask in the Telegram channel [@projector_lumenos](https://t.me/projector_lumenos) or in
  [GitHub issues](https://github.com/Project-Lumen-OS/lumen-os/issues). Say your model, the XGIMI firmware,
  the Lumen OS version and what you did. Lumen Installer shows the details for us under About, Details for help.

<a name="stock"></a>

## Back to the XGIMI system

- In Lumen OS Live, restart the projector.
- After an install, the XGIMI recovery USB stick with the official XGIMI file for your model brings the XGIMI
  system back. It erases everything on the projector. Lumen OS shows the file name for the stick in
  Settings > Device Preferences > About > Back to the XGIMI system.
- The files and the steps: [Z9X](z9x/en.md#stock), [Z9X HB](hb/en.md#stock),
  [X50 Ultra and X50 Ultra Max](x50/en.md#stock), [Play 6](play6/en.md#play6-stock).

Lumen OS is an independent project. XGIMI, Google and LineageOS have nothing to do with it. You install it at
your own risk.
