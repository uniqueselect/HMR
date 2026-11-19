package com.uniqueselect.hmr;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "HMR_MainActivity";
    private WebView webView;
    private KsuBridge ksuBridge;
    private BroadcastReceiver hotspotStateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Apply dark cyber theme to status and navigation bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#0c1017"));
            window.setNavigationBarColor(Color.parseColor("#0c1017"));
        }

        webView = findViewById(R.id.webView);
        ksuBridge = new KsuBridge(this);
        setupWebView();

        // Listen for OS-level Hotspot state changes (Settings / Quick Settings Tile)
        registerHotspotBroadcastReceiver();

        // Extract internal assets and prime persistent root session asynchronously
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                extractAssets();
                primeRootSession();
            }
        });
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Inject persistent root bridge matching KernelSU WebUI specification
        webView.addJavascriptInterface(ksuBridge, "ksu");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "HMR WebUI loaded successfully: " + url);
            }
        });

        // Load responsive mobile WebUI
        webView.loadUrl("file:///android_asset/webroot/index.html");
    }

    private void registerHotspotBroadcastReceiver() {
        hotspotStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && "android.net.wifi.WIFI_AP_STATE_CHANGED".equals(intent.getAction())) {
                    int state = intent.getIntExtra("wifi_state", -1);
                    Log.d(TAG, "OS Hotspot state broadcast received: " + state);
                    // Instant synchronization with the WebUI
                    notifyHotspotStateChanged();
                }
            }
        };

        IntentFilter filter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        registerReceiver(hotspotStateReceiver, filter);
    }

    public void notifyHotspotStateChanged() {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("if (typeof refreshStatus === 'function') { refreshStatus(); }", null);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sync hotspot status immediately whenever returning to the foreground
        notifyHotspotStateChanged();
    }

    @Override
    protected void onDestroy() {
        if (hotspotStateReceiver != null) {
            try {
                unregisterReceiver(hotspotStateReceiver);
            } catch (Exception ignored) {}
            hotspotStateReceiver = null;
        }

        if (ksuBridge != null) {
            ksuBridge.destroy();
            ksuBridge = null;
        }

        super.onDestroy();
    }

    private void extractAssets() {
        try {
            File binDir = new File(getFilesDir(), "bin");
            if (!binDir.exists()) {
                binDir.mkdirs();
            }

            copyAssetToFile("bin/hotspot-ctl", new File(binDir, "hotspot-ctl"));
            copyAssetToFile("bin/hotspot_helper.jar", new File(binDir, "hotspot_helper.jar"));
            copyAssetToFile("module.prop", new File(getFilesDir(), "module.prop"));

            File ctl = new File(binDir, "hotspot-ctl");
            ctl.setExecutable(true, false);
            ctl.setReadable(true, false);

            File jar = new File(binDir, "hotspot_helper.jar");
            jar.setReadable(true, false);

            // Ensure permissions via persistent root shell session
            if (ksuBridge != null) {
                ksuBridge.exec("chmod -R 755 " + getFilesDir().getAbsolutePath(), "", null);
            }

            Log.d(TAG, "HMR internal binaries extracted to " + binDir.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error extracting assets", e);
        }
    }

    private void copyAssetToFile(String assetPath, File outFile) {
        try {
            InputStream in = getAssets().open(assetPath);
            OutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy asset " + assetPath + " to " + outFile.getAbsolutePath(), e);
        }
    }

    private void primeRootSession() {
        if (ksuBridge != null) {
            ksuBridge.exec("id", "", null);
            Log.d(TAG, "Persistent root shell primed and verified");
        }
    }

    public WebView getWebView() {
        return webView;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
