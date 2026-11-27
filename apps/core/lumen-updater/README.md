# lumen-updater: the one updater of every Lumen projector

`org.lumen.updater` ("Lumen OS update", the TvSettings About entry through `android.settings.SYSTEM_UPDATE_SETTINGS`)
is one app on every model. This module holds all of it: the signed channel manifests (Channels, Trust, Checker,
CheckJob), the download and its checks (UpdateService, PackageDownload over the shared apps/core/lumen-download, Http), the checks before an install (Preflight), the two ways an
update is installed (InstallWay: ViaUpdateEngine, ViaRecovery), the update screen (UpdaterActivity, Ui; its QR codes come from apps/core/lumen-qr), the
result after the restart (BootReceiver, Outcome), the one-time notice and the statistics adapter (Notice, Stats over
lumen-stats), the strings in 18 languages.

A model is data. Its recipe (`devices/<codename>/app/*.layers`) adds the module and one small layer with two files:

- `src/org/lumen/updater/Target.java`: one `UpdateTarget` (`Target.SPEC`), values only;
- `AndroidManifest.xml`: the package, the uid and the permissions of that model.

No code of the module names a model (`test/run.sh` fails on a model name). A new model is a new `Target` (or the
neutral one with its image properties) and a recipe, never a copy of a class.

## The interface

`UpdateTarget` (built by `UpdateTarget.Builder`, every value must be named):

