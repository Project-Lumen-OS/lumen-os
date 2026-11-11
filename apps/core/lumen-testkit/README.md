# lumen-testkit

Host-test helpers shared by the app suites (plain Java, no android.jar), so each suite stops carrying its own copy of
the same assert helpers: `Check` counts passes and failures, prints `FAIL <what>` for each failure and
`<Suite>: N passed, M failed` at the end, and exits 1 on a failure and also when nothing ran (a suite that runs no
check is red, the lesson of the rc=127 loops). `Check.read`, `Check.count` and `Check.countCode` are the file and
text helpers the static checks use.

Test code only: no recipe may name it (`module lumen-testkit` would put test code into an APK). A suite's `test/run.sh`
compiles `apps/core/lumen-testkit/src/org/lumen/testkit/Check.java` with its tests; a mutation script that copies the
tree copies `apps/core/lumen-testkit` too.

Users (2026-11-10): X50EyeGuard (EyeGuardTest), X50Projector (ProjectorTest, HdmiAudioTest, HomePanelTest).
