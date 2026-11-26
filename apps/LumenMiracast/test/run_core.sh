#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen Miracast core host checks (plain Java, no android.jar): RTSP framing, WFD parameters and
# formats, the sink session state machine, RTP reorder, MPEG-TS demux, audio payloads, the media
# pipeline, the source locator, and one end-to-end run against a scripted source over real
# localhost sockets; then the control API decisions (ControlTest). sh test/run_core.sh ; exit 0 = all passed.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -Xlint:all -Werror -proc:none -d "$OUT" \
  "$HERE"/src/org/lumen/miracast/core/*.java "$HERE"/test/org/lumen/miracast/core/*.java
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.miracast.core.CoreTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.miracast.core.SenderTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.miracast.core.DiagTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.miracast.core.ControlTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.miracast.core.CapabilityTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.miracast.core.HealthTest
# the control API wiring (StateProvider, manifest): CONTROL enforced in call(), the provider guarded by it too,
# a set call only with a valid switch value, the status line from the one shared rule
P="$HERE/src/org/lumen/miracast/StateProvider.java"
M="$HERE/AndroidManifest.xml"
bad=0
grep -A1 '        Context c = getContext();' "$P" | grep -qx '        c.enforceCallingOrSelfPermission(PERM_CONTROL, "screen mirroring control");' \
  || { echo "FAIL provider: CONTROL not enforced first in call()"; bad=1; }
grep -q 'PERM_CONTROL = "org.lumen.miracast.permission.CONTROL"' "$P" || { echo "FAIL provider: wrong permission"; bad=1; }
grep -q 'if (on == null) return null;' "$P" || { echo "FAIL provider: a set call without a valid value changes something"; bad=1; }
grep -q 'ReceiverStatus st = StatusText.status(c);' "$P" || { echo "FAIL provider: status not from StatusText"; bad=1; }
grep -A5 'android:name=".StateProvider"' "$M" | grep -q 'android:readPermission="org.lumen.miracast.permission.CONTROL"' \
  || { echo "FAIL manifest: provider read not guarded by CONTROL"; bad=1; }
grep -A5 'android:name=".StateProvider"' "$M" | grep -q 'android:writePermission="org.lumen.miracast.permission.CONTROL"' \
  || { echo "FAIL manifest: provider write not guarded by CONTROL"; bad=1; }
grep -B2 -A2 'android:name="org.lumen.miracast.permission.CONTROL"' "$M" | grep -q 'android:protectionLevel="signature"' \
  || { echo "FAIL manifest: CONTROL is not a signature permission"; bad=1; }
# no screen of its own: the old settings address only opens the casting screen, the status page is the menu's
SA="$HERE/src/org/lumen/miracast/SettingsActivity.java"
grep -q 'new Intent(CAST_SCREEN)' "$SA" && grep -q 'startActivity(castScreen(this, getIntent().getStringExtra(EXTRA_EDIT)));' "$SA" \
  || { echo "FAIL settings: the old address does not open the casting screen"; bad=1; }
grep -qE 'setContentView|new (TextView|LinearLayout|ScrollView)' "$SA" && { echo "FAIL settings: the old address builds a screen"; bad=1; }
grep -A5 'android:name=".SettingsActivity"' "$M" | grep -q 'Theme.NoDisplay' || { echo "FAIL manifest: the redirect shows a window"; bad=1; }
[ -e "$HERE/src/org/lumen/miracast/DiagActivity.java" ] && { echo "FAIL the old status page is back"; bad=1; }
grep -q 'DiagActivity' "$M" && { echo "FAIL manifest: the old status page is back"; bad=1; }
grep -q 'StatusText.quality(c, Prefs.quality(c))' "$P" || { echo "FAIL provider: no quality in the state"; bad=1; }
[ $bad = 0 ] && echo "control wiring: ok"
# the receiver fixes of 1.0.11 (Android side, not runnable on the host): the wildcard approver comes back after
# every answer, a request without a scanned peer is still answered, the screensaver ends before the prompt and
# the picture, a mirror screen that never came up does not end the session, the stage report prints in dumpsys
S="$HERE/src/org/lumen/miracast"
fixbad=0
grep -q 'if (P2pRules.reattach(reason, running, removingApprover)) {' "$S/P2pController.java" \
  || { echo "FAIL p2p: approver not attached again after the framework drops it"; fixbad=1; }
grep -A2 'if (dev == null && config != null && config.deviceAddress != null) {' "$S/P2pController.java" | grep -q 'dev = new WifiP2pDevice();' \
  || { echo "FAIL p2p: a request without a scanned peer is dropped"; fixbad=1; }
grep -q 'if (!P2pRules.ownPrompt(wps)) {' "$S/P2pController.java" || { echo "FAIL p2p: PIN rule not from P2pRules"; fixbad=1; }
grep -B1 -A1 'wakeFromScreensaver("picture");' "$S/MiracastService.java" | grep -q 'takeFocus();' \
  || { echo "FAIL service: the picture starts without ending the screensaver"; fixbad=1; }
grep -B2 'wakeFromScreensaver("connection request");' "$S/MiracastService.java" | grep -q 'the card and later the picture' \
  || { echo "FAIL service: the prompt shows without ending the screensaver"; fixbad=1; }
grep -q 'dm.stopDream();' "$S/MiracastService.java" || { echo "FAIL service: no stopDream"; fixbad=1; }
grep -q 'protected void dump(FileDescriptor fd, PrintWriter pw, String\[\] args) {' "$S/MiracastService.java" \
  || { echo "FAIL service: no dumpsys report"; fixbad=1; }
grep -A2 '        if (!resumedOnce) {' "$S/MirrorActivity.java" | grep -q 'started under another window' \
  || { echo "FAIL mirror: a screen that was never shown ends the session"; fixbad=1; }
grep -q 'android:name="android.permission.WRITE_DREAM_STATE"' "$M" || { echo "FAIL manifest: no WRITE_DREAM_STATE"; fixbad=1; }
grep -q 'DIAG.equals(method)' "$S/StateProvider.java" || { echo "FAIL provider: no stage report for the status page"; fixbad=1; }
grep -q 'case "wfd_idr_request_capability": return "1";' "$S/core/WfdSession.java" || { echo "FAIL rtsp: IDR capability"; fixbad=1; }
# the link rule (P2pHealth): never the listen calls (on the MediaTek TV vendors the framework turns them into a
# supplicant call the vendor lacks and loses its P2P interface), failures reach P2pHealth, a restart reopens the
# channel, an accepted connection is probed, the casting screen gets the cause
grep -q 'startListening(\|stopListening(' "$S/P2pController.java" && { echo "FAIL p2p: listen calls are back"; fixbad=1; }
grep -q 'mgr.discoverPeers(channel, stageListener("discoverPeers", Diag.Stage.VISIBLE, "discovery"));' "$S/P2pController.java" \
  || { echo "FAIL p2p: not visible by discovery"; fixbad=1; }
[ "$(grep -c 'healthFailed(reason, what);' "$S/P2pController.java")" = 2 ] || { echo "FAIL p2p: a failure does not reach P2pHealth"; fixbad=1; }
grep -A12 'private void restart(String why) {' "$S/P2pController.java" | grep -q 'channel.close();' \
  || { echo "FAIL p2p: a restart does not close the channel"; fixbad=1; }
grep -q 'main.postDelayed(acceptProbe, ACCEPT_PROBE_MS);' "$S/P2pController.java" || { echo "FAIL p2p: an accepted connection is not probed"; fixbad=1; }
grep -q 'act(health.lostAfterAccept(), "after an accepted connection");' "$S/P2pController.java" \
  || { echo "FAIL p2p: a link lost after accepting does not restart"; fixbad=1; }
grep -q 'b.putString("detail", detail != null ? detail : "");' "$S/StateProvider.java" || { echo "FAIL provider: no detail"; fixbad=1; }
grep -q 's == null ? null : s.fault().name());' "$S/StatusText.java" || { echo "FAIL status: the fault does not reach the status"; fixbad=1; }
for f in "$HERE"/res/values*/strings.xml; do
  for k in status_waiting_wifi status_restarting status_no_answer status_connect_broken; do
    grep -q "name=\"$k\"" "$f" || { echo "FAIL strings: $k missing in $f"; fixbad=1; }
  done
