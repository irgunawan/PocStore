package com.pocindonesia.pocstore;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import java.util.List;

/** Membuka installer APK dari URI yang diberikan DownloadManager. */
public final class Installer {

    private Installer() {}

    public static void install(Context context, Uri uri) {
        if (context == null || uri == null) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> targets = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo ri : targets) {
                context.grantUriPermission(ri.activityInfo.packageName, uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
        try {
            context.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(context,
                    "Izinkan POC Store memasang aplikasi (Install unknown apps) lalu tekan unduh lagi.",
                    Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Intent settings = new Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    settings.setData(Uri.parse("package:" + context.getPackageName()));
                    settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(settings);
                } catch (Throwable ignored) {}
            }
        }
    }
}
