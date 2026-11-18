# lumen-aipq

The AI picture mode as stock XGIMI runs it (XRMService `AIPQ.java`, the same logic on every model): the menu writes
picture mode AI (10) only, and this module turns it into the AI scene the model file names (`ro.xgimi.default.aipq`,
film = 16) whenever the vendor reports a picture mode change, at start and at every screen on.

- `AiPq`: the stock logic, plain Java (host tested by `test/AiPqTest.java`).
- `AiPqVendor`: the HAL calls, one implementation per projector family (gmpf apps: `AiScenePolicy`, X50 family: `X50AiPq`).
- `AiPqDriver`: the Android side (video type broadcasts, the 1.5 s video monitor, one lock over every call).

Tests: `sh apps/core/lumen-aipq/test/run.sh`, mutants: `python3 apps/core/lumen-aipq/test/mutate.py`.
