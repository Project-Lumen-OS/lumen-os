# lumen-gmpf

The client of XGIMI's gmpf vendor HAL for every projector app built on the Z9X app: the Z9X (HIDL IGmpf / IGmpf2),
the Z9X HB (stable AIDL services) and the Play 6 (HIDL, its IGmpf2 behind an identity guard). One body, the device
differences as data:

| layer | where | what |
|---|---|---|
| clients | app `hal/GmpfClient`, `hal/Gmpf2Client` (Play 6: `Play6Gmpf`, `Play6Gmpf2`) | the public API the app calls; extend the shared bodies and add the device's own calls (keystone point structs, HDMI and output timing structs, the boot and connect handshake, the game menu names) |
| shared bodies | `org.lumen.projector.hal.GmpfBase`, `Gmpf2Base` | every call both projectors make: argument checks, gates, read budgets, reply checks |
| call layer | `org.lumen.hal.gmpf` | `GmpfOp` (every operation, by stock method), `GmpfCodes` (a device's code table), `GmpfWire` (the transport contract), `GmpfRules` / `GmpfGate` (the device rules), `ZoomGuard` |
| device data | app `hal/Z9xCodes`, `Z9xRules`, `HidlWire` / `HbCodes`, `HbRules`, `AidlWire` / `Play6Codes`, `Play6Rules`, `Play6Gmpf2Wire`, `Gmpf2Vendor` | the code of each operation (generated `HalCodes` / allow-list `GmpfCall`), the feature gates, the wire layout; the Play 6 IGmpf2 guard and the code table of each accepted vendor build |

An operation without a row in the device table is not on that projector: the call throws (`GmpfRules.absent`) and
nothing is sent. Value types the callers see (`HdmiSignal`, `OutputTimingInfo`, `ReplyException`,
`NuiNotReadyException`, `FocusEventSink`) are top-level types of the hal package. What the Play 6 may reach on top
(bans, value rules, the guard, the boot handshake) is checked on its assembled tree by tools/play6/check_play6_hal.py
and check_handshake.py.

`test/wire`: the wire golden. `sh apps/core/lumen-gmpf/test/wire/run.sh` assembles each app, compiles its HAL
package against recording stubs of android.os and calls every public member of GmpfClient and Gmpf2Client with a
fixed set of arguments and reply patterns, then scripted sessions (zoom guard, game option, sensor budgets, struct
replies, transport errors, the focus callback). The Play 6 (`P6WireTrace`) does the same in two passes, one per IGmpf2
vendor table, plus the boot handshake and the identity guard; `P6Api` maps each member of the Play 6 API the golden
was recorded with onto the shared one. Every transaction (interface, code, flags, argument words) and every
result must equal `golden/<codename>.txt`, recorded from the clients before they shared one body. `compare.py OLD NEW`
tells wire and result changes (red) from message changes; `RECORD=1` writes a new golden after a reviewed change.
`python3 test/wire/mutate.py`: mutants of the codes, wires, rules and guards, each must turn the golden red.
