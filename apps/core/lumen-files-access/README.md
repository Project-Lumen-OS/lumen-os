# lumen-files-access

All files access and drawing over other apps for trusted apps, the same on every Lumen projector (Z9X, Z9X HB,
Play 6, X50 Ultra, X50 Ultra Max, RS30 Ultra Max, TITAN). Built once, run by each projector app.

## Why

Lumen Play (`com.xgimiru.player`) is in every image as its published APK with its own key (tools/build/prebuilt.py,
devices/_image/prebuilt.tsv) and updates itself from GitHub over that copy; older images let users install it
themselves. Either way Android gives it two things only through switches in the system settings:

- all files access (app op `MANAGE_EXTERNAL_STORAGE`): without it Android shows the app only the videos of a USB
  stick, not the `.srt` and `.ass` files next to them;
- drawing over other apps (app op `SYSTEM_ALERT_WINDOW`): without it Android 10 and later never let the app open a
  video sent from a phone (its DLNA renderer) or from a browser while the app is in the background, so casting
  would work only with Lumen Play open.

On Lumen the system gives both, and only to a build signed with our release key. It also grants the media read
permissions the app asks for (`READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES`,
`READ_MEDIA_VISUAL_USER_SELECTED`, only those in its manifest), so Lumen Play 2.0 does not ask for "Music and audio"
and "Photos and videos" at its first start while all files access is already there.

## Rule (FilesAccessPolicy, pure Java, host tested)

A package gets an app op of `FilesAccessPolicy.GRANTS` (`android:manage_external_storage`,
`android:system_alert_window`) = allowed when all of these hold:

- it is in the trusted table (`FilesAccessPolicy.TRUSTED`: package name and SHA-256 of its release certificate);
- it is signed by exactly that certificate (every current signer, no other one);
- it asks for the permission behind the op in its manifest (`android.permission.MANAGE_EXTERNAL_STORAGE`,
  `android.permission.SYSTEM_ALERT_WINDOW`);
- this install (its `lastUpdateTime`) was not handled yet for this op, and the op is not allowed already
  (`handledKey`: all files access keeps the package name as its key, so units from before keep their state).

So a user who turns a switch off himself keeps it off until the next update of the app. A package of the
same name signed with any other key never gets it. The media permissions follow the same rule
(`shouldGrantRuntime`, its own handled key per permission) and are never granted over a fixed state (the user's
"don't ask again", a policy or the system).

## When (FilesAccess)

- `PACKAGE_ADDED` and `PACKAGE_REPLACED` of a trusted package: a receiver registered in the running projector
  app process (Android skips manifest receivers for these broadcasts in apps that target O or later: "Background
  execution not allowed"). The projector apps are persistent, so the receiver lives as long as the system runs.
- Once per start of the projector app process: `FilesAccessStart`, a not exported provider that Android creates
  before anything else runs in the process (also after the process came back in the same start).

## How

`AppOpsManager.setUidMode(String, int, int)` (system API, needs `MANAGE_APP_OPS_MODES`), with
`setMode(String, int, String, int)` as the second way. The media permissions: `PackageManager.grantRuntimePermission`
(system API, needs `GRANT_RUNTIME_PERMISSIONS`), read back with `checkPermission`. Both are called directly: the apps compile against the
framework header jar `apps/sdk/framework-full-headers.jar`, which has them (no reflection, as
`tools/devices/check_aidl_hal.py` rule 1 demands). The projector apps are platform signed (Z9X, Play 6) or `android.uid.system` (Z9X HB, X50
family), so the permission is theirs. Nothing is logged about other apps beyond the package name and the result.

Lumen Play keeps its own prompts for both switches as the way on other systems.

## Wiring

Each projector recipe (`devices/<codename>/app/*Projector.layers`) names `module lumen-files-access`, and each
projector manifest (`apps/LumenProjector/core`, `apps/HbProjector`, `apps/LumenProjector/host/activity` (X50 family), `apps/Play6Projector`)
declares the permissions `android.permission.MANAGE_APP_OPS_MODES` and `android.permission.GRANT_RUNTIME_PERMISSIONS` and the provider
`org.lumen.filesaccess.FilesAccessStart`. `test/run.sh` checks all of it.

## Tests

`sh apps/core/lumen-files-access/test/run.sh`: the policy table (trusted package, wrong key, extra signer, no
permission request, already allowed, already handled install, unknown package, certificate digest format) and the
wiring of every projector recipe and manifest.
