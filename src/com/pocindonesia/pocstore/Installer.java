package com.pocindonesia.pocstore;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.Toast;

import java.util.List;

/** Membuka installer APK dari URI yang diberikan DownloadManager. */
public final class Installer {

    private Installer() {}

    /** @return true bila installer berhasil dipanggil. */
    public static boolean install(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        PackageManager pm = context.getPackageManager();

        // Izin "Install unknown apps" harus dinyalakan sekali pada Android 8+.
        // Tanpa pengecekan ini installer gagal diam-diam dari notifikasi.
        if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) {
            Toast.makeText(context,
                    "Izinkan POC Store memasang aplikasi, lalu pilih PASANG UNDUHAN TERAKHIR.",
                    Toast.LENGTH_LONG).show();
            bukaPengaturanInstall(context);
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        List<ResolveInfo> targets = pm.queryIntentActivities(intent, 0);
        if (targets.isEmpty()) {
            intent.setAction(Intent.ACTION_INSTALL_PACKAGE);
            targets = pm.queryIntentActivities(intent, 0);
        }
        for (ResolveInfo ri : targets) {
            try {
                context.grantUriPermission(ri.activityInfo.packageName, uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {}
        }

        try {
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Toast.makeText(context,
                    "Gagal membuka installer. Izinkan POC Store memasang aplikasi lalu pilih PASANG UNDUHAN TERAKHIR.",
                    Toast.LENGTH_LONG).show();
            bukaPengaturanInstall(context);
            return false;
        }
    }

    private static void bukaPengaturanInstall(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            settings.setData(Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
        } catch (Throwable ignored) {}
    }
}
