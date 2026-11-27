# HDMI framework patches

`0001-hdmi-vendor-earc-link.patch`: the stock XGIMI ARC/eARC linkage in the LineageOS 21 `frameworks/base` HDMI
service (all families: Z9X, HB, RS 20, X50 line). See the patch header for the rules.

- Status: built into every image as the services.jar fix `services_hdmi` of `tools/base_patch` (README "services.jar":
  the patched classes swapped into each base's services.jar, odex, vdex, app image and profiles made with the base
  build's own soong dexpreopt command, pinned per base in `tools/base_patch/framework.tsv`). A soong rebuild of
  services.jar is not reproducible (protologtool names a ProtoLogImpl_<random> class every build), so the base jar
  keeps every other class byte for byte. Proven on the HDMI-CEC harness of xgimi-llm before it went into the images.
- `RequestArcInitiationAction.ANSWER_TIMEOUT_MS` = `HdmiConfig.TIMEOUT_MS`, 2000 ms started before the request is
  sent: the stock XGIMI value (dexdump of the stock services.jar of G0082, G0148 6.17.12 and X50 v1.3.38). Harness
  scenario `21_slow_bar_arc_timeout` shows what 6000 would change (a soundbar answering after 5.4 s keeps ARC).
- The app side reads `sys.lumen.vendor_earc_glue`: with it set the framework owns the HDMI ARC output.
