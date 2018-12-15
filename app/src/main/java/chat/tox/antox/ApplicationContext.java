package chat.tox.antox;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDexApplication;
import android.arch.lifecycle.DefaultLifecycleObserver;
public class ApplicationContext extends MultiDexApplication implements  DefaultLifecycleObserver  {
    private volatile boolean isAppVisible;
    public static ApplicationContext getInstance(Context context) {
        return (ApplicationContext)context.getApplicationContext();
    }
    @Override
    public void onCreate() {
        super.onCreate();
        LogToFile.init(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }
    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        isAppVisible = true;
    }
    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isAppVisible = false;
    }
    public boolean isAppVisible() {
        return isAppVisible;
    }

}
