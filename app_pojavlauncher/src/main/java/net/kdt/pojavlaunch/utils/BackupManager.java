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
            set.add("accounts");
            set.add("controlmap");
            set.add(".minecraft/saves");
            set.add(".minecraft/mods");
            set.add(".minecraft/config");
            set.add(".minecraft/resourcepacks");
            set.add(".minecraft/shaderpacks");
            set.add(".minecraft/schematics");
            set.add(".minecraft/servers.dat");
            set.add(".minecraft/options.txt");
            set.add(".minecraft/launcher_profiles.json");
        }
        return new HashSet<String>(set);
    }

    public static void saveSelectedItems(Context context, Set<String> items) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putStringSet(KEY_SELECTED_ITEMS, items).apply();
    }

    public static void promptExportBackup(final Activity activity) {
        if (activity == null) return;

        // Check All Files Access permission on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Storage Permission Required")
                        .setMessage("PojavLauncher needs 'All files access' permission to export and save your backups safely to your device storage.\n\nPlease enable 'Allow access to manage all files' for PojavLauncher in the next screen.")
                        .setPositiveButton("Grant Access", new DialogInterface.OnClickListener() {
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
                        })
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
                        .setMessage("PojavLauncher needs 'All files access' permission to import and restore your backups safely.\n\nPlease enable 'Allow access to manage all files' for PojavLauncher in the next screen.")
                        .setPositiveButton("Grant Access", new DialogInterface.OnClickListener() {
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
                        })
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

    public static void executeExportBackup(final Activity activity, final Uri treeUri) {
        if (activity == null || treeUri == null) return;

        final Set<String> selectedItems = getSelectedItems(activity);
        if (selectedItems.isEmpty()) {
            Toast.makeText(activity, "No items selected for backup. Please select items in 'Select Backup Items' first.", Toast.LENGTH_LONG).show();
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Creating Backup");
        progressDialog.setMessage("Scanning files...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setProgress(0);
        progressDialog.setMax(100);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(new Runnable() {
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

                    final List<BackupEntry> entriesToZip = new ArrayList<BackupEntry>();

                    // 1. Scan external filesDir (Pojav home: Android/data/net.kdt.pojavlaunch/files/)
                    if (filesDir != null && filesDir.exists()) {
                        scanFilesDir(filesDir, filesDir, selectedItems, entriesToZip);
                    }

                    // 2. Scan internal accounts if not already captured
                    if (accountsDir.exists()) {
                        File[] accs = accountsDir.listFiles();
                        if (accs != null) {
                            for (File acc : accs) {
                                if (acc.isFile()) {
                                    String relPath = "accounts/" + acc.getName();
                                    if (selectedItems.contains(relPath) || selectedItems.contains("accounts")) {
                                        boolean alreadyIn = false;
                                        for (BackupEntry be : entriesToZip) {
                                            if (be.zipPath.equals(relPath)) {
                                                alreadyIn = true;
                                                break;
                                            }
                                        }
                                        if (!alreadyIn) {
                                            entriesToZip.add(new BackupEntry(acc, relPath));
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (entriesToZip.isEmpty()) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                Toast.makeText(activity, "No matching files found to backup.", Toast.LENGTH_LONG).show();
                            }
                        });
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
                        zos.putNextEntry(ze);

                        FileInputStream fis = new FileInputStream(entry.file);
                        int len;
                        while ((len = fis.read(buffer)) != -1) {
                            zos.write(buffer, 0, len);
                        }
                        fis.close();
                        zos.closeEntry();

                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > 80 || count == totalFiles) {
                            lastUpdateTime = now;
                            final int current = count;
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    int progress = (int) (((long) current * 100) / totalFiles);
                                    progressDialog.setProgress(progress);
                                    progressDialog.setMessage("Backing up (" + current + "/" + totalFiles + "):\n" + entry.file.getName());
                                }
                            });
                        }
                    }

                    zos.close();
                    os.close();

                    final int finalCount = count;
                    final String finalZipName = zipName;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(activity)
                                    .setTitle("Backup Complete!")
                                    .setMessage("Successfully created " + finalZipName + "\n\nTotal files backed up: " + finalCount)
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });

                } catch (final Exception e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(activity)
                                    .setTitle("Backup Failed")
                                    .setMessage("Error during backup: " + e.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                }
            }
        });
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

        Executors.newSingleThreadExecutor().execute(new Runnable() {
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

                    if (filesDir != null && !filesDir.exists()) filesDir.mkdirs();
                    if (!accountsDir.exists()) accountsDir.mkdirs();

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

                        if (ze.isDirectory()) {
                            File outDir = new File(filesDir, name);
                            outDir.mkdirs();
                            zis.closeEntry();
                            continue;
                        }

                        File outFile = new File(filesDir, name);
                        File parent = outFile.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();

                        FileOutputStream fos = new FileOutputStream(outFile);
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();

                        // Also mirror accounts to internal accountsDir if in accounts/
                        if (name.startsWith("accounts/")) {
                            File internalAcc = new File(activity.getFilesDir(), name);
                            File internalParent = internalAcc.getParentFile();
                            if (internalParent != null && !internalParent.exists()) internalParent.mkdirs();
                            copyFile(outFile, internalAcc);
                        }

                        count++;
                        zis.closeEntry();
                    }

                    zis.close();
                    is.close();

                    final int restoredCount = count;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(activity)
                                    .setTitle("Restore Complete!")
                                    .setMessage("Successfully restored " + restoredCount + " files!\nAll accounts, worlds, controls, mods, and settings have been restored.\n\nPlease restart PojavLauncher to apply all changes.")
                                    .setCancelable(false)
                                    .setPositiveButton("Restart Now", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            restartApplication(activity);
                                        }
                                    })
                                    .setNegativeButton("Later", null)
                                    .show();
                        }
                    });

                } catch (final Exception e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(activity)
                                    .setTitle("Restore Failed")
                                    .setMessage("Error during restore: " + e.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                }
            }
        });
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

    private static void scanFilesDir(File rootDir, File current, Set<String> selectedItems, List<BackupEntry> results) {
        File[] list = current.listFiles();
        if (list == null) return;

        for (File f : list) {
            String name = f.getName();
            // Skip temporary cache directory
            if (name.equals(".cache")) {
                continue;
            }

            String relPath = getRelativePath(rootDir, f);
            if (relPath.startsWith(".minecraft/assets") || relPath.startsWith(".minecraft/libraries") || relPath.startsWith(".minecraft/.fabric")) {
                continue;
            }

            if (f.isDirectory()) {
                scanFilesDir(rootDir, f, selectedItems, results);
            } else {
                if (selectedItems.contains(relPath) || isAnyParentSelected(relPath, selectedItems)) {
                    results.add(new BackupEntry(f, relPath));
                }
            }
        }
    }

    private static boolean isAnyParentSelected(String relPath, Set<String> selectedPaths) {
        int idx = relPath.lastIndexOf('/');
        while (idx != -1) {
            String parentPath = relPath.substring(0, idx);
            if (selectedPaths.contains(parentPath)) {
                return true;
            }
            idx = parentPath.lastIndexOf('/');
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
        BackupEntry(File file, String zipPath) {
            this.file = file;
            this.zipPath = zipPath;
        }
    }
}
