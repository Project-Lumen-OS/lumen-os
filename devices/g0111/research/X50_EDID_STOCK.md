# X50 family: the stock HDMI mode (EDID) rows, the files behind them and the Lumen fix

Branch `x50-edid-stock` (from volume-z9x-play6 742bca0b). Stock: X50 Ultra G0111 v1.2.64 dump, Ultra Max G0100
(same firmware, its own xgimiconfig folder), Titan H0062 / H0063 (folders of the same firmware), RS30 G0155 dump.
Live: one X50 Ultra on Lumen x50-1.0.10-beta7, a Homatics Box R 4K Plus on HDMI 2, an LG soundbar on HDMI 1 (eARC),
2026-11-15 14:22-14:31 MSK. PROVEN = seen in code, config or live. NOT REPRODUCED = reported, not seen.

## 1. Cause (PROVEN)

Lumen up to x50-1.0.10-beta7 sent TV 40 setEdidVersion(1) for "Compatible (HDMI 1.4)" and (2) for "Enhanced (HDMI 2.0)".
Stock sends 0 and 1: newsettings `SignalManager.setHdmiVersion` passes SignalEnum HDMI1_4 = 1 / HDMI2_0 = 2 to
`GmDeviceManager.setEDIDVersion`, and gmuiapi `GmTvManager.setEDIDVersion` does `if (SDK_INT > 26) version--` before
the vendor call, and `getEDIDVersion` returns raw + 1. The same chain and decrement are in the RS30 dump. The HB had the
same bug and was fixed on 2026-11-04 (`devices/goat_elegant/research/EDID_live.md`), the X50 port kept the old values.

| Lumen beta 7 row | raw sent | vendor file on HDMI 2 (live log) | what the box got |
|---|---|---|---|
| Compatible (HDMI 1.4) | 1 | `edid20_non_hrr_2.bin` (raw 1 -> index 4, support_hfr + game mode 3.0) | stock "Enhanced": 4K60, HDR10, HDR10+, Dolby Vision, no HLG |
| Enhanced (HDMI 2.0) | 2 | `edid14_2.bin` (raw 2 outside game mode -> index 2 = edid14) | stock "Compatible": up to 4K30 / 1080p60, no HDR static block (box hdr_cap all 0, HDR types [DV] only), no ALLM |