| value | meaning |
|---|---|
| `props` | property namespace of the version facts and the manifest URL: `ro.z9x` or `ro.lumen` |
| `device` / `deviceFrom` | wire id a manifest must carry, fixed or read from an image property ("" = no update) |
| `versions` | `VersionScheme.LEGACY` (x*10000+y*100+z) or `O1` (x*1000000+y*10000+z*100+n, betas and rcs below the final) |
| `channels` | `ChannelRule.FIXED` (image URL else a default, one fixed beta file beside one stable file) or `PAIR` (the image's own URL, update-&lt;device&gt;-nogms / -test pair beside it) |
| `defaultManifest`, `beta`, `fallbackFile` | the FIXED rule's data |
| `guide` | install guide folder; null = the image's `ro.lumen.ota.guide` or `docs/install/<device>` |
| `dir` | `OtaDir.APP_DATA` (device-protected, update_engine reads the metadata by path) or `CACHE` (/cache/lumen_ota for the recovery) |
| `install` | `InstallKind.UPDATE_ENGINE` (the other slot) or `RECOVERY` (whole package after a restart) |
| `checks` / `checksFrom` | `InstallChecks`: the ordered checks before an install, fixed or named by an image property |
| `dsuHandover` | in a DSU trial the update screen opens the in-trial installer and no update work runs |
| `hideWithoutDevice` | without a wire id the update screen and its TvSettings row are hidden |
| `compressedSnapshots` | the wire ids whose payloads use compressed snapshots (ota.conf `vabc=yes`); STORAGE wants the vendor's `ro.virtual_ab.compression.enabled` to match ("compression on" / "compression off" otherwise) |

`InstallChecks` (first refusal wins):

| name (image property value) | checks | published by |
|---|---|---|
| `Z9X_GATE` (`z9x_ota`) | VBMETA, PENDING, THERMAL, ENGINE | tools/ota/image/z9x_ota.sh |
| `LUMEN_GATE` (`lumen_ota`) | VBMETA, STORAGE, GATE_PROBE, SLOT_STATE, THERMAL, ENGINE | tools/devices/ota/image/lumen_ota.sh |
| `RECOVERY` | RECOVERY_CERT, POWER, THERMAL | lumen_recovery.sh (Play 6) |
| any other value | always refused (blk_hal "boot gate ...") | |

## The models

| | Z9X G0082 | HB G0130/G0148 | Play 6 G0058 | X50 family G0111/G0100/H0062/H0063 |
|---|---|---|---|---|
| layer | apps/Z9xUpdater | apps/LumenUpdater (neutral) | apps/Play6Updater | apps/LumenUpdater (neutral) |
| manifest file | apps/LumenUpdater (same file, recipe `file`) | apps/LumenUpdater | apps/Play6Updater (system uid, RECOVERY) | apps/LumenUpdater |
| props | ro.z9x | ro.lumen | ro.lumen | ro.lumen |
| wire id | z9x | ro.lumen.ota.device = hb | play6 | ro.lumen.ota.device = x50 (G0111), x50max (G0100), the registry installer_id of a later model |
| versions | LEGACY | O1 | LEGACY | O1 (ro.lumen.version_code) |
| channel | FIXED: z9x/update-public-nogms.json, beta z9x/update-public-beta.json | PAIR: z9x-hb/update-hb-nogms.json + update-hb-test.json | FIXED: play6/update-play6.json, no beta | PAIR: x50/update-x50-test.json (beta, what beta images read) + update-x50-nogms.json; never the installer's update-x50-beta.json |
| install | UPDATE_ENGINE | UPDATE_ENGINE | RECOVERY | UPDATE_ENGINE |
| checks | Z9X_GATE | ro.lumen.ota.boot_gate, default LUMEN_GATE | RECOVERY | LUMEN_GATE (the image gets tools/devices/ota/image/lumen_ota.sh like the HB) |
| DSU hand-over | no | yes | no | yes |
| OTA certificate | ~/.lumen-keys ota (otacerts.zip) | goat_elegant ota | Play 6 ota | devices/<codename>/certs/ota.x509.pem (g0111: CN Lumen OS g0111 OTA, RSA-4096; g0100 its own) |
| signing | Z9X platform key | HB platform key | Play 6 platform key | the model's platform key |
| snapshots (STORAGE) | not checked (Z9X_GATE) | legacy (vendor compression off) | not checked | compressed, lz4 (vendor compression on), wire ids x50 x50max |

## Which channel, which update

- The image URL names the channel pair (which tag, which wire id), not the side of it. Until the user picks one with
  "Beta updates", the build type does (`Ota.betaOn`, `VersionScheme.prerelease`): a build whose version ends in
  -betaN or -rcN reads the stable and the beta manifest and offers the higher version, a final build reads only the
  stable one. So a Z9X or HB beta follows the newer betas although its image names the stable file, and an X50 final
  stays on stable although its image names the beta file. The user's choice wins either way and is kept.
- `Preflight.applicable` checks the identity (schema, wire id), then the version: a manifest whose `version_code` is
  not above the installed one is "Lumen OS is up to date", never a refusal (a beta build reading the stable channel
  of an older release sees no firmware or channel text). Only a newer release is checked for its channel, the
  firmware it supports (`blk_vendor` + `blk_vendor_body`: the supported firmware versions, the projector's own and
  what to do) and the minimum version.

## Always the newest build (Newest)

`Newest` is the one rule every path follows: `Checker`, `UpdateService`, `CheckJob`, `BootReceiver` and the update
screen only call it.

- Every check reads every channel manifest fresh (`Http.fetchFresh`): no-cache request headers, no conditional request,
  a query of its own (`lumen_nc`), the GitHub redirect resolved anew. A json whose .sig does not match yet (an upload in
  progress) is read again after a pause. The highest verified `version_code` of the channels read is the candidate.
- A saved offer (found, downloading, paused, downloaded) is replaced by any newer build and its files are deleted. A
  running download keeps its state while the check reads; when its package is replaced it stops, its files go and the
  newest one is downloaded (`downloadNewest`, the DOWNLOADED mark is set under the same lock the check uses).
- An older manifest than the saved offer (a cache, a mirror, a USB stick with an older build) and no answer at all
  (offline, a 404 while a channel file is replaced) leave the newer offer and its state as they are.
- A saved offer that is not newer than the running build, or of a channel the check no longer reads, is dropped with
  its files: at boot, at every check, before a download and before an install. The same build is never offered again.
- Checks run at every opening of the update screen (at most once a minute, monotonic clock), every 6 hours (the job
  of the old one day period is rescheduled), 10 minutes after boot, before a paused download is resumed, as a download starts,
  every 20 minutes of it and right after a network gap in it (`Recheck`), and right before an install
  (`beforeInstall`: a newer build is downloaded first, so Install never installs anything but the newest verified
  build; without network the saved verified offer is installed).
- Publishing: `tools/release/channel_guard.py` refuses a channel file below its live `version_code`.
- Host test `test/org/lumen/updater/NewestTest.java`: a fake GitHub over HTTPS (channel file 302 to an asset with an
  ETag) and the real check, download switch and install recheck through publish A, B during A's download, C after the
  download, offline and online, a stale cached copy then the fresh one, a json ahead of its .sig, a 404, a build at the
  Install press, the installed build, a lower stable channel, beta off and a beta15 download left over after the unit was updated another way.

## X50 family (active since 2026-11-14 on the X50 Ultra and Ultra Max)

The X50 updater is the neutral recipe (devices/g0111/app/LumenUpdater.layers and devices/g0100/app, byte copies) and
devices/<codename>/ota.conf (tools/devices/ota, like the HB): wire id x50 (G0111) or x50max (G0100), super group
group_oem 4613734400, lz4 snapshots, manifests on the tag x50, ab_ota_partitions = optee owl satf xgimiconfig (what the
in-trial install itself writes into the Lumen slot). The public image (build.conf derive=ota:pub, steps.tsv flag ota)
carries the updater in /system/app/LumenUpdater, otacerts.zip with the model's OTA certificates, the neutral boot gate
(lumen_ota.rc / lumen_ota.sh / update_verifier.rc, devices/g0111/image/ota_gate.conf: hal=aidl) in place of the base's
Z9X gate, and reads update-<wire id>-test.json (beta images; never the installer's update-<wire id>-beta.json). An
update goes through update_engine into the other slot, which on an installed X50 is the stock slot a: the first
update replaces the stock system there, the XGIMI USB stick is the way back. LumenDsuInstall's X50 and X50MAX profiles
(updatesToStockSlot=true) give the stock vbmeta flags 3 after the switch so that first update boots; the TITAN Noir
models have no updater yet and keep refusing an image with an update channel.

The X50 vendor declares compressed snapshots (ro.virtual_ab.compression.enabled, userspace snapshots, io_uring, xor)
and its boot ramdisk starts snapuserd, so its payloads are VABC lz4. update_engine of the running Lumen wrote such a
payload on the X50 Ultra (beta4 to beta5, 2026-11-14: VABC partition writer, 984088576 B COW in super), the new slot
booted and the merge ended (MergeCompleted in 20 s, no COW in /data). 1.0.10-beta5 and beta6 shipped the HB's storage
check, which refuses compression: their cloud and USB stick installs stop at "compression on". A unit on them takes
the fixed updater as an update of the system app (adb install), then updates over the cloud. The updater's
versionCode (apps/LumenUpdater/BUILD_VERSION) was 2 with that fix, 3 since the plain-words texts (the X50 Ultra
of the beta tests carries a hand-installed 2) 4 since the parallel download, 5 since the channel by build type and is 6 since the deltas: raise it with every later change of the updater, or a copy installed
that way stays in front of the image's own.

## The download

`PackageDownload` waits for the network, runs `ParallelDownload`, tries again after a growing pause (404 or 410 from
the stable URL ends it) and checks the sha256 of the whole zip against the signed manifest, the same check a zip on the
USB stick gets. After a mismatch the zip is deleted and downloaded once more from the start, then `err_hash`.

`ParallelDownload` is the shared download of apps/core/lumen-download (its README: 4 to 8 range connections against
the per-connection shaping of GitHub, resume per range from `<zip>.part.state`, the CDN link renewed when refused,
`If-Range` with the ETag), the one Lumen Installer uses for its images too. The speed on screen and the time left
come from its last 10 s.

update_engine can also fetch the payload from a URL itself (applyPayload with an https URL, offset and size). That
would save the ~700 MB in the app's folder, but update_engine downloads over one connection (no gain against
per-connection shaping), keeps one signed CDN link that expires, and writes the other slot before the whole file was
checked against the manifest's sha256 (only the payload signature and update_engine's per-operation hashes guard it).
The updater keeps downloading the zip first. The zip is the payload plus about 28 KB of zip entries, nothing more.

