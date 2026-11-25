package com.pocindonesia.pocstore;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Kirim stack trace crash ke portal.irgun.web.id/api/crash. */
public final class CrashReporter implements Thread.UncaughtExceptionHandler {
    private final Context ctx;
    private final String app;
    private final String version;
    private final Thread.UncaughtExceptionHandler previous;

    public CrashReporter(Context ctx, String app, String version) {
        this.ctx = ctx.getApplicationContext();
        this.app = app;
        this.version = version;
        this.previous = Thread.getDefaultUncaughtExceptionHandler();
    }

    public void install() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String device = Build.MODEL == null ? "unknown" : Build.MODEL;
            String did = "";
            try {
                did = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Throwable ignored) {}
            String json = "{\"app\":\"" + esc(app) + "\","
                    + "\"package\":\"" + esc(ctx.getPackageName()) + "\","
                    + "\"version\":\"" + esc(version) + "\","
                    + "\"device\":\"" + esc(device) + "\","
                    + "\"device_id\":\"" + esc(did == null ? "" : did) + "\","
                    + "\"android\":\"" + esc(Build.VERSION.RELEASE == null ? "?" : Build.VERSION.RELEASE) + "\","
                    + "\"stack\":\"" + esc(sw.toString()) + "\"}";
            HttpURLConnection c = (HttpURLConnection) new URL("https://portal.irgun.web.id/api/crash").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            OutputStream os = c.getOutputStream();
            os.write(body);
            os.flush();
            os.close();
            Log.i("CrashReporter", "HTTP " + c.getResponseCode());
            c.disconnect();
        } catch (Throwable ignored) {
            Log.w("CrashReporter", "gagal mengirim laporan crash", ignored);
        }
        if (previous != null) previous.uncaughtException(t, e);
        else System.exit(10);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", "\\n");
    }
}
