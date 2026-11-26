#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the Wi-Fi Direct link rule (P2pHealth), its status and its Android wiring: each one must turn
sh test/run_core.sh red. python3 test/mutate_health.py (uses the tree runner of mutate_control.py)"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mutate_control as mc  # noqa: E402

H = 'src/org/lumen/miracast/core/P2pHealth.java'
CTL = 'src/org/lumen/miracast/core/Control.java'
P2P = 'src/org/lumen/miracast/P2pController.java'
PROV = 'src/org/lumen/miracast/StateProvider.java'
ST = 'src/org/lumen/miracast/StatusText.java'
RU = 'res/values-ru/strings.xml'

MUTANTS = [
    ('BUSY restarts too', H, '        if (reason == REASON_BUSY) {\n            busy = true;\n            return Action.NONE;\n        }\n', ''),
    ('one ERROR restarts', H, '        if (++errors < ERRORS_TO_RESTART) return Action.NONE;', '        ++errors;'),
    ('no restart budget', H, '        if (restarts.size() >= MAX_RESTARTS) {', '        if (false) {'),
    ('restarts never leave the window', H,
     '        while (!restarts.isEmpty() && now - restarts.peekFirst() >= RESTART_WINDOW_MS) restarts.pollFirst();\n', ''),
    ('errors not in a row', H, '        errors = 0;\n        busy = false;\n        restarting = false;\n        gaveUp = false;',
     '        busy = false;\n        restarting = false;\n        gaveUp = false;'),
    ('a lost accept is not a fault', H, '        connectBroken = true;\n        errors = 0;', '        errors = 0;'),
    ('a success hides a broken connection', H, '        if (connectBroken) return Fault.CONNECT_BROKEN;\n', ''),
    ('restarting is shown as lasting', H, '        return f == Fault.NOT_ANSWERING || f == Fault.CONNECT_BROKEN;', '        return f != Fault.NONE;'),
    ('a fault reads as starting', CTL, '        if (P2pHealth.lasting(P2pHealth.fault(fault))) return ReceiverStatus.FAULT;\n', ''),
    ('a fault wins over Wi-Fi off', CTL,
     '        if ("WIFI_OFF".equals(p2pState)) return ReceiverStatus.NO_WIFI;\n        if (P2pHealth.lasting(P2pHealth.fault(fault))) return ReceiverStatus.FAULT;',
     '        if (P2pHealth.lasting(P2pHealth.fault(fault))) return ReceiverStatus.FAULT;\n        if ("WIFI_OFF".equals(p2pState)) return ReceiverStatus.NO_WIFI;'),
    ('listen is back', P2P, '        mgr.discoverPeers(channel, stageListener("discoverPeers", Diag.Stage.VISIBLE, "discovery"));',
     '        mgr.startListening(channel, stageListener("discoverPeers", Diag.Stage.VISIBLE, "discovery"));'),
    ('a failure misses P2pHealth', P2P, '                decide(cap.callFailed(reason, what));\n                healthFailed(reason, what);\n            }\n        };\n    }\n\n    /** As',
     '                decide(cap.callFailed(reason, what));\n            }\n        };\n    }\n\n    /** As'),
    ('a restart keeps the dead channel', P2P, '                channel.close();\n', ''),
    ('accepted connections not probed', P2P, '            main.postDelayed(acceptProbe, ACCEPT_PROBE_MS);\n', ''),
    ('a lost accept does not restart', P2P, '                act(health.lostAfterAccept(), "after an accepted connection");', '                health.lostAfterAccept();'),
    ('no detail for the casting screen', PROV, '        b.putString("detail", detail != null ? detail : "");\n', ''),
    ('the fault never reaches the status', ST, 's == null ? null : s.fault().name());', 'null);'),
    ('a language misses a cause', RU, '<string name="status_connect_broken">', '<string name="status_connect_brokenx">'),
]


def main():
    mc.MUTANTS[:] = MUTANTS
    mc.main()


if __name__ == '__main__':
    main()
