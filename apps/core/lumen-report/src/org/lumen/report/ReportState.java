// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

/** The screens of the manual report; QUEUED = saved, the projector sends it when the server can be reached. */
enum ReportState { IDLE, COLLECTING, UPLOADING, DONE, QUEUED, FAILED }
