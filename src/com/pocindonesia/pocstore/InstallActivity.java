package com.pocindonesia.pocstore;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Jembatan notifikasi unduhan -> installer. Berjalan di foreground sehingga
 * pengecekan izin "Install unknown apps" dan startActivity diizinkan di Android 8+.
 */
public class InstallActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        if (uri == null) uri = uriUnduhanTerakhir();
        if (uri == null) {
            Toast.makeText(this, "Berkas APK tidak ditemukan — unduh lagi", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Installer.install(this, uri);
        finish();
    }

    private Uri uriUnduhanTerakhir() {
        try {
            java.io.File f = ApkProvider.file(this);
            if (f.exists() && f.length() > 0) return ApkProvider.uriFor(this);
        } catch (Throwable ignored) {}

        long id = getSharedPreferences("store", Context.MODE_PRIVATE)
                .getLong("last_done_id", -1);
        if (id < 0) return null;
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return null;
        DownloadManager.Query q = new DownloadManager.Query();
        q.setFilterById(id);
        Cursor c = dm.query(q);
        try {
            if (c != null && c.moveToFirst()) {
                int st = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (st == DownloadManager.STATUS_SUCCESSFUL) return dm.getUriForDownloadedFile(id);
            }
        } finally {
            if (c != null) c.close();
        }
        return null;
    }
}
