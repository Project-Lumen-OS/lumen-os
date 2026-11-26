# Files (org.lumen.files)

The file manager of every Lumen model (Z9X, Z9X HB, X50 family, TITAN, RS30, Play 6), in place of XGIMI's
`com.xgimi.filemanager`. Free on every model, like the stock one. Its own tile on Home (LEANBACK_LAUNCHER), and Home's USB
card opens it at that drive. Until 1.1.0 this was a side panel inside Home (1.0.4 to 1.0.10). The code moved here with
its history, and Home keeps no file code.

## What it does

- Places strip: projector storage, every USB drive and SD card (they come and go live, a removed drive drops its
  clipboard and its screen), Network (saved SMB servers, servers found on the network, DLNA media servers).
- Kind views over every drive: Videos, Music, Photos, Apps (.apk), Archives. Search by name: from a folder it looks
  in that folder and below, from a kind view or Network it looks across every drive.
- Rows: name, kind icon, size, date. Order by name, date or size, with folders first.
- Hold OK (or MENU) for actions: Open, Open with, Select, Select all, Copy, Move, Rename, Delete (with a confirm
  window), New folder, Unpack (zip, tar, tar.gz), Details (APK details show the label, package and version, and
  whether it is installed).
- Copy and move run with a progress bar and Cancel. A cancelled copy leaves no half files. Free space is checked
  first. A move on the same drive is a rename. A folder cannot go into itself. A name clash gets a free name
  ("name (2).ext"), so nothing is overwritten.
- Open: videos and music in Lumen Play (`com.xgimiru.player`) when it is installed, otherwise in the built-in player.
  Photos open in the built-in viewer. Anything else opens in another app chosen by the system.
- APK: handed to the system package installer (`ACTION_INSTALL_PACKAGE` as a trusted source). Its confirm window,
  restyled by `LumenPackageInstallerOverlay`, asks the user. Files never installs by itself. An APK on a network share
  is copied to the app cache first, then handed over.
- SMB 2/3 client (moved from Home): NTLMv2, signing, share list. Passwords are sealed with an AndroidKeyStore AES-GCM
  key (`lumen_files_net_pw`). Servers saved in Home's old panel move here once (`ServersMove` calls Home's
  `ServersHandOff`, which only answers Files signed with Home's key, then Home forgets them).
- DLNA: SSDP search for MediaServer / ContentDirectory, Browse in pages, http resources only. XML is parsed with no
  DTD and no entity expansion.
- NFS: second wave. Android has no NFS client without root, so it needs a user-space client first.

## Layout

| package | what |
|---|---|
| `model` | plain Java with no Android, tested on the host: `Kind`/`Kinds`, `Names`, `Sorter`, `Search`, `Selection`, `Place`/`Places`, `Sizes`, `Nav`, `Fs` + `LocalFs`, `Transfer`, `Clip`, `Archive`, `Walk`, `Upnp`/`Didl`/`Xml` |
| `data` | Android side of the stores: `Volumes` (StorageManager, live), `LocalFiles`, `SmbFs`/`SmbFinder`, `DlnaFs`/`DlnaFinder`, `ServersMove` |
| `net` | the SMB 2/3 client and `NetServers` (saved servers), `NetFilesProvider` (streams a share's file to a player) |
| `open` | `Opener` (which app opens what), `ApkInstall`, `ApkFiles`, `LocalProvider` (read-only content addresses), `PlayerActivity`, `PhotoActivity` |
| `ui` | `FilesActivity` (strip, list, bar), `Sheet` (actions panel), `Prompt` (confirm, progress, name entry), `Ui` (Lumen look over `org.lumen.ui`) |

The look comes from `apps/core/lumen-ui`: palette (dark and Sand light, following the system theme), Inter, the
focus ring, design px of a 1920x1080 screen. Icons come from `apps/core/lumen-icons`. Strings are English plus
Russian, and the Russian set is complete. Other system languages show English, never a part-translated screen.

## Images

`devices/*/build.conf` `apps=` lists LumenFiles. Each image recipe signs it with the model's platform key and places it
at `/system/priv-app/LumenFiles/LumenFiles.apk`. That is a priv-app with no privileged permission, so it needs no
permissions file. On Z9X and Play 6 it is dexpreopted like Home.

## Tests

- `sh test/run.sh`: model rules (`test/model/ModelTest`), the SMB client (`test/net`), APK names, and wiring
  (`test/wiring.py`: manifest, installer hand-off, Lumen Play first, Home's link and hand-off, strings, icons, every
  image recipe).
- `python3 test/mutate.py`: mutants of the rules and the wiring. Each one must turn the run red.
- Renders: headless emulator on the build host only (`~/lumen-build-tmp/files-app/sheet.jpg`).
