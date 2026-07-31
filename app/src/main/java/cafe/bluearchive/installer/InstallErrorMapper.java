package cafe.bluearchive.installer;

import android.content.Context;
import android.content.pm.PackageInstaller;

final class InstallErrorMapper {
    private InstallErrorMapper() { }

    static String parse(Context context, int status, String rawMessage, int legacyStatus) {
        switch (legacyStatus) {
            case -1:  return context.getString(R.string.error_already_exists);
            case -2:  return context.getString(R.string.error_invalid_apk);
            case -4:  return context.getString(R.string.error_insufficient_storage);
            case -7:  return context.getString(R.string.error_update_incompatible);
            case -25: return context.getString(R.string.error_version_downgrade);
            case -28: return context.getString(R.string.error_missing_split);
            case -113: return context.getString(R.string.error_no_matching_abis);
            case -118: return context.getString(R.string.error_bad_signature);
        }

        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return context.getString(R.string.error_aborted);
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return context.getString(R.string.error_blocked,
                        context.getString(R.string.error_blocked_device));
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return context.getString(R.string.error_conflict);
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return context.getString(R.string.error_incompatible);
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return context.getString(R.string.error_invalid_apk);
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return context.getString(R.string.error_storage);
        }

        if (rawMessage != null && !rawMessage.isEmpty()) {
            return rawMessage;
        }
        return context.getString(R.string.error_generic, status);
    }
}