done
[ $fixbad = 0 ] && echo "receiver fix wiring: ok"
[ $fixbad = 0 ] || bad=1
# Wi-Fi Direct capability (every model and chip, decided at run time): a successful call proves it, no framework is a
# definitive NO, Wi-Fi on / off feeds the proof window, the verdict switches the control provider (the quick menu tile),
# a definitive NO stops the service; the decoder starts again without low-latency when the key is refused
capbad=0
[ "$(grep -c 'decide(cap.proven(what + " ok"));' "$S/P2pController.java")" = 2 ] \
  && grep -A1 '                diag.ok(stage, okDetail);' "$S/P2pController.java" | grep -q 'decide(cap.proven(what + " ok"));' \
  || { echo "FAIL p2p: a successful call does not prove Wi-Fi Direct"; capbad=1; }
grep -A1 'mgr.discoverPeers(channel, stageListener("discoverPeers", Diag.Stage.VISIBLE, "discovery"));' "$S/P2pController.java" >/dev/null \
  && grep -A4 '                diag.ok(stage, okDetail);' "$S/P2pController.java" | grep -q 'if (stage == Diag.Stage.VISIBLE) setState(P2pState.VISIBLE);' \
  || { echo "FAIL p2p: a discovery success is no proof of visibility"; capbad=1; }
