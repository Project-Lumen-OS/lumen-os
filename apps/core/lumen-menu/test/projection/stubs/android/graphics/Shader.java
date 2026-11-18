package android.graphics;

/** Host stub: a shader is a Java2D paint. */
public class Shader {
    public enum TileMode { CLAMP, REPEAT, MIRROR }

    java.awt.Paint awt() { return null; }

    static java.awt.Color color(int argb) { return new java.awt.Color(argb, true); }
}
