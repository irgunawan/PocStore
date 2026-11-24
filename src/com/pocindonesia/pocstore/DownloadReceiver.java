package com.pocindonesia.pocstore;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

public class DownloadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;

        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        long last = context.getSharedPreferences("store", Context.MODE_PRIVATE).getLong("last_id", -1);
        if (id != last) return;

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;

        DownloadManager.Query q = new DownloadManager.Query();
        q.setFilterById(id);
        Cursor c = dm.query(q);
        boolean ok = false;
        try {
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                ok = status == DownloadManager.STATUS_SUCCESSFUL;
            }
        } finally {
            if (c != null) c.close();
        }
        if (!ok) return;

        Uri uri = dm.getUriForDownloadedFile(id);
        if (uri == null) return;

        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.content.pm.PackageManager pm = context.getPackageManager();
                java.util.List<android.content.pm.ResolveInfo> targets =
                        pm.queryIntentActivities(install, 0);
                for (android.content.pm.ResolveInfo ri : targets) {
                    context.grantUriPermission(
                            ri.activityInfo.packageName, uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
            context.startActivity(install);
        } catch (Throwable ignored) {
            // Unduhan tetap ada di folder Downloads untuk dipasang manual.
        }
    }
}
