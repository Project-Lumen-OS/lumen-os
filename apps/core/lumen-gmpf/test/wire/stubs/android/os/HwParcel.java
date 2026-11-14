package android.os;

import java.util.ArrayList;

public class HwParcel {
    public static final int STATUS_SUCCESS = 0;
    private final StringBuilder w = new StringBuilder();
    boolean replyMode;

    public HwParcel() { }
    private void put(String s) { if (w.length() > 0) w.append(' '); w.append(s); }
    String dump() { return w.toString(); }

    public final void writeInterfaceToken(String d) { put("tok:" + d); }
    public final void writeInt8(byte v) { put("i8:" + v); }
    public final void writeInt16(short v) { put("i16:" + v); }
    public final void writeInt32(int v) { put("i32:" + v); }
    public final void writeInt64(long v) { put("i64:" + v); }
    public final void writeBool(boolean v) { put("b:" + v); }
    public final void writeFloat(float v) { put("f:" + Float.floatToIntBits(v)); }
    public final void writeString(String v) { put("s:" + v); }
    public final void writeStrongBinder(IHwBinder b) { Wire.lastBinder = b; put("binder:" + (b == null ? "null" : "cb")); }
    public final void writeBuffer(HwBlob b) { put("blob:" + b.hex()); }
    public final void writeStatus(int s) { put("status:" + s); }
    public final void writeStringVector(ArrayList<String> v) { put("sv:" + v); }
    public final void writeNativeHandle(Object h) { put("nh"); }

    public final void verifySuccess() { }
    public final void enforceInterface(String d) { }
    public final void releaseTemporaryStorage() { }
    public final void release() { }
    public final void send() { Wire.log("REPLY " + dump()); }

    public final byte readInt8() { return (byte) Wire.nextInt(); }
    public final short readInt16() { return (short) Wire.nextInt(); }
    public final int readInt32() { return Wire.nextInt(); }
    public final boolean readBool() { return Wire.nextInt() != 0; }
    public final float readFloat() { return Wire.nextFloat(); }
    public final String readString() { Object o = Wire.next("str"); return String.valueOf(o); }
    public final ArrayList<String> readStringVector() { return new ArrayList<>(); }
    public final Object readNativeHandle() { return null; }
    public final HwBlob readEmbeddedBuffer(long size, long parent, long offset, boolean nullable) { return readBuffer(size); }
    public final HwBlob readBuffer(long size) {
        Object o = Wire.next("blob");
        HwBlob b = new HwBlob((int) size);
        if (o instanceof byte[]) {
            byte[] a = (byte[]) o;
            if (a.length != size) throw new java.util.NoSuchElementException("buffer of " + a.length + ", expected " + size);
            System.arraycopy(a, 0, b.b, 0, a.length);
        } else {
            int v = ((Number) o).intValue();
            for (int i = 0; i + 3 < size; i += 4) { b.putInt32(i, v); v = v * 7 + 3; }
            for (int i = (int) size & ~3; i < size; i++) b.b[i] = (byte) v;
        }
        return b;
    }
}
