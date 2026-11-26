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

## Naming

One scheme for every release, computed by `naming.py` from the registry (name, models, ota_engine, catalog.conf):

    title  Lumen OS <version> · <Model> (<codes>)          e.g. Lumen OS 1.1.0-beta4 · Z9X (G0082)
           Lumen OS <version> · <Model> (<codes>) · Live   a model with Lumen OS Live only
    tag    <slug>-<version>                                the catalog.conf tag, e.g. z9x-hb-1.0.10-beta24
    channel releases: Lumen OS updates · <Model> (<codes>)

A new model's slug is its name in lowercase letters and digits (`naming.py --slug "XGIMI RS 20 Pro"` gives rs20pro,
a slug already taken gets `-<code>`). Published slugs never change: they are in download URLs, asset names, the
Installer catalog patterns and the site. Test: `test/naming_test.py`.
