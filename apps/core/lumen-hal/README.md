# lumen-hal

The one raw Binder path from a Lumen app to XGIMI's stable AIDL vendor services (`xgimi.hardware.gmpf.I*Manager/default`,
format aidl): `AidlTransport` and the paced call log `HalLog`. Taken on 2026-11-10 from the two transports of
`apps/X50EyeGuard` and `apps/X50Projector` (`Gmpf`), whose transport parts were the same code.

- An app subclasses `AidlTransport<Gmpf.Call>` once. Its `enum Iface implements AidlTransport.Service` names the
  interfaces, its `enum Call implements AidlTransport.Method` is its allow list (= `devices/<c>/research/allowed_<app>.tsv`)
  and the only type `call()` accepts. Every value a setter may carry is fixed in the subclass.
- Guard: getInterfaceVersion and getInterfaceHash must be the app's before the first call on an interface; a mismatch
  closes it for the process. `checkService` only, never `getService`; calls on the main thread are refused; every
  call gets a paced INFO line under the app's tag.
- `tools/devices/check_aidl_hal.py --module apps/core/lumen-hal` checks the app with this transport on the sources and
  the dex (one transact site, here; the allow list in the app); `devices/g0111/image/x50_apk_check.py` reads the
  allow list from the dex.

Consumers: X50EyeGuard (8 calls), X50Projector (the menu). The HB (`HbProjector` AidlCaller, `LumenInfo` HbGmpf) and
LumenSensors keep their own transports while their APKs are frozen or until they move (phase 4).
