package cafe.bluearchive.installer;

/**
 * Supported installation methods.
 * <p>
 * {@link #SYSTEM} uses the standard {@link android.content.pm.PackageInstaller} API.
 * {@link #SHIZUKU} uses Shizuku to run {@code pm} commands with ADB/root identity.
 * {@link #ROOT} uses {@code su} via libsu to run {@code pm} commands directly.
 */
public enum InstallMode {
    SYSTEM,
    SHIZUKU,
    ROOT
}
