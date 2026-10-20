package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AssetsExtractor {

    public static final int REQUEST_PICK_ASSETS_ZIP = 42069;

    public static void promptPickAssets(final Activity activity) {
        if (activity == null) return;

        // Check All Files Access permission on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Storage Permission Required")
                        .setMessage("PojavLauncher needs 'All files access' permission to import and extract asset packs.\n\nPlease enable 'Allow access to manage all files' for PojavLauncher in the next screen.")
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
            activity.startActivityForResult(intent, REQUEST_PICK_ASSETS_ZIP);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                activity.startActivityForResult(intent, REQUEST_PICK_ASSETS_ZIP);
            } catch (Exception ex) {
                Toast.makeText(activity, "Error opening file picker: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void extractAssetsFromUri(final Activity activity, final Uri uri) {
        if (activity == null || uri == null) return;

        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Extracting Assets");
        progressDialog.setMessage("Preparing extraction...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setProgress(0);
        progressDialog.setMax(100);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                int filesExtracted = 0;
                try {
                    long totalBytes = -1;
                    try {
                        ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(uri, "r");
                        if (pfd != null) {
                            totalBytes = pfd.getStatSize();
                            pfd.close();
                        }
                    } catch (Exception ignored) {}

                    File filesDir = activity.getExternalFilesDir(null);
                    File mcDir = new File(filesDir, ".minecraft");
                    File assetsDir = new File(mcDir, "assets");
                    assetsDir.mkdirs();

                    InputStream rawIn = activity.getContentResolver().openInputStream(uri);
                    if (rawIn == null) {
                        throw new Exception("Unable to open input stream from selected file.");
                    }

                    ZipInputStream zis = new ZipInputStream(rawIn);
                    byte[] buffer = new byte[65536];
                    long bytesRead = 0;
                    long lastUpdateTime = 0;
                    ZipEntry entry;

                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (name.contains("..")) {
                            zis.closeEntry();
                            continue;
                        }

                        // Normalise path so it lands in .minecraft/assets/
                        String targetRelPath;
                        if (name.startsWith(".minecraft/assets/")) {
                            targetRelPath = name.substring(".minecraft/assets/".length());
                        } else if (name.startsWith("assets/")) {
                            targetRelPath = name.substring("assets/".length());
                        } else {
                            targetRelPath = name;
                        }

                        File outFile = new File(assetsDir, targetRelPath);
                        if (entry.isDirectory()) {
                            outFile.mkdirs();
                        } else {
                            File parent = outFile.getParentFile();
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs();
                            }

                            FileOutputStream fos = new FileOutputStream(outFile);
                            int len;
                            while ((len = zis.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                                bytesRead += len;

                                long now = System.currentTimeMillis();
                                if (now - lastUpdateTime > 100) {
                                    lastUpdateTime = now;
                                    final int percent = totalBytes > 0
                                            ? (int) Math.min(100, Math.max(0, (bytesRead * 100) / totalBytes))
                                            : 0;
                                    final String currentFile = outFile.getName();
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            progressDialog.setProgress(percent);
                                            progressDialog.setMessage("Extracting: " + currentFile);
                                        }
                                    });
                                }
                            }
                            fos.close();
                            filesExtracted++;
                        }
                        zis.closeEntry();
                    }
                    zis.close();
                    rawIn.close();

                    final int finalCount = filesExtracted;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(activity)
                                    .setTitle("Assets Installed!")
                                    .setMessage("Successfully extracted " + finalCount + " asset files directly into .minecraft/assets/\nAll Minecraft versions are ready to play offline!")
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
                                    .setTitle("Extraction Failed")
                                    .setMessage("Error extracting assets: " + e.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                }
            }
        });
    }

    public static void setupAddAssetsButton(final Fragment fragment) {
        if (fragment == null) return;
        final Activity activity = fragment.getActivity();
        if (activity == null) return;
        View view = fragment.getView();
        if (view == null) return;

        View btn = view.findViewById(0x7f0a021c); // add_assets_button
        if (btn != null) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promptPickAssets(activity);
                }
            });
        }
    }
}
