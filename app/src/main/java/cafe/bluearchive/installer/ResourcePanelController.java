package cafe.bluearchive.installer;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ResourcePanelController {
    private final InstallerActivity activity;
    private final View root;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ResourcePanelUidService uidService;
    private final ResourcePanelService service;
    private final Map<ResourcePanelModels.ResourceCode, ItemViews> itemViews = new EnumMap<>(ResourcePanelModels.ResourceCode.class);
    private final Map<ResourcePanelModels.ResourceCode, ResourcePanelModels.Item> items = new EnumMap<>(ResourcePanelModels.ResourceCode.class);

    private TextView uidSummary;
    private RadioGroup uidSourceGroup;
    private RadioButton uidSourceAuto;
    private RadioButton uidSourceCustom;
    private TextInputLayout uidInputLayout;
    private TextInputEditText uidInput;
    private MaterialButton uidSaveButton;
    private TextView messageView;
    private ProgressBar progressBar;
    private MaterialButton refreshButton;
    private MaterialButton saveButton;

    private String effectiveUid = "";
    private String uidSource = ResourcePanelUidService.SOURCE_AUTO;
    private boolean busy;
    private boolean loadedOnce;
    private boolean destroyed;
    private int generation;

    ResourcePanelController(InstallerActivity activity, View root) {
        this.activity = activity;
        this.root = root;
        uidService = new ResourcePanelUidService(activity);
        service = new ResourcePanelService(new ResourcePanelApiClient(), executor);
    }

    void bind() {
        if (root == null) return;
        destroyed = false;
        uidSummary = root.findViewById(R.id.resourcePanelUidSummary);
        uidSourceGroup = root.findViewById(R.id.resourcePanelUidSourceGroup);
        uidSourceAuto = root.findViewById(R.id.resourcePanelUidSourceAuto);
        uidSourceCustom = root.findViewById(R.id.resourcePanelUidSourceCustom);
        uidInputLayout = root.findViewById(R.id.resourcePanelUidInputLayout);
        uidInput = root.findViewById(R.id.resourcePanelUidInput);
        uidSaveButton = root.findViewById(R.id.resourcePanelUidSaveButton);
        messageView = root.findViewById(R.id.resourcePanelMessage);
        progressBar = root.findViewById(R.id.resourcePanelProgress);
        refreshButton = root.findViewById(R.id.resourcePanelRefreshButton);
        saveButton = root.findViewById(R.id.resourcePanelSaveButton);

        bindItem(ResourcePanelModels.ResourceCode.TEXT, root.findViewById(R.id.resourcePanelTextItem));
        bindItem(ResourcePanelModels.ResourceCode.VOICE, root.findViewById(R.id.resourcePanelVoiceItem));
        bindItem(ResourcePanelModels.ResourceCode.MEDIA, root.findViewById(R.id.resourcePanelMediaItem));

        uidSourceGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean custom = checkedId == R.id.resourcePanelUidSourceCustom;
            setCustomUidVisible(custom);
            if (custom) {
                uidService.saveSource(ResourcePanelUidService.SOURCE_CUSTOM);
            } else {
                uidService.saveSource(ResourcePanelUidService.SOURCE_AUTO);
            }
            resolveUidAndMaybeLoad(true);
        });
        uidSaveButton.setOnClickListener(view -> saveManualUid());
        refreshButton.setOnClickListener(view -> resolveUidAndMaybeLoad(true));
        saveButton.setOnClickListener(view -> save());
        renderInitialItems();
        renderStoredUid(false);
    }

    void onShown() {
        if (!loadedOnce) {
            resolveUidAndMaybeLoad(false);
        }
    }

    void destroy() {
        destroyed = true;
        generation++;
        executor.shutdownNow();
    }

    private void bindItem(ResourcePanelModels.ResourceCode code, View itemRoot) {
        if (itemRoot == null) return;
        ItemViews views = new ItemViews(itemRoot);
        views.title.setText(code.titleResId);
        views.description.setText(code.descriptionResId);
        views.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ResourcePanelModels.Item old = items.get(code);
            if (old == null) return;
            items.put(code, new ResourcePanelModels.Item(
                    code, old.officialVersion, old.localizedVersion, isChecked, old.state, old.error));
            updateButtons();
        });
        itemViews.put(code, views);
    }

    private void renderInitialItems() {
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            ResourcePanelModels.Item item = new ResourcePanelModels.Item(
                    code, null, null, false, ResourcePanelModels.ItemState.LOADING, null);
            items.put(code, item);
            renderItem(item);
        }
        updateButtons();
    }

    private void resolveUidAndMaybeLoad(boolean force) {
        int requestGeneration = ++generation;
        busy = true;
        showMessage(null);
        setProgressVisible(true);
        updateButtons();

        executor.execute(() -> {
            ResourcePanelUidService.UidResult result = uidService.resolve();
            post(requestGeneration, () -> {
                applyUidResult(result, true);
                busy = false;
                setProgressVisible(false);
                if (result.hasValidUid()) {
                    load(force);
                } else {
                    loadedOnce = true;
                    showMessage(activity.getString(R.string.resource_panel_uid_missing));
                    updateButtons();
                }
            });
        });
    }

    private void renderStoredUid(boolean updateSourceButtons) {
        String source = InstallerPreferences.resourcePanelUidSource(activity);
        String manualUid = ResourcePanelUidService.sanitize(InstallerPreferences.resourcePanelUid(activity));
        ResourcePanelUidService.UidResult result = ResourcePanelUidService.isValid(manualUid)
                ? new ResourcePanelUidService.UidResult(manualUid, source, ResourcePanelUidService.SourceDetail.SAVED)
                : new ResourcePanelUidService.UidResult("", source, ResourcePanelUidService.SourceDetail.NONE);
        applyUidResult(result, updateSourceButtons);
    }

    private void applyUidResult(ResourcePanelUidService.UidResult result, boolean updateSourceButtons) {
        effectiveUid = result.uid;
        uidSource = result.source;
        if (uidInput != null) {
            uidInput.setText(InstallerPreferences.resourcePanelUid(activity));
        }
        if (updateSourceButtons && uidSourceGroup != null) {
            uidSourceGroup.setOnCheckedChangeListener(null);
            uidSourceGroup.check(ResourcePanelUidService.SOURCE_CUSTOM.equals(uidSource)
                    ? R.id.resourcePanelUidSourceCustom
                    : R.id.resourcePanelUidSourceAuto);
            uidSourceGroup.setOnCheckedChangeListener((group, checkedId) -> {
                boolean custom = checkedId == R.id.resourcePanelUidSourceCustom;
                setCustomUidVisible(custom);
                if (custom) {
                    uidService.saveSource(ResourcePanelUidService.SOURCE_CUSTOM);
                } else {
                    uidService.saveSource(ResourcePanelUidService.SOURCE_AUTO);
                }
                resolveUidAndMaybeLoad(true);
            });
        } else {
            if (uidSourceAuto != null) uidSourceAuto.setChecked(ResourcePanelUidService.SOURCE_AUTO.equals(uidSource));
            if (uidSourceCustom != null) uidSourceCustom.setChecked(ResourcePanelUidService.SOURCE_CUSTOM.equals(uidSource));
        }
        setCustomUidVisible(ResourcePanelUidService.SOURCE_CUSTOM.equals(uidSource));
        String sourceText = sourceText(result.detail);
        uidSummary.setText(result.hasValidUid()
                ? activity.getString(R.string.resource_panel_uid_summary, result.uid, sourceText)
                : activity.getString(R.string.resource_panel_uid_missing));
    }

    private void setCustomUidVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (uidInputLayout != null) uidInputLayout.setVisibility(visibility);
        if (uidSaveButton != null) uidSaveButton.setVisibility(visibility);
        if (!visible && uidInputLayout != null) uidInputLayout.setError(null);
    }

    private String sourceText(ResourcePanelUidService.SourceDetail detail) {
        switch (detail) {
            case DIRECT_FILE: return activity.getString(R.string.resource_panel_uid_source_cookie);
            case SHIZUKU: return activity.getString(R.string.resource_panel_uid_source_shizuku);
            case ROOT: return activity.getString(R.string.resource_panel_uid_source_root);
            default: return activity.getString(R.string.resource_panel_uid_source_saved);
        }
    }

    private void saveManualUid() {
        String uid = ResourcePanelUidService.sanitize(uidInput == null ? "" : String.valueOf(uidInput.getText()));
        if (!ResourcePanelUidService.isValid(uid)) {
            uidInputLayout.setError(activity.getString(R.string.resource_panel_uid_invalid));
            return;
        }
        uidInputLayout.setError(null);
        uidService.saveManualUid(uid);
        uidService.saveSource(ResourcePanelUidService.SOURCE_CUSTOM);
        resolveUidAndMaybeLoad(true);
    }

    private void load(boolean force) {
        loadedOnce = true;
        int requestGeneration = ++generation;
        busy = true;
        showMessage(null);
        setProgressVisible(true);
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            ResourcePanelModels.Item item = new ResourcePanelModels.Item(
                    code, null, null, false, ResourcePanelModels.ItemState.LOADING, null);
            items.put(code, item);
            renderItem(item);
        }
        updateButtons();

        executor.execute(() -> {
            try {
                ResourcePanelModels.LoadResult result = service.load(effectiveUid);
                post(requestGeneration, () -> {
                    busy = false;
                    setProgressVisible(false);
                    items.clear();
                    items.putAll(result.items);
                    for (ResourcePanelModels.Item item : items.values()) {
                        renderItem(item);
                    }
                    if (result.configError != null) {
                        showMessage(activity.getString(R.string.resource_panel_config_failed));
                    }
                    updateButtons();
                });
            } catch (Exception e) {
                post(requestGeneration, () -> {
                    busy = false;
                    setProgressVisible(false);
                    for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
                        ResourcePanelModels.Item item = new ResourcePanelModels.Item(
                                code, null, null, false, ResourcePanelModels.ItemState.FAILED, e.getMessage());
                        items.put(code, item);
                        renderItem(item);
                    }
                    showMessage(activity.getString(R.string.resource_panel_load_failed));
                    updateButtons();
                });
            }
        });
    }

    private void save() {
        if (!canSave()) return;
        int requestGeneration = ++generation;
        busy = true;
        showMessage(null);
        setProgressVisible(true);
        updateButtons();
        executor.execute(() -> {
            try {
                service.save(effectiveUid, items);
                post(requestGeneration, () -> {
                    busy = false;
                    setProgressVisible(false);
                    Toast.makeText(activity, R.string.resource_panel_saved, Toast.LENGTH_LONG).show();
                    updateButtons();
                });
            } catch (Exception e) {
                post(requestGeneration, () -> {
                    busy = false;
                    setProgressVisible(false);
                    showMessage(activity.getString(R.string.resource_panel_save_failed));
                    updateButtons();
                });
            }
        });
    }

    private boolean canSave() {
        if (busy || !ResourcePanelUidService.isValid(effectiveUid)) return false;
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            ResourcePanelModels.Item item = items.get(code);
            if (item == null || !item.isOperable()) return false;
        }
        return true;
    }

    private void updateButtons() {
        boolean enabled = !busy;
        uidSourceAuto.setEnabled(enabled);
        uidSourceCustom.setEnabled(enabled);
        uidInput.setEnabled(enabled);
        uidSaveButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        saveButton.setEnabled(canSave());
        for (ResourcePanelModels.ResourceCode code : ResourcePanelModels.ResourceCode.values()) {
            ResourcePanelModels.Item item = items.get(code);
            ItemViews views = itemViews.get(code);
            if (views != null && item != null) {
                views.toggle.setEnabled(enabled && item.isOperable());
            }
        }
    }

    private void renderItem(ResourcePanelModels.Item item) {
        ItemViews views = itemViews.get(item.code);
        if (views == null) return;
        views.statusIcon.setImageResource(iconFor(item.state));
        views.status.setText(statusText(item.state));
        views.officialVersion.setText(activity.getString(
                R.string.resource_panel_version_official, item.officialVersion));
        views.localizedVersion.setText(activity.getString(
                R.string.resource_panel_version_localized, item.localizedVersion));
        views.toggle.setOnCheckedChangeListener(null);
        views.toggle.setChecked(item.enabled);
        views.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ResourcePanelModels.Item old = items.get(item.code);
            if (old == null) return;
            items.put(item.code, new ResourcePanelModels.Item(
                    item.code, old.officialVersion, old.localizedVersion, isChecked, old.state, old.error));
            updateButtons();
        });
        views.toggle.setEnabled(!busy && item.isOperable());
    }

    private int iconFor(ResourcePanelModels.ItemState state) {
        switch (state) {
            case READY: return R.drawable.ic_status_success;
            case WAITING: return R.drawable.ic_status_warning;
            case FAILED: return R.drawable.ic_status_error;
            default: return R.drawable.ic_status_info;
        }
    }

    private String statusText(ResourcePanelModels.ItemState state) {
        switch (state) {
            case READY: return activity.getString(R.string.resource_panel_status_ready);
            case WAITING: return activity.getString(R.string.resource_panel_status_waiting);
            case FAILED: return activity.getString(R.string.resource_panel_status_failed);
            default: return activity.getString(R.string.resource_panel_status_loading);
        }
    }

    private void showMessage(String message) {
        if (messageView == null) return;
        if (message == null || message.isEmpty()) {
            messageView.setVisibility(View.GONE);
            messageView.setText(null);
        } else {
            messageView.setText(message);
            messageView.setVisibility(View.VISIBLE);
        }
    }

    private void setProgressVisible(boolean visible) {
        if (progressBar != null) {
            progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void post(int requestGeneration, Runnable runnable) {
        mainHandler.post(() -> {
            if (!destroyed && requestGeneration == generation) {
                runnable.run();
            }
        });
    }

    private static final class ItemViews {
        final ImageView statusIcon;
        final TextView title;
        final TextView description;
        final TextView status;
        final TextView officialVersion;
        final TextView localizedVersion;
        final CompoundButton toggle;

        ItemViews(View root) {
            statusIcon = root.findViewById(R.id.resourceItemStatusIcon);
            title = root.findViewById(R.id.resourceItemTitle);
            description = root.findViewById(R.id.resourceItemDescription);
            status = root.findViewById(R.id.resourceItemStatus);
            officialVersion = root.findViewById(R.id.resourceItemOfficialVersion);
            localizedVersion = root.findViewById(R.id.resourceItemLocalizedVersion);
            toggle = root.findViewById(R.id.resourceItemSwitch);
        }
    }
}
