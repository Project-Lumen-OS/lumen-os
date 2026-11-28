package androidx.lifecycle;

import android.app.Application;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class ViewModelProvider {
    public interface Factory {
    }

    public static class NewInstanceFactory implements Factory {
    }

    public static class AndroidViewModelFactory extends NewInstanceFactory {
        public static AndroidViewModelFactory getInstance(Application application) { return null; }
    }

    public ViewModelProvider(ViewModelStoreOwner owner, Factory factory) {}

    public <T extends ViewModel> T get(Class<T> modelClass) { return null; }
}
