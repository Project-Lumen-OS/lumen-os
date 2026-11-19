# lumen-remote

The remote pairing of every Lumen projector: Z9X (horse_popular), Z9X HB (goat_elegant), Play 6 (dragon_healthy),
X50 Ultra (g0111), X50 Ultra Max (g0100), RS30 (g0155). One source module, copied into each projector app by
`tools/devices/assemble_app.py` (recipe line `module    lumen-remote` in `devices/<dev>/app/*.layers`).

## Pieces

| file | role |
|---|---|
| `devices/_remotes/remotes.tsv` | every XGIMI remote and remote-like input device known from the stock dumps (data) |
| `RemoteCatalog.java` | generated from the table by `tools/remotes/gen_catalog.py` (never edit by hand; `--check` is a test) |
| `RemoteMatch` | which advertiser is a remote and may be bonded now (pure rules over the table) |
| `RemotePairing` | LE scan windows, bond, re-pair of a reset remote, HID host connect, reconnect, battery, permissions |
| `RemotePrompt`, `RemoteArt` | the remote-lost prompt and the remote picture (texts and drawables `lumen_remote_*` in `res/`) |
| `RemoteState` | snapshot for the Setup bridge (`remote_state`) and the menus |
| `RemoteHost` | the only per-app part: setup event, boot reason rule, the app's own prompt blockers, standby |

Hosts: `apps/common/projector/.../remote/ProjectorRemoteHost.java` (Z9X, HB, Play 6) and
`apps/X50Projector/.../X50RemoteHost.java` (X50 family, RS30). Bluetooth itself is AOSP on every image (the stock
vendor HAL `IBluetoothHci/default` on X50, RS30 and Titan, same rc), so there is no per-model Bluetooth code.

## Remote buttons (package `org.lumen.remote.keys`)

"Rebind a button": the user gives a remote or keyboard key an app or a projector action, saved on the projector
(prefs `lumen_key_binds`, one value per Android key code, so the same key of any remote of any model does the same).
Settings: "Remote buttons" ("Кнопки пульта") in the remote section next to the pairing row (Z9X, HB, Play 6
SettingsActivity, X50 family `X50MenuPages` remote page).

- Screen (`KeyBindActivity`, drawn by `KeyBindView`, logic in `KeyScreen`): "Assign a button", then "Press a button on
  the remote". A fixed key says "This button cannot be reassigned" and the screen keeps waiting. A key the user may
  change opens its choice: "As before", the projector actions, then the installed apps with a TV or normal launcher
  entry (icons, by name in the current language). Below "Assign a button": every key with its app or action (OK =
  change it) and "Reset all". Rows follow the stock Android TV lists: an icon at the start of each row (the app's
  icon, each action its own icon on a tile: input, HDMI port, keystone, focus, the gear for quick settings), a radio
  button at the end of every entry of the choice (the chosen one filled), a chevron on a key's row, the row under the
  last one fading out when there are more.
- Key names say what is printed on the key (`keys.tsv` column `name`, evidence in its `note`): the app keys by their
  logo (Youku, Tencent Video, iQIYI, Mango TV, bilibili: XRMService `XrmDoAction` map of the stock default remote,
  the iQIYI and bilibili texts are the Z9X ones), the screensaver key and the Shortcut button (keys 1 to 4 on the pro
  remotes) by their stock names, keyboard keys by their own label ("F5 key"). Some remote versions print another logo
  on a key (B992B, B995C, see `note`). The remote does not tell Lumen its version, so the name follows the stock default.
- Open to more actions: a `key_actions.tsv` row adds one (stable `id`, saved as `do:<id>`, text, icon, no code). An
  action presses a key or sends an intent to the projector app itself (`KeyActions.run`: its activity if one answers,
  else a broadcast), and a `feature` row is offered only where the device profile (`devices/<c>/menu.json`, in the
  app as `assets/lumen_menu/menu.json`, read by `KeyFeatures`) names that feature. The 3D action (`do:3d`, "3D", icon
  `lumen_ic_3d`, stock function 1011) sends `org.lumen.projector.action.QUICK_3D` and needs `picture.3d`. Merge point
  with branch quick-3d (the small 3D window: Off, Side by side, Top and bottom, 3D to 2D): it must declare that intent
  action in the projector apps and add `picture.3d` to the menu.json of every model whose stock has 3D, or change
  this row to its names. Until then no profile has it and 3D is offered nowhere (`check_keys.py` refuses a profile
  with the feature while no app declares the intent).
- Icons: Material Symbols of the Lumen icon set `apps/core/lumen-icons` (`res/drawable/lumen_ic_*` there, rows in
  `tools/icons/icons.tsv`, `KeyIcons` names them), so every recipe that names module lumen-remote names lumen-icons too.