## Deltas (DeltaRule)

A channel manifest may list deltas beside its full package: the optional `incremental` list (tools/release/delta.py;
older updaters do not read it) and `delta` entries of `packages` (the 1.0.9 form). A delta is taken only when the
running build is exactly its source: `from_build_id` = the image's build_id and, when the entry has it,
`from_version_code` = the running version_code; never for an install from a USB stick (the stick holds the full
package and is the way back) and never again once it failed on this build (`Store.refusedDeltas`: sha256 values keyed
by the running build_id). Any failure of a delta gives way to the full package of the same manifest
(`Newest.deltaFailed`): its sha256 after the download's retries, its metadata sha256, the way's check of the download,
a 404 or 410 of its file (the download goes on with the full package in the same run), the payload metadata signature,
applyPayload throwing, or update_engine ending with any error but a cancel, missing space or a downgrade (the offer is
then the full package, state found; the user starts it as usual). update_engine itself checks every block against the
source and the result against the target, so a delta on the wrong system never changes the other slot's boot.
Host test `test/org/lumen/updater/DeltaTest.java`, mutants `delta_*` in `test/mutate.py`.

## Tests

- `test/run.sh`: Outcome, the notice rule, the service worker (JobRunner), the gate strings in every locale, the
  statistics adapter on every assembled updater, the service wiring, no model name in the code, then parity.
- `test/parity/run.sh`: the behaviour transcript (`Probe.java`: channels, version codes, slots, manifest parsing,
  applicable, blob sets, install checks with a fake boot gate, download folder) and the static face (`static.py`:
  manifest, resources, every string) of each assembled updater must equal `golden/`, recorded from the three updaters
  of integ 389dc716 before this module existed. Strings may only be added.
- `test/download.sh`: PackageDownload over the shared download against the GitHub-like server of
  apps/core/lumen-download's test (the network wait, a corrupt first pass for the sha256 retry, 404 ends it, a
  complete file is checked without network). The download itself: `sh apps/core/lumen-download/test/run.sh`.
- `test/jobs_mutate.py`, `test/mutate.py`: mutants of the worker, the download and of the model data and rules.
- `test/run.sh` also pins the model table above per recipe (TargetFactsTest) and the two install ways (KindTest).
- Model data: `apps/Z9xUpdater/test/run.sh`, `apps/LumenUpdater/test/ota/run.sh`, `apps/Play6Updater/test/run.sh`.
