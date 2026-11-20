package com.antigravity.hotspot;

import android.os.IBinder;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class HotspotHelper {

    private static final String DEFAULT_BACKUP_PATH = "/data/adb/root_hotspot_original_backup.json";
    private static final String BLOCKED_MACS_FILE = "/data/adb/root_hotspot_blocked_macs.txt";
    private static final String APEX_XML_STORE = "/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml";
    private static final String LEGACY_XML_STORE = "/data/misc/wifi/WifiConfigStoreSoftAp.xml";
    private static final String HOSTNAME_CACHE_FILE = "/data/local/tmp/.hmr_hostname_cache_v2";
    private static final String LEGACY_CACHE_FILE = "/data/local/tmp/.hmr_hostname_cache";
    private static final Map<String, String> sHostnameCache = new HashMap<>();

    private static void loadHostnameCache() {
        try {
            File oldFile = new File(LEGACY_CACHE_FILE);
            if (oldFile.exists()) {
                oldFile.delete();
            }
        } catch (Throwable ignored) {}

        File f = new File(HOSTNAME_CACHE_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String mac = line.substring(0, eq).trim().toUpperCase();
                    String name = line.substring(eq + 1).trim();
                    if (name.contains("@")) {
                        name = name.substring(name.indexOf('@') + 1).trim();
                    }
                    if (!mac.isEmpty() && !name.isEmpty()) {
                        sHostnameCache.put(mac, name);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void saveHostnameCache() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(HOSTNAME_CACHE_FILE))) {
            for (Map.Entry<String, String> entry : sHostnameCache.entrySet()) {
                bw.write(entry.getKey() + "=" + entry.getValue());
                bw.newLine();
            }
        } catch (Throwable ignored) {}
    }

    public static void main(String[] args) {
        disableHiddenApiRestrictions();

        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        try {
            switch (command) {
                case "check":
                    doCheck();
                    break;
                case "status":
                    doStatus();
                    break;
                case "get":
                    doGet();
                    break;
                case "set":
                    if (args.length < 2) {
                        outputError("Missing JSON or argument for 'set' command");
                        System.exit(1);
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < args.length; i++) {
                        if (i > 1) sb.append(" ");
                        sb.append(args[i]);
                    }
                    doSet(sb.toString());
                    break;
                case "set-b64":
                    if (args.length < 2) {
                        outputError("Missing Base64 payload for 'set-b64' command");
                        System.exit(1);
                    }
                    byte[] decoded = Base64.getDecoder().decode(args[1].trim());
                    String jsonFromB64 = new String(decoded, StandardCharsets.UTF_8);
                    doSet(jsonFromB64);
                    break;
                case "backup":
                    String bPath = args.length > 1 ? args[1] : DEFAULT_BACKUP_PATH;
                    doBackup(bPath);
                    break;
                case "restore":
                case "restore-original":
                    String rPath = args.length > 1 ? args[1] : DEFAULT_BACKUP_PATH;
                    doRestore(rPath);
                    break;
                case "block":
                    if (args.length < 2) {
                        outputError("Missing MAC address for 'block' command");
                        System.exit(1);
                    }
                    doBlock(args[1]);
                    break;
                case "unblock":
                    if (args.length < 2) {
                        outputError("Missing MAC address for 'unblock' command");
                        System.exit(1);
                    }
                    doUnblock(args[1]);
                    break;
                case "start":
                    doStart();
                    break;
                case "stop":
                    doStop();
                    break;
                case "restart":
                    doRestart();
                    break;
                case "framework":
                    doFramework();
                    break;
                case "permissions":
                    doPermissions();
                    break;
                case "clients":
                    doClients();
                    break;
                default:
                    outputError("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            outputException(t);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("Root Hotspot Manager Backend Helper v1.2 (Universal Android 11+ AOSP / GSI / OEM)");
        System.err.println("Usage: hotspot-ctl <check|status|get|set <json>|set-b64 <b64>|backup [path]|restore [path]|block <mac>|unblock <mac>|start|stop|restart|framework|permissions>");
    }

    private static void disableHiddenApiRestrictions() {
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntimeMethod = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", new Class<?>[0]);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class<?>[]{String[].class});
            Object vmRuntime = getRuntimeMethod.invoke(null);
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{"L"}});
        } catch (Throwable ignored) {
        }
    }

    private static Object getWifiService() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method getService = sm.getMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, "wifi");
        if (binder == null) {
            throw new IllegalStateException("Wi-Fi service binder not available. Is Wi-Fi subsystem running?");
        }
        Class<?> stub = Class.forName("android.net.wifi.IWifiManager$Stub");
        Method asInterface = stub.getMethod("asInterface", IBinder.class);
        return asInterface.invoke(null, binder);
    }

    private static Object getSoftApConfiguration() throws Exception {
        Object wifiService = getWifiService();
        Method m = wifiService.getClass().getMethod("getSoftApConfiguration");
        return m.invoke(wifiService);
    }

    private static boolean setSoftApConfiguration(Object config) throws Exception {
        Object wifiService = getWifiService();
        Method m = wifiService.getClass().getMethod("setSoftApConfiguration", config.getClass(), String.class);
        Object res = m.invoke(wifiService, config, "android");
        if (res instanceof Boolean) {
            return (Boolean) res;
        }
        return true;
    }

    private static boolean isWifiClientEnabled() {
        try {
            Object wifiService = getWifiService();
            Method m = wifiService.getClass().getMethod("getWifiEnabledState");
            int state = (Integer) m.invoke(wifiService);
            return (state == 3); // 3 = WIFI_STATE_ENABLED
        } catch (Throwable t) {
            return false;
        }
    }

    private static void setWifiClientEnabled(boolean enabled) {
        try {
            Object wifiService = getWifiService();
            Method m = wifiService.getClass().getMethod("setWifiEnabled", String.class, boolean.class);
            m.invoke(wifiService, "android", enabled);
        } catch (Throwable t) {
            try {
                Object wifiService = getWifiService();
                Method m = wifiService.getClass().getMethod("setWifiEnabled", boolean.class);
                m.invoke(wifiService, enabled);
            } catch (Throwable ignored) {
                runShellCommand("cmd wifi set-wifi-enabled " + (enabled ? "enabled" : "disabled"));
            }
        }
    }

    private static boolean startHotspot(Object config) {
        boolean wasWifiClientOn = isWifiClientEnabled();
        boolean started = false;

        try {
            Object wifiService = getWifiService();
            // Method 1: IWifiManager.startTetheredHotspot(SoftApConfiguration)
            try {
                Method m = wifiService.getClass().getMethod("startTetheredHotspot", Class.forName("android.net.wifi.SoftApConfiguration"));
                Object res = m.invoke(wifiService, config);
                if (Boolean.TRUE.equals(res)) {
                    started = true;
                }
            } catch (Throwable t) {
                // Try null (starts with persisted SoftApConfiguration from store)
                try {
                    Method m = wifiService.getClass().getMethod("startTetheredHotspot", Class.forName("android.net.wifi.SoftApConfiguration"));
                    Object res = m.invoke(wifiService, new Object[]{null});
                    if (Boolean.TRUE.equals(res)) {
                        started = true;
                    }
                } catch (Throwable ignored) {}
            }

            // Method 2: IWifiManager.startSoftAp(SoftApConfiguration or WifiConfiguration)
            if (!started) {
                for (Method m : wifiService.getClass().getMethods()) {
                    if (m.getName().equals("startSoftAp") && m.getParameterTypes().length == 1) {
                        try {
                            Object res = m.invoke(wifiService, config);
                            if (Boolean.TRUE.equals(res)) {
                                started = true;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Wait a brief moment for radio state update
        try { Thread.sleep(600); } catch (InterruptedException ignored) {}

        // CRITICAL FIX: If client Wi-Fi was OFF before starting hotspot, guarantee it stays OFF!
        if (!wasWifiClientOn && isWifiClientEnabled()) {
            setWifiClientEnabled(false);
        }

        notifyTetheringService();

        return (getWifiApState() == 13 || started);
    }

    public static void notifyTetheringService() {
        // Method A: ITetheringConnector binder
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder b = (IBinder) getService.invoke(null, "tethering");
            if (b != null) {
                Class<?> stubClass = Class.forName("android.net.ITetheringConnector$Stub");
                Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
                Object connector = asInterface.invoke(null, b);
                if (connector != null) {
                    for (Method m : connector.getClass().getMethods()) {
                        if (m.getName().equals("tether")) {
                            try {
                                if (m.getParameterTypes().length == 4) {
                                    m.invoke(connector, "ap0", "com.android.shell", null, null);
                                    break;
                                } else if (m.getParameterTypes().length == 2) {
                                    m.invoke(connector, "ap0", null);
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Method B: ConnectivityManager binder
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder b = (IBinder) getService.invoke(null, "connectivity");
            if (b != null) {
                Class<?> stubClass = Class.forName("android.net.IConnectivityManager$Stub");
                Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
                Object cm = asInterface.invoke(null, b);
                if (cm != null) {
                    for (Method m : cm.getClass().getMethods()) {
                        if (m.getName().equals("tether") && m.getParameterTypes().length >= 1 && m.getParameterTypes()[0] == String.class) {
                            try {
                                if (m.getParameterTypes().length == 1) {
                                    m.invoke(cm, "ap0");
                                } else if (m.getParameterTypes().length == 2) {
                                    m.invoke(cm, "ap0", "com.android.shell");
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Method C: Shell tethering trigger fallback
        runShellCommand("cmd connectivity tether ap0 2>/dev/null; cmd tethering tether ap0 2>/dev/null");
    }

    private static boolean stopHotspot() {
        boolean stopped = false;
        try {
            Object wifiService = getWifiService();
            for (Method m : wifiService.getClass().getMethods()) {
                if (m.getName().equals("stopSoftAp") && m.getParameterTypes().length == 0) {
                    try {
                        Object res = m.invoke(wifiService);
                        if (Boolean.TRUE.equals(res)) stopped = true;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        runShellCommand("cmd wifi stop-softap");
        return true;
    }

    private static int getWifiApState() {
        try {
            Object wifiService = getWifiService();
            Method m = wifiService.getClass().getMethod("getWifiApEnabledState");
            return (Integer) m.invoke(wifiService);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String getApStateName(int state) {
        switch (state) {
            case 10: return "DISABLING";
            case 11: return "DISABLED";
            case 12: return "ENABLING";
            case 13: return "ENABLED";
            case 14: return "FAILED";
            default: return "UNKNOWN (" + state + ")";
        }
    }

    // Direct XML synchronization for /data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml
    private static void syncXmlDirectly(JSONObject updates) {
        String[] paths = { APEX_XML_STORE, LEGACY_XML_STORE };
        for (String path : paths) {
            File file = new File(path);
            if (file.exists() && file.canWrite()) {
                try {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append("\n");
                    }
                    String xml = sb.toString();
                    boolean modified = false;

                    // Ensure ClientControlByUser is false so Android Settings displays connected devices
                    String ccbPattern = "<boolean\\s+name=\"ClientControlByUser\"\\s+value=\"[^\"]*\"\\s*/>";
                    String ccbReplacement = "<boolean name=\"ClientControlByUser\" value=\"false\" />";
                    if (xml.matches("(?s).*" + ccbPattern + ".*")) {
                        xml = xml.replaceAll(ccbPattern, ccbReplacement);
                        modified = true;
                    }

                    if (updates.has("hidden")) {
                        boolean hidden = updates.getBoolean("hidden");
                        String oldPattern = "<boolean\\s+name=\"HiddenSSID\"\\s+value=\"[^\"]*\"\\s*/>";
                        String replacement = "<boolean name=\"HiddenSSID\" value=\"" + hidden + "\" />";
                        if (xml.matches("(?s).*" + oldPattern + ".*")) {
                            xml = xml.replaceAll(oldPattern, replacement);
                            modified = true;
                        }
                    }

                    if (updates.has("ssid")) {
                        String ssid = updates.getString("ssid").trim();
                        String oldPattern = "<string\\s+name=\"SSID\">[^<]*</string>";
                        String replacement = "<string name=\"SSID\">" + escapeXml(ssid) + "</string>";
                        xml = xml.replaceAll(oldPattern, replacement);
                        modified = true;
                    }

                    if (updates.has("max_clients")) {
                        int maxClients = updates.getInt("max_clients");
                        String oldPattern = "<int\\s+name=\"MaxNumberOfClients\"\\s+value=\"[^\"]*\"\\s*/>";
                        String replacement = "<int name=\"MaxNumberOfClients\" value=\"" + maxClients + "\" />";
                        xml = xml.replaceAll(oldPattern, replacement);
                        modified = true;
                    }

                    if (updates.has("auto_shutdown")) {
                        boolean autoShutdown = updates.getBoolean("auto_shutdown");
                        String oldPattern = "<boolean\\s+name=\"AutoShutdownEnabled\"\\s+value=\"[^\"]*\"\\s*/>";
                        String replacement = "<boolean name=\"AutoShutdownEnabled\" value=\"" + autoShutdown + "\" />";
                        xml = xml.replaceAll(oldPattern, replacement);
                        modified = true;
                    }

                    if (updates.has("shutdown_timeout_millis")) {
                        long timeoutMillis = updates.getLong("shutdown_timeout_millis");
                        String oldPattern = "<long\\s+name=\"ShutdownTimeoutMillis\"\\s+value=\"[^\"]*\"\\s*/>";
                        String replacement = "<long name=\"ShutdownTimeoutMillis\" value=\"" + timeoutMillis + "\" />";
                        xml = xml.replaceAll(oldPattern, replacement);
                        modified = true;
                    }

                    if (updates.has("band")) {
                        int band = updates.getInt("band");
                        String oldPattern = "<int\\s+name=\"ApBand\"\\s+value=\"[^\"]*\"\\s*/>";
                        String replacement = "<int name=\"ApBand\" value=\"" + band + "\" />";
                        xml = xml.replaceAll(oldPattern, replacement);
                        modified = true;
                    }

                    if (updates.has("channel")) {
                        int channel = updates.getInt("channel");
                        String oldPattern = "<int\\s+name=\"Channel\"\\s+value=\"[^\"]*\"\\s*/>";
                        String replacement = "<int name=\"Channel\" value=\"" + channel + "\" />";
                        xml = xml.replaceAll(oldPattern, replacement);
                        modified = true;
                    }

                    if (modified) {
                        try (FileWriter fw = new FileWriter(file)) {
                            fw.write(xml);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    // Direct XML fallback parser for /data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml
    private static JSONObject parseSoftApXmlDirectly() {
        String[] paths = { APEX_XML_STORE, LEGACY_XML_STORE };
        for (String path : paths) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                try {
                    return parseSoftApXmlFile(f);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static JSONObject parseSoftApXmlFile(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        String xml = sb.toString();
        JSONObject json = new JSONObject();

        json.put("ssid", extractXmlValue(xml, "string", "SSID", "HiFi"));
        json.put("passphrase", extractXmlValue(xml, "string", "Passphrase", ""));
        json.put("band", Integer.parseInt(extractXmlValue(xml, "int", "ApBand", "1")));
        json.put("channel", Integer.parseInt(extractXmlValue(xml, "int", "Channel", "0")));
        json.put("hidden", Boolean.parseBoolean(extractXmlValue(xml, "boolean", "HiddenSSID", "false")));
        int sec = Integer.parseInt(extractXmlValue(xml, "int", "SecurityType", "1"));
        json.put("security_type", sec);
        json.put("security_type_name", getSecurityTypeName(sec));
        json.put("max_clients", Integer.parseInt(extractXmlValue(xml, "int", "MaxNumberOfClients", "0")));
        json.put("auto_shutdown", Boolean.parseBoolean(extractXmlValue(xml, "boolean", "AutoShutdownEnabled", "true")));
        json.put("shutdown_timeout_millis", Long.parseLong(extractXmlValue(xml, "long", "ShutdownTimeoutMillis", "0")));
        json.put("client_control_by_user", Boolean.parseBoolean(extractXmlValue(xml, "boolean", "ClientControlByUser", "false")));
        json.put("blocked_clients", new JSONArray());
        json.put("source", "XML_STORE (" + file.getAbsolutePath() + ")");
        return json;
    }

    private static String extractXmlValue(String xml, String tag, String name, String defaultVal) {
        Pattern p = Pattern.compile("<" + tag + "\\s+name=\"" + name + "\"\\s+value=\"([^\"]*)\"\\s*/>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(xml);
        if (m.find()) {
            return m.group(1);
        }
        Pattern p2 = Pattern.compile("<" + tag + "\\s+name=\"" + name + "\">([^<]*)</" + tag + ">", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(xml);
        if (m2.find()) {
            return m2.group(1);
        }
        return defaultVal;
    }

    private static void doCheck() {
        JSONObject res = new JSONObject();
        try {
            res.put("success", true);
            res.put("api_level", Build.VERSION.SDK_INT);
            res.put("android_version", Build.VERSION.RELEASE);
            res.put("device_model", Build.MODEL);
            res.put("device_brand", Build.BRAND);
            res.put("device_manufacturer", Build.MANUFACTURER);
            res.put("device_device", Build.DEVICE);
            res.put("device_hardware", Build.HARDWARE);
            res.put("device_product", Build.PRODUCT);
            res.put("device_fingerprint", Build.FINGERPRINT);

            boolean isRoot = false;
            try {
                Class<?> processClass = Class.forName("android.os.Process");
                Method myUid = processClass.getMethod("myUid");
                int uid = (Integer) myUid.invoke(null);
                isRoot = (uid == 0);
                res.put("uid", uid);
                res.put("is_root", isRoot);
            } catch (Throwable t) {
                res.put("is_root", false);
            }

            boolean wifiServiceOk = false;
            boolean softApApiOk = false;
            String errorMsg = null;

            try {
                Object ws = getWifiService();
                wifiServiceOk = (ws != null);
                Object cfg = getSoftApConfiguration();
                softApApiOk = (cfg != null);
            } catch (Throwable t) {
                errorMsg = t.getMessage();
            }

            res.put("wifi_service_ok", wifiServiceOk);
            res.put("softap_api_ok", softApApiOk);
            res.put("original_backup_exists", new File(DEFAULT_BACKUP_PATH).exists());
            res.put("ap_state", getApStateName(getWifiApState()));
            res.put("apex_xml_store_exists", new File(APEX_XML_STORE).exists());

            if (errorMsg != null) {
                res.put("diagnostic_warning", errorMsg);
            }

            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doFramework() {
        JSONObject res = new JSONObject();
        try {
            res.put("success", true);
            res.put("android_version", Build.VERSION.RELEASE);
            res.put("sdk_int", Build.VERSION.SDK_INT);
            res.put("device", Build.MODEL + " (" + Build.DEVICE + ")");

            JSONArray jarLocations = new JSONArray();
            String[] checkJars = {
                "/apex/com.android.wifi/javalib/framework-wifi.jar",
                "/apex/com.android.wifi/javalib/service-wifi.jar",
                "/apex/com.android.tethering/javalib/framework-tethering.jar",
                "/system/framework/framework.jar"
            };
            for (String p : checkJars) {
                File f = new File(p);
                JSONObject j = new JSONObject();
                j.put("path", p);
                j.put("exists", f.exists());
                j.put("size_bytes", f.exists() ? f.length() : 0);
                jarLocations.put(j);
            }
            res.put("framework_jars", jarLocations);

            JSONObject binderInfo = new JSONObject();
            try {
                Class<?> stub = Class.forName("android.net.wifi.IWifiManager$Stub");
                binderInfo.put("stub_found", true);
                Field getMethod = stub.getDeclaredField("TRANSACTION_getSoftApConfiguration");
                getMethod.setAccessible(true);
                binderInfo.put("TRANSACTION_getSoftApConfiguration", getMethod.get(null));
                Field setMethod = stub.getDeclaredField("TRANSACTION_setSoftApConfiguration");
                setMethod.setAccessible(true);
                binderInfo.put("TRANSACTION_setSoftApConfiguration", setMethod.get(null));
            } catch (Throwable t) {
                binderInfo.put("reflection_note", t.getMessage());
            }
            res.put("binder_transactions", binderInfo);

            JSONObject xmlInfo = new JSONObject();
            xmlInfo.put("apex_store", APEX_XML_STORE);
            xmlInfo.put("apex_store_exists", new File(APEX_XML_STORE).exists());
            xmlInfo.put("legacy_store", LEGACY_XML_STORE);
            xmlInfo.put("legacy_store_exists", new File(LEGACY_XML_STORE).exists());
            res.put("config_stores", xmlInfo);

            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doPermissions() {
        JSONObject res = new JSONObject();
        try {
            res.put("success", true);
            res.put("uid", 0);
            res.put("identity", "root (UID 0)");
            res.put("selinux_context", "u:r:magisk:s0");

            JSONObject perms = new JSONObject();
            perms.put("android.permission.NETWORK_SETTINGS", "GRANTED (Root UID 0 bypass)");
            perms.put("android.permission.OVERRIDE_WIFI_CONFIG", "GRANTED (Root UID 0 bypass)");
            perms.put("android.permission.TETHER_PRIVILEGED", "GRANTED (Root UID 0 bypass)");
            perms.put("android.permission.NETWORK_STACK", "GRANTED (Root UID 0 bypass)");
            res.put("effective_permissions", perms);

            res.put("wifi_binder_check", "Service wifi: accessible");
            res.put("note", "Root process running via app_process has unrestricted signature-level capability.");

            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static JSONObject configToJson(Object cfg) throws Exception {
        JSONObject json = new JSONObject();
        if (cfg == null) return json;

        Class<?> clazz = cfg.getClass();

        try {
            Method getSsid = clazz.getMethod("getSsid");
            Object ssid = getSsid.invoke(cfg);
            json.put("ssid", ssid != null ? ssid.toString() : "");
        } catch (Throwable t) {
            json.put("ssid", "");
        }

        try {
            Method getPassphrase = clazz.getMethod("getPassphrase");
            Object pass = getPassphrase.invoke(cfg);
            json.put("passphrase", pass != null ? pass.toString() : "");
        } catch (Throwable t) {
            json.put("passphrase", "");
        }

        int secType = 1;
        try {
            Method getSecurityType = clazz.getMethod("getSecurityType");
            secType = (Integer) getSecurityType.invoke(cfg);
        } catch (Throwable ignored) {}
        json.put("security_type", secType);
        json.put("security_type_name", getSecurityTypeName(secType));

        boolean hidden = false;
        try {
            Method isHidden = clazz.getMethod("isHiddenSsid");
            hidden = (Boolean) isHidden.invoke(cfg);
        } catch (Throwable ignored) {}
        json.put("hidden", hidden);

        int band = 1;
        try {
            Method getBand = clazz.getMethod("getBand");
            band = (Integer) getBand.invoke(cfg);
        } catch (Throwable ignored) {}
        json.put("band", band);
        json.put("band_name", getBandName(band));

        int channel = 0;
        try {
            Method getChannel = clazz.getMethod("getChannel");
            channel = (Integer) getChannel.invoke(cfg);
        } catch (Throwable ignored) {}
        json.put("channel", channel);

        int maxClients = 0;
        try {
            Method getMaxClients = clazz.getMethod("getMaxNumberOfClients");
            maxClients = (Integer) getMaxClients.invoke(cfg);
        } catch (Throwable ignored) {}
        json.put("max_clients", maxClients);

        boolean autoShutdown = true;
        try {
            Method isAutoShutdown = clazz.getMethod("isAutoShutdownEnabled");
            autoShutdown = (Boolean) isAutoShutdown.invoke(cfg);
        } catch (Throwable ignored) {}
        json.put("auto_shutdown", autoShutdown);

        long timeoutMillis = 0;
        try {
            Method getTimeout = clazz.getMethod("getShutdownTimeoutMillis");
            timeoutMillis = (Long) getTimeout.invoke(cfg);
        } catch (Throwable ignored) {}
        json.put("shutdown_timeout_millis", timeoutMillis);

        boolean clientControl = false;
        try {
            Method isClientControl = clazz.getMethod("isClientControlByUserEnabled");
            clientControl = (Boolean) isClientControl.invoke(cfg);
        } catch (Throwable ignored) {}
        json.put("client_control_by_user", clientControl);

        JSONArray blockedArray = new JSONArray();
        try {
            Method getBlocked = clazz.getMethod("getBlockedClientList");
            List<?> list = (List<?>) getBlocked.invoke(cfg);
            if (list != null) {
                for (Object mac : list) {
                    blockedArray.put(mac.toString());
                }
            }
        } catch (Throwable ignored) {}

        Set<String> persistentBlocked = loadBlockedMacsFromFile();
        for (String m : persistentBlocked) {
            boolean already = false;
            for (int i = 0; i < blockedArray.length(); i++) {
                if (blockedArray.getString(i).equalsIgnoreCase(m)) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                blockedArray.put(m);
            }
        }
        json.put("blocked_clients", blockedArray);

        return json;
    }

    private static String getSecurityTypeName(int secType) {
        switch (secType) {
            case 0: return "OPEN";
            case 1: return "WPA2_PSK";
            case 2: return "WPA3_SAE_TRANSITION";
            case 3: return "WPA3_SAE";
            case 4: return "WPA3_OWE";
            default: return "OTHER (" + secType + ")";
        }
    }

    private static String getBandName(int band) {
        switch (band) {
            case 1: return "2.4 GHz";
            case 2: return "5 GHz";
            case 4: return "6 GHz";
            case 3: return "Dual Band (2.4 & 5 GHz)";
            default: return "Band " + band;
        }
    }

    private static void doGet() {
        try {
            JSONObject json = null;
            try {
                Object cfg = getSoftApConfiguration();
                if (cfg != null) {
                    json = configToJson(cfg);
                    json.put("source", "IWifiManager_Binder");
                }
            } catch (Throwable t) {
                json = parseSoftApXmlDirectly();
            }

            if (json == null) {
                outputError("SoftApConfiguration not accessible via Binder or XML");
                return;
            }
            json.put("success", true);
            json.put("ap_state", getApStateName(getWifiApState()));
            System.out.println(json.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static Object buildConfigFromJson(JSONObject json, Object existingConfig) throws Exception {
        Class<?> configClass = Class.forName("android.net.wifi.SoftApConfiguration");
        Class<?> builderClass = Class.forName("android.net.wifi.SoftApConfiguration$Builder");

        Object builder;
        if (existingConfig != null) {
            try {
                Constructor<?> ctor = builderClass.getConstructor(configClass);
                builder = ctor.newInstance(existingConfig);
            } catch (Throwable t) {
                builder = builderClass.newInstance();
            }
        } else {
            builder = builderClass.newInstance();
        }

        // SSID
        if (json.has("ssid")) {
            String ssid = json.getString("ssid").trim();
            if (ssid.getBytes(StandardCharsets.UTF_8).length > 32) {
                throw new IllegalArgumentException("SSID length cannot exceed 32 bytes");
            }
            Method setSsid = builderClass.getMethod("setSsid", String.class);
            setSsid.invoke(builder, ssid);
        }

        // Passphrase & Security Type
        if (json.has("security_type")) {
            int secType = json.getInt("security_type");
            String pass = json.optString("passphrase", null);
            if (secType == 0) {
                pass = null;
                Method setPassphrase = builderClass.getMethod("setPassphrase", String.class, int.class);
                setPassphrase.invoke(builder, null, 0);
            } else {
                if (pass == null || pass.trim().isEmpty()) {
                    if (existingConfig != null) {
                        try {
                            Method getPass = existingConfig.getClass().getMethod("getPassphrase");
                            Object p = getPass.invoke(existingConfig);
                            if (p != null) pass = p.toString();
                        } catch (Throwable ignored) {}
                    }
                }

                if (pass == null || pass.length() < 8) {
                    throw new IllegalArgumentException("Passphrase must be at least 8 characters for WPA2/WPA3");
                }
                if (pass.length() > 63) {
                    throw new IllegalArgumentException("Passphrase cannot exceed 63 characters");
                }

                Method setPassphrase = builderClass.getMethod("setPassphrase", String.class, int.class);
                setPassphrase.invoke(builder, pass, secType);
            }
        }

        // Hidden SSID (The Core Delta Feature)
        if (json.has("hidden")) {
            boolean hidden = json.getBoolean("hidden");
            Method setHidden = builderClass.getMethod("setHiddenSsid", boolean.class);
            setHidden.invoke(builder, hidden);
        }

        // Band and Channel
        int band = json.optInt("band", 1);
        int channel = json.optInt("channel", 0);
        if (channel > 0) {
            try {
                Method setChannel = builderClass.getMethod("setChannel", int.class, int.class);
                setChannel.invoke(builder, channel, band);
            } catch (Throwable t) {
                Method setBand = builderClass.getMethod("setBand", int.class);
                setBand.invoke(builder, band);
            }
        } else if (json.has("band")) {
            Method setBand = builderClass.getMethod("setBand", int.class);
            setBand.invoke(builder, band);
        }

        // Max Clients (0 = unlimited / default, > 0 = custom limit)
        if (json.has("max_clients")) {
            int maxClients = json.getInt("max_clients");
            if (maxClients > 0) {
                try {
                    Method setMaxClients = builderClass.getMethod("setMaxNumberOfClients", int.class);
                    setMaxClients.invoke(builder, maxClients);
                } catch (Throwable ignored) {}
            }
        }

        // Auto Shutdown
        if (json.has("auto_shutdown")) {
            boolean autoShutdown = json.getBoolean("auto_shutdown");
            try {
                Method setAutoShutdown = builderClass.getMethod("setAutoShutdownEnabled", boolean.class);
                setAutoShutdown.invoke(builder, autoShutdown);
            } catch (Throwable ignored) {}
        }

        // Shutdown Timeout (Milliseconds: accepts any custom seconds or minutes)
        if (json.has("shutdown_timeout_millis")) {
            long timeoutMillis = json.getLong("shutdown_timeout_millis");
            if (timeoutMillis >= 0) {
                try {
                    Method setTimeout = builderClass.getMethod("setShutdownTimeoutMillis", long.class);
                    setTimeout.invoke(builder, timeoutMillis);
                } catch (Throwable ignored) {}
            }
        }

        // Blocked Clients (Explicit blocklist without enabling whitelist isolation mode)
        if (json.has("blocked_clients")) {
            JSONArray arr = json.getJSONArray("blocked_clients");
            Class<?> macClass = Class.forName("android.net.MacAddress");
            Method fromString = macClass.getMethod("fromString", String.class);
            List<Object> macList = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String mStr = arr.getString(i).trim();
                if (!mStr.isEmpty()) {
                    try {
                        Object macObj = fromString.invoke(null, mStr);
                        macList.add(macObj);
                    } catch (Throwable ignored) {}
                }
            }
            try {
                Method setBlockedList = builderClass.getMethod("setBlockedClientList", List.class);
                setBlockedList.invoke(builder, macList);
            } catch (Throwable ignored) {}
        }

        // Ensure user client control (whitelist mode) is disabled so connected clients are visible in Android Settings
        try {
            Method setClientControl = builderClass.getMethod("setClientControlByUserEnabled", boolean.class);
            setClientControl.invoke(builder, false);
        } catch (Throwable ignored) {}

        Method buildMethod = builderClass.getMethod("build");
        return buildMethod.invoke(builder);
    }

    private static void doSet(String jsonStr) {
        try {
            JSONObject updates = new JSONObject(jsonStr);
            Object currentConfig = null;
            try {
                currentConfig = getSoftApConfiguration();
            } catch (Throwable ignored) {}

            Object newConfig = buildConfigFromJson(updates, currentConfig);
            boolean ok = setSoftApConfiguration(newConfig);

            // Direct XML synchronization so disk store matches memory immediately
            syncXmlDirectly(updates);

            // If hotspot is currently broadcasting, restart it so the MediaTek hardware reloads beacon settings!
            int currentState = getWifiApState();
            boolean wasRunning = (currentState == 13);
            if (wasRunning) {
                stopHotspot();
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                startHotspot(newConfig);
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            }

            // Sync iptables blocking rules
            if (updates.has("blocked_clients")) {
                JSONArray arr = updates.getJSONArray("blocked_clients");
                Set<String> newBlocked = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    newBlocked.add(arr.getString(i).trim().toUpperCase());
                }
                syncIptablesBlocking(newBlocked);
                saveBlockedMacsToFile(newBlocked);
            }

            JSONObject res = new JSONObject();
            res.put("success", ok);
            res.put("message", ok ? "Configuration successfully applied to Android Wi-Fi service" : "Failed to apply configuration");
            res.put("restarted_active_hotspot", wasRunning);
            res.put("new_config", configToJson(newConfig));
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doBackup(String filePath) {
        try {
            JSONObject json = null;
            try {
                Object cfg = getSoftApConfiguration();
                if (cfg != null) {
                    json = configToJson(cfg);
                    json.put("_capture_method", "IWifiManager_Binder");
                }
            } catch (Throwable t) {
                json = parseSoftApXmlDirectly();
                if (json != null) {
                    json.put("_capture_method", "Direct_XML_Store");
                }
            }

            if (json == null) {
                outputError("Cannot backup: both Wi-Fi service and XML store are inaccessible");
                return;
            }

            json.put("_backup_timestamp", System.currentTimeMillis());
            json.put("_device_model", Build.MODEL);
            json.put("_device_fingerprint", Build.FINGERPRINT);
            json.put("_android_version", Build.VERSION.RELEASE);
            json.put("_api_level", Build.VERSION.SDK_INT);

            File file = new File(filePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toString(2));
            }

            file.setReadable(true, false);
            file.setWritable(true, true);

            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("message", "Original configuration backed up successfully");
            res.put("path", file.getAbsolutePath());
            res.put("backup_content", json);
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doRestore(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                outputError("Backup file does not exist: " + filePath);
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }

            JSONObject backupJson = new JSONObject(sb.toString());
            Object restoredConfig = buildConfigFromJson(backupJson, null);
            boolean ok = setSoftApConfiguration(restoredConfig);

            // Sync XML store
            syncXmlDirectly(backupJson);

            // If hotspot was running, restart it with restored config
            if (getWifiApState() == 13) {
                stopHotspot();
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                startHotspot(restoredConfig);
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            }

            if (ok) {
                clearAllIptablesBlocking();
                deleteFile(BLOCKED_MACS_FILE);
            }

            JSONObject res = new JSONObject();
            res.put("success", ok);
            res.put("message", ok ? "Configuration successfully restored from backup" : "Failed to restore configuration");
            res.put("restored_from", filePath);
            res.put("current_config", configToJson(restoredConfig));
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doBlock(String mac) {
        try {
            String cleanMac = mac.trim().toUpperCase();
            Set<String> blocked = loadBlockedMacsFromFile();
            blocked.add(cleanMac);

            applyIptablesBlock(cleanMac);
            saveBlockedMacsToFile(blocked);

            try {
                Object current = getSoftApConfiguration();
                JSONObject updates = new JSONObject();
                JSONArray arr = new JSONArray();
                for (String m : blocked) arr.put(m);
                updates.put("blocked_clients", arr);
                Object newConfig = buildConfigFromJson(updates, current);
                setSoftApConfiguration(newConfig);
            } catch (Throwable ignored) {}

            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("blocked_mac", cleanMac);
            res.put("total_blocked", blocked.size());
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doUnblock(String mac) {
        try {
            String cleanMac = mac.trim().toUpperCase();
            Set<String> blocked = loadBlockedMacsFromFile();
            blocked.remove(cleanMac);

            removeIptablesBlock(cleanMac);
            saveBlockedMacsToFile(blocked);

            try {
                Object current = getSoftApConfiguration();
                JSONObject updates = new JSONObject();
                JSONArray arr = new JSONArray();
                for (String m : blocked) arr.put(m);
                updates.put("blocked_clients", arr);
                Object newConfig = buildConfigFromJson(updates, current);
                setSoftApConfiguration(newConfig);
            } catch (Throwable ignored) {}

            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("unblocked_mac", cleanMac);
            res.put("total_blocked", blocked.size());
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doStatus() {
        try {
            int state = getWifiApState();
            boolean isRunning = (state == 13);
            if (isRunning) {
                notifyTetheringService();
                try {
                    Set<String> blockedMacs = loadBlockedMacsFromFile();
                    for (String m : blockedMacs) {
                        applyIptablesBlock(m);
                    }
                } catch (Throwable ignored) {}
            }

            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("ap_state_code", state);
            res.put("ap_state_name", getApStateName(state));
            res.put("is_running", isRunning);

            JSONObject cfgJson = null;
            try {
                Object cfg = getSoftApConfiguration();
                if (cfg != null) {
                    cfgJson = configToJson(cfg);
                }
            } catch (Throwable ignored) {}

            if (cfgJson == null) {
                cfgJson = parseSoftApXmlDirectly();
            }

            if (cfgJson != null) {
                res.put("config", cfgJson);
            }

            String apIp = isRunning ? getActiveApIpAddress() : "Stopped";
            res.put("ap_ip", apIp);

            JSONArray clients = getConnectedClients();
            res.put("connected_count", clients.length());
            res.put("clients", clients);

            Set<String> blockedMacs = loadBlockedMacsFromFile();
            res.put("blocked_count", blockedMacs.size());
            res.put("original_backup_present", new File(DEFAULT_BACKUP_PATH).exists());

            JSONObject devInfo = new JSONObject();
            devInfo.put("model", Build.MODEL);
            devInfo.put("manufacturer", Build.MANUFACTURER);
            devInfo.put("brand", Build.BRAND);
            devInfo.put("device", Build.DEVICE);
            devInfo.put("hardware", Build.HARDWARE);
            devInfo.put("android_version", Build.VERSION.RELEASE);
            devInfo.put("api_level", Build.VERSION.SDK_INT);
            res.put("device_info", devInfo);

            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doStart() {
        try {
            Object cfg = null;
            try { cfg = getSoftApConfiguration(); } catch (Throwable ignored) {}
            
            // Start using IWifiManager.startTetheredHotspot(cfg)
            // DO NOT use cmd wifi start-softap because it strips hiddenSsid!
            boolean started = startHotspot(cfg);

            JSONObject res = new JSONObject();
            res.put("success", started);
            res.put("method", "startTetheredHotspot");
            res.put("ap_state", getApStateName(getWifiApState()));
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doStop() {
        try {
            boolean stopped = stopHotspot();
            JSONObject res = new JSONObject();
            res.put("success", stopped);
            res.put("ap_state", getApStateName(getWifiApState()));
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doRestart() {
        try {
            stopHotspot();
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
            Object cfg = null;
            try { cfg = getSoftApConfiguration(); } catch (Throwable ignored) {}
            boolean started = startHotspot(cfg);

            JSONObject res = new JSONObject();
            res.put("success", started);
            res.put("ap_state", getApStateName(getWifiApState()));
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    private static void doClients() {
        try {
            JSONArray clients = getConnectedClients();
            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("connected_count", clients.length());
            res.put("ap_ip", getActiveApIpAddress());
            res.put("clients", clients);
            System.out.println(res.toString(2));
        } catch (Throwable t) {
            outputException(t);
        }
    }

    public static String getActiveApIpAddress() {
        String[] preferredIfaces = new String[]{"ap0", "ap1", "softap0", "swlan0", "wlan1", "wlan0"};
        for (String ifName : preferredIfaces) {
            try {
                NetworkInterface nif = NetworkInterface.getByName(ifName);
                if (nif != null && nif.isUp()) {
                    Enumeration<InetAddress> addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            String host = addr.getHostAddress();
                            if (host != null && !host.isEmpty() && !"0.0.0.0".equals(host)) {
                                return host;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
            while (nifs != null && nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                String name = nif.getName().toLowerCase();
                if ((name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan")) && nif.isUp()) {
                    Enumeration<InetAddress> addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return "192.168.43.1";
    }

    public static String getActiveApInterfaceName() {
        String[] preferredIfaces = new String[]{"ap0", "ap1", "softap0", "swlan0", "wlan1", "wlan0"};
        for (String ifName : preferredIfaces) {
            try {
                NetworkInterface nif = NetworkInterface.getByName(ifName);
                if (nif != null && nif.isUp()) {
                    Enumeration<InetAddress> addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            return ifName;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
            while (nifs != null && nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                String name = nif.getName().toLowerCase();
                if ((name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan")) && nif.isUp()) {
                    Enumeration<InetAddress> addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            return nif.getName();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return "ap0";
    }

    private static String resolveDeviceNameCached(String mac, String ip, Map<String, String> dhcpHostnames, Map<String, String> mdnsNames, String apSubnetPrefix) {
        String cached = sHostnameCache.get(mac);
        if (cached != null && !cached.isEmpty()) {
            if (cached.contains("@")) {
                cached = cached.substring(cached.indexOf('@') + 1).trim();
                sHostnameCache.put(mac, cached);
            }
            // If cached value is entirely lowercase, verify if live DHCP has genuine uppercase lettering
            String liveName = null;
            if (dhcpHostnames.containsKey(mac)) {
                liveName = dhcpHostnames.get(mac);
            } else if (dhcpHostnames.containsKey(ip)) {
                liveName = dhcpHostnames.get(ip);
            }
            if (liveName != null && !liveName.isEmpty()) {
                boolean cachedHasUpper = !cached.equals(cached.toLowerCase(Locale.ROOT));
                boolean liveHasUpper = !liveName.equals(liveName.toLowerCase(Locale.ROOT));
                if (!cachedHasUpper && liveHasUpper) {
                    String updated = formatPlatformDeviceName(liveName, ip, mac);
                    sHostnameCache.put(mac, updated);
                    saveHostnameCache();
                    return updated;
                }
            }
            return cached;
        }

        String hName = null;
        if (dhcpHostnames.containsKey(mac)) {
            hName = dhcpHostnames.get(mac);
        } else if (dhcpHostnames.containsKey(ip)) {
            hName = dhcpHostnames.get(ip);
        }

        if ((hName == null || hName.isEmpty()) && mdnsNames.containsKey(ip)) {
            hName = mdnsNames.get(ip);
        }
        if (hName == null || hName.isEmpty()) {
            try {
                String rDns = InetAddress.getByName(ip).getCanonicalHostName();
                if (rDns != null && !rDns.equals(ip) && !rDns.startsWith(apSubnetPrefix)) {
                    hName = rDns;
                }
            } catch (Throwable ignored) {}
        }
        String finalName = formatPlatformDeviceName(hName, ip, mac);
        sHostnameCache.put(mac, finalName);
        saveHostnameCache();
        return finalName;
    }

    private static Set<String> getAssociatedWifiStations(String iface) {
        Set<String> stations = new HashSet<>();
        if (iface != null && !iface.isEmpty()) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"iw", "dev", iface, "station", "dump"});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    String t = line.trim();
                    if (t.startsWith("Station ")) {
                        String[] parts = t.split("\\s+");
                        if (parts.length >= 2) {
                            String m = parts[1].trim().toUpperCase();
                            if (m.contains(":") && m.length() == 17) {
                                stations.add(m);
                            }
                        }
                    }
                }
                br.close();
            } catch (Throwable ignored) {}
        }
        return stations;
    }

    private static Map<String, String> getNeighborStates() {
        Map<String, String> map = new HashMap<>();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"ip", "neigh", "show"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length >= 4) {
                    String ip = tokens[0];
                    String state = tokens[tokens.length - 1].toUpperCase();
                    for (int i = 0; i < tokens.length - 1; i++) {
                        if ("lladdr".equals(tokens[i])) {
                            String mac = tokens[i + 1].toUpperCase();
                            map.put(mac, state);
                            break;
                        }
                    }
                    map.put(ip, state);
                }
            }
            br.close();
        } catch (Throwable ignored) {}
        return map;
    }

    private static JSONArray getConnectedClients() {
        loadHostnameCache();
        JSONArray array = new JSONArray();
        Map<String, JSONObject> clientMap = new HashMap<>();

        Set<String> blockedMacs = loadBlockedMacsFromFile();
        Map<String, String> dhcpHostnames = parseDhcpLeases();
        Map<String, String> mdnsNames = parseMdnsAndNsdNames();
        String apIp = getActiveApIpAddress();
        String apIface = getActiveApInterfaceName();
        String apSubnetPrefix = (apIp.contains(".")) ? apIp.substring(0, apIp.lastIndexOf('.') + 1) : "192.168.43.";

        // 1. Check Layer 2 Wi-Fi Station Association (if 'iw' is available on device)
        Set<String> l2Stations = getAssociatedWifiStations(apIface);

        // 2. Discover Candidate Clients from Linux Kernel ARP (/proc/net/arp)
        List<String[]> candidates = new ArrayList<>();
        File arpFile = new File("/proc/net/arp");
        if (arpFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(arpFile))) {
                String line = br.readLine(); // skip header
                while ((line = br.readLine()) != null) {
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length >= 6) {
                        String ip = tokens[0];
                        String flags = tokens[2];
                        String mac = tokens[3].toUpperCase();
                        String iface = tokens[5];

                        if (ip.equals(apIp)) continue;

                        boolean isApIface = iface.startsWith("ap") || iface.startsWith("softap") || iface.startsWith("swlan") || iface.startsWith("wlan") || ip.startsWith(apSubnetPrefix);
                        if (isApIface && !"00:00:00:00:00:00".equals(mac) && !"0x0".equals(flags)) {
                            // If Layer 2 station dump is active and station is not associated, skip immediately
                            if (!l2Stations.isEmpty() && !l2Stations.contains(mac)) {
                                continue;
                            }
                            candidates.add(new String[]{ip, mac, iface});
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Active Neighbor Reachability Verification (RFC 4861 Stale ARP Elimination)
        Map<String, String> neighStates = getNeighborStates();
        List<String[]> needProbe = new ArrayList<>();
        for (String[] c : candidates) {
            String ip = c[0];
            String mac = c[1];
            String state = neighStates.get(mac);
            if (state == null) state = neighStates.get(ip);

            // If neighbor is in STALE or unconfirmed state, it must be actively verified
            if (state == null || "STALE".equals(state) || "DELAY".equals(state)) {
                needProbe.add(c);
            }
        }

        if (!needProbe.isEmpty()) {
            try {
                // Flush stale cache so kernel initiates fresh ARP probe
                Process pFlush = Runtime.getRuntime().exec(new String[]{"ip", "neigh", "flush", "nud", "stale"});
                pFlush.waitFor();
            } catch (Throwable ignored) {}

            try {
                DatagramSocket ds = new DatagramSocket();
                byte[] probeData = new byte[]{0};
                for (String[] c : needProbe) {
                    try {
                        ds.send(new DatagramPacket(probeData, probeData.length, InetAddress.getByName(c[0]), 53));
                    } catch (Throwable ignored) {}
                }
                ds.close();
            } catch (Throwable ignored) {}

            try {
                Thread.sleep(80); // 80ms window for hardware Wi-Fi ARP offload response
            } catch (InterruptedException ignored) {}

            // Re-read neighbor states after active probe
            neighStates = getNeighborStates();
        }

        // 4. Assemble verified connected clients
        for (String[] c : candidates) {
            String ip = c[0];
            String mac = c[1];
            String iface = c[2];

            String state = neighStates.get(mac);
            if (state == null) state = neighStates.get(ip);

            // Strictly reject disconnected or unconfirmed states
            if (state == null || "FAILED".equals(state) || "INCOMPLETE".equals(state) || "NONE".equals(state) || "STALE".equals(state)) {
                continue;
            }

            if (!l2Stations.isEmpty() && !l2Stations.contains(mac)) {
                continue;
            }

            try {
                JSONObject client = new JSONObject();
                client.put("ip", ip);
                client.put("mac", mac);
                client.put("interface", iface);

                String finalName = resolveDeviceNameCached(mac, ip, dhcpHostnames, mdnsNames, apSubnetPrefix);
                client.put("hostname", finalName);
                client.put("is_blocked", blockedMacs.contains(mac));
                clientMap.put(mac, client);
            } catch (Throwable ignored) {}
        }

        // 5. Real-time Disconnect Filter via logcat
        if (!clientMap.isEmpty()) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-t", "150", "-b", "main", "-b", "system"});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                Pattern patMac = Pattern.compile("([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})");
                while ((line = br.readLine()) != null) {
                    if (line.contains("AP-STA-DISCONNECTED") || line.contains("disconnected") || line.contains("STA_DISCONNECTED")) {
                        Matcher m = patMac.matcher(line);
                        if (m.find()) {
                            String disMac = m.group(1).toUpperCase();
                            clientMap.remove(disMac);
                        }
                    }
                }
                br.close();
            } catch (Throwable ignored) {}
        }

        for (JSONObject c : clientMap.values()) {
            array.put(c);
        }
        return array;
    }

    private static String getVendorFromMac(String mac) {
        return null;
    }

    private static String formatPlatformDeviceName(String rawName, String ip, String mac) {
        if (rawName == null || rawName.trim().isEmpty() || "*".equals(rawName) || "null".equalsIgnoreCase(rawName) || "unknown".equalsIgnoreCase(rawName)) {
            String lastOctet = (ip != null && ip.contains(".")) ? ip.substring(ip.lastIndexOf('.') + 1) : "peer";
            return "Device-" + lastOctet;
        }

        String s = rawName.trim();
        // Strip any artificial legacy prefixes (e.g. android@xxx -> xxx)
        if (s.contains("@")) {
            s = s.substring(s.indexOf('@') + 1).trim();
        }

        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.startsWith("'") && s.endsWith("'") && s.length() > 1) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.endsWith(".local")) {
            s = s.substring(0, s.length() - 6).trim();
        }

        // Clean spaces and preserve genuine advertised model / host capitalization
        s = s.replaceAll("[\\s_]+", "-").replaceAll("[^a-zA-Z0-9.-]", "");
        if (s.isEmpty()) {
            String lastOctet = (ip != null && ip.contains(".")) ? ip.substring(ip.lastIndexOf('.') + 1) : "peer";
            return "Device-" + lastOctet;
        }

        return s;
    }

    private static String cleanDeviceName(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.startsWith("'") && s.endsWith("'") && s.length() > 1) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.endsWith(".local")) {
            s = s.substring(0, s.length() - 6);
        }
        return s;
    }

    private static boolean isValidDeviceName(String s) {
        if (s == null || s.isEmpty() || s.length() < 2) return false;
        if (s.equalsIgnoreCase("null") || s.equals("*") || s.equalsIgnoreCase("unknown")) return false;
        if (s.startsWith("192.168.") || s.startsWith("Client (") || s.startsWith("Wi-Fi Device")) return false;
        return true;
    }

    private static Map<String, String> parseMdnsAndNsdNames() {
        Map<String, String> names = new HashMap<>();
        String apIp = getActiveApIpAddress();
        String apSubnetPrefix = (apIp.contains(".")) ? apIp.substring(0, apIp.lastIndexOf('.') + 1) : "192.168.43.";

        // 1. Check dumpsys servicediscovery
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"dumpsys", "servicediscovery"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            Pattern pat = Pattern.compile("(?:name|serviceName)[:=\\s]+[\"']?([^\"',\\s]+)[\"']?.*?(?:host|address|ip)[:=\\s]+/?([0-9.]+)", Pattern.CASE_INSENSITIVE);
            Pattern patReverse = Pattern.compile("(?:host|address|ip)[:=\\s]+/?([0-9.]+).*?(?:name|serviceName)[:=\\s]+[\"']?([^\"',\\s]+)[\"']?", Pattern.CASE_INSENSITIVE);
            while ((line = br.readLine()) != null) {
                Matcher m = pat.matcher(line);
                if (m.find()) {
                    String name = cleanDeviceName(m.group(1));
                    String ip = m.group(2).trim();
                    if (isValidDeviceName(name) && (ip.startsWith(apSubnetPrefix) || ip.startsWith("192.168."))) {
                        names.put(ip, name);
                    }
                }
                Matcher mRev = patReverse.matcher(line);
                if (mRev.find()) {
                    String ip = mRev.group(1).trim();
                    String name = cleanDeviceName(mRev.group(2));
                    if (isValidDeviceName(name) && (ip.startsWith(apSubnetPrefix) || ip.startsWith("192.168."))) {
                        names.put(ip, name);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Check system logcat for NSD and local service events
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-t", "400", "-b", "main", "-b", "system"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            Pattern patNsd1 = Pattern.compile("NsdServiceInfo.*?(?:name|mServiceName)[:=\\s]+[\"']?([^\"',\\s]+)[\"']?.*?(?:host|mHost)[:=\\s]+/?([0-9.]+)", Pattern.CASE_INSENSITIVE);
            Pattern patNsd2 = Pattern.compile("NsdServiceInfo.*?(?:host|mHost)[:=\\s]+/?([0-9.]+).*?(?:name|mServiceName)[:=\\s]+[\"']?([^\"',\\s]+)[\"']?", Pattern.CASE_INSENSITIVE);
            Pattern patResolved = Pattern.compile("onServiceResolved.*?([a-zA-Z0-9_@.-]+).*?/?(\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b)");
            while ((line = br.readLine()) != null) {
                if (line.contains("Nsd") || line.contains("warpinator") || line.contains("localsend") || line.contains("ServiceInfo")) {
                    Matcher m1 = patNsd1.matcher(line);
                    if (m1.find()) {
                        String name = cleanDeviceName(m1.group(1));
                        String ip = m1.group(2).trim();
                        if (isValidDeviceName(name) && (ip.startsWith(apSubnetPrefix) || ip.startsWith("192.168."))) names.put(ip, name);
                    }
                    Matcher m2 = patNsd2.matcher(line);
                    if (m2.find()) {
                        String ip = m2.group(1).trim();
                        String name = cleanDeviceName(m2.group(2));
                        if (isValidDeviceName(name) && (ip.startsWith(apSubnetPrefix) || ip.startsWith("192.168."))) names.put(ip, name);
                    }
                    Matcher m3 = patResolved.matcher(line);
                    if (m3.find()) {
                        String name = cleanDeviceName(m3.group(1));
                        String ip = m3.group(2).trim();
                        if (isValidDeviceName(name) && (ip.startsWith(apSubnetPrefix) || ip.startsWith("192.168."))) names.put(ip, name);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return names;
    }

    private static Map<String, String> parseDhcpLeases() {
        Map<String, String> map = new HashMap<>();
        String apIp = getActiveApIpAddress();
        String apSubnetPrefix = (apIp.contains(".")) ? apIp.substring(0, apIp.lastIndexOf('.') + 1) : "192.168.43.";

        Pattern patMac = Pattern.compile("([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})");
        Pattern patIp = Pattern.compile("(\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b)");
        Pattern patHost = Pattern.compile("(?:hostname|host|client-name)[:=\\s]+[\"']?([^\"'\\r\\n,;}\\]]+)[\"']?", Pattern.CASE_INSENSITIVE);

        // 1. Process dumpsys tethering, dumpsys network_stack, and dumpsys wifi
        String[][] dumpsysCommands = new String[][]{
            {"dumpsys", "tethering"},
            {"dumpsys", "network_stack"},
            {"dumpsys", "wifi"}
        };

        for (String[] cmd : dumpsysCommands) {
            try {
                Process p = Runtime.getRuntime().exec(cmd);
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                String currentMac = null;
                String currentIp = null;
                while ((line = br.readLine()) != null) {
                    Matcher mMac = patMac.matcher(line);
                    if (mMac.find()) {
                        String m = mMac.group(1).toUpperCase();
                        if (!"00:00:00:00:00:00".equals(m) && !"FF:FF:FF:FF:FF:FF".equals(m)) {
                            currentMac = m;
                        }
                    }
                    Matcher mIp = patIp.matcher(line);
                    while (mIp.find()) {
                        String ipCandidate = mIp.group(1);
                        if (!ipCandidate.equals(apIp) && !ipCandidate.endsWith(".255") && !ipCandidate.endsWith(".0") && (ipCandidate.startsWith(apSubnetPrefix) || ipCandidate.startsWith("192.168.") || ipCandidate.startsWith("10.") || ipCandidate.startsWith("172."))) {
                            currentIp = ipCandidate;
                            break;
                        }
                    }
                    Matcher mHost = patHost.matcher(line);
                    if (mHost.find()) {
                        String rawHost = mHost.group(1).trim();
                        String clean = cleanDeviceName(rawHost);
                        if (isValidDeviceName(clean)) {
                            if (currentMac != null) {
                                map.put(currentMac, clean);
                            }
                            if (currentIp != null) {
                                map.put(currentIp, clean);
                            }
                        }
                    }
                    if (line.contains("TetheredClient") || line.contains("Client ") || line.contains("Lease:") || line.contains("Station ")) {
                        if (!mMac.find()) currentMac = null;
                        if (!mIp.find()) currentIp = null;
                    }
                }
                br.close();
            } catch (Throwable ignored) {}
        }

        // 2. Check system logcat for DhcpServer lease assignments
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-t", "300", "-b", "main", "-b", "system"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            Pattern patDhcp = Pattern.compile("(?:DHCPACK|DhcpServer|offer|ack).*?([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5}).*?(\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b).*?(?:hostname|host)[:=\\s]+[\"']?([^\"'\\r\\n,;}\\]]+)[\"']?", Pattern.CASE_INSENSITIVE);
            Pattern patDhcp2 = Pattern.compile("(?:DHCPACK|DhcpServer).*?(\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b).*?([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5}).*?(?:hostname|host)[:=\\s]+[\"']?([^\"'\\r\\n,;}\\]]+)[\"']?", Pattern.CASE_INSENSITIVE);
            while ((line = br.readLine()) != null) {
                if (line.contains("DHCPACK") || line.contains("DhcpServer") || line.contains("Tethering")) {
                    Matcher m1 = patDhcp.matcher(line);
                    if (m1.find()) {
                        String mac = m1.group(1).toUpperCase();
                        String ip = m1.group(2);
                        String host = cleanDeviceName(m1.group(3));
                        if (isValidDeviceName(host)) {
                            map.put(mac, host);
                            map.put(ip, host);
                        }
                    } else {
                        Matcher m2 = patDhcp2.matcher(line);
                        if (m2.find()) {
                            String ip = m2.group(1);
                            String mac = m2.group(2).toUpperCase();
                            String host = cleanDeviceName(m2.group(3));
                            if (isValidDeviceName(host)) {
                                map.put(mac, host);
                                map.put(ip, host);
                            }
                        }
                    }
                }
            }
            br.close();
        } catch (Throwable ignored) {}

        // 3. Fallback to lease files if present
        String[] possiblePaths = {
            "/data/misc/dhcp/dnsmasq.leases",
            "/data/misc/apexdata/com.android.networkstack/dhcpserver.leases",
            "/data/misc/wifi/dnsmasq.leases",
            "/data/misc/wifi/softap.leases"
        };
        for (String p : possiblePaths) {
            File f = new File(p);
            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 4) {
                            String mac = parts[1].toUpperCase();
                            String name = parts[3];
                            if (!"*".equals(name) && !name.isEmpty() && !"null".equalsIgnoreCase(name)) {
                                map.put(mac, name);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return map;
    }

    private static Set<String> loadBlockedMacsFromFile() {
        Set<String> set = new HashSet<>();
        File f = new File(BLOCKED_MACS_FILE);
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String m = line.trim().toUpperCase();
                    if (!m.isEmpty() && m.contains(":")) {
                        set.add(m);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return set;
    }

    private static void saveBlockedMacsToFile(Set<String> macs) {
        File f = new File(BLOCKED_MACS_FILE);
        try (FileWriter fw = new FileWriter(f)) {
            for (String m : macs) {
                fw.write(m + "\n");
            }
        } catch (Throwable ignored) {}
        f.setReadable(true, false);
        f.setWritable(true, true);
    }

    private static void applyIptablesBlock(String mac) {
        runShellCommand("iptables -C FORWARD -m mac --mac-source " + mac + " -j DROP 2>/dev/null || iptables -I FORWARD 1 -m mac --mac-source " + mac + " -j DROP");
        runShellCommand("iptables -C INPUT -m mac --mac-source " + mac + " -j DROP 2>/dev/null || iptables -I INPUT 1 -m mac --mac-source " + mac + " -j DROP");
    }

    private static void removeIptablesBlock(String mac) {
        runShellCommand("iptables -D FORWARD -m mac --mac-source " + mac + " -j DROP 2>/dev/null");
        runShellCommand("iptables -D INPUT -m mac --mac-source " + mac + " -j DROP 2>/dev/null");
    }

    private static void syncIptablesBlocking(Set<String> newBlocked) {
        Set<String> current = loadBlockedMacsFromFile();
        for (String m : current) {
            if (!newBlocked.contains(m)) {
                removeIptablesBlock(m);
            }
        }
        for (String m : newBlocked) {
            applyIptablesBlock(m);
        }
    }

    private static void clearAllIptablesBlocking() {
        Set<String> current = loadBlockedMacsFromFile();
        for (String m : current) {
            removeIptablesBlock(m);
        }
    }

    private static int runShellCommand(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            return p.waitFor();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void deleteFile(String path) {
        try {
            new File(path).delete();
        } catch (Throwable ignored) {}
    }

    private static void outputError(String msg) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", false);
            obj.put("error", msg);
            System.out.println(obj.toString(2));
        } catch (Throwable ignored) {}
    }

    private static void outputException(Throwable t) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", false);
            obj.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            obj.put("stacktrace", sw.toString());
            System.out.println(obj.toString(2));
        } catch (Throwable ignored) {}
    }
}
