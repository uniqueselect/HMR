package com.uniqueselect.hmr;

import android.app.Activity;
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
        setupWebView();

        // Extract assets and request root in background
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                extractAssets();
                requestRoot();
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

        // Inject KSU WebUI bridge directly matching the KernelSU spec
        webView.addJavascriptInterface(new KsuBridge(this), "ksu");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "HMR WebUI loaded successfully: " + url);
            }
        });

        // Load our responsive mobile WebUI
        webView.loadUrl("file:///android_asset/webroot/index.html");
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

            // Ensure permissions via su if available
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod -R 755 " + getFilesDir().getAbsolutePath()});
            } catch (Exception ignored) {}

            Log.d(TAG, "HMR internal binaries extracted to " + binDir.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error extracting assets", e);
        }
    }

    private void copyAssetToFile(String assetPath, File outFile) {
        try {
            if (outFile.exists() && outFile.length() > 0) {
                // Already extracted and valid
                return;
            }
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

    private void requestRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            int code = p.waitFor();
            if (code != 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Root access denied. HMR requires root permissions.", Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                Log.d(TAG, "Root access granted successfully!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking root access", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Root not detected or su binary missing.", Toast.LENGTH_LONG).show();
                }
            });
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
