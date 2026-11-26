# lumen-hal

The one raw Binder path from a Lumen app to XGIMI's stable AIDL vendor services (`xgimi.hardware.gmpf.I*Manager/default`,
format aidl): `AidlTransport` and the paced call log `HalLog`. Taken on 2026-11-10 from the two transports of
`apps/X50EyeGuard` and the X50 family adapter `apps/LumenProjector/vendor/xgimi/mt9681-aidl` (`Gmpf`), whose transport parts were the same code.

- An app subclasses `AidlTransport<GmpfCall>` once (its `Gmpf`). Two top-level enums of the app go with it:
  `GmpfIface implements AidlTransport.Service` names the interfaces, `GmpfCall implements AidlTransport.Method` is its
  allow list (= `devices/<c>/research/allowed_<app>.tsv`) and the only type `call()` accepts. Every code in it is a
  constant of the app's generated `HalCodes` (`tools/hal/gen_codes.py` from `devices/<c>/hal/codes.tsv`, named by the
  interface and the stock method, proven against the stock table by `tools/hal/check_codes.py`), never a number.
  Every value a setter may carry is fixed in the subclass.
- Guard: getInterfaceVersion and getInterfaceHash must be the app's before the first call on an interface; a mismatch
  closes it for the process. The hashes come from a `HashRule`: a base hash on every firmware, and extra hashes each
  only on its own stock firmware (`ro.vendor.build.version.incremental`; X50 v1.2.54 ecadd76e, whose code table is
  v1.2.64's without seven appended methods). Add an extra hash only with that firmware's code table checked against
  the app's allow list (`check_aidl_hal.py` with one `--codes` per firmware). `checkService` only, never `getService`; calls on the main thread are refused; every
  call gets a paced INFO line under the app's tag.
- `tools/devices/check_aidl_hal.py --module apps/core/lumen-hal` checks the app with this transport on the sources and
  the dex (one transact site, here; the allow list in the app); `devices/g0111/image/x50_apk_check.py` reads the
  allow list from the dex.

Consumers: X50EyeGuard (8 calls), X50Projector (the menu) and the HB projector app (aidl-gmpf `AidlWire`, since
2026-11-25: the HB rules as the family hooks `replyError` (ReplyException for a delivered failure), `closesOnFailedGuard`,
`watchesDeath` / `vendorDied`, the stable parcelables of its calls through `proxyCall` with `Out` / `In`, the callbacks
it registers at every connect through `sendCallback`; the X50 family keeps the defaults). `LumenInfo` HbGmpf (a
diagnostic app outside the release images) and LumenSensors keep their own transports until they move.
