package com.uniqueselect.hmr;

import android.webkit.JavascriptInterface;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KsuBridge {
    private static final String TAG = "HMR_Bridge";
    private final MainActivity activity;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public KsuBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void exec(final String cmd, final String env, final String callbackName) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                executeRootCommand(cmd, callbackName);
            }
        });
    }

    private void executeRootCommand(String rawCmd, String callbackName) {
        int errno = 0;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try {
            String appDir = activity.getFilesDir().getAbsolutePath();
            String localHotspotCtl = appDir + "/bin/hotspot-ctl";

            // Transparently redirect Magisk module path to the APK internal extracted binary
            String execCmd = rawCmd;
            if (execCmd.contains("/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl")) {
                execCmd = execCmd.replace("/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl", localHotspotCtl);
            }

            // Execute via root shell (su)
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", execCmd});

            BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = outReader.readLine()) != null) {
                stdout.append(line).append("\n");
            }
            outReader.close();

            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errReader.readLine()) != null) {
                stderr.append(line).append("\n");
            }
            errReader.close();

            errno = process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "Error executing root command: " + rawCmd, e);
            errno = -1;
            stderr.append(e.getMessage());
        }

        final int finalErrno = errno;
        final String finalStdout = stdout.toString().trim();
        final String finalStderr = stderr.toString().trim();

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String js = String.format("if (typeof window['%s'] === 'function') { window['%s'](%d, %s, %s); }",
                        callbackName, callbackName, finalErrno,
                        JSONObject.quote(finalStdout), JSONObject.quote(finalStderr));
                activity.getWebView().evaluateJavascript(js, null);
            }
        });
    }
}
