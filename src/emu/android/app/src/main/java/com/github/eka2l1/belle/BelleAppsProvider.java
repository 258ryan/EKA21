package com.github.eka2l1.belle;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import com.github.eka2l1.applist.AppItem;
import com.github.eka2l1.emu.Emulator;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets the Belle launcher list the installed Symbian apps like normal apps:
 * content://com.github.eka2l1.dualstick.belle/apps (uid, name, device code) and /icon/&lt;uid&gt; (a PNG).
 * Launching is an ordinary EmulatorActivity intent, see {@link #launchIntent}.
 */
public class BelleAppsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.github.eka2l1.dualstick.belle";

    private List<AppItem> apps() {
        try {
            Emulator.initializeForShortcutLaunch(getContext());
            return Emulator.getAppsList().blockingGet();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /** The intent that opens app [uid] directly, with no emulator screen in front. */
    public static Intent launchIntent(android.content.Context c, long uid, String name, String deviceCode) {
        Intent i = new Intent(Intent.ACTION_DEFAULT, null, c, com.github.eka2l1.emu.EmulatorActivity.class);
        i.putExtra(com.github.eka2l1.emu.Constants.KEY_APP_UID, uid);
        i.putExtra(com.github.eka2l1.emu.Constants.KEY_APP_NAME, name);
        i.putExtra(com.github.eka2l1.emu.Constants.KEY_DEVICE_CODE, deviceCode);
        i.putExtra(com.github.eka2l1.emu.Constants.KEY_APP_IS_SHORTCUT, true);
        return i;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cur = new MatrixCursor(new String[]{"uid", "name", "device"});
        String device = "";
        try {
            int id = Emulator.getCurrentDevice();
            String[] codes = Emulator.getDeviceFirmwareCodes();
            if (id >= 0 && id < codes.length) {
                device = codes[id];
            }
        } catch (Throwable ignored) {
        }
        for (AppItem a : apps()) {
            cur.addRow(new Object[]{a.getUid(), a.getTitle(), device});
        }
        return cur;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 2 || !"icon".equals(seg.get(0))) {
            throw new java.io.FileNotFoundException(uri.toString());
        }
        long uid = Long.parseLong(seg.get(1));
        for (AppItem a : apps()) {
            if (a.getUid() == uid && a.getIcon() != null) {
                try {
                    File f = new File(getContext().getCacheDir(), "icon_" + uid + ".png");
                    try (FileOutputStream o = new FileOutputStream(f)) {
                        a.getIcon().compress(Bitmap.CompressFormat.PNG, 100, o);
                    }
                    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                } catch (java.io.IOException e) {
                    throw new java.io.FileNotFoundException(e.getMessage());
                }
            }
        }
        throw new java.io.FileNotFoundException(uri.toString());
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
