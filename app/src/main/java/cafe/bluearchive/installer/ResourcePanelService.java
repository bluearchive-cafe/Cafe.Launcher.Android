package cafe.bluearchive.installer;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

final class ResourcePanelService {
    private final ResourcePanelApiClient apiClient;
    private final ExecutorService executor;

    ResourcePanelService(ResourcePanelApiClient apiClient, ExecutorService executor) {
        this.apiClient = apiClient;
        this.executor = executor;
    }

    ResourcePanelModels.LoadResult load(String uid) throws Exception {
        Future<Map<ResourcePanelModels.ResourceCode, ResourcePanelModels.VersionPair>> statusFuture =
                executor.submit(apiClient::fetchStatus);
        Future<Map<ResourcePanelModels.ResourceCode, String>> configFuture =
                executor.submit(() -> apiClient.fetchConfig(uid));

        Map<ResourcePanelModels.ResourceCode, ResourcePanelModels.VersionPair> status = get(statusFuture);
        Map<ResourcePanelModels.ResourceCode, String> config = null;
        String configError = null;
        try {
            config = get(configFuture);
        } catch (Exception e) {
            configError = e.getMessage();
        }
        if (config == null) {
            config = defaultConfig();
        }

        Map<ResourcePanelModels.ResourceCode, ResourcePanelModels.Item> items = new EnumMap<>(ResourcePanelModels.ResourceCode.class);
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            ResourcePanelModels.VersionPair pair = status.get(code);
            String official = pair == null ? null : pair.officialVersion;
            String localized = pair == null ? null : pair.localizedVersion;
            items.put(code, new ResourcePanelModels.Item(
                    code,
                    official,
                    localized,
                    ResourcePanelModels.enabledForMode(config.get(code)),
                    ResourcePanelModels.stateFor(official, localized),
                    null));
        }
        return new ResourcePanelModels.LoadResult(items, configError);
    }

    void save(String uid, Map<ResourcePanelModels.ResourceCode, ResourcePanelModels.Item> items) throws Exception {
        Map<ResourcePanelModels.ResourceCode, String> modes = new EnumMap<>(ResourcePanelModels.ResourceCode.class);
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            ResourcePanelModels.Item item = items.get(code);
            modes.put(code, item == null ? ResourcePanelModels.MODE_JP : item.mode());
        }
        apiClient.saveConfig(uid, modes);
    }

    private static Map<ResourcePanelModels.ResourceCode, String> defaultConfig() {
        Map<ResourcePanelModels.ResourceCode, String> result = new EnumMap<>(ResourcePanelModels.ResourceCode.class);
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            result.put(code, ResourcePanelModels.MODE_JP);
        }
        return result;
    }

    private static <T> T get(Future<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }
}
