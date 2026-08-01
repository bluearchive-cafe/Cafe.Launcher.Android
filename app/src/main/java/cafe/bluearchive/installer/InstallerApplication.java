package cafe.bluearchive.installer;

import android.app.Application;

public final class InstallerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        InstallerPreferences.applyGlobalThemeMode(this);
    }
}
