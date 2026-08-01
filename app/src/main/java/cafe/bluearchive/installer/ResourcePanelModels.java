package cafe.bluearchive.installer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

final class ResourcePanelModels {
    static final String MODE_CN = "cn";
    static final String MODE_JP = "jp";
    static final String VERSION_EMPTY = "--";

    enum ResourceCode {
        TEXT("text", R.string.resource_panel_item_text, R.string.resource_panel_item_text_desc),
        VOICE("voice", R.string.resource_panel_item_voice, R.string.resource_panel_item_voice_desc),
        MEDIA("media", R.string.resource_panel_item_media, R.string.resource_panel_item_media_desc);

        final String wireName;
        final int titleResId;
        final int descriptionResId;

        ResourceCode(String wireName, int titleResId, int descriptionResId) {
            this.wireName = wireName;
            this.titleResId = titleResId;
            this.descriptionResId = descriptionResId;
        }
    }

    enum ItemState {
        LOADING,
        READY,
        WAITING,
        FAILED
    }

    static final class VersionPair {
        final String officialVersion;
        final String localizedVersion;

        VersionPair(String officialVersion, String localizedVersion) {
            this.officialVersion = officialVersion;
            this.localizedVersion = localizedVersion;
        }
    }

    static final class Item {
        final ResourceCode code;
        final String officialVersion;
        final String localizedVersion;
        final boolean enabled;
        final ItemState state;
        final String error;

        Item(ResourceCode code, String officialVersion, String localizedVersion,
             boolean enabled, ItemState state, String error) {
            this.code = code;
            this.officialVersion = displayVersion(officialVersion);
            this.localizedVersion = displayVersion(localizedVersion);
            this.enabled = enabled;
            this.state = state;
            this.error = error;
        }

        boolean isOperable() {
            return state == ItemState.READY || state == ItemState.WAITING;
        }

        String mode() {
            return enabled ? MODE_CN : MODE_JP;
        }
    }

    static final class LoadResult {
        final Map<ResourceCode, Item> items;
        final String configError;

        LoadResult(Map<ResourceCode, Item> items, String configError) {
            this.items = Collections.unmodifiableMap(new EnumMap<>(items));
            this.configError = configError;
        }
    }

    static String displayVersion(String version) {
        return version == null || version.trim().isEmpty() ? VERSION_EMPTY : version.trim();
    }

    static ItemState stateFor(String officialVersion, String localizedVersion) {
        return displayVersion(officialVersion).equals(displayVersion(localizedVersion))
                ? ItemState.READY
                : ItemState.WAITING;
    }

    static boolean enabledForMode(String mode) {
        return MODE_CN.equals(mode);
    }

    private ResourcePanelModels() { }
}
