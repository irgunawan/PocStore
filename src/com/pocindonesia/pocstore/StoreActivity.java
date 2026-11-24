package com.pocindonesia.pocstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class StoreActivity extends Activity {

    private static final int CURRENT_VERSION = 1;
    private static final String GITHUB_APK_LIST =
            "https://api.github.com/repos/irgunawan/PocDial/contents/apk";
    private static final String FALLBACK_DIAL_URL =
            "https://raw.githubusercontent.com/irgunawan/PocDial/master/apk/POC-DIAL-v1.0.apk";
    private static final String OTA_CHECK =
            "https://ota.irgun.web.id/check.php?pkg=com.pocindonesia.pocstore";
    private static final String OTA_PTTIN =
            "https://ota.irgun.web.id/app.apk?pkg=id.irgun.pttin";

    private static final int[] BUTTON_IDS = {
            R.id.btnDial, R.id.btnPttin, R.id.btnUpdate, R.id.btnAbout
    };

    private TextView status;
    private DownloadManager downloadManager;
    private volatile String dialUrl = FALLBACK_DIAL_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);
        status = (TextView) findViewById(R.id.status);
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        findViewById(R.id.btnDial).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                download(dialUrl, "POC-DIAL-v1.0.apk");
            }
        });
        findViewById(R.id.btnPttin).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                download(OTA_PTTIN, "PTTin-v1.7.0.apk");
            }
        });
        findViewById(R.id.btnUpdate).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { checkSelfUpdate(); }
        });
        findViewById(R.id.btnAbout).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(StoreActivity.this)
                        .setTitle("POC STORE")
                        .setMessage(R.string.about_text)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        findViewById(R.id.btnDial).requestFocus();
        refreshFromGitHub();
    }

    private void refreshFromGitHub() {
        new Thread(new Runnable() {
            public void run() {
                String text;
                try {
                    String body = httpGet(GITHUB_APK_LIST);
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String name = o.optString("name", "");
                        String url = o.optString("download_url", "");
                        if (name.endsWith(".apk") && url.length() > 0) {
                            dialUrl = url;
                            break;
                        }
                    }
                    text = "GitHub: POC DIAL siap diunduh";
                } catch (Throwable t) {
                    text = "GitHub tak terjangkau · memakai URL cadangan";
                }
                final String statusText = text;
                runOnUiThread(new Runnable() {
                    public void run() { status.setText(statusText); }
                });
            }
        }).start();
    }

    private void checkSelfUpdate() {
        status.setText("Mengecek OTA...");
        new Thread(new Runnable() {
            public void run() {
                final int[] versi = {-1};
                final String[] url = {""};
                try {
                    JSONObject all = new JSONObject(httpGet(OTA_CHECK));
                    JSONObject self = all.optJSONObject("com.pocindonesia.pocstore");
                    if (self != null) {
                        versi[0] = self.optInt("version", -1);
                        url[0] = self.optString("url", "");
                    }
                } catch (Throwable ignored) {}

                final int v = versi[0];
                final String u = url[0];
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (v > CURRENT_VERSION && u.length() > 0) {
                            status.setText("POC STORE v" + v + " tersedia");
                            download(u, "POC-STORE-v" + v + ".apk");
                        } else if (v > 0) {
                            status.setText("POC STORE sudah versi terbaru");
                            Toast.makeText(StoreActivity.this, "Sudah versi terbaru", Toast.LENGTH_SHORT).show();
                        } else {
                            status.setText("OTA tak terjangkau");
                            Toast.makeText(StoreActivity.this, "Gagal mengecek OTA", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void download(String url, String name) {
        if (downloadManager == null) return;
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("Mengunduh " + name);
            req.setDescription("POC STORE · irgun.web.id");
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            long id = downloadManager.enqueue(req);
            getSharedPreferences("store", MODE_PRIVATE).edit().putLong("last_id", id).apply();
            Toast.makeText(this, "Mengunduh " + name, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "POC-STORE/1.0");
        c.setRequestProperty("Accept", "application/json");
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private void moveFocus(int step) {
        View cur = getCurrentFocus();
        int idx = -1;
        for (int i = 0; i < BUTTON_IDS.length; i++) {
            if (cur != null && cur.getId() == BUTTON_IDS[i]) { idx = i; break; }
        }
        idx = (idx < 0 ? 0 : (idx + step + BUTTON_IDS.length) % BUTTON_IDS.length);
        findViewById(BUTTON_IDS[idx]).requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_F5:
                moveFocus(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveFocus(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true; // navigasi hanya atas/bawah
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: {
                View v = getCurrentFocus();
                if (v != null) v.performClick();
                return true;
            }
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