So the two rows were swapped, and "Enhanced" also lost Enhanced in game mode (raw 2 is the vendor's game request).
The tester's "720p" was NOT REPRODUCED: the box keeps its own chosen 1080p60 on edid14, but on edid14 it cannot offer 4K60
or HDR10, which matches "no HDR".

Live vendor lines (GM_DISP_TVCTRL / GM_DISP_HDMIIN, read with `logcat -d`):
- raw 2: `_setEdidVersion: in enEdidVer:2, src:2, bSave:1`, `m_u8GameModeVersion:3 m_bSupportHightRateFrame:1
  m_eGameModeState:1 m_eGameOption:0 m_enVrrEnableState:0 m_bAmdFreeSync:0, m_kstType:11`, `setEdid 4 -> 2`,
  `edidPath = /mnt/vendor/xgimiconfig/G0111/tv/edid14_2.bin`
- raw 0: `set edid[2]:0 for not game mode`, `setEdid 2 -> 0`, `edidPath = .../edid14_2.bin`
- raw 1 (the stock default, CustomerEnvTbl `edid_mode` 1:1:1:1): the box EDID equals `edid20_non_hrr_2.bin` except
  the audio block (the kernel rewrites it from the soundbar's SADs on a non-ARC input, 14 bytes)
- the vendor HAL (u:r:aidl_gmpf_xgimi) reads the file under an SELinux denial that only the permissive Lumen X50 image
  lets through (`avc: denied { read open } ... mnt_xgimiconfig_file permissive=1`): an enforcing X50 image needs that
  rule or no EDID loads (`read edid file failed`)

## 2. Stock menu (PROVEN)

newsettings settings tree `settings_tree_product_dianapro` (k0.json) and `_dianamax` (rF.json), page
`signal_setting_hdmi_version` "HDMI模式": two rows, `signal_source_setting_hdmi_version1_4` "兼容模式" (Compatible) and
`..._2_0` "增强模式" (Enhanced), hint `signal_enhance_tips` "HDMI 2.0 supports 4K 60 Hz with the full colour range". No
2.1 row: SignalEnum HDMI2_0 is 4 (2.1) only when feature `edid_2_version_val` is "3", and G0111, G0100, H0062, H0063,
G0155 (and the HB G0130, Z9X G0082) all have 1. The call has no port: the vendor stores and loads it for the input on
screen. Shown value: getHdmiVersion = Enhanced only for raw 1, Compatible for any other raw value. The only other
writer in the dump is the factory app (minitvfactory GmTvApi, value - 1). No stock writer sends raw 2.

## 3. Files (PROVEN, xgimiconfig/<model>/tv/, 256 bytes unless noted)

The vendor builds `<root>/xgimiconfig/<model>/tv/<name>_<port>.bin` itself (libxgimi Device_HdmiInput constructor,
name table at 0x2eb7640), the `[HDMI_EDID_n]` lines of edid_cfg.ini are not used for it. G0111 owns edid14_1..3 and
edid20_non_hrr_1..3, the rest are links to G0000. G0100 equals G0111 except the Dolby Vision block of edid14 and
edid20_non_hrr (its own primaries and target luminance). Decoded with edid-decode (2022 build):

| index | file | blocks | advertises |
|---|---|---|---|
| 0, 2 | edid14 | 2 | DTD 1080p60, 4K24/25/30, TMDS 340, no HF-VSDB, no HDR static block, Dolby Vision LL, LPCM 2ch, AC-3 6, DTS 6, DTS-HD 8, DD+ 8, TrueHD 8 |
| 1 | edid20 | 2 | DTD 4K60 + 1080p240, VIC 63 1080p120, TMDS 600, FRL listed, 4:2:0 map 4K50/60, SDR + PQ + HLG, HDR10+, ALLM, no DV, DD+ 2ch |
| 3 | edid21 | 2 | as edid20 + VRR 40-240 Hz |
| 4 | edid20_non_hrr | 2 | DTD 4K60 + 1080p60, no 120 Hz, TMDS 600, 4:2:0 map, SDR + PQ (no HLG), HDR10+, Dolby Vision, ALLM |
| 5 | edid20_120hz | 2 | as edid20 with DTD 1080p120 instead of 240 |
| 6 | edid21_120hz | 2 | as edid21 with VRR 40-120 |
| 7 | edid21_freesync | 3 (384) | edid21 + AMD FreeSync block 40-240 Hz |
| 8 | edid21_freesync_120hz | 3 (384) | edid21_120hz + AMD FreeSync block |

`edid21_4.bin` (port 4, unused on the 3-input X50) also lists 4K120. sha256 of every file: `x50-edid-stock` kit
(`~/lumen-build-tmp/x50-edid-stock/edid_files.tsv`).

## 4. When the vendor writes which file (PROVEN, libxgimi v1.2.64 Msrv_Tv_Control::_setEdidVersion 0x19b8a78)

Request r for source s (1..3), bSave = TV 40 only:
- r not 2 or 9: index r, and r = 1 becomes 4 with support_hfr and game mode version 3 (`set edid[s]:i for not game mode`).
- r = 2 or 9 (the vendor's own game request, bSave 0): with the game mode off on s, the user's stored value (1 -> 4).
  With the game mode on: VRR on -> 3 (edid21), 7 with FreeSync, else game speed with high frame rate (option 1 or 2)
  -> 1 (edid20), else the user's value: 4 for Enhanced, 0 for Compatible. When the keystone type is 10 (this unit: 11)
  the same choice uses 6 / 8 / 5 (the 120 Hz files), unless top speed with flag 0x31b.
- Same index as loaded: nothing written. Otherwise the file is written and the source reads it again (live: signal
  status 3 -> 1 at once, back to 3 after 6 s).

Callers of _setEdidVersion: init (every source at boot, the stored value or the game request), switchInputSource (the
stored value of the new input), setEdidVersion (TV 40, the menu), setGameModeState (DisplayManager 146),
setGameModeOption (76), setVRRModeEnable (TV 145), setAmdFreeSyncState. So the four game files are never a menu
choice: they follow the game mode, its speed, VRR and FreeSync.

## 5. Lumen after the fix

- `apps/core/lumen-hdmi` `EdidModes`: the stock rule once (rows Compatible / Enhanced, raw = SignalEnum - 1, shown
  Enhanced only for its own value, a stored value other than the row's own one is rewritten, so a beta 7 value 2 is
  repaired when the user picks a row). Model data = edid_2_version_val (1 on every model).
- X50 family (X50Projector, HdmiHal.EDID): TV 40 gets 0 / 1 only (Gmpf guard), both menus (shared rows X50HdmiRows,
  old MenuActivity row) and the relink (X50RelinkHost) use it.
- Game files: reached as on stock through the calls Lumen already sends: game mode (146 / 147), game speed with high
  frame rate (76), VRR in the game menu (145). The AMD FreeSync item of the stock game menu is not ported (X50GameTest
  pins that no FreeSync call exists), so edid21_freesync / _freesync_120hz are not reachable on Lumen.
- A unit left on raw 2 by beta 7 shows Compatible (true: it gets edid14) until the user picks a row again.
