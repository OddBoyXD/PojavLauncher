package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;

import net.kdt.pojavlaunch.customcontrols.ControlData;

public class CommandMacroEditorHook {
    private static final String TAG = "CommandMacroEditorHook";
    public static ControlData sCurrentData;
    public static boolean sInternalChanges = false;

    public static void bind(final View scrollView) {
        if (scrollView == null) return;
        Context context = scrollView.getContext();
        String pkg = context.getPackageName();

        int switchId = context.getResources().getIdentifier("checkboxIsCommand", "id", pkg);
        int editId = context.getResources().getIdentifier("editCommand_editText", "id", pkg);

        if (switchId != 0) {
            Switch isCommandSwitch = scrollView.findViewById(switchId);
            if (isCommandSwitch != null) {
                isCommandSwitch.setOnCheckedChangeListener(new CommandToggleListener(scrollView));
            }
        }

        if (editId != 0) {
            EditText commandEditText = scrollView.findViewById(editId);
            if (commandEditText != null) {
                commandEditText.addTextChangedListener(new CommandTextListener());
            }
        }
    }

    public static void onLoadValues(final View scrollView, final ControlData data) {
        if (scrollView == null || data == null) return;

        sCurrentData = data;
        sInternalChanges = true;

        Context context = scrollView.getContext();
        String pkg = context.getPackageName();

        int switchId = context.getResources().getIdentifier("checkboxIsCommand", "id", pkg);
        int editId = context.getResources().getIdentifier("editCommand_editText", "id", pkg);

        if (switchId != 0) {
            Switch isCommandSwitch = scrollView.findViewById(switchId);
            if (isCommandSwitch != null) {
                isCommandSwitch.setChecked(data.isCommand);
            }
        }

        if (editId != 0) {
            EditText commandEditText = scrollView.findViewById(editId);
            if (commandEditText != null) {
                commandEditText.setText(data.commandText != null ? data.commandText : "");
            }
        }

        applyVisibility(scrollView, data.isCommand);

        sInternalChanges = false;
    }

    public static void applyVisibility(View scrollView, boolean isCommand) {
        if (scrollView == null) return;
        Context context = scrollView.getContext();
        String pkg = context.getPackageName();

        int editId = context.getResources().getIdentifier("editCommand_editText", "id", pkg);
        if (editId != 0) {
            View commandEditText = scrollView.findViewById(editId);
            if (commandEditText != null) {
                commandEditText.setVisibility(isCommand ? View.VISIBLE : View.GONE);
            }
        }

        int unneededVis = isCommand ? View.GONE : View.VISIBLE;

        String[] unneededIds = new String[]{
                "editMapping_textView",
                "mapping_1_textview",
                "mapping_2_textview",
                "mapping_3_textview",
                "mapping_4_textview",
                "editMapping_plus_1",
                "editMapping_plus_3",
                "editOrientation_textView",
                "editOrientation_spinner",
                "checkboxToggle",
                "checkboxPassThrough",
                "checkboxSwipeable",
                "checkboxForwardLock"
        };

        for (String idName : unneededIds) {
            int resId = context.getResources().getIdentifier(idName, "id", pkg);
            if (resId != 0) {
                View view = scrollView.findViewById(resId);
                if (view != null) {
                    view.setVisibility(unneededVis);
                }
            }
        }
    }

    static class CommandToggleListener implements CompoundButton.OnCheckedChangeListener {
        private final View mScrollView;

        CommandToggleListener(View scrollView) {
            this.mScrollView = scrollView;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (sInternalChanges) return;

            if (sCurrentData != null) {
                sCurrentData.isCommand = isChecked;
            }
            applyVisibility(mScrollView, isChecked);
        }
    }

    static class CommandTextListener implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (sInternalChanges) return;

            if (sCurrentData != null) {
                sCurrentData.commandText = s.toString();
            }
        }
    }
}
