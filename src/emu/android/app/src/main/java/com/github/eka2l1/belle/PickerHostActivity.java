package com.github.eka2l1.belle;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;

import com.github.eka2l1.util.FileUtils;

/**
 * Runs EKA21's own file / folder pickers for the Belle screens (which are not AndroidX activities) and hands the
 * chosen path back as the "path" extra. Extras in: "dir" (boolean), "ext" (String[] of extensions for files).
 */
public class PickerHostActivity extends ComponentActivity {
    public static final String EXTRA_DIR = "dir";
    public static final String EXTRA_EXT = "ext";
    public static final String EXTRA_PATH = "path";

    private final ActivityResultLauncher<String[]> filePicker = registerForActivityResult(FileUtils.getFilePicker(), this::done);
    private final ActivityResultLauncher<Void> dirPicker = registerForActivityResult(FileUtils.getDirPicker(), this::done);

    private void done(String path) {
        if (path != null) {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_PATH, path));
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            return; // the picker is already up
        }
        if (getIntent().getBooleanExtra(EXTRA_DIR, false)) {
            dirPicker.launch(null);
        } else {
            String[] ext = getIntent().getStringArrayExtra(EXTRA_EXT);
            filePicker.launch(ext != null ? ext : new String[0]);
        }
    }
}
