package com.pocindonesia.pocstore;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

public class DownloadReceiver extends BroadcastReceiver {

    private static final String CHANNEL = "pocstore_download";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;

        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        android.content.SharedPreferences sp =
                context.getSharedPreferences("store", Context.MODE_PRIVATE);
        long last = sp.getLong("last_id", -1);
        if (id != last || id < 0) return;
        // tandai sudah diproses supaya receiver statis + dinamis tidak menjalankan install dua kali
        sp.edit().putLong("last_id", -1).apply();

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;

        DownloadManager.Query q = new DownloadManager.Query();
        q.setFilterById(id);
        boolean ok = false;
        Cursor c = dm.query(q);
        try {
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                ok = status == DownloadManager.STATUS_SUCCESSFUL;
            }
        } finally {
            if (c != null) c.close();
        }
        if (!ok) {
            Toast.makeText(context, "Unduhan gagal", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = dm.getUriForDownloadedFile(id);
        if (uri == null) return;

        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(install, 0)) {
                context.grantUriPermission(ri.activityInfo.packageName, uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Throwable ignored) {}

        // Android 8+ melarang Activity start dari background; pakai notifikasi yang
        // diketuk pengguna supaya proses instalasi tetap berjalan.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            showInstallNotification(context, install);
            return;
        }
        try {
            context.startActivity(install);
        } catch (Throwable t) {
            showInstallNotification(context, install);
        }
    }

    private static void showInstallNotification(Context context, Intent install) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Unduhan POC Store", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, 100, install, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(context, CHANNEL);
        } else {
            b = new Notification.Builder(context);
        }
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Unduhan selesai")
                .setContentText("Ketuk untuk memasang aplikasi")
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            nm.notify(100, b.build());
        } catch (Throwable ignored) {}
    }
}
