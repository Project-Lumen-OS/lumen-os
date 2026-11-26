# Screen casting on every model

Owner decision 2026-11-21: screen casting is one shared, free feature on every model (Z9X, Z9X HB, Play 6, X50 Ultra,
X50 Ultra Max, RS30 Ultra Max, TITAN H0061 to H0063). One code path, model differences only as data.

## What ships

| Part | Where | Models |
|---|---|---|
| AirPlay receiver with the casting screen (org.lumen.airplay, APK Z9xAirPlay 1.0.11) | apps/LumenAirPlay, recipe app.layers | all, built on each model's own key |
| Screen mirroring sink (Miracast, org.lumen.miracast) | apps/LumenMiracast, priv-app | all (new on HB, X50, X50 Max, TITAN) |
| Wi-Fi Direct device type TV (static RRO) | apps/LumenWifiP2pOverlay | all |
| DLNA renderer | Lumen Play (com.xgimiru.player, prebuilt=LumenPlay) | all |
| Quick menu tile `mirroring`, All settings row `settings.casting` | devices/<c>/menu.json, lumen-menu | all |

Model data: `devices/<c>/cast.conf` (`airplay=1`, `miracast=1`, `mirror_4k=0|1`). `tools/devices/cast_models.py check`
enforces the rule (every recipe ships the three apps, the sink's permission files, Lumen Play, the two menu entries),
`write` generates `apps/LumenAirPlay/assets/cast/models.tsv`, which the app reads by `ro.boot.xgimi.modelname`.
Only the Play 6 (1080p) has `mirror_4k=0`: the 4K (HEVC) mirroring option is hidden there.

The DLNA card shows Lumen Play's real state: the casting screen asks its renderer for the device description on
`127.0.0.1:49595` every 3 s (`cast/DlnaProbe`). Ready (with the renderer's own name) when it answers, off when Lumen
Play is disabled or DLNA is switched off in its settings, needs Wi-Fi without a network, hidden without Lumen Play.

## Why the HB and the X50 family had no Miracast

Not the hardware. Every stock dump probed (G0082, G0111 v1.2.64 and v1.3.38, G0130, G0148, G0155, H0062 v1.2.99)
declares `android.hardware.wifi.direct` (vendor/etc/permissions/mtv_core_hardware.xml), starts the vendor
wpa_supplicant with `p2p_supplicant.conf` (`driver_param=shared_interface=p2p0`, `p2p_go_intent=14`) and the stock
receiver auto-accepts P2P invitations (`persist.xgimi.auto_accept_p2p_invite=true`). Probe:
`~/lumen-build-tmp/casting/probe_dumps.sh`, output `stock_probe.txt`.

The reason was the recipes: the HB recipe never listed the sink, and the X50 family image is the Z9X 1.0.9 base whose
system predates it (it carries only the old AirPlay 1.0.9 copy). The sink is Java only (WifiP2pManager, no vendor
code), so it needs no framework overlay beyond the Wi-Fi Direct TV device type. HDCP: the sink does not do HDCP. Android
and Windows senders mirror without it, protected content (some streaming apps) stays black on the sender's choice.
The sink hides its tile and card on a unit without Wi-Fi Direct (LumenMiracast ControlGate), so a unit that breaks
the probe's evidence still shows a clean screen.

## AirPlay: version, iOS, decoders

- Native: UxPlay 1.74 at 3dbf7cee (jni/BUILDINFO.txt) with our 6 patches. That commit already contains the upstream
  security fixes of 2026-08 and 2026-09 (NAL length bound, AES-CTR re-key reset, memcpy/strstr fixes) and the
  TEARDOWN 110 fix for iOS 27 beta. Upstream master has 36 newer commits (to 48f5ce47, 2026-11-17): memory leak fixes,
  SRP6a PIN pairing leak and a pairing fix for a dropped leading zero, NTP fixes, FairPlay v2 (macOS Music). Worth a
  native rebuild (jni/build_native.sh, NDK r29) after the live checks below, with a new LIBS.sha256 and pin.
- iOS 17, 18 and 26: mirroring, video URL (YouTube, Safari) and audio go through the same UxPlay protocol code the Z9X
  1.0.10 release ships, live-checked only on the Z9X so far. Each model needs the live list below with one device per
  iOS line the testers have. Code on screen (PIN) uses the SRP6a pairing path: test it explicitly.
- Decoders: the app uses MediaCodec by MIME type, so the vendor Codec2 decoders serve it. Stock media_codecs.xml of
  every MT9952/MT9681/MT9660 dump lists `c2.mtk.avc.decoder` and `c2.mtk.hevc.decoder` up to 4096x2304 with
  `low-latency`, `adaptive-playback` and `tunneled-playback`. The Play 6 (MT5889) is 1080p, so no 4K option there.
  Check on each unit with `dumpsys media.player` / logcat which codec instance the receiver opened (READ-ONLY list).
- Latency plan: (1) glass to glass: a phone stopwatch at 1/100 s mirrored, film the phone and the wall in one shot,
  10 samples, median. (2) logcat timestamps of the receiver (first frame, frame render) for jitter. Same Wi-Fi AP, 5 GHz,
  one meter, mirror quality 1080p then 4K where offered. Record per model in the test report.

## GPL-3.0 obligations (AirPlay)

The receiver is GPL-3.0 (UxPlay). Every image that ships it must offer the Corresponding Source:
- the source zip `lumen-os-<version>-airplay-source.zip` on every release that carries the app, made by
  `tools/devices/airplay_source.py OUT.zip` (reproducible, the app, jni with patches and build script, the shared
  modules, the recipe and the APK build script, no binaries),
- the written offer in the app (Screen casting, Licences: SOURCE.txt, GPL-3.0.txt, NOTICE.txt) and in the menu's
  Licences dialog (GmLicences.AIRPLAY_SOURCE).
- Release checklist item: attach the zip next to the image for every model release, keep it 3 years.

## Tests

- `sh apps/LumenAirPlay/test/run.sh` (rules, model table, manifest wiring), `python3 apps/LumenAirPlay/test/mutate.py`
- `python3 tools/devices/test/cast_models_test.py` (the rule, red cases, the source zip)
- lumen-menu, HB menu, X50Projector, RS30 image tests (snapshots carry the new tile and row)
- Renders (headless emulator, demo states): `~/lumen-build-tmp/casting/sheet.jpg`
