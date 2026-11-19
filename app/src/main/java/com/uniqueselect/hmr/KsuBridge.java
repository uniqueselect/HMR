package com.uniqueselect.hmr;

import android.webkit.JavascriptInterface;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KsuBridge {
    private static final String TAG = "HMR_Bridge";
    private final MainActivity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Persistent interactive root shell session
    // Spawns 'su' once on app launch to eliminate repetitive Magisk toast popups
    private Process rootProcess;
    private BufferedWriter rootWriter;
    private BufferedReader rootReader;
    private final Object shellLock = new Object();

    public KsuBridge(MainActivity activity) {
        this.activity = activity;
    }

    private void ensureShellOpen() throws Exception {
        synchronized (shellLock) {
            boolean isAlive = false;
            if (rootProcess != null) {
                try {
                    rootProcess.exitValue();
                    isAlive = false;
                } catch (IllegalThreadStateException e) {
                    isAlive = true;
                }
            }

            if (!isAlive) {
                closeShell();
                ProcessBuilder pb = new ProcessBuilder("su");
                pb.redirectErrorStream(true); // Merge stderr into stdout stream
                rootProcess = pb.start();
                rootWriter = new BufferedWriter(new OutputStreamWriter(rootProcess.getOutputStream()));
                rootReader = new BufferedReader(new InputStreamReader(rootProcess.getInputStream()));
                Log.d(TAG, "Persistent interactive root shell session established");
            }
        }
    }

    private void closeShell() {
        synchronized (shellLock) {
            if (rootWriter != null) {
                try {
                    rootWriter.write("exit\n");
                    rootWriter.flush();
                    rootWriter.close();
                } catch (Exception ignored) {}
                rootWriter = null;
            }
            if (rootReader != null) {
                try { rootReader.close(); } catch (Exception ignored) {}
                rootReader = null;
            }
            if (rootProcess != null) {
                try { rootProcess.destroy(); } catch (Exception ignored) {}
                rootProcess = null;
            }
        }
    }

    public void destroy() {
        executor.shutdownNow();
        closeShell();
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

        try {
            String appDir = activity.getFilesDir().getAbsolutePath();
            String localHotspotCtl = appDir + "/bin/hotspot-ctl";

            // Transparently redirect Magisk module path to internal extracted binary
            String execCmd = rawCmd;
            if (execCmd.contains("/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl")) {
                execCmd = execCmd.replace("/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl", localHotspotCtl);
            }

            synchronized (shellLock) {
                ensureShellOpen();
                String delimiter = "__HMR_DONE_" + System.currentTimeMillis() + "_" + ((int)(Math.random() * 10000)) + "__";

                // Execute command and output exit code delimiter
                rootWriter.write(execCmd + "\n");
                rootWriter.write("echo \"" + delimiter + ":$?\"\n");
                rootWriter.flush();

                boolean gotDelimiter = false;
                String line;
                while ((line = rootReader.readLine()) != null) {
                    if (line.startsWith(delimiter + ":")) {
                        String codeStr = line.substring((delimiter + ":").length()).trim();
                        try {
                            errno = Integer.parseInt(codeStr);
                        } catch (Exception e) {
                            errno = 0;
                        }
                        gotDelimiter = true;
                        break;
                    }
                    stdout.append(line).append("\n");
                }
                if (!gotDelimiter) {
                    Log.w(TAG, "Shell process closed or reached EOF unexpectedly");
                    closeShell();
                    errno = -1;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in persistent root shell execution: " + rawCmd, e);
            errno = -1;
            stdout.append(e.getMessage());
            closeShell();
        }

        final int finalErrno = errno;
        final String finalStdout = stdout.toString().trim();

        if (callbackName != null && !callbackName.isEmpty()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String js = String.format("if (typeof window['%s'] === 'function') { window['%s'](%d, %s, \"\"); }",
                            callbackName, callbackName, finalErrno,
                            JSONObject.quote(finalStdout));
                    activity.getWebView().evaluateJavascript(js, null);
                }
            });
        }
    }
}
