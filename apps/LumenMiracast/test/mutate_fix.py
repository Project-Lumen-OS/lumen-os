#!/usr/bin/env python3
"""Mutants of the 1.0.11 receiver fixes (M3 reply, Wi-Fi Direct rules, stage diagnostics, Android wiring): each one
must turn sh test/run_core.sh red. python3 test/mutate_fix.py (uses the tree runner of mutate_control.py)"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mutate_control as mc  # noqa: E402

SESS = 'src/org/lumen/miracast/core/WfdSession.java'
FMT = 'src/org/lumen/miracast/core/WfdFormats.java'
RULES = 'src/org/lumen/miracast/core/P2pRules.java'
DIAG = 'src/org/lumen/miracast/core/Diag.java'
END = 'src/org/lumen/miracast/core/EndReason.java'
P2P = 'src/org/lumen/miracast/P2pController.java'
SVC = 'src/org/lumen/miracast/MiracastService.java'
MIR = 'src/org/lumen/miracast/MirrorActivity.java'

MUTANTS = [
    ('M3 answers "none" for unknown keys (the 1.0.10 reply)', SESS, '            default: return null;\n', '            default: return "none";\n'),
    ('M3 drops the IDR capability', SESS, '            case "wfd_idr_request_capability": return "1";\n', ''),
    ('M3 leaves out content protection', SESS, '            case "wfd_content_protection":\n', ''),
    ('codec entry not 58 chars (AOSP parser)', FMT, '00 0000 0000 00 none none",', '00 0000 0000 00 none  none",'),
    ('framework removal not undone', RULES, '                return !removedByUs;', '                return false;'),
    ('our own removal undone', RULES, '                return !removedByUs;', '                return true;'),
    ('another app\'s wildcard fought', RULES, '            case DETACH_FAILURE:\n                return true;\n            default:\n                return false;',
     '            default:\n                return true;'),
    ('PIN entry gets our card', RULES, 'return wpsSetup != WPS_KEYPAD && wpsSetup != WPS_DISPLAY;', 'return wpsSetup != WPS_DISPLAY;'),
    ('request port ignored', RULES, '        if (fromRequest > 0 && fromRequest < 65536) return fromRequest;\n', ''),
    ('attempt keeps the old stages', DIAG, '        for (Stage s : Stage.values()) if (s.perAttempt()) last.remove(s);\n', ''),
    ('verdict ignores failures', DIAG, '        if (f != null) return "stopped at "', '        if (f == null) return "stopped at "'),
    ('PLAY without RTP counts as fine', END, '            if (!media) return Diag.Stage.MEDIA;\n', ''),
    ('connect failure blamed on RTSP', END, '        if (w.startsWith("connect failed")) return Diag.Stage.RTSP_TCP;\n', ''),
    ('approver re-added only on the old rule', P2P, 'if (P2pRules.reattach(reason, running, removingApprover)) {',
     'if (running && reason != P2pRules.DETACH_REMOVE && reason != P2pRules.DETACH_CLOSE) {'),
    ('request without a scanned peer dropped', P2P, '                dev = new WifiP2pDevice();\n                dev.deviceAddress = config.deviceAddress;\n', ''),
    ('picture under the screensaver', SVC, '            takeFocus();\n            wakeFromScreensaver("picture");\n', '            takeFocus();\n'),
    ('prompt under the screensaver', SVC, '        wakeFromScreensaver("connection request");\n', ''),
    ('a hidden mirror screen ends the session', MIR, '        if (!resumedOnce) {\n            // started under another window',
     '        if (false) {\n            // started under another window'),
]


def main():
    mc.MUTANTS[:] = MUTANTS
    mc.main()


if __name__ == '__main__':
    main()
