package androidx.lifecycle;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class Lifecycle {
    public enum Event { ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY }
}
