# tools/release: one release kit for every model

A beta of any model is the same pipeline; what differs is data, read from the registry (`devices/README.md`) by
`model.py`: the tag prefix, the asset names, trial or updates, the channel files, the key folder, the certificate and a
sibling model's certificate that must refuse. No script here names a model, and no release gets a copied kit any more
(the `~/lumen-build-tmp/<kit>` copies of x50beta*, rs30pub*, g0170 are replaced by this folder; a kit folder now holds
only that release's files and logs).

| step | host | command |
|---|---|---|
| facts | any | `python3 tools/release/model.py CODENAME VERSION` (shell: `eval "$(...)"`) |
| name | any | `python3 tools/release/naming.py CODENAME VERSION`: TITLE and TAG of the release (model.py passes them on, the Z9X and HB kits call it too) |
| tables | Mac | `python3 tools/release/models_table.py` after every release or catalog change: the supported projectors tables of the README sources, the install overview and the Installer README, from the live catalog and release list (`--check` only reports) |
| apps + platform set | Mac | `sh tools/devices/build_apps.sh CODENAME build/CODENAME/stage` (+ the family's base signing, `devices/g0111/image/sign_x50_base.py` for the X50 recipe) |
| stage + compose | Mac, llm | `python3 tools/build/build.py files CODENAME` lists every staged input (family files included); `build.py compose VERSION --stage-only`, then `--image-only` on the build host |
| digests | llm | `python3 tools/devices/ota/make_ota.py --device CODENAME unsigned ...` (models with updates): `payload.hash`, `metadata.hash` |
| delta | llm, Mac | `python3 tools/release/delta.py` (below): the update from the build the channel names now, beside the full package |
| sign | Mac | `bash tools/release/sign.sh CODENAME VERSION KIT`: SHA256SUMS, the two channel manifests, the payload digests; only small files cross the line, keys never leave the Mac |
| verify | any | `bash tools/release/verify.sh CODENAME VERSION DIR`: sums, signatures (sibling refuses), trial.sh, manifests, payload |
| publish | llm | `gh auth token \| ssh xgimi-llm 'export GH_TOKEN=$(cat); bash <tree>/tools/release/publish.sh CODENAME VERSION KIT'`: pre-release, upload, download back + verify, channel files, Latest unchanged; `--dry-run` first (no token) |
| channel guard | any | `python3 tools/release/channel_guard.py NEW.json... --live-dir DIR` or `--live-base URL`: a channel file never goes below its live version_code (publish.sh runs it in the preflight and right before the channel upload; a kit outside the tree, the Z9X and HB kits, runs it before its own channel upload; test `test/channel_guard_test.py`) |
| catalog | Mac + llm | `tools/devices/installer_catalog.py` with `--out-dir --live` from a tree that holds every live row's data, signed on the Mac, verified on llm |

`KIT` layout: the assets of `model.py ASSETS` (images as hard links into the build output), `notes.md`, and for a model
with updates `channel/update-<wire>-beta.json` and `update-<wire>-test.json` (each signed by `sign.sh`).

Proof of this kit against what the copied kits published (2026-11-21): `test/sign_proof.sh` (Mac) re-signs the live
SHA256SUMS and channel manifests of X50 Ultra beta12, Ultra Max beta12, TITAN Noir beta4, Noir Pro beta5, Noir Max
beta5 and RS30 Ultra beta1 byte for byte (10 of 10 signatures), and `publish.sh --dry-run` on xgimi-llm downloaded
back and verified the live TITAN Noir Max beta5, RS30 Ultra beta1 and X50 Ultra beta12 (sums, signatures with the
sibling refusing, trial.sh, both channel manifests; the payload signature check runs where `delta_generator` is on
PATH, llm has none).

## Deltas (incremental updates)

A full package is the whole system (Z9X ~770 MB); a delta holds only the blocks that changed since the build a unit runs.
`delta.py` makes one per release from the build its channel file names right before the publish (the last published
build of that channel), for every model with update_engine updates: the model's OTA tools come from its registry entry
(`ota_tools`, else `tools/ota`), nothing is named per model. Files stay on GitHub only: the source is the published
system.img of the source release (its signed SHA256SUMS), the delta zip and metadata are assets of the new release.

| step | host | command |
|---|---|---|
| source | llm | `delta.py source CODENAME --channel <live updater channel URL> --out SRC`: downloads and checks the source image (signature of SHA256SUMS, sha256, its build_id and version_code = the channel's) |
| build | llm | `delta.py build CODENAME --source SRC --like FULL_DIR --new NEW.img --out DIR`: make_ota unsigned with --old (same timestamp and super group as the full package in FULL_DIR), then APPLY_OK: update_engine's apply code on the host turns SRC's image into NEW byte for byte |
| sign | Mac | copy DIR/{ota.json,payload.hash,metadata.hash,source.json} to the Mac, `delta.py sign CODENAME DIR`, payload.sig + metadata.sig back (refused by every other model's certificate) |
| finish | llm | `delta.py finish CODENAME DIR --old SRC/<image> --new NEW.img`: the signed `...-from-<old>-ota.zip` + `-payload_metadata.bin`, applied again from the zip |
| entry | llm | `delta.py entry CODENAME DIR --manifest <the updater channel json> --base-url https://github.com/<repo>/releases/download/<TAG>/`: the `incremental` entry, then the Mac signs the manifest as usual |

Put the two delta files into KIT: `publish.sh` uploads and downloads back every `*-from-*` file, `verify.sh` checks
the entries. The manifest entry (`incremental`, read only by updaters that fall back to the full package) carries
`from_build_id`, `from_version`, `from_version_code`, `from_sha256`, url, size, sha256, payload range, metadata and
payload_properties; older updaters read only `packages` and keep taking the full one. The updater takes a delta only
when its build_id and version_code are the source's, never from a USB stick, and after any failure of the delta (its
sha256, metadata, a 404, update_engine's error) it offers the full package of the same manifest
(apps/core/lumen-updater DeltaRule). A Z9X kit outside the tree runs the same steps on its `update-public-beta.json`
before `mac_sign.sh manifest`.

## Naming

One scheme for every release, computed by `naming.py` from the registry (name, models, ota_engine, catalog.conf):

    title  Lumen OS <version> · <Model> (<codes>)          e.g. Lumen OS 1.1.0-beta4 · Z9X (G0082)
           Lumen OS <version> · <Model> (<codes>) · Live   a model with Lumen OS Live only
    tag    <slug>-<version>                                the catalog.conf tag, e.g. z9x-hb-1.0.10-beta24
    channel releases: Lumen OS updates · <Model> (<codes>)

A new model's slug is its name in lowercase letters and digits (`naming.py --slug "XGIMI RS 20 Pro"` gives rs20pro,
a slug already taken gets `-<code>`). Published slugs never change: they are in download URLs, asset names, the
Installer catalog patterns and the site. Test: `test/naming_test.py`.
