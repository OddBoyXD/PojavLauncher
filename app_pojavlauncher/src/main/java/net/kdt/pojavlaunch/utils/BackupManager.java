package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.widget.Toast;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    public static final int REQUEST_EXPORT_BACKUP_TREE = 42070;
    public static final int REQUEST_IMPORT_BACKUP_ZIP = 42071;

    private static final String PREF_NAME = "pojav_backup_preferences";
    private static final String KEY_SELECTED_ITEMS = "selected_backup_items";

    public static Set<String> getSelectedItems(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> set = sp.getStringSet(KEY_SELECTED_ITEMS, null);
        if (set == null) {
            set = new HashSet<String>();
            File filesDir = null;
            if (Tools.DIR_GAME_HOME != null) {
                filesDir = new File(Tools.DIR_GAME_HOME);
            }
            if (filesDir == null || !filesDir.exists()) {
                filesDir = context.getExternalFilesDir(null);
            }
            if (filesDir != null && filesDir.exists()) {
                File[] list = filesDir.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (!f.getName().equals(".cache")) {
                            set.add(f.getName());
                        }
                    }
                }
            }
            if (set.isEmpty()) {
                set.add(".minecraft");
                set.add("controlmap");
            }
        }
        return new HashSet<String>(set);
    }

    public static void saveSelectedItems(Context context, Set<String> items) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putStringSet(KEY_SELECTED_ITEMS, items).apply();
    }

    public static class StoragePermissionListener implements DialogInterface.OnClickListener {
        Activity activity;
        public StoragePermissionListener(Activity activity) {
            this.activity = activity;
        }
        @Override
        public void onClick(DialogInterface dialog, int which) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivity(intent);
                } catch (Exception ex) {
                    Toast.makeText(activity, "Cannot open permission settings: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public static void promptExportBackup(final Activity activity) {
        if (activity == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Storage Permission Required")
                        .setMessage("PojavLauncher needs All files access permission to export and save your backups safely to your device storage.\n\nPlease enable Allow access to manage all files for PojavLauncher in the next screen.")
                        .setPositiveButton("Grant Access", new StoragePermissionListener(activity))
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_EXPORT_BACKUP_TREE);
        } catch (Exception e) {
            Toast.makeText(activity, "Error opening folder picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void promptImportBackup(final Activity activity) {
        if (activity == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Storage Permission Required")
                        .setMessage("PojavLauncher needs All files access permission to restore backups properly.\n\nPlease enable Allow access to manage all files for PojavLauncher in the next screen.")
                        .setPositiveButton("Grant Access", new StoragePermissionListener(activity))
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "*/*"
            });
            activity.startActivityForResult(intent, REQUEST_IMPORT_BACKUP_ZIP);
        } catch (Exception e) {
            Toast.makeText(activity, "Error opening backup zip picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static class RestartListener implements DialogInterface.OnClickListener {
        Activity activity;
        public RestartListener(Activity activity) {
            this.activity = activity;
        }
        @Override
        public void onClick(DialogInterface dialog, int which) {
            restartApplication(activity);
        }
    }

    public static class ExportTask implements Runnable {
        Activity activity;
        Uri treeUri;
        Set<String> selectedItems;
        ProgressDialog progressDialog;

        public ExportTask(Activity activity, Uri treeUri, Set<String> selectedItems, ProgressDialog progressDialog) {
            this.activity = activity;
            this.treeUri = treeUri;
            this.selectedItems = selectedItems;
            this.progressDialog = progressDialog;
        }

        @Override
        public void run() {
            try {
                File filesDir = null;
                if (Tools.DIR_GAME_HOME != null) {
                    filesDir = new File(Tools.DIR_GAME_HOME);
                }
                if (filesDir == null || !filesDir.exists()) {
                    filesDir = activity.getExternalFilesDir(null);
                }

                File internalAccounts = new File(activity.getFilesDir(), "accounts");
                File externalAccounts = new File(filesDir, "accounts");
                File sharedPrefsDir = new File(activity.getApplicationInfo().dataDir, "shared_prefs");

                final List<BackupEntry> entriesToZip = new ArrayList<BackupEntry>();
                final Set<String> addedPaths = new HashSet<String>();

                // 1. ALWAYS AUTOMATICALLY BACKUP ACCOUNTS
                if (internalAccounts.exists()) {
                    File[] accs = internalAccounts.listFiles();
                    if (accs != null) {
                        for (File acc : accs) {
                            if (acc.isFile()) {
                                String zipPath = "accounts/" + acc.getName();
                                if (addedPaths.add(zipPath)) {
                                    entriesToZip.add(new BackupEntry(acc, zipPath, false));
                                }
                                String filesAccPath = "files/accounts/" + acc.getName();
                                if (addedPaths.add(filesAccPath)) {
                                    entriesToZip.add(new BackupEntry(acc, filesAccPath, false));
                                }
                            }
                        }
                    }
                }
                if (externalAccounts.exists()) {
                    File[] accs = externalAccounts.listFiles();
                    if (accs != null) {
                        for (File acc : accs) {
                            if (acc.isFile()) {
                                String zipPath = "accounts/" + acc.getName();
                                if (addedPaths.add(zipPath)) {
                                    entriesToZip.add(new BackupEntry(acc, zipPath, false));
                                }
                                String filesAccPath = "files/accounts/" + acc.getName();
                                if (addedPaths.add(filesAccPath)) {
                                    entriesToZip.add(new BackupEntry(acc, filesAccPath, false));
                                }
                            }
                        }
                    }
                }

                // 2. ALWAYS AUTOMATICALLY BACKUP SETTINGS & PREFERENCES
                if (sharedPrefsDir.exists()) {
                    File[] prefs = sharedPrefsDir.listFiles();
                    if (prefs != null) {
                        for (File pref : prefs) {
                            if (pref.isFile()) {
                                String zipPath = "shared_prefs/" + pref.getName();
                                if (addedPaths.add(zipPath)) {
                                    entriesToZip.add(new BackupEntry(pref, zipPath, false));
                                }
                                String filesPrefPath = "files/shared_prefs/" + pref.getName();
                                if (addedPaths.add(filesPrefPath)) {
                                    entriesToZip.add(new BackupEntry(pref, filesPrefPath, false));
                                }
                            }
                        }
                    }
                }

                // 3. BACKUP SELECTED FILES & FOLDERS FROM filesDir (ALL INSIDE files/ FOLDER IN ZIP)
                if (filesDir != null && filesDir.exists()) {
                    scanFilesDir(filesDir, filesDir, selectedItems, entriesToZip, addedPaths);
                }

                if (entriesToZip.isEmpty()) {
                    activity.runOnUiThread(new DismissAndToastTask(progressDialog, activity, "No matching files found to backup."));
                    return;
                }

                // Create backup zip in tree directory
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String zipName = "PojavBackup_" + timeStamp + ".zip";

                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                Uri targetZipUri = DocumentsContract.createDocument(activity.getContentResolver(), docUri, "application/zip", zipName);

                if (targetZipUri == null) {
                    throw new Exception("Failed to create backup file in selected directory.");
                }

                OutputStream os = activity.getContentResolver().openOutputStream(targetZipUri);
                if (os == null) {
                    throw new Exception("Unable to open output stream to backup archive.");
                }

                ZipOutputStream zos = new ZipOutputStream(os);
                byte[] buffer = new byte[65536];
                final int totalFiles = entriesToZip.size();
                int count = 0;
                long lastUpdateTime = 0;

                for (final BackupEntry entry : entriesToZip) {
                    count++;
                    ZipEntry ze = new ZipEntry(entry.zipPath);
                    try {
                        ze.setTime(entry.file.lastModified());
                    } catch (Exception ignored) {}
                    zos.putNextEntry(ze);

                    if (!entry.isDirectory) {
                        FileInputStream fis = new FileInputStream(entry.file);
                        int len;
                        while ((len = fis.read(buffer)) != -1) {
                            zos.write(buffer, 0, len);
                        }
                        fis.close();
                    }
                    zos.closeEntry();

                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime > 80 || count == totalFiles) {
                        lastUpdateTime = now;
                        final int current = count;
                        activity.runOnUiThread(new ProgressUpdateTask(progressDialog, current, totalFiles, entry.file.getName()));
                    }
                }

                zos.close();
                os.close();

                final int finalCount = count;
                final String finalZipName = zipName;
                activity.runOnUiThread(new BackupCompleteDialogTask(progressDialog, activity, finalZipName, finalCount));

            } catch (final Exception e) {
                activity.runOnUiThread(new ErrorDialogTask(progressDialog, activity, "Backup Failed", e.getMessage()));
            }
        }
    }

    public static class ImportTask implements Runnable {
        Activity activity;
        Uri zipUri;
        ProgressDialog progressDialog;

        public ImportTask(Activity activity, Uri zipUri, ProgressDialog progressDialog) {
            this.activity = activity;
            this.zipUri = zipUri;
            this.progressDialog = progressDialog;
        }

        @Override
        public void run() {
            try {
                File filesDir = null;
                if (Tools.DIR_GAME_HOME != null) {
                    filesDir = new File(Tools.DIR_GAME_HOME);
                }
                if (filesDir == null || !filesDir.exists()) {
                    filesDir = activity.getExternalFilesDir(null);
                }

                File accountsDir = new File(activity.getFilesDir(), "accounts");
                File sharedPrefsDir = new File(activity.getApplicationInfo().dataDir, "shared_prefs");

                if (filesDir != null && !filesDir.exists()) filesDir.mkdirs();
                if (!accountsDir.exists()) accountsDir.mkdirs();
                if (!sharedPrefsDir.exists()) sharedPrefsDir.mkdirs();

                InputStream is = activity.getContentResolver().openInputStream(zipUri);
                if (is == null) throw new Exception("Cannot open backup archive.");

                ZipInputStream zis = new ZipInputStream(is);
                byte[] buffer = new byte[65536];
                int count = 0;
                ZipEntry ze;

                while ((ze = zis.getNextEntry()) != null) {
                    String name = ze.getName();
                    if (name.contains("..")) {
                        zis.closeEntry();
                        continue;
                    }

                    File outFile;
                    boolean isSpecial = false;

                    if (name.startsWith("accounts/")) {
                        outFile = new File(accountsDir, name.substring("accounts/".length()));
                        isSpecial = true;
                    } else if (name.startsWith("shared_prefs/")) {
                        outFile = new File(sharedPrefsDir, name.substring("shared_prefs/".length()));
                        isSpecial = true;
                    } else if (name.startsWith("files/accounts/")) {
                        outFile = new File(accountsDir, name.substring("files/accounts/".length()));
                        isSpecial = true;
                    } else if (name.startsWith("files/shared_prefs/")) {
                        outFile = new File(sharedPrefsDir, name.substring("files/shared_prefs/".length()));
                        isSpecial = true;
                    } else if (name.startsWith("files/")) {
                        outFile = new File(filesDir, name.substring("files/".length()));
                    } else {
                        // Backwards compatibility for root files
                        outFile = new File(filesDir, name);
                    }

                    if (ze.isDirectory() || name.endsWith("/")) {
                        outFile.mkdirs();
                        zis.closeEntry();
                        continue;
                    }

                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();

                    FileOutputStream fos = new FileOutputStream(outFile);
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();

                    if (isSpecial && (name.startsWith("accounts/") || name.startsWith("files/accounts/"))) {
                        String subAcc = name.startsWith("files/accounts/") ? name.substring("files/accounts/".length()) : name.substring("accounts/".length());
                        File extAcc = new File(filesDir, "accounts/" + subAcc);
                        File extParent = extAcc.getParentFile();
                        if (extParent != null && !extParent.exists()) extParent.mkdirs();
                        copyFile(outFile, extAcc);
                    }

                    count++;
                    zis.closeEntry();
                }

                zis.close();
                is.close();

                final int restoredCount = count;
                activity.runOnUiThread(new RestoreCompleteDialogTask(progressDialog, activity, restoredCount));

            } catch (final Exception e) {
                activity.runOnUiThread(new ErrorDialogTask(progressDialog, activity, "Restore Failed", e.getMessage()));
            }
        }
    }

    public static class ProgressUpdateTask implements Runnable {
        ProgressDialog progressDialog;
        int current;
        int totalFiles;
        String fileName;
        public ProgressUpdateTask(ProgressDialog progressDialog, int current, int totalFiles, String fileName) {
            this.progressDialog = progressDialog;
            this.current = current;
            this.totalFiles = totalFiles;
            this.fileName = fileName;
        }
        @Override
        public void run() {
            int progress = (int) (((long) current * 100) / totalFiles);
            progressDialog.setProgress(progress);
            progressDialog.setMessage("Backing up (" + current + "/" + totalFiles + "):\n" + fileName);
        }
    }

    public static class DismissAndToastTask implements Runnable {
        ProgressDialog progressDialog;
        Activity activity;
        String message;
        public DismissAndToastTask(ProgressDialog progressDialog, Activity activity, String message) {
            this.progressDialog = progressDialog;
            this.activity = activity;
            this.message = message;
        }
        @Override
        public void run() {
            progressDialog.dismiss();
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }

    public static class BackupCompleteDialogTask implements Runnable {
        ProgressDialog progressDialog;
        Activity activity;
        String zipName;
        int count;
        public BackupCompleteDialogTask(ProgressDialog progressDialog, Activity activity, String zipName, int count) {
            this.progressDialog = progressDialog;
            this.activity = activity;
            this.zipName = zipName;
            this.count = count;
        }
        @Override
        public void run() {
            progressDialog.dismiss();
            new AlertDialog.Builder(activity)
                    .setTitle("Backup Complete!")
                    .setMessage("Successfully created " + zipName + "\n\nTotal items backed up: " + count)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    public static class RestoreCompleteDialogTask implements Runnable {
        ProgressDialog progressDialog;
        Activity activity;
        int restoredCount;
        public RestoreCompleteDialogTask(ProgressDialog progressDialog, Activity activity, int restoredCount) {
            this.progressDialog = progressDialog;
            this.activity = activity;
            this.restoredCount = restoredCount;
        }
        @Override
        public void run() {
            progressDialog.dismiss();
            new AlertDialog.Builder(activity)
                    .setTitle("Restore Complete!")
                    .setMessage("Successfully restored " + restoredCount + " files!\nAll accounts, worlds, controls, mods, and settings have been restored.\n\nPlease restart PojavLauncher to apply all changes.")
                    .setCancelable(false)
                    .setPositiveButton("Restart Now", new RestartListener(activity))
                    .setNegativeButton("Later", null)
                    .show();
        }
    }

    public static class ErrorDialogTask implements Runnable {
        ProgressDialog progressDialog;
        Activity activity;
        String title;
        String message;
        public ErrorDialogTask(ProgressDialog progressDialog, Activity activity, String title, String message) {
            this.progressDialog = progressDialog;
            this.activity = activity;
            this.title = title;
            this.message = message;
        }
        @Override
        public void run() {
            progressDialog.dismiss();
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage("Error: " + message)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    public static void executeExportBackup(final Activity activity, final Uri treeUri) {
        if (activity == null || treeUri == null) return;

        final Set<String> selectedItems = getSelectedItems(activity);

        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Creating Backup");
        progressDialog.setMessage("Scanning files...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setProgress(0);
        progressDialog.setMax(100);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(new ExportTask(activity, treeUri, selectedItems, progressDialog));
    }

    public static void executeImportBackup(final Activity activity, final Uri zipUri) {
        if (activity == null || zipUri == null) return;

        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Restoring Backup");
        progressDialog.setMessage("Extracting files...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(new ImportTask(activity, zipUri, progressDialog));
    }

    public static void restartApplication(Activity activity) {
        if (activity == null) return;
        try {
            Context context = activity.getApplicationContext();
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            }
            activity.finishAffinity();
            Process.killProcess(Process.myPid());
            System.exit(0);
        } catch (Exception e) {
            activity.finishAffinity();
            System.exit(0);
        }
    }

    private static void scanFilesDir(File rootDir, File current, Set<String> selectedItems, List<BackupEntry> results, Set<String> addedPaths) {
        File[] list = current.listFiles();
        if (list == null) return;

        for (File f : list) {
            String name = f.getName();
            if (name.equals(".cache")) continue;

            String relPath = getRelativePath(rootDir, f);
            boolean isSelected = isSelectedOrParentSelected(relPath, selectedItems);

            if (f.isDirectory()) {
                if (isSelected) {
                    String zipDirPath = "files/" + relPath + "/";
                    if (addedPaths.add(zipDirPath)) {
                        results.add(new BackupEntry(f, zipDirPath, true));
                    }
                }
                scanFilesDir(rootDir, f, selectedItems, results, addedPaths);
            } else {
                if (isSelected) {
                    String zipFilePath = "files/" + relPath;
                    if (addedPaths.add(zipFilePath)) {
                        results.add(new BackupEntry(f, zipFilePath, false));
                    }
                }
            }
        }
    }

    private static boolean isSelectedOrParentSelected(String relPath, Set<String> selectedPaths) {
        if (selectedPaths.contains(relPath)) {
            return true;
        }
        int idx = relPath.lastIndexOf("/");
        while (idx != -1) {
            String parentPath = relPath.substring(0, idx);
            if (selectedPaths.contains(parentPath)) {
                return true;
            }
            idx = parentPath.lastIndexOf("/");
        }
        return false;
    }

    private static String getRelativePath(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(rootPath)) {
            String sub = filePath.substring(rootPath.length());
            if (sub.startsWith("/")) sub = sub.substring(1);
            return sub;
        }
        return file.getName();
    }

    private static void copyFile(File src, File dst) {
        try {
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (Exception ignored) {}
    }

    private static class BackupEntry {
        File file;
        String zipPath;
        boolean isDirectory;

        BackupEntry(File file, String zipPath, boolean isDirectory) {
            this.file = file;
            this.zipPath = zipPath;
            this.isDirectory = isDirectory;
        }
    }
}
