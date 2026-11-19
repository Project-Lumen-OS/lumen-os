# lumen-files-access

All files access for trusted user apps, the same on every Lumen projector (Z9X, Z9X HB, Play 6, X50 Ultra,
X50 Ultra Max, RS30 Ultra Max, TITAN). Built once, run by each projector app.

## Why

Lumen Play (`com.xgimiru.player`) is not part of the image: it installs and updates itself from GitHub as a
normal user app. Android shows such an app only the videos of a USB stick, not the `.srt` and `.ass` files next
to them, unless the app has all files access (app op `MANAGE_EXTERNAL_STORAGE`). Users had to find that switch in
the system settings. On Lumen the system gives it, and only to a build signed with our release key.

## Rule (FilesAccessPolicy, pure Java, host tested)

A package gets the app op `android:manage_external_storage` = allowed when all of these hold:

- it is in the trusted table (`FilesAccessPolicy.TRUSTED`: package name and SHA-256 of its release certificate);
- it is signed by exactly that certificate (every current signer, no other one);
- it asks for `android.permission.MANAGE_EXTERNAL_STORAGE` in its manifest;
- this install (its `lastUpdateTime`) was not handled yet, and the app op is not allowed already.

So a user who turns the switch off himself keeps it off until the next update of the app. A package of the
same name signed with any other key never gets it.

## When (FilesAccess)

- `PACKAGE_ADDED` and `PACKAGE_REPLACED` of a trusted package: a receiver registered in the running projector
  app process (Android skips manifest receivers for these broadcasts in apps that target O or later: "Background
  execution not allowed"). The projector apps are persistent, so the receiver lives as long as the system runs.
- Once per start of the projector app process: `FilesAccessStart`, a not exported provider that Android creates
  before anything else runs in the process (also after the process came back in the same start).

## How

`AppOpsManager.setUidMode(String, int, int)` (system API, needs `MANAGE_APP_OPS_MODES`), with
`setMode(String, int, String, int)` as the second way. Both are called directly: the apps compile against the
framework header jar `apps/sdk/framework-full-headers.jar`, which has them (no reflection, as
`tools/devices/check_aidl_hal.py` rule 1 demands). The projector apps are platform signed (Z9X, Play 6) or `android.uid.system` (Z9X HB, X50
family), so the permission is theirs. Nothing is logged about other apps beyond the package name and the result.

Lumen Play keeps its own prompt for the switch as the way on other systems.

## Wiring

Each projector recipe (`devices/<codename>/app/*Projector.layers`) names `module lumen-files-access`, and each
projector manifest (`apps/ProjectorBase`, `apps/HbProjector`, `apps/X50Projector`, `apps/Play6Projector`)
declares the permission `android.permission.MANAGE_APP_OPS_MODES` and the provider
`org.lumen.filesaccess.FilesAccessStart`. `test/run.sh` checks all of it.

## Tests

`sh apps/core/lumen-files-access/test/run.sh`: the policy table (trusted package, wrong key, extra signer, no
permission request, already allowed, already handled install, unknown package, certificate digest format) and the
wiring of every projector recipe and manifest.
