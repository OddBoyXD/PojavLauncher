package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Toast;

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
            set.add("ACCOUNTS");
            set.add("SETTINGS");
            set.add("controlmap");
            set.add("saves");
            set.add("mods");
            set.add("config");
            set.add("resourcepacks");
            set.add("shaderpacks");
            set.add("schematics");
            set.add("servers.dat");
            set.add("options.txt");
            set.add("launcher_profiles.json");
        }
        return new HashSet<String>(set);
    }

    public static void saveSelectedItems(Context context, Set<String> items) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putStringSet(KEY_SELECTED_ITEMS, items).apply();
    }

    public static void promptExportBackup(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_EXPORT_BACKUP_TREE);
        } catch (Exception e) {
            Toast.makeText(activity, "Error opening folder picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void promptImportBackup(Activity activity) {
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
            Toast.makeText(activity, "No items selected for backup. Please select items first.", Toast.LENGTH_LONG).show();
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
                    File filesDir = activity.getExternalFilesDir(null);
                    File mcDir = new File(filesDir, ".minecraft");
                    File controlDir = new File(filesDir, "controlmap");
                    File accountsDir = new File(activity.getFilesDir(), "accounts");

                    final List<BackupEntry> entriesToZip = new ArrayList<BackupEntry>();

                    // 1. Accounts
                    if (selectedItems.contains("ACCOUNTS") && accountsDir.exists()) {
                        scanFolder(accountsDir, "accounts", selectedItems, entriesToZip);
                    }

                    // 2. Control maps
                    if (selectedItems.contains("controlmap") && controlDir.exists()) {
                        scanFolder(controlDir, "controlmap", selectedItems, entriesToZip);
                    }

                    // 3. .minecraft items
                    if (mcDir.exists()) {
                        File[] mcFiles = mcDir.listFiles();
                        if (mcFiles != null) {
                            for (File f : mcFiles) {
                                String name = f.getName();
                                if (selectedItems.contains(name) || isSubItemIncluded(name, selectedItems)) {
                                    if (f.isDirectory()) {
                                        scanFolder(f, ".minecraft/" + name, selectedItems, entriesToZip);
                                    } else {
                                        entriesToZip.add(new BackupEntry(f, ".minecraft/" + name));
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
                                Toast.makeText(activity, "No files found for selected backup items.", Toast.LENGTH_LONG).show();
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
                        throw new Exception("Failed to create backup file in selected folder.");
                    }

                    OutputStream os = activity.getContentResolver().openOutputStream(targetZipUri);
                    if (os == null) {
                        throw new Exception("Unable to open output stream to backup file.");
                    }

                    ZipOutputStream zos = new ZipOutputStream(os);
                    byte[] buffer = new byte[65536];
                    int totalFiles = entriesToZip.size();
                    int count = 0;
                    long lastUpdateTime = 0;

                    for (BackupEntry entry : entriesToZip) {
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
                        if (now - lastUpdateTime > 150 || count == totalFiles) {
                            lastUpdateTime = now;
                            final int currentCount = count;
                            final int percent = (currentCount * 100) / totalFiles;
                            final String currentName = entry.file.getName();
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.setProgress(percent);
                                    progressDialog.setMessage("Backing up: " + currentName);
                                }
                            });
                        }
                    }

                    zos.close();
                    os.close();

                    final int finalTotal = totalFiles;
                    final String finalZipName = zipName;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(activity)
                                    .setTitle("Backup Completed!")
                                    .setMessage("Successfully created backup archive:\n\nFile: " + finalZipName + "\nFiles backed up: " + finalTotal + "\n\nSaved to selected folder.")
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
        progressDialog.setMessage("Reading backup archive...");
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
                    File filesDir = activity.getExternalFilesDir(null);
                    File mcDir = new File(filesDir, ".minecraft");
                    File controlDir = new File(filesDir, "controlmap");
                    File accountsDir = new File(activity.getFilesDir(), "accounts");

                    accountsDir.mkdirs();
                    controlDir.mkdirs();
                    mcDir.mkdirs();

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
                        if (name.startsWith("accounts/")) {
                            outFile = new File(accountsDir, name.substring("accounts/".length()));
                        } else if (name.startsWith("controlmap/")) {
                            outFile = new File(controlDir, name.substring("controlmap/".length()));
                        } else if (name.startsWith(".minecraft/")) {
                            outFile = new File(mcDir, name.substring(".minecraft/".length()));
                        } else {
                            outFile = new File(mcDir, name);
                        }

                        if (ze.isDirectory()) {
                            outFile.mkdirs();
                        } else {
                            File parent = outFile.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();

                            FileOutputStream fos = new FileOutputStream(outFile);
                            int len;
                            while ((len = zis.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                            fos.close();
                            count++;
                        }
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
                                    .setMessage("Successfully restored " + restoredCount + " files!\nAll accounts, worlds, controls, mods, and settings have been restored.")
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

    private static void scanFolder(File dir, String zipPrefix, Set<String> selectedItems, List<BackupEntry> results) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            String subPath = zipPrefix + "/" + f.getName();
            if (f.isDirectory()) {
                scanFolder(f, subPath, selectedItems, results);
            } else {
                results.add(new BackupEntry(f, subPath));
            }
        }
    }

    private static boolean isSubItemIncluded(String name, Set<String> selectedItems) {
        for (String s : selectedItems) {
            if (s.startsWith(name + "/")) return true;
        }
        return false;
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
