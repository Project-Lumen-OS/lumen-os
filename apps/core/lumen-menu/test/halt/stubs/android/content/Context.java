// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;

/** Host stub: the one system service Halt asks for. */
public class Context {
    private final android.os.PowerManager pm = new android.os.PowerManager();

    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> c) { return c == android.os.PowerManager.class ? (T) pm : null; }
}
