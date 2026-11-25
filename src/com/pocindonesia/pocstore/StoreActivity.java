package com.pocindonesia.pocstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class StoreActivity extends Activity {

    private static final int CURRENT_VERSION = 5;
    private static final String CATALOG_URL = "https://portal.irgun.web.id/api/pocstore";
    private static final String GITHUB_APK_LIST =
            "https://api.github.com/repos/irgunawan/PocDial/contents/apk";
    private static final String FALLBACK_DIAL_URL =
            "https://raw.githubusercontent.com/irgunawan/PocDial/master/apk/POC-DIAL-v1.0.apk";
    private static final String OTA_CHECK =
            "https://ota.irgun.web.id/check.php?pkg=com.pocindonesia.pocstore";

    private TextView status;
    private LinearLayout appList;
    private DownloadManager downloadManager;
    private final java.util.ArrayList<View> nav = new java.util.ArrayList<View>();
    private final BroadcastReceiver downloadDone = new DownloadReceiver();
    private static volatile boolean sActive = false;

    static boolean isActive() { return sActive; }

    @Override
    protected void onResume() {
        super.onResume();
        sActive = true;
        try {
            registerReceiver(downloadDone, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        sActive = false;
        try { unregisterReceiver(downloadDone); } catch (Throwable ignored) {}
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);
        status = (TextView) findViewById(R.id.status);
        appList = (LinearLayout) findViewById(R.id.appList);
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        loadCatalog();
    }

    private void loadCatalog() {
        status.setText("Memuat katalog dari portal…");
        new Thread(new Runnable() {
            public void run() {
                JSONArray apps = null;
                String source = "portal.irgun.web.id";
                try {
                    String body = httpGet(CATALOG_URL);
                    apps = new JSONObject(body).optJSONArray("apps");
                } catch (Throwable ignored) {}

                if (apps == null || apps.length() == 0) {
                    source = "GitHub (cadangan)";
                    apps = new JSONArray();
                    try {
                        JSONArray files = new JSONArray(httpGet(GITHUB_APK_LIST));
                        for (int i = 0; i < files.length(); i++) {
                            JSONObject o = files.getJSONObject(i);
                            String nm = o.optString("name", "");
                            String url = o.optString("download_url", "");
                            if (nm.endsWith(".apk") && url.length() > 0) {
                                JSONObject item = new JSONObject();
                                item.put("nama", "POC DIAL");
                                item.put("versi", "1.0");
                                item.put("url", url);
                                apps.put(item);
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                    if (apps.length() == 0) {
                        try {
                            JSONObject item = new JSONObject();
                            item.put("nama", "POC DIAL");
                            item.put("versi", "1.0");
                            item.put("url", FALLBACK_DIAL_URL);
                            apps.put(item);
                        } catch (Throwable ignored) {}
                    }
                }
                renderApps(apps, source);
            }
        }).start();
    }

    private void renderApps(final JSONArray apps, final String source) {
        runOnUiThread(new Runnable() {
            public void run() {
                appList.removeAllViews();
                nav.clear();

                for (int i = 0; i < apps.length(); i++) {
                    final JSONObject item = apps.optJSONObject(i);
                    if (item == null) continue;
                    final String url = item.optString("url", "");
                    if (!url.startsWith("http")) continue;
                    String label = item.optString("nama", "Aplikasi");
                    String versi = item.optString("versi", "");
                    if (!versi.isEmpty()) label = label + " " + versi;
                    addButton(label, new View.OnClickListener() {
                        public void onClick(View v) { download(url, fileNameFrom(url)); }
                    });
                }

                addButton("UPDATE POC STORE", new View.OnClickListener() {
                    public void onClick(View v) { checkSelfUpdate(); }
                });
                addButton("ABOUT · irgun.web.id", new View.OnClickListener() {
                    public void onClick(View v) {
                        new AlertDialog.Builder(StoreActivity.this)
                                .setTitle("POC STORE")
                                .setMessage("POC STORE v1.2\nKatalog diatur di portal.irgun.web.id\n\nirgun.web.id")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });

                for (int i = 0; i < nav.size(); i++) {
                    View row = nav.get(i);
                    row.setNextFocusUpId(nav.get((i - 1 + nav.size()) % nav.size()).getId());
                    row.setNextFocusDownId(nav.get((i + 1) % nav.size()).getId());
                }
                status.setText("Katalog: " + source + " · " + apps.length() + " aplikasi");
                if (!nav.isEmpty()) nav.get(0).requestFocus();
            }
        });
    }

    private void addButton(String label, View.OnClickListener click) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setFocusable(true);
        row.setClickable(true);
        row.setTextSize(15f);
        row.setTextColor(Color.rgb(33, 33, 33));
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setPadding(14, 12, 14, 12);
        row.setId(View.generateViewId());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 46);
        row.setLayoutParams(lp);
        row.setOnClickListener(click);
        appList.addView(row);
        nav.add(row);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(221, 221, 221));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        appList.addView(divider);
    }

    private static String fileNameFrom(String url) {
        String name = url;
        int q = name.indexOf('?');
        if (q > 0) name = name.substring(0, q);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (!name.toLowerCase().endsWith(".apk")) name = "POC-STORE.apk";
        return name;
    }

    private void checkSelfUpdate() {
        status.setText("Mengecek OTA…");
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
        c.setRequestProperty("User-Agent", "POC-STORE/1.1");
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
        if (nav.isEmpty()) return;
        View cur = getCurrentFocus();
        int idx = -1;
        for (int i = 0; i < nav.size(); i++) {
            if (nav.get(i) == cur) { idx = i; break; }
        }
        idx = (idx < 0 ? 0 : (idx + step + nav.size()) % nav.size());
        nav.get(idx).requestFocus();
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
                return true;
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
