// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.vcontrol;

/** One message being answered: what came in, from whom, and the TCP port while it listens (stock Params). */
final class Exchange {
    final Request request;
    final Peer peer;
    /** The listening TCP port, null while the TCP input is down. */
    final Integer tcpPort;

    Exchange(Request request, Peer peer, Integer tcpPort) {
        this.request = request;
        this.peer = peer;
        this.tcpPort = tcpPort;
    }

    /** Stock moduleControl: a blank msgid answers as "0". */
    String msgid() {
        String m = request.msgid;
        return m == null || m.trim().isEmpty() ? "0" : m;
    }

    /** Stock Params in Gson order: agreementType, isTcp, msgId, phoneIp, port (port only while TCP listens). */
    Json.Obj agreement() {
        return new Json.Obj()
                .put("agreementType", peer.tcp ? AgreementType.TCP : AgreementType.UDP)
                .put("isTcp", tcpPort != null)
                .put("msgId", msgid())
                .put("phoneIp", peer.address)
                .put("port", tcpPort);
    }
}
