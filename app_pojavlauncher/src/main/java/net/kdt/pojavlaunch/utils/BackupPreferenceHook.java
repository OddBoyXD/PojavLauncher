package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class BackupPreferenceHook {
    public static void hookPreferences(final PreferenceFragmentCompat fragment) {
        if (fragment == null) return;
        final FragmentActivity activity = fragment.getActivity();
        if (activity == null) return;

        Preference selectPref = fragment.findPreference("pref_select_backup_items");
        if (selectPref != null) {
            selectPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    BackupFolderSelector.showSelector(activity);
                    return true;
                }
            });
        }

        Preference exportPref = fragment.findPreference("pref_export_backup");
        if (exportPref != null) {
            exportPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    BackupManager.promptExportBackup(activity);
                    return true;
                }
            });
        }

        Preference importPref = fragment.findPreference("pref_import_backup");
        if (importPref != null) {
            importPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    BackupManager.promptImportBackup(activity);
                    return true;
                }
            });
        }

        Preference cleanNowPref = fragment.findPreference("pref_clean_logs_caches_now");
        if (cleanNowPref != null) {
            cleanNowPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    CleanUtils.promptManualClean(activity);
                    return true;
                }
            });
        }
    }
}
