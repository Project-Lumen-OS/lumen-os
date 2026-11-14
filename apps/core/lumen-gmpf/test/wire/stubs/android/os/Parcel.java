package android.os;

import java.io.ByteArrayOutputStream;

/** Little-endian int32 words as the Java Parcel lays them out; a reply is either scripted words or generated. */
public final class Parcel {
    private byte[] d = new byte[64];
    private int pos, size;
    String descriptor = "";
    private int payloadStart;
    private boolean gen;

    public static Parcel obtain() { return new Parcel(); }
    public void recycle() { }

    private void grow(int n) { if (pos + n > d.length) d = java.util.Arrays.copyOf(d, Math.max(d.length * 2, pos + n + 16)); }
    private void raw(int v) { grow(4); for (int i = 0; i < 4; i++) d[pos + i] = (byte) (v >> (8 * i)); pos += 4; if (pos > size) size = pos; }
    private int rawRead() {
        if (gen && pos + 4 > size) { int p = pos; pos = size; raw(Wire.nextInt()); pos = p; }
        if (pos + 4 > size) throw new IllegalStateException("read past the end");
        int v = 0; for (int i = 3; i >= 0; i--) v = (v << 8) | (d[pos + i] & 0xff); pos += 4; return v;
    }

    public void writeInterfaceToken(String s) { descriptor = s; payloadStart = pos; }
    public void enforceInterface(String s) { }
    public void enforceNoDataAvail() { }
    public void writeInt(int v) { raw(v); }
    public void writeByte(byte v) { raw(v); }
    public void writeBoolean(boolean v) { raw(v ? 1 : 0); }
    public void writeFloat(float v) { raw(Float.floatToIntBits(v)); }
    public void writeString(String s) {
        if (s == null) { raw(-1); return; }
        raw(s.length());
        for (int i = 0; i <= s.length(); i += 2) {
            int a = i < s.length() ? s.charAt(i) : 0, b = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            raw(a | (b << 16));
        }
    }
    public void writeByteArray(byte[] b) {
        if (b == null) { raw(-1); return; }
        raw(b.length);
        for (int i = 0; i < b.length; i += 4) {
            int v = 0; for (int k = 0; k < 4 && i + k < b.length; k++) v |= (b[i + k] & 0xff) << (8 * k);
            raw(v);
        }
    }
    public void writeStrongBinder(IBinder b) { Wire.lastBinder = b; raw(b == null ? 0 : 0x62696e64); }
    public void writeNoException() { raw(0); }

    public int readInt() { return rawRead(); }
    public byte readByte() { return (byte) rawRead(); }
    public boolean readBoolean() { return rawRead() != 0; }
    public float readFloat() { return Float.intBitsToFloat(rawRead()); }
    public String readString() {
        int n = rawRead();
        if (n < 0) return null;
        if (gen) return "gen" + n;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i <= n; i += 2) { int w = rawRead(); if (i < n) s.append((char) (w & 0xffff)); if (i + 1 < n) s.append((char) (w >>> 16)); }
        return s.toString();
    }
    public void readByteArray(byte[] dst) {
        int n = rawRead();
        if (n != dst.length) throw new RuntimeException("bad array lengths");
        for (int i = 0; i < n; i += 4) { int w = rawRead(); for (int k = 0; k < 4 && i + k < n; k++) dst[i + k] = (byte) (w >> (8 * k)); }
    }
    public void readException() { int c = rawRead(); if (c != 0) throw new SecurityException("exception code " + c); }

    public int dataPosition() { return pos; }
    public void setDataPosition(int p) { if (gen) while (size < p) { int q = pos; pos = size; raw(Wire.nextInt()); pos = q; } pos = p; }
    public int dataSize() { return gen ? Integer.MAX_VALUE / 2 : size; }
    public int dataAvail() { return gen ? Integer.MAX_VALUE / 2 : size - pos; }

    /** Reply after the status word: scripted words first, then the seed's generator. */
    void generate() { gen = true; pos = 0; }

    String payload() {
        StringBuilder s = new StringBuilder();
        for (int i = payloadStart; i + 3 < size; i += 4) {
            int v = 0; for (int k = 3; k >= 0; k--) v = (v << 8) | (d[i + k] & 0xff);
            if (s.length() > 0) s.append(' ');
            s.append(v);
        }
        return s.toString();
    }
}
