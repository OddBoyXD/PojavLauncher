package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;

import java.io.File;
import java.util.Locale;

public class CleanUtils {
    public static final String TAG = "CleanUtils";
    public static final String PREF_AUTO_CLEAN = "pref_auto_clean_on_launch";

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public static long deleteRecursively(File file, boolean deleteSelf) {
        if (file == null || !file.exists()) return 0;
        long bytesFreed = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    bytesFreed += deleteRecursively(child, true);
                }
            }
            if (deleteSelf) {
                file.delete();
            }
        } else {
            bytesFreed += file.length();
            file.delete();
        }
        return bytesFreed;
    }

    public static long cleanLogsAndCaches(Context context) {
        long totalFreed = 0;
        try {
            // 1. latestlog.txt & latestcrash.txt
            if (net.kdt.pojavlaunch.Tools.DIR_GAME_HOME != null) {
                File gameHome = new File(net.kdt.pojavlaunch.Tools.DIR_GAME_HOME);
                File latestLog = new File(gameHome, "latestlog.txt");
                if (latestLog.exists()) {
                    totalFreed += latestLog.length();
                    latestLog.delete();
                }
                File latestCrash = new File(gameHome, "latestcrash.txt");
                if (latestCrash.exists()) {
                    totalFreed += latestCrash.length();
                    latestCrash.delete();
                }

                // 2. .cache in game home
                File dotCache = new File(gameHome, ".cache");
                totalFreed += deleteRecursively(dotCache, true);
            }

            // 3. DIR_GAME_NEW logs, crash-reports, .fabric, webcache2
            if (net.kdt.pojavlaunch.Tools.DIR_GAME_NEW != null) {
                File gameNew = new File(net.kdt.pojavlaunch.Tools.DIR_GAME_NEW);

                // logs
                File logsDir = new File(gameNew, "logs");
                totalFreed += deleteRecursively(logsDir, false);

                // crash-reports
                File crashDir = new File(gameNew, "crash-reports");
                totalFreed += deleteRecursively(crashDir, false);

                // .fabric
                File fabricDir = new File(gameNew, ".fabric");
                totalFreed += deleteRecursively(fabricDir, true);

                // webcache2
                File webcacheDir = new File(gameNew, "webcache2");
                totalFreed += deleteRecursively(webcacheDir, true);
            }

            // 4. Android cache directory (DIR_CACHE)
            if (context != null) {
                File cacheDir = context.getCacheDir();
                if (cacheDir != null) {
                    totalFreed += deleteRecursively(cacheDir, false);
                }
                File extCacheDir = context.getExternalCacheDir();
                if (extCacheDir != null) {
                    totalFreed += deleteRecursively(extCacheDir, false);
                }
            }
        } catch (Throwable th) {
            Log.e(TAG, "Error cleaning logs and caches", th);
        }
        Log.i(TAG, "Clean complete. Freed: " + formatBytes(totalFreed));
        return totalFreed;
    }

    public static void onPreLaunch(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs.getBoolean(PREF_AUTO_CLEAN, false)) {
                long freed = cleanLogsAndCaches(context);
                Log.i(TAG, "Auto-cleaned " + formatBytes(freed) + " before game launch.");
            }
        } catch (Throwable th) {
            Log.e(TAG, "Error in onPreLaunch", th);
        }
        performMemoryTrim(context);
    }

    public static void performMemoryTrim(Context context) {
        try {
            net.kdt.pojavlaunch.Tools.releaseRenderersCache();
        } catch (Throwable ignored) {}

        if (context instanceof Activity) {
            try {
                Activity act = (Activity) context;
                Window win = act.getWindow();
                if (win != null) {
                    View decor = win.getDecorView();
                    if (decor != null) {
                        decor.destroyDrawingCache();
                    }
                }
            } catch (Throwable ignored) {}
        }

        try {
            System.gc();
            Runtime.getRuntime().gc();
        } catch (Throwable ignored) {}

        Log.i(TAG, "RAM auto-trim and GC executed.");
    }

    public static void handleTrimMemory(Activity activity, int level) {
        Log.i(TAG, "onTrimMemory received level: " + level);
        performMemoryTrim(activity);
    }

    public static void promptManualClean(final Activity activity) {
        if (activity == null) return;

        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setTitle("Cleaning Storage");
        dialog.setMessage("Cleaning logs, crash reports, and caches...");
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.show();

        new Thread(new ManualCleanWorker(activity, dialog)).start();
    }

    static class ManualCleanWorker implements Runnable {
        private final Activity mActivity;
        private final ProgressDialog mDialog;

        ManualCleanWorker(Activity activity, ProgressDialog dialog) {
            this.mActivity = activity;
            this.mDialog = dialog;
        }

        @Override
        public void run() {
            final long freed = cleanLogsAndCaches(mActivity);
            performMemoryTrim(mActivity);

            mActivity.runOnUiThread(new ShowResultRunnable(mActivity, mDialog, freed));
        }
    }

    static class ShowResultRunnable implements Runnable {
        private final Activity mActivity;
        private final ProgressDialog mDialog;
        private final long mFreed;

        ShowResultRunnable(Activity activity, ProgressDialog dialog, long freed) {
            this.mActivity = activity;
            this.mDialog = dialog;
            this.mFreed = freed;
        }

        @Override
        public void run() {
            try {
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                }
            } catch (Throwable ignored) {}

            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle("Clean Complete");
                builder.setMessage("Successfully cleaned " + formatBytes(mFreed) + " of logs and cache files.\n\nRAM has been trimmed.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.show();
            } catch (Throwable th) {
                Log.e(TAG, "Failed to show clean result dialog", th);
            }
        }
    }
}
