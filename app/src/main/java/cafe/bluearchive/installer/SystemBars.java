package cafe.bluearchive.installer;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class SystemBars {
    private SystemBars() {
    }

    static void applyAppBars(Activity activity) {
        Window window = activity.getWindow();
        boolean darkTheme = isDarkTheme(activity);
        window.setStatusBarColor(activity.getColor(R.color.primary_dark));
        window.setNavigationBarColor(activity.getColor(R.color.surface));
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getDecorView().getWindowInsetsController();
            if (controller != null) {
                int appearance = darkTheme
                        ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(appearance,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            } else {
                applyLegacyBarIcons(window, darkTheme);
            }
        } else {
            applyLegacyBarIcons(window, darkTheme);
        }
    }

    static void applyFullscreen(Activity activity) {
        Window window = activity.getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                applyLegacyFullscreen(window);
            }
        } else {
            applyLegacyFullscreen(window);
        }
    }

    static void applyTopAndBottomInsets(Activity activity, View topBar, View bottomContent) {
        View root = activity.getWindow().getDecorView();
        InsetsAwareView topBarInsets = InsetsAwareView.from(topBar, true);
        InsetsAwareView bottomContentInsets = InsetsAwareView.from(bottomContent, false);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (topBarInsets != null) {
                topBarInsets.apply(systemBars.top, 0);
            }
            if (bottomContentInsets != null) {
                bottomContentInsets.apply(0, systemBars.bottom);
            }

            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static boolean isDarkTheme(Activity activity) {
        int nightMode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static void applyLegacyBarIcons(Window window, boolean darkTheme) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (darkTheme && Build.VERSION.SDK_INT >= 23) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (!darkTheme && Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private static void applyLegacyFullscreen(Window window) {
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private static final class InsetsAwareView {
        private final View view;
        private final int paddingLeft;
        private final int paddingTop;
        private final int paddingRight;
        private final int paddingBottom;
        private final int height;
        private final boolean adjustHeight;

        private InsetsAwareView(View view, boolean adjustHeight) {
            this.view = view;
            this.adjustHeight = adjustHeight;
            paddingLeft = view.getPaddingLeft();
            paddingTop = view.getPaddingTop();
            paddingRight = view.getPaddingRight();
            paddingBottom = view.getPaddingBottom();
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            height = layoutParams != null ? layoutParams.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        static InsetsAwareView from(View view, boolean adjustHeight) {
            return view == null ? null : new InsetsAwareView(view, adjustHeight);
        }

        void apply(int topInset, int bottomInset) {
            view.setPadding(paddingLeft, paddingTop + topInset, paddingRight, paddingBottom + bottomInset);

            if (adjustHeight && height >= 0) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.height = height + topInset + bottomInset;
                    view.setLayoutParams(layoutParams);
                }
            }
        }
    }
}