grep -q 'decide(cap.callFailed(reason, what));' "$S/P2pController.java" || { echo "FAIL p2p: failures do not reach the capability rule"; capbad=1; }
grep -q 'decide(cap.noFramework("android.hardware.wifi.direct missing"));' "$S/P2pController.java" \
  || { echo "FAIL p2p: no Wi-Fi Direct framework is not a NO"; capbad=1; }
grep -q 'f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);' "$S/P2pController.java" || { echo "FAIL p2p: Wi-Fi state not watched"; capbad=1; }
grep -q 'if (in >= 0) main.postDelayed(capCheck, in + 100);' "$S/P2pController.java" || { echo "FAIL p2p: the proof window is never checked"; capbad=1; }
grep -A3 '    void stop() {' "$S/P2pController.java" | grep -q 'cap.wifi(false, now());' || { echo "FAIL p2p: stopped time counts as proof window"; capbad=1; }
grep -q 'ControlGate.apply(this, P2pCapability.providerOn(verdict));' "$S/MiracastService.java" \
  || { echo "FAIL service: the verdict does not switch the control provider"; capbad=1; }
grep -A3 'if (verdict == P2pVerdict.NO && definitive) {' "$S/MiracastService.java" | grep -q 'main.post(this::stopSelf);' \
  || { echo "FAIL service: no Wi-Fi Direct keeps the service running"; capbad=1; }
grep -q 'ComponentName cn = new ComponentName(c, StateProvider.class);' "$S/ControlGate.java" || { echo "FAIL gate: not the control provider"; capbad=1; }
grep -q 'on ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT' "$S/ControlGate.java" || { echo "FAIL gate: on is not the manifest default"; capbad=1; }
grep -q 'for (boolean lowLatency : LOW_LATENCY_FIRST) {' "$S/VideoRenderer.java" \
  && grep -q 'LOW_LATENCY_FIRST = {true, false};' "$S/VideoRenderer.java" || { echo "FAIL video: no start without low-latency"; capbad=1; }
grep -A1 '        if (lowLatency) {' "$S/VideoRenderer.java" | grep -q 'KEY_LOW_LATENCY, 1' || { echo "FAIL video: low-latency key set on every try"; capbad=1; }
[ $capbad = 0 ] && echo "capability wiring: ok"
[ $capbad = 0 ] || bad=1
exit $bad
