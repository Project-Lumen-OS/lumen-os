# Wi-Fi module patches (both LineageOS 21 builds)

`0001-p2p-supplicant-v3-snapshot.patch`: packages/modules/Wifi (pinned revision 6f6ab02c in
`lineage/manifests/lumen-lineage-21.xml`). The MediaTek TV vendors (Z9X, X50 family, TITAN, RS30 Ultra Max) ship a
supplicant that answers AIDL version 3 from a snapshot taken before V3 froze (interface hash 4652398b...): the V2
calls sit at their frozen codes, a vendor call sits where frozen V3 has connectWithParams, nothing above it. The P2P HAL
now decides each V3 call (connectWithParams, findWithParams, configureExtListenWithParams) from the interface hash:
only the frozen V3 hash gets them, any other hash gets the V2 calls, and a V3 call that answers unimplemented falls
back to its V2 call without dropping the interface. Evidence and delivery: `docs/casting/WIFI_P2P_HAL.md`.

The Play 6 vendor has a HIDL supplicant and the Z9X HB one reports V1: neither reaches the changed code.

Delivery: the change is inside the com.android.wifi APEX (service-wifi.jar). The jar is rebuilt from the pinned tree
with this patch and swapped into the base's own APEX, which is re-signed like every other APEX of the image
(`tools/sign/apex_sign.py run ... --swap`).

Checks:
- `sh lineage/patches/wifi/check.sh`: the patch applies to the pinned source (fetched read-only from GitHub).
- `sh lineage/patches/wifi/test/run.sh`: SupplicantP2pV3Calls from the patch itself (host JDK only) and the wiring of
  the patched SupplicantP2pIfaceHalAidlImpl.
- `python3 lineage/patches/wifi/test/mutate.py`: every mutant of the patch must turn run.sh red.
- `python3 lineage/patches/wifi/test/dexclass_diff.py BASE.jar PATCHED.jar OUT`: the rebuilt service-wifi.jar against
  the base's, class by class (only the P2P HAL classes may differ).
