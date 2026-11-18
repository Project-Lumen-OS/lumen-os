# lumen-fwcopy: the XGIMI firmware set and its copy

Owner decision 2026-11-15: Lumen may copy the projector's own XGIMI files from the running half into the other half,
unchanged, as XGIMI's own update and the Z9X updater do. Owner decision 2026-11-17: Lumen OS runs on exactly the
firmware stock runs on, so the install copies the whole set of the running half on every model, the MBoot bootloader of
the HB too (this lifts decision O3 for the install copy). Owner decision 2026-11-18: the first boot stage of the other
half is written too, exactly the way XGIMI's own post-install step writes it, on every model whose stock does that
(section "The first boot stage"). Source module, compiled into every caller:

- `LumenDsuInstall` (the in-trial install): `Engine.check` plans the copy, `InstallActivity` asks the user, the
  install step `slots` runs it, the step before the switch reads both halves again (`verifyEqual`).
- `lumen-updater` (the LumenUpdater, Z9xUpdater and Play6Updater recipes): `Preflight.partitions` accepts an update
  only when the build's `ro.product.ab_ota_partitions` is exactly the model's update list (`FwSet.updateNames`: the
  set without the model's `installOnly`). The updater does not copy itself:
  update_engine copies that list from the running half (SOURCE_COPY with its own hash checks, the target half
  unbootable while it writes, read-back verify).
- The image tools: `tools/devices/ota/fwset.py` reads the rows of `FwModel.java` for `ota_conf.py`;
  `test/FwListsTest` pins every list in the tree (images, ota.conf, image checks) to the module.

Classes: `FwModel` (per model family the stock `ro.product.ab_ota_partitions`, the A/B pairs of the stock dump, what
the model keeps out of the copy or out of Lumen's own update, the first stage its stock post-install step writes; a new
projector is one row), `PostWrite` (the first stage's data: file, writer sha256, areas, the projector's own ranges),
`FwSet` (the set of one projector; `updateNames`, `updateList`, `checkUpdateList`),
`FwCopy` (plan, real or dry run, `verifyEqual`), `FwPlan`, `FwResult`, `Blocks`, `BootAreas` (by-name links,
EXT_CSD[179], force_ro, vendor files), `Halves`, `Journal`, `Mode`.

## The set of a model

A partition is copied only when all of these hold (`FwSet.of`):

1. it is in the model's stock `ro.product.ab_ota_partitions` (from the stock dump, `FwModel` rows);
2. it is not in `FwSet.NEVER` (vbmeta, the whole eMMC and its boot areas by kernel name, uenv, calibration and factory
   data, misc, user data) and not in `FwSet.POST_ONLY` (rom_emmc_boot: only ever written as a `PostWrite`, below);
3. it is not a logical partition in super (`FwSet.DYNAMIC`);
4. the model's dump has it as an `_a`/`_b` pair, and this projector has both halves (else the plan refuses);
5. it is not `keepEqual` on the model (compared, never written, a difference refuses). No model has such a
   partition now: the HB mboot was one until 2026-11-17.

`installOnly` (the HB mboot) changes nothing in the install copy. It only takes the partition out of the list a Lumen
image gives update_engine, so Lumen's own update never writes a bootloader on the HB: the install has made both halves
the same and an update has nothing to change there.

| partition | Z9X G0082 | X50 family G0111 G0100 H0062 H0063 | HB G0130 G0118 G0148 | RS30 G0155 | Play 6 G0058 | why |
|---|---|---|---|---|---|---|
| boot | copy | copy | copy | never (model) | no A/B | kernel, in the stock list |
| bootdata | copy | copy | copy | never | no A/B | XGIMI boot data (dtbo mapping, PM51 signature) |
| dtbo | copy | copy | copy | never | no A/B | device tree overlays |
| mboot | copy | copy | copy by the install, not in Lumen's own update | never | no A/B | MBoot bootloader, written by XGIMI's update |
| optee | copy | copy | copy | never | no A/B | TEE |
| owl | copy | copy | not on the model | never | no A/B | mt5877 only |
| satf | copy | copy | not on the model | never | no A/B | mt5877 only |
| armfw | not on the model | not on the model | copy | never | no A/B | HB only |
| tvconfig | copy | copy | copy | never | no A/B | TV firmware data |
| tvservice | copy | copy | copy | never | no A/B | TV service binaries |
| vbmeta_system | copy | copy | copy | never | no A/B | chained descriptor, written by XGIMI's update |
| xgimiconfig | copy | copy | copy | never | no A/B | XGIMI configuration |
| vbmeta | never | never | never | never | - | the AVB root: the install writes its own copy, an update never |
| system, system_ext, product, vendor | never | never | never | never | - | logical, in super |
| rom_emmc_boot | written by the install, last, the stock way | written by the install, last, the stock way | written by the install, last, the stock way | never (model) | no A/B | the first boot stage in the eMMC boot areas: the running vendor's `rom_emmc_boot.bin`, never Lumen's own update (section below) |
| misc, uenv, persist, xgimicri, xgimisps, xgimidatabase, project_id, tvcertificate, frp, metadata, userdata, cache, super | never | never | never | never | - | one copy for both halves: boot record, calibration, factory, user data |

Evidence (xgimi-builder `~/devices`): G0082 6.15.62 `v61562` product build.prop and lumendump by-name; G0111 1.2.64
full dump 2026-11-08 (getprop, parts, by-name); G0130 6.16.24 and G0148 6.17.12 product build.prop and by-name;
G0155 getprop and by-name; G0058 has no A/B. The RS30 copies nothing: it has no install and no updater, its second
half is no way back.

### The other half holds another stock build

After XGIMI's own update the other half still holds the build before it. What the install does then:

| family | the install |
|---|---|
| HB G0130 G0118 G0148 | copies the whole set from the running half (boot, dtbo, mboot and the rest). No slot-set rule: the two HB stock builds have different kernels |
| X50 family G0111 G0100 H0062 H0063 | copies the whole set from the running half |
| Z9X G0082 | the older build is one of the known builds of the same boot chain: its own chain stays, nothing of it is written (the installer's `SlotSet`). Anything else: the whole set is copied from the running half |

No family refuses because the halves differ. A refusal is left for a stock version the installer's `Revisions` table
does not know, a missing half, halves of different size and a running half that starts with 4 KiB of zeros.

### The first boot stage (`PostWrite`, owner 2026-11-18)

`rom_emmc_boot_a` / `_b` are the two eMMC boot areas (`mmcblk0boot0` / `boot1`). They hold the first boot stage, the
code that runs before MBoot. XGIMI's update writes it outside update_engine's list, from the post-install step of the
new vendor (payload: vendor `run_postinstall`, `bin/postinst_mtk`, not optional): `postinst_mtk` reads
`rom_emmc_boot.bin` of the new vendor and writes the whole file at offset 0 of the half being installed (running a:
`mmcblk0boot1`, running b: `mmcblk0boot0`), `force_ro` 0 before and 1 after. The boot HAL's `setActiveBootSlot` then
enables that area in EXT_CSD[179]. So stock never leaves a half with an older first stage under a newer MBoot, and the
install does not either: it writes the first stage of the other half the same way, as the last copy.

| model | stock writer (`vendor/bin/postinst_mtk`) | first stage in the install copy |
|---|---|---|
| HB G0130 G0118 G0148 | d57af229 (arm32, 6020 B), G0130 6.16.24 and G0148 6.17.12, disassembled | yes |
| Z9X G0082 | 303152c9 (arm64, 134000 B), 6.15.19, 6.15.58, 6.15.62, disassembled: the multi-chip branch is not taken for these files (no `MOPF`) | yes |
| X50 family G0111 G0100 H0062 H0063 | 303152c9, G0111 1.2.64 and H0062 1.2.54 / 1.2.99 (G0100, H0063: no dump, the sha256 is checked on the unit) | yes |
| RS30 G0155 | 288c51e8 (arm32, 6024 B), the HB's code built again | no: the RS30 copies nothing (trial only) |
| Play 6 G0058 | no A/B | no |

What the install writes and when (`FwCopy`, the HB rules in `PostWrite.HB_FIRST_STAGE`, the others in
`PostWrite.MT5877_FIRST_STAGE`):

- The source is the running vendor's own `/vendor/bin/rom_emmc_boot.bin`, the file stock wrote into the running area,
  never a raw copy of the running area (that carries this unit's memory training data). The running area must hold
  that file outside the projector's own ranges, read from the dumps: HB `0x46000-0x46300` (DRAM training table) and
  `0x6cc00-0x6d000` (a blob zeroed on the unit), mt5877 `0x66c00-0x67000` and `0x9a400-0x9a800`. Compared on the
  G0130 (before and after its update), the G0148, the G0082 6.15.62, the G0111 1.2.64 and the G0155 1.1.42: every area
  is its vendor file outside those ranges, zero after it.
- Only when the running vendor's `postinst_mtk` has a sha256 whose code was read, the file is no multi-chip package,
  the by-name links are `mmcblk0boot0` / `mmcblk0boot1`, and EXT_CSD[179] (the kernel's debugfs text) enables the
  running half's area and not the one to write. Else the install is refused with the reason, nothing written.
- The other half's area already holding the file outside those ranges counts as the same: nothing written.
- Written after mboot and the rest of the set, before the switch: `force_ro` 0 (read back), the first 4 KiB zeroed,
  the rest of the file, the first 4 KiB, `force_ro` 1 again on every path (read back). Nothing past the file's length.
  Read back against the file. Before the switch `verifyEqual` reads the running vendor's file, the running area,
  the other area and EXT_CSD again. A power cut at any point leaves the running half, its area and EXT_CSD as they were:
  the projector starts stock, a new run sees the other area unfinished from its bytes and writes it again.
- Z9X slot-set rule: when the other half keeps an older known chain whole (its own boot, mboot and the rest), it keeps
  its own first stage too, which the stock update that wrote that chain wrote with it. A chain copied from the
  running half always takes the running first stage with it.
- Lumen's own update never writes it (update_engine cannot): the install has made both first stages the same file.

Not proven: which code rewrites the projector's own ranges (encrypted), and the first start from a first stage the
install wrote. That is the first live check, on the HB.

## The copy (`FwCopy.run`)

1. Plan: every compared partition of both halves hashed (sha256), sizes equal and whole 4 KiB blocks, the running
   half never starts with 4 KiB of zeros; the plan id is the sha256 of every line, so a copy runs only on the bytes the
   user allowed. Equal halves: nothing to copy, no consent asked, nothing written, no boot record call.
2. No consent: stop, nothing written.
3. The other half marked unbootable through the boot HAL and read back (`Halves`).
4. Every source read a second time: the same sha256 as the plan, else stop before any write.
5. The fence: the first 4 KiB of every target zeroed and synced. No stock partition starts with 4 KiB of zeros, so a
   copy cut by a power loss is seen from the bytes alone, even without the journal.
6. Per partition: the body from 4 KiB on, sync, then the first 4 KiB, sync, then the target read back: the sha256 must
   be the source's. Any difference, read or write error: stop, the other half stays unbootable, the running half was
   never opened for writing.
7. A journal (`fwcopy.state`, replaced as a whole) names the plan, the state and the parts done; a run after a power
   loss plans again from the bytes and copies what still differs.
8. Making the other half usable again is the caller's step, only after `verifyEqual` read both halves equal.

`Mode.DRY` does every read of a real run (both reads of every source) and prints `WOULD:` lines: no write, no boot
record call, no journal. `RootMain fwcopy` (LumenDsuInstall) is that dry run on a projector.

## Tests

- `sh apps/core/lumen-fwcopy/test/run.sh`: FwCopyTests (fake block devices: every branch, power cuts at many points
  with and without the journal, read and write errors, corrupted writes, changing sources, size changes, the sync
  order, the HB mboot copied by the install and absent from the update list, a kept part of a model, single copies,
  missing halves, the dry run), FirstStageTests (the first stage: order and force_ro around the write, every refusal,
  force_ro faults, a bad write, EXT_CSD or the file changing after the plan, the slot-set rule, power cuts inside it,
  the rows of every model) and FwListsTest (every list in the tree).
- `python3 apps/core/lumen-fwcopy/test/mutate.py`: every safety rule broken once, every mutant must be killed.
- `bash apps/core/lumen-fwcopy/test/fwsim.sh DUMP OTA_IMG`: the module on real partition images (the G0111 1.2.64
  dump with XGIMI's 1.2.54 images written over half b), a power cut in the middle, the run again, a third run.
- `bash apps/core/lumen-fwcopy/test/fwsim_hb.sh SLOTSET`: the module on both real halves of an HB after its stock
  update (xgimi-builder `~/hb-out/slotset`, the G0130 of 2026-11-17, every pair differs, with the EXT_CSD read on it and
  the official 6.17.12 `rom_emmc_boot.bin` and `postinst_mtk`): the plan is the ten partitions of the stock list and
  then the first stage, vbmeta left out, the dry run writes nothing, a power cut inside mboot, inside tvconfig, in the
  last partition and inside the first stage, the run again ends with the set equal and the other area holding the
  running vendor's file, the running half (its area too) byte for byte as before.
- The installer's side (consent bound to the plan id, the copy step, the read again before the switch, the write
  guard, the slot-set rule): Z9xTests, X50Tests and SlotSetTests of the installer app, with a mutator each in
  `tools/devices/test`.
