# lumen-stats

The one statistics client of every Lumen projector. `Ping` is the protocol (plain Java: the body, when to send, the
channel of a version), `Models` the device registry table (`python3 tools/stats/gen_models.py --write`, checked by
`test/run.sh`), `Device` the reads of the running system (model from the registry by `ro.boot.xgimi.modelname`, the
serial, a DSU trial run, version and build), `StatsClient.maybeSend` the send with a host's `StatsState`.

Hosts:
- the updater (Z9X `apps/Z9xUpdater`, HB `LumenUpdater`, Play 6 `Play6Updater`): `Stats` adapts its `Store` (the install
  id and the switch stay where they were, so an update does not make a new install id) and holds the first send after
  an update until the one-time notice was shown;
- an app without an updater (Play6Projector, X50Projector on the X50 Ultra, Ultra Max and RS30): `StatsJob.schedule`
  at boot (a run 30 s to 15 min after boot and a daily run), `PrefsState` as storage, the switch in its settings
  (`PrefsState.setOn`). The manifest declares `org.lumen.stats.StatsJob` (BIND_JOB_SERVICE) and INTERNET.

Privacy (README "Privacy"): a random install id (a new one after the switch was off or the app data was cleared), the
serial (the server keeps a keyed hash), model, XGIMI model, version, version code, build, channel, trial or installed,
and after an update the previous version and how it was installed. Nothing before the first-run setup ends, nothing
while the switch is off. Tests: `sh apps/core/lumen-stats/test/run.sh`.
