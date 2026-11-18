# Remote table

`remotes.tsv` lists every XGIMI remote and remote-like input device found in the stock dumps on xgimi-builder
(`~/devices/<model>/...`, read only). The columns are described in the file header. `tools/remotes/gen_catalog.py`
turns it into `apps/core/lumen-remote/src/org/lumen/remote/RemoteCatalog.java`; `tools/remotes/check_images.py`
checks that every image installs a Lumen key layout for each row with `lumen_kl=1`.

"Remote buttons" (apps/core/lumen-remote README) reads three more tables here, also turned into Java by
`gen_catalog.py` (`keys/KeyTable.java`): `keys.tsv` (the shortcut keys of each remote, what stock does with them and
whether a user may give them an app or action), `fixed_keys.tsv` (keys never changed: arrows, OK, Home, Back, the
gear, the microphone, the focus key, power, volume) and `key_actions.tsv` (projector actions a key can do: the stock
custom-key functions Lumen has, each with its icon). Stock evidence: `xgimi_action_config.xml` (`custom_key_v2`),
XRMService `XrmDoAction` / `XrmShortcutEvent` (2116 screen saver key, 2117 Shortcut Key, 2118 to 2126 app keys) and
SystemUI_release_838 / 848 `RemoteCustomKeyUI`, `res/xk.json` (bindable keys 2320 to 2323) and `res/_i.json`
(functions) of G0111, G0155, H0062.

Key names (`keys.tsv` `name`) say what is printed on the key. App keys: the logo the stock opens for that key,
`XrmDoAction` by `persist.xgimi.rc.version` (the remote's own version): default (B992C) VIDEO0 Youku, VIDEO1 Tencent
Video, VIDEO2 iQIYI, VIDEO3 Mango TV, VIDEO4 bilibili, B992B VIDEO2 bilibili and VIDEO3 Mango TV, B995C VIDEO3
bilibili (`sys_quick_app_config.json` of every dump: the same five apps, no Netflix or YouTube key on any XGIMI
remote here). The owner's Z9X remote sends 230 (a Chinese "TV" logo) and 134 (bilibili), the default map. Function
keys: `services.jar` `XgimiWindowManager` names 2116 `remote_screen_saver_button` and 2117 `remote_diy_button`,
newsettings `bluetooth_rc_custom_key_text` "Shortcut Key", `bluetooth_rc_custom_key1_text` to `4` "Shortcut Keys
1" to "4" (SystemUI 848 `res/raw/shortcut_professional_remote_config.json`: remote B975D_CY03 keys 2320 to 2323,
B870C / B872C keys 2320 and 2321).

`focus_guide.tsv` picks the remote picture and text of the focus page (menu.json hint `focus.guide`): stock newsettings
`RemoteFocusCell` chooses them by `persist.xgimi.rc.version`, the remote's own firmware string that the stock Bluetooth
service reads over GATT. Lumen never reads that string, so the table names the remote each model ships with (from its
stock getprop) and `apps/core/lumen-menu/test/FocusGuideTest.java` holds every profile's token to the stock rule. Every
remote of our models has the focus key on the front (B970C X50 family, B870C RS30): only the B990C remote has it on the
side.

## Evidence

- Names: the stock Bluetooth stack of the X50 / RS30 family (btservices APEX, `Bluetooth.apk`,
  `com.android.bluetooth.btservice.BluRemConService.isXgimiRc`: `String.contains` of `XGIMI RC`, `BLuetooth 4.0 RC`,
  `Aladdin RC`, `MOVIN RC`, `Dangbei`, the Chinese name), the RS30 bootwizard `PairRemoteReceiver` and
  `com.xgimi.bluetoothservice` (`XgimiRcInfo`: Battery Service and the OTA UUIDs).
- Key layouts: `vendor/usr/keylayout/Vendor_*.kl` of G0082 (Z9X), G0111 (X50), G0130 / G0148 (HB family), G0155
  (RS30), H0062 (Titan) and G0058 (Play 6); `stock` lists the models that ship each file.
- Voice: `vendor/etc/remote.txt` (HID audio report of the XGIMI remotes) on G0082, G0111, G0148 (same file), G0130
  (its own).
- Live: the X50 at Slava's (Lumen x50-1.0.10-beta4): `XGIMI RC` bonded, input device 000d:3840 with the Lumen
  `/system/product/usr/keylayout/Vendor_000d_Product_3840.kl`.

## IR (what a foreign remote's IR keys can and cannot do)

The IR receiver decodes in the vendor partition (`tvconfig/config/ir_config.ini`, `mufirkeymap`), which Lumen never
writes. Its Android key map (`[Android_TV]`, header 0x8877) is the same file on G0082, G0111 and G0155 and the same
map on Play 6 (`cusdata`), so the IR keys of any XGIMI remote (power, and every key while Bluetooth is not bonded)
reach Android as the same scancodes of `Vendor_3697_Product_0001.kl` on these models. The HB family dumps
(G0130 / G0148) carry no `ir_config.ini` in the extracted tree: not verified there. A remote with another IR header
(not 0x8877) would only work through Bluetooth.
