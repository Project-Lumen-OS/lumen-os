package android.content;

/** Host stub: only what ColourVision touches. */
public class Context {
    private final ContentResolver resolver = new ContentResolver();

    public ContentResolver getContentResolver() { return resolver; }
}