- Which keys (data, `tools/remotes/gen_catalog.py` writes `keys/KeyTable.java`): `devices/_remotes/keys.tsv` (the
  shortcut keys of every remote of `remotes.tsv` and the keyboard keys F1 to F12 and launch keys, never letters or
  digits), `fixed_keys.tsv` (arrows, OK, Home, Back, the gear, the microphone, the focus key, power and volume: never
  changed, so they always work), `key_actions.tsv` (input, HDMI 1 / 2, keystone, focus, quick settings: an action
  presses that key, so each model runs its own route for it; HDMI rows only with that many HDMI inputs).
- Handler (`KeyBind`, one for all models): remote keys come from each projector app's KeyReceiver
  (`KeyBind.onGlobalKey` after its guards), keyboard keys from `KeyFilterService` (an accessibility key filter, on
  only while a keyboard key has something or the screen waits for a key). A short press runs the choice at the key's
  UP, a held remote key opens its choice. A key with nothing keeps its normal action (the stock shortcut key 184 the
  app list, the custom keys 1 to 4 ask which app). An app that is gone loses the key, which works as before again.
  While the screen is open keys run nothing, while it waits for a key it gets every key except power.
- Module hook (`KeyHook`): a module that must see keys while something of its own is on screen (lumen-signal
  `CardHold`: holding OK on a developer card) sets one hook. While it is set KeyFilterService runs and asks the hook
  first, a key it does not take goes on unchanged; cleared, the filter runs only for the keyboard binds again.
- Stock: X50, RS30 and Titan have the same feature (SystemUI `RemoteCustomKeyUI`, `shortcut_function_bind_list`,
  custom keys 1 to 4 of the B975D remote, 1 and 2 of the RS30 remote; function list `res/_i.json`). Lumen offers at
  least the same keys and, of the stock functions, the ones Lumen has (keystone and lens, HDMI, brightness and
  picture through quick settings), plus any app, on every model.

## Rules (RemoteMatch)

- A remote is an advertised name that contains a name of a `ble_remote` row, ignoring case (stock RcPair:
  `String.contains`; Lumen OS 1.0 Z9X: prefix `XGIMI RC`; both stay accepted).
- Unattended windows (first-run setup, no remote bonded, remote lost, the recover window after boot or screen on):
  connectable, not this projector's own name, and either the stock RcPair pairing advert (the HID service plus the
  manufacturer data of company 0x000D starting `38 38 02 00 00`, flags ignored, a table name or no name at all) or a
  table remote in pairing mode = LE Limited or General Discoverable, or the HID service while no remote is bonded
  and either no flags (X50 rule) or the first-run setup (stock checks no flags). Other XGIMI products, the music
  controller X_Remote (mark `39 38`), a sleeping remote after the setup and strangers are never bonded.
- Every advert with an XGIMI or table name, the 0x000D company data or the HID service gets one log line per address
  and scan (`adb logcat -s LumenRemote`, `advert <addr> name= rssi= flags= hid= connectable= xgimi=<hex> bond= -> verdict`):
  a remote that does not pair on a new model shows there which rule refused it.
- The user-started window (Settings "Pair remote", the X50 menu row, 120 s): also an XGIMI name with RC / REMOTE /
  HID / Limited, and any Bluetooth LE HID remote or keyboard in pairing mode.
- Pairing keys: Home + Back held until the light blinks (every `ble_remote` row; the table's `combo` column).
- Any XGIMI remote pairs with any projector: the rules depend on the table only, never on the model.

## Trial (DSU, empty data)

Nothing comes from stock: the first boot runs the setup window, which bonds a remote held in pairing mode.
SCAN and CONNECT come from the image's default-permissions file at the first boot of the user, and the module
grants them to itself (GRANT_RUNTIME_PERMISSIONS, platform key) when they are missing; BLUETOOTH_PRIVILEGED is a
signature grant (or the HB priv-app allowlist). `tools/remotes/check_images.py` proves this per image recipe,
`test/` scenario S01, S02, S17, S21 and S25 prove it on the fake Bluetooth world.

## Tests

`sh test/run.sh` (JDK 17+, python3; builder-llm): the table check, `RemoteMatchTest`, the shared engine on the fake
Bluetooth world compared with the traces of the two implementations it replaced (`test/parity`: the goldens were
recorded from the old code at 389dc716 by `parity/record_old.sh`, and `parity.py` lists every scenario where the
shared engine differs on purpose), the resource check and the timing pin. `python3 test/mutate.py`: targeted
mutants, every one must turn `run.sh` red.

Remote buttons: `sh test/keys/run.sh [PNG_DIR]` (the tables against the layouts, the global keys of every framework
overlay, the texts of every locale, the wiring of each projector app, `KeysTest` on host stubs with the screens drawn
in Russian and English, their icons, radio buttons and names checked by pixels, the module's vector icons drawn by
`VectorIcon` from the XML) and `python3 test/keys/mutate.py` (targeted mutants, every one must turn it red).
