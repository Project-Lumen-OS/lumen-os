package androidx.lifecycle;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class LiveData<T> {
    public void observe(LifecycleOwner owner, Observer<? super T> observer) {}
}
