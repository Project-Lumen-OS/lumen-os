package org.lumen.projector.hal;

/** Receives the vendor focus events (type, value) on a binder thread, after the vendor got its answer: only post. */
public interface FocusEventSink {
    void onFocusEvent(int type, String value);
}
