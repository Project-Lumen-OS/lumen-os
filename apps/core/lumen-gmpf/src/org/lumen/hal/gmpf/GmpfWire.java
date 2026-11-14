package org.lumen.hal.gmpf;

import android.os.RemoteException;

/**
 * The transport of one projector's vendor HAL: typed calls keyed by that HAL's transaction key (K: a HIDL code, an
 * AIDL Call). Each method is one two-way transaction with exactly these argument and reply types, laid out as the
 * stock proxies of that transport do. A failure inside the transaction is a RemoteException (not delivered); a reply
 * that does not parse, a false return of a struct call or a struct its Check refuses is a ReplyException (delivered:
 * never repeat one-shot calls). Calls on the main thread are refused before anything is sent.
 */
public interface GmpfWire<K> {
    /** Refuses a reply struct (IllegalStateException): the call fails as a bad reply. */
    interface Check<T> { void check(T v); }

    /** The vendor code of key, for messages. */
    int code(K k);

    /** The interface and code of key (AIDL: and its stock method), for messages. */
    String name(K k);

    /** Connects now when this interface connects on first use; RemoteException when it cannot. */
    void ready() throws RemoteException;

    boolean connected();

    /** Forgets the connection after a vendor death (no call). */
    void drop();

    void u8(K k, int v) throws RemoteException;
    boolean u8RetBool(K k, int v) throws RemoteException;
    void i32(K k, int v) throws RemoteException;
    boolean i32RetBool(K k, int v) throws RemoteException;
    int i32RetI32(K k, int v) throws RemoteException;
    byte i32RetI8(K k, int v) throws RemoteException;
    boolean i32I32RetBool(K k, int a, int b) throws RemoteException;
    int i32I32RetI32(K k, int a, int b) throws RemoteException;
    boolean i32BoolRetBool(K k, int a, boolean b) throws RemoteException;
    int i32BoolRetI32(K k, int a, boolean b) throws RemoteException;
    boolean i32F32RetBool(K k, int a, float b) throws RemoteException;
    boolean i32x3RetBool(K k, int a, int b, int c) throws RemoteException;
    int i32x4RetI32(K k, int a, int b, int c, int d) throws RemoteException;
    void bool(K k, boolean v) throws RemoteException;
    boolean boolRetBool(K k, boolean v) throws RemoteException;
    int boolRetI32(K k, boolean v) throws RemoteException;
    boolean boolI32RetBool(K k, boolean a, int b) throws RemoteException;
    int boolI32I32RetI32(K k, boolean a, int b, int c) throws RemoteException;
    boolean retBool(K k) throws RemoteException;
    int retI32(K k) throws RemoteException;
    /** A byte reply as 0..255. */
    int retU8(K k) throws RemoteException;
    byte retI8(K k) throws RemoteException;
    byte strI32I32RetI8(K k, String s, int a, int b) throws RemoteException;
    byte strRetI8(K k, String s) throws RemoteException;

    /** Registers the transport's one focus callback object with the vendor. */
    void focusCallback(K k) throws RemoteException;

    /** An int32 return, then a struct of n int32: {ret, f0 .. fn-1}. */
    int[] i32Struct(K k, int n) throws RemoteException;

    /** A boolean return (false fails), then a struct of n int32. */
    int[] boolStruct(K k, int n) throws RemoteException;

    /** A boolean return (false fails), then a struct of n float32. */
    float[] boolFloats(K k, int n, Check<float[]> check) throws RemoteException;

    /** One int32 argument; a boolean return (false fails), then a struct of n int32. */
    int[] i32BoolStruct(K k, int a, int n, Check<int[]> check) throws RemoteException;

    /** Two int32 arguments; a boolean return (false fails), then n signed bytes. */
    int[] i32I32BoolBytes(K k, int a, int b, int n, Check<int[]> check) throws RemoteException;

    /** Two int32 arguments and a byte array; a boolean return. */
    boolean i32I32BytesRetBool(K k, int a, int b, byte[] v) throws RemoteException;

    /** One int32 argument and a struct of int32; a boolean return. */
    boolean i32StructRetBool(K k, int a, int[] fields) throws RemoteException;

    /** The motion sensor struct: {acc x, y, z, gyro x, y, z, type}. */
    int[] motion(K k) throws RemoteException;
}
