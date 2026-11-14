package org.lumen.projector.report;

/** Reasons the activity can explain; NETWORK covers DNS, connect, TLS and timeouts. */
enum ReportFailure { NO_NETWORK, NETWORK, RATE_LIMITED, REJECTED, SERVER, COLLECT }
