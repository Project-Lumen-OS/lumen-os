package android.os;

public class HwBlob {
    final byte[] b;
    public HwBlob(int size) { b = new byte[size]; }
    private int u(int o, int n) { int v = 0; for (int i = n - 1; i >= 0; i--) v = (v << 8) | (b[o + i] & 0xff); return v; }
    public final byte getInt8(long o) { return b[(int) o]; }
    public final short getInt16(long o) { return (short) u((int) o, 2); }
    public final int getInt32(long o) { return u((int) o, 4); }
    public final float getFloat(long o) { return Float.intBitsToFloat(getInt32(o)); }
    public final boolean getBool(long o) { return b[(int) o] != 0; }
    public final void putInt8(long o, byte v) { b[(int) o] = v; }
    public final void putBool(long o, boolean v) { b[(int) o] = (byte) (v ? 1 : 0); }
    public final void putInt16(long o, short v) { for (int i = 0; i < 2; i++) b[(int) o + i] = (byte) (v >> (8 * i)); }
    public final void putInt32(long o, int v) { for (int i = 0; i < 4; i++) b[(int) o + i] = (byte) (v >> (8 * i)); }
    public final void putInt64(long o, long v) { for (int i = 0; i < 8; i++) b[(int) o + i] = (byte) (v >> (8 * i)); }
    public final void putFloat(long o, float v) { putInt32(o, Float.floatToIntBits(v)); }
    public final void putInt8Array(long o, byte[] a) { System.arraycopy(a, 0, b, (int) o, a.length); }
    public final void putBlob(long o, HwBlob x) { }
    public final long handle() { return 0L; }
    public final void copyToInt8Array(long o, byte[] a, int n) { System.arraycopy(b, (int) o, a, 0, n); }
    public String hex() { return Wire.hex(b, b.length); }
}
