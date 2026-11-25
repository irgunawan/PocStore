package com.pocindonesia.pocstore;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Menyajikan berkas APK hasil unduhan ke paket installer lewat content://
 * sehingga tidak butuh WRITE_EXTERNAL_STORAGE (gagal di radio POC seperti
 * Hytera PNC360S karena izin runtime tidak pernah diminta).
 *
 * Berkas ditulis DownloadManager ke getExternalFilesDir(DOWNLOADS) milik aplikasi
 * sendiri — direktori itu tidak butuh izin penyimpanan di semua versi Android.
 */
public class ApkProvider extends ContentProvider {

    public static final String AUTHORITY = "com.pocindonesia.pocstore.apk";
    public static final String FILE_NAME = "pocstore-install.apk";

    public static File dir(Context c) {
        File d = c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (d == null) d = new File(c.getFilesDir(), "downloads");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File file(Context c) {
        return new File(dir(c), FILE_NAME);
    }

    public static Uri uriFor(Context c) {
        return Uri.parse("content://" + AUTHORITY + "/" + FILE_NAME);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context c = getContext();
        if (c == null) throw new FileNotFoundException("context null");
        File f = file(c);
        if (!f.exists() || f.length() == 0) throw new FileNotFoundException(f.getAbsolutePath());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
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