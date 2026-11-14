# RS30 Ultra Max (g0155) base image

The RS30 Ultra Max has a 32-bit userspace on an arm64 kernel with `CONFIG_USERFAULTFD=y`, so its image cannot start
from the arm64 Z9X 1.0.9 image the X50 uses, and the Play 6 / HB arm32 base (built without uffd GC) would make
odrefresh compile the boot classpath before zygote on this kernel. Its base is its own build:

- LineageOS 21 `lineage_gsi_tv_arm` from `lineage/manifests/lumen-lineage-21.xml` (Lumen's pinned manifest)
- `lineage/patches/play6/0001..0006` and `lineage/patches/frameworks_av_C2Store_igba_hidl.patch`
- `OVERRIDE_ENABLE_UFFD_GC=true`, dexpreopt of the boot image and system server only
- `m systemimage apksigner`, AOSP test keys (the Mac moves the platform set to the g0155 key: SIGN_ON_MAC)

Scripts: `base/run_base.sh` (builder-llm, docker image `lineage-builder`: sync, patches, build, memory guard) and
`base/build_loop.sh` (the build inside the container, retries a failed step at most once more). Inputs go to
`~/rs30-base/in/` (manifest, patches, build_loop.sh), the output is
`~/rs30-base/out/target/product/generic/system.img`, pinned by sha256 in `base.sha256`.

Checks of the 2026-11-12 build (`eng.root.20261112.004111`, sha256 `7d92ee57...a173`): abilist `armeabi-v7a,armeabi`
only, `ro.dalvik.vm.enable_uffd_gc=true`, boot.oat `concurrent-copying=false`. the recipe (steps.tsv) refuses a base that
fails any of them, and the stock kernel of the dump must have `CONFIG_USERFAULTFD=y` and `CONFIG_COMPAT=y`.
