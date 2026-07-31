package cafe.bluearchive.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Locale;

public class InstallerPreferencesTest {
    @Test
    public void themeModeIndexMappingUsesSystemFallback() {
        assertEquals(0, InstallerPreferences.themeModeToIndex(InstallerPreferences.THEME_SYSTEM));
        assertEquals(1, InstallerPreferences.themeModeToIndex(InstallerPreferences.THEME_DARK));
        assertEquals(2, InstallerPreferences.themeModeToIndex(InstallerPreferences.THEME_LIGHT));
        assertEquals(0, InstallerPreferences.themeModeToIndex("unexpected"));

        assertEquals(InstallerPreferences.THEME_SYSTEM, InstallerPreferences.indexToThemeMode(0));
        assertEquals(InstallerPreferences.THEME_DARK, InstallerPreferences.indexToThemeMode(1));
        assertEquals(InstallerPreferences.THEME_LIGHT, InstallerPreferences.indexToThemeMode(2));
        assertEquals(InstallerPreferences.THEME_SYSTEM, InstallerPreferences.indexToThemeMode(99));
    }

    @Test
    public void languageModeIndexMappingUsesSystemFallback() {
        assertEquals(0, InstallerPreferences.languageModeToIndex(InstallerPreferences.LANGUAGE_SYSTEM));
        assertEquals(1, InstallerPreferences.languageModeToIndex(InstallerPreferences.LANGUAGE_ZH_HANS));
        assertEquals(2, InstallerPreferences.languageModeToIndex(InstallerPreferences.LANGUAGE_ZH_HANT));
        assertEquals(3, InstallerPreferences.languageModeToIndex(InstallerPreferences.LANGUAGE_EN));
        assertEquals(0, InstallerPreferences.languageModeToIndex("unexpected"));

        assertEquals(InstallerPreferences.LANGUAGE_SYSTEM, InstallerPreferences.indexToLanguageMode(0));
        assertEquals(InstallerPreferences.LANGUAGE_ZH_HANS, InstallerPreferences.indexToLanguageMode(1));
        assertEquals(InstallerPreferences.LANGUAGE_ZH_HANT, InstallerPreferences.indexToLanguageMode(2));
        assertEquals(InstallerPreferences.LANGUAGE_EN, InstallerPreferences.indexToLanguageMode(3));
        assertEquals(InstallerPreferences.LANGUAGE_SYSTEM, InstallerPreferences.indexToLanguageMode(99));
    }

    @Test
    public void localeForLanguageModeMapsSupportedLanguages() {
        assertEquals(Locale.SIMPLIFIED_CHINESE,
                InstallerPreferences.localeForLanguageMode(InstallerPreferences.LANGUAGE_ZH_HANS));
        assertEquals(Locale.TRADITIONAL_CHINESE,
                InstallerPreferences.localeForLanguageMode(InstallerPreferences.LANGUAGE_ZH_HANT));
        assertEquals(Locale.ENGLISH,
                InstallerPreferences.localeForLanguageMode(InstallerPreferences.LANGUAGE_EN));
        assertNull(InstallerPreferences.localeForLanguageMode(InstallerPreferences.LANGUAGE_SYSTEM));
        assertNull(InstallerPreferences.localeForLanguageMode("unexpected"));
    }
}
