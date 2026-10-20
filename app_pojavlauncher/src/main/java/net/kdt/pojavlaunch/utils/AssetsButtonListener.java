package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.view.View;

public class AssetsButtonListener implements View.OnClickListener {
    private final Activity mActivity;

    public AssetsButtonListener(Activity activity) {
        this.mActivity = activity;
    }

    @Override
    public void onClick(View v) {
        if (this.mActivity != null) {
            AssetsExtractor.promptPickAssets(this.mActivity);
        }
    }
}
