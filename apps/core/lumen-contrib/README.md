# lumen-contrib

The contribution client of Lumen Installer (later also the in-trial installer and the Lumen system): asks the
contribution base what this unit can still contribute for its model and stock firmware (`GET /v1/pair`), sends one
signed event (`POST /v1/earn`: stock system info sent, trial ran, install done) and turns the answer into one card
(`Card`: nothing, "send the system info", a claim code, a Spark already taken, "installed before"), and asks what
this projector earned before (`POST /v1/mine`, `Mine`, by the serial hash). `Shown.decide` picks what the card shows
and whether the report button leads, from the base's answers and the answers the app kept.

- The body is fixed text (`ContribBody`): context line `LUMEN-CONTRIB-V1`, then kind, model, firmware, serial hash,
  ref, time, nonce. The serial never leaves the unit: only SHA-256 of `LUMEN-SERIAL-V1|` + serial.
- Signed with an Android Keystore EC P-256 key (`KeystoreSigner`, alias `lumen-contrib`, signing only, never exported).
- Best effort: 10 s timeouts, answers capped at 4 KB, every failure (offline, refused, a strange answer) is "no card".
  Nothing here may block or change the install path.
- The base is `https://spark.lumen-os.net`. What a person gets is a Spark (RU "Вспышка", owner 2026-11-21), never a
  word that means writing firmware in Lumen Installer: the test run fails if one appears in this module.
- Tests: `sh apps/core/lumen-contrib/test/run.sh` (host, on a builder).
