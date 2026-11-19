# lumen-sensors

The developer overlays of every Lumen projector (Z9X, Z9X HB, Play 6, X50 Ultra, Ultra Max, RS30 Ultra Max, TITAN),
built once on the signal panel's overlay look (apps/core/lumen-signal: SignalPlace, SignalLook, PanelToggle). It
replaces the old separate LumenSensors app (owner 2026-11-19: an overlay only, no launcher app).

## Sensor card

- Opened from All settings, the For developers page (lumen-menu `FeatureId.SENSORS_PANEL`, a module screen)
  and by the intent `org.lumen.projector.action.SENSORS_PANEL` (SensorReceiver). Same three states as the signal
  panel: the small card top left (keys stay with the film), the full sheet on the right (BACK closes), hidden.
- Holding OK on the card opens the full sheet, holding it again or BACK folds it back, a short OK goes on to the film
  unchanged (lumen-signal `OkHold` / `CardHold`, the same rules and the same first-three-times hint as the signal card).
- The signal panel and the card never show together: opening one closes the other, so the vendor thread sees at most
  one overlay's reads (SensorBudget: 2 calls a second, none while hidden).
- Rows only where the model reads them (`devices/<c>/sensors.tsv`, in the app as `assets/lumen_sensors/items.tsv`):

| row | Z9X | HB | X50 family | Play 6 |
|---|---|---|---|---|
| temperature inside | IGmpf 438 | TEMPERATURE 6 | TEMPERATURE 6 | IGmpf 438 |
| light source temperature | 439 sensor 5 | no | TEMPERATURE 7 sensor 5 | no |
| distance to the wall | 279, once a minute, full sheet | waits for the HB MOTION gate | no getter | no |
| accelerometer, gyroscope | 584, every 10 s, full sheet | waits for the HB MOTION gate | no getter | no |
| processor temperature, load, memory | kernel files | kernel files | kernel files | kernel files |

- Never IGmpf 194, IGmpf 275 or IGmpf2 252 (check_sensors.py). Fans are not read: the shared gmpf clients have no fan
  getter (Z9X 435 / 437 would be a new client method with its wire golden).

## Diagnostics

`DiagPanel` shows the projector app's own diagnostic lines (HAL state, versions) in the same full sheet, OK reads them
again. Opened from the same group (`FeatureId.SETTINGS_DIAGNOSTICS`, the device adapter supplies the text).

## Pieces

| file | role |
|---|---|
| `SensorItem`, `SensorItems` | the values and the model's table |
| `SensorReader` | the per-model part: the app's adapter over its own HAL client |
| `SensorBudget` | at most 2 vendor calls a second, card items only on the small card, the distance once a minute |
| `SystemStats` | processor temperature, load, memory from kernel files |
| `SensorSheet` | what to draw, rows without a value are not there |
| `SensorPanel`, `SensorWindow`, `SensorReceiver` | controller, overlay window, the intent |
| `DiagPanel` | the diagnostics sheet |

Readers: `apps/ProjectorBase/.../panel/GmpfSensorReader` (Z9X and, through the recipe, HB),
`apps/Play6Projector/.../panel/Play6SensorReader`, `apps/X50Projector/.../X50SensorReader`.
