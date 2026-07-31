package cafe.bluearchive.installer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class InstallerPreferences {
    static final String PREFS_NAME = "installer_state";
    static final String PREF_THEME_MODE = "theme_mode";
    static final String PREF_LANGUAGE_MODE = "language_mode";

    static final String THEME_SYSTEM = "system";
    static final String THEME_DARK = "dark";
    static final String THEME_LIGHT = "light";

    static final String LANGUAGE_SYSTEM = "system";
    static final String LANGUAGE_ZH_HANS = "zh-Hans";
    static final String LANGUAGE_ZH_HANT = "zh-Hant";
    static final String LANGUAGE_EN = "en";

    private InstallerPreferences() { }

    static SharedPreferences sharedPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static String themeMode(Context context) {
        return sharedPreferences(context).getString(PREF_THEME_MODE, THEME_SYSTEM);
    }

    static String languageMode(Context context) {
        return sharedPreferences(context).getString(PREF_LANGUAGE_MODE, LANGUAGE_SYSTEM);
    }

    static void save(Context context, String key, String value) {
        sharedPreferences(context).edit().putString(key, value).apply();
    }

    static boolean isStoredValue(Context context, String key, String value) {
        return value.equals(sharedPreferences(context).getString(key, null));
    }

    static int themeModeToIndex(String value) {
        if (THEME_DARK.equals(value)) return 1;
        if (THEME_LIGHT.equals(value)) return 2;
        return 0;
    }

    static String indexToThemeMode(int index) {
        if (index == 1) return THEME_DARK;
        if (index == 2) return THEME_LIGHT;
        return THEME_SYSTEM;
    }

    static int languageModeToIndex(String value) {
        if (LANGUAGE_ZH_HANS.equals(value)) return 1;
        if (LANGUAGE_ZH_HANT.equals(value)) return 2;
        if (LANGUAGE_EN.equals(value)) return 3;
        return 0;
    }

    static String indexToLanguageMode(int index) {
        if (index == 1) return LANGUAGE_ZH_HANS;
        if (index == 2) return LANGUAGE_ZH_HANT;
        if (index == 3) return LANGUAGE_EN;
        return LANGUAGE_SYSTEM;
    }

    static Locale localeForLanguageMode(String languageMode) {
        if (LANGUAGE_ZH_HANS.equals(languageMode)) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if (LANGUAGE_ZH_HANT.equals(languageMode)) {
            return Locale.TRADITIONAL_CHINESE;
        }
        if (LANGUAGE_EN.equals(languageMode)) {
            return Locale.ENGLISH;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    static Context applyUserConfiguration(Context base) {
        String themeMode = themeMode(base);
        String languageMode = languageMode(base);

        Configuration config = new Configuration(base.getResources().getConfiguration());

        if (THEME_DARK.equals(themeMode)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;
        } else if (THEME_LIGHT.equals(themeMode)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_NO;
        }

        Locale locale = localeForLanguageMode(languageMode);
        if (locale != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList locales = new LocaleList(locale);
                LocaleList.setDefault(locales);
                config.setLocale(locale);
                config.setLocales(locales);
            } else {
                Locale.setDefault(locale);
                config.locale = locale;
            }
        }

        base.getResources().updateConfiguration(config, base.getResources().getDisplayMetrics());
        return base.createConfigurationContext(config);
    }
}
