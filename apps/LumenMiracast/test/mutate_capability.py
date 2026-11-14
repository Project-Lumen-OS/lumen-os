#!/usr/bin/env python3
"""Mutants of the Wi-Fi Direct capability gate (core/P2pCapability, its Android wiring) and the decoder start without
low-latency: each one must turn sh test/run_core.sh red. python3 test/mutate_capability.py (tree runner of
mutate_control.py)"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mutate_control as mc  # noqa: E402

CAP = 'src/org/lumen/miracast/core/P2pCapability.java'
P2P = 'src/org/lumen/miracast/P2pController.java'
SVC = 'src/org/lumen/miracast/MiracastService.java'
GATE = 'src/org/lumen/miracast/ControlGate.java'
VID = 'src/org/lumen/miracast/VideoRenderer.java'

MUTANTS = [
    ('window too short', CAP, 'now - wifiOnSince >= PROOF_WINDOW_MS', 'now - wifiOnSince >= PROOF_WINDOW_MS / 2'),
    ('NO while Wi-Fi is off', CAP, 'verdict == P2pVerdict.UNKNOWN && wifiOnSince >= 0 && now', 'verdict == P2pVerdict.UNKNOWN && now'),
    ('Wi-Fi off keeps the window', CAP, '        if (!on) wifiOnSince = -1;\n', ''),
    ('a second Wi-Fi on restarts the window', CAP, '        else if (wifiOnSince < 0) wifiOnSince = now;', '        else wifiOnSince = now;'),
    ('BUSY counts as unsupported', CAP, 'if (reason == REASON_UNSUPPORTED && verdict', 'if (reason != 0 && verdict'),
    ('unsupported after a proof takes it back', CAP, 'REASON_UNSUPPORTED && verdict != P2pVerdict.YES)', 'REASON_UNSUPPORTED)'),
    ('a window NO is definitive', CAP, "            verdict = P2pVerdict.NO;\n            why = \"Wi-Fi on for \"",
     "            verdict = P2pVerdict.NO;\n            definitive = true;\n            why = \"Wi-Fi on for \""),
    ('a proof does not win over a window NO', CAP, '        if (definitive) return verdict;', '        if (verdict != P2pVerdict.UNKNOWN) return verdict;'),
    ('a definitive NO is proven later', CAP, '        if (definitive) return verdict;\n', ''),
    ('provider left on for NO', CAP, 'if (v == P2pVerdict.NO) return Boolean.FALSE;', 'if (v == P2pVerdict.NO) return null;'),
    ('provider switched for UNKNOWN', CAP, '        return null;\n    }\n}', '        return Boolean.FALSE;\n    }\n}'),
    ('next check ignores the start', CAP, 'Math.max(0, wifiOnSince + PROOF_WINDOW_MS - now)', 'PROOF_WINDOW_MS'),
    ('a successful call is no proof', P2P, '                diag.ok(stage, okDetail);\n                decide(cap.proven(what + " ok"));\n',
     '                diag.ok(stage, okDetail);\n'),
    ('a successful plain call is no proof', P2P, '                Log.i(TAG, what + " ok");\n                decide(cap.proven(what + " ok"));\n',
     '                Log.i(TAG, what + " ok");\n'),
    ('no manager is not a NO', P2P, '            decide(cap.noFramework("android.hardware.wifi.direct missing"));\n', ''),
    ('Wi-Fi state not watched', P2P, '            f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);\n', ''),
    ('stopped time counts as proof window', P2P, '        cap.wifi(false, now());   // a stopped receiver makes no Wi-Fi Direct call: its time proves nothing\n', ''),
    ('proof window never checked', P2P, '        if (in >= 0) main.postDelayed(capCheck, in + 100);\n', ''),
    ('verdict does not switch the provider', SVC, '        ControlGate.apply(this, P2pCapability.providerOn(verdict));\n', ''),
    ('definitive NO keeps running', SVC, '            main.post(this::stopSelf);\n', ''),
    ('gate switches another component', GATE, 'new ComponentName(c, StateProvider.class)', 'new ComponentName(c, SettingsActivity.class)'),
    ('gate on = forced enabled', GATE, 'on ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT', 'on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED'),
    ('decoder only with low-latency', VID, 'LOW_LATENCY_FIRST = {true, false};', 'LOW_LATENCY_FIRST = {true};'),
    ('low-latency key on every try', VID, '        if (lowLatency) {\n            f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);',
     '        {\n            f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);'),
]


def main():
    mc.MUTANTS[:] = MUTANTS
    mc.main()


if __name__ == '__main__':
    main()
