package com.pocindonesia.pocstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.net.Uri;
import android.os.Build;
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

    private static final int CURRENT_VERSION = 8;
    /* Sumber utama: katalog OTA. Isinya diturunkan langsung dari APK yang
       dipublikasikan (versi + sha256 + URL), jadi daftar selalu menunjuk build terbaru. */
    private static final String OTA_CATALOG = "https://ota.irgun.web.id/check.php";
    private static final String PORTAL_CATALOG = "https://portal.irgun.web.id/api/pocstore";
    private static final String SELF_PKG = "com.pocindonesia.pocstore";

    private TextView status;
    private LinearLayout appList;
    /* Layar radio POC (mis. Hytera PNC360S: 128x160 px @120dpi = 170x213dp).
       resources -small sudah mengecilkan @dimen, ini jaring pengaman kalau
       perangkat mengklasifikasi dirinya lain. */
    private boolean tinyScreen;

    private boolean detectTiny() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float wDp = dm.widthPixels / dm.density;
        float hDp = dm.heightPixels / dm.density;
        return wDp < 220f || hDp < 300f;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private float sp(float v) {
        return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }
    private DownloadManager downloadManager;
    private final java.util.ArrayList<View> nav = new java.util.ArrayList<View>();
    private final BroadcastReceiver downloadDone = new DownloadReceiver();
    private static volatile boolean sActive = false;

    static boolean isActive() { return sActive; }

    private boolean firstResume = true;

    @Override
    protected void onResume() {
        super.onResume();
        sActive = true;
        /* Setiap kembali ke depan: ambil katalog terbaru dari OTA. */
        if (!firstResume) loadCatalog();
        firstResume = false;
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
        new CrashReporter(this, "POC STORE", "1.8").install();
        setContentView(R.layout.activity_store);
        tinyScreen = detectTiny();
        status = (TextView) findViewById(R.id.status);
        appList = (LinearLayout) findViewById(R.id.appList);
        if (tinyScreen) {
            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(12f));
            status.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(8f));
            View root = appList.getRootView();
            int p = dp(4);
            appList.setPadding(0, 0, 0, 0);
            if (root instanceof android.view.ViewGroup) ((android.view.ViewGroup) root).setPadding(p, p, p, p);
        }
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2001);
                }
            } catch (Throwable ignored) {}
        }
        loadCatalog();
    }

    private void loadCatalog() {
        status.setText("Memuat katalog OTA…");
        new Thread(new Runnable() {
            public void run() {
                JSONArray apps = null;
                String source = "ota.irgun.web.id";
                try {
                    apps = catalogFromOta();
                } catch (Throwable ignored) {}

                if (apps == null || apps.length() == 0) {
                    source = "portal.irgun.web.id";
                    try {
                        JSONArray list = new JSONObject(httpGet(PORTAL_CATALOG)).optJSONArray("apps");
                        apps = new JSONArray();
                        if (list != null) {
                            for (int i = 0; i < list.length(); i++) {
                                JSONObject o = list.optJSONObject(i);
                                if (o == null) continue;
                                JSONObject item = new JSONObject();
                                item.put("pkg", o.optString("package_name", ""));
                                item.put("nama", o.optString("nama", "Aplikasi"));
                                item.put("versi", o.optString("versi", ""));
                                item.put("code", 0);
                                item.put("url", o.optString("url", ""));
                                apps.put(item);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                if (apps == null) apps = new JSONArray();
                renderApps(apps, source);
            }
        }).start();
    }

    /** Katalog dari OTA: {pkg: {version, version_name, url, name}} -> array terurut versi terbaru. */
    private JSONArray catalogFromOta() throws Exception {
        JSONObject all = new JSONObject(httpGet(OTA_CATALOG + "?t=" + System.currentTimeMillis()));
        if (!all.optBoolean("ok", true)) throw new Exception("ota tidak ok");
        java.util.List<JSONObject> items = new java.util.ArrayList<JSONObject>();
        java.util.Iterator<String> keys = all.keys();
        while (keys.hasNext()) {
            String pkg = keys.next();
            if (pkg.equals("ok") || pkg.equals("selected") || pkg.equals("device") || pkg.equals("checked_at")) continue;
            JSONObject meta = all.optJSONObject(pkg);
            if (meta == null) continue;
            JSONObject item = new JSONObject();
            item.put("pkg", pkg);
            item.put("nama", meta.optString("name", pkg));
            item.put("versi", meta.optString("version_name", ""));
            item.put("code", meta.optInt("version", 0));
            item.put("url", meta.optString("url", ""));
            items.add(item);
        }
        java.util.Collections.sort(items, new java.util.Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                int c = b.optInt("code", 0) - a.optInt("code", 0);
                return c != 0 ? c : a.optString("nama").compareToIgnoreCase(b.optString("nama"));
            }
        });
        JSONArray out = new JSONArray();
        for (JSONObject o : items) out.put(o);
        return out;
    }

    /** Versi terpasang di perangkat, -1 kalau aplikasi tidak ada. */
    private int installedVersion(String pkg) {
        if (pkg == null || pkg.length() == 0) return -1;
        try {
            android.content.pm.PackageInfo pi =
                    getPackageManager().getPackageInfo(pkg, 0);
            if (Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
            return pi.versionCode;
        } catch (Throwable t) {
            return -1;
        }
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
                    String nama = item.optString("nama", "Aplikasi");
                    String versi = item.optString("versi", "");
                    String pkg = item.optString("pkg", "");
                    int code = item.optInt("code", 0);
                    int pasang = installedVersion(pkg);

                    String label;
                    if (pasang < 0) {
                        label = "Install · " + nama + (versi.isEmpty() ? "" : " " + versi);
                    } else if (code > pasang) {
                        label = "Update · " + nama + (versi.isEmpty() ? "" : " " + versi);
                    } else {
                        label = nama + (versi.isEmpty() ? "" : " " + versi) + " · Terpasang";
                    }
                    addButton(label, new View.OnClickListener() {
                        public void onClick(View v) { download(url, fileNameFrom(url)); }
                    });
                }

                addButton(getString(R.string.btn_about), new View.OnClickListener() {
                    public void onClick(View v) { showAbout(); }
                });

                for (int i = 0; i < nav.size(); i++) {
                    View row = nav.get(i);
                    row.setNextFocusUpId(nav.get((i - 1 + nav.size()) % nav.size()).getId());
                    row.setNextFocusDownId(nav.get((i + 1) % nav.size()).getId());
                }
                status.setText(source + " · " + apps.length() + " app");
                if (!nav.isEmpty()) nav.get(0).requestFocus();
            }
        });
    }

    private void addButton(String label, View.OnClickListener click) {
        final android.content.res.Resources res = getResources();
        int padH = res.getDimensionPixelSize(R.dimen.row_pad_h);
        int padV = res.getDimensionPixelSize(R.dimen.row_pad_v);
        float textPx = res.getDimension(R.dimen.row_text_size);
        int minH = res.getDimensionPixelSize(R.dimen.row_min_height);
        if (tinyScreen) {                    // prioritas untuk layar 128x160
            padH = dp(5f);
            padV = dp(4f);
            minH = dp(22f);
            textPx = sp(10f);
        }

        TextView row = new TextView(this);
        row.setText(label);
        row.setFocusable(true);
        row.setClickable(true);
        row.setFocusableInTouchMode(false);
        row.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
        row.setTextColor(res.getColorStateList(R.color.row_text));
        row.setBackgroundResource(R.drawable.row_bg);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setPadding(padH, padV, padH, padV);
        row.setMinHeight(minH);
        row.setId(View.generateViewId());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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

    private void showAbout() {
        String versi = String.valueOf(CURRENT_VERSION);
        try {
            versi = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) {}
        String pesan = "POC STORE " + versi + "\n"
                + getString(R.string.about_desc) + "\n\n"
                + "Sumber: " + getString(R.string.about_source);
        new AlertDialog.Builder(this)
                .setTitle("About")
                .setMessage(pesan)
                .setPositiveButton("OK", null)
                .show();
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

    private void download(String url, String name) {
        if (downloadManager == null) {
            Toast.makeText(this, "DownloadManager tidak tersedia", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            /* Tulis ke direktori aplikasi sendiri: tidak butuh WRITE_EXTERNAL_STORAGE,
               dan itulah yang gagal di PNC360S ("No permission to write .../Download"). */
            java.io.File target = ApkProvider.file(this);
            if (target.exists() && !target.delete()) {
                Toast.makeText(this, "Berkas unduhan lama tak bisa dihapus", Toast.LENGTH_SHORT).show();
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("Mengunduh " + name);
            req.setDescription("POC STORE · irgun.web.id");
            req.setMimeType("application/vnd.android.package-archive");
            try {
                req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                        ApkProvider.FILE_NAME);
            } catch (Throwable ignored) { /* receiver akan pakai URI dari DownloadManager */ }
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            long id = downloadManager.enqueue(req);
            getSharedPreferences("store", MODE_PRIVATE).edit()
                    .putLong("last_id", id)
                    .putString("last_name", name)
                    .apply();
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
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache, no-store");
        c.setRequestProperty("Pragma", "no-cache");
        c.setRequestProperty("User-Agent", "POC-STORE/1.8");
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
