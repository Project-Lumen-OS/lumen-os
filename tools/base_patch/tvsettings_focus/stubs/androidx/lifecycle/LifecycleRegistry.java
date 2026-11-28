package androidx.lifecycle;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class LifecycleRegistry extends Lifecycle {
    public LifecycleRegistry(LifecycleOwner provider) {}
    public void handleLifecycleEvent(Lifecycle.Event event) {}
}
