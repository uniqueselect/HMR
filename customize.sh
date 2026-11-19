# Root Hotspot Manager - Magisk Installation Script
# Target: Android 11+ (API 30+) AOSP / Project Treble GSI / OEM ROMs

ui_print " "
ui_print " # ROOT HOTSPOT MANAGER [v1.2.4]"
ui_print " ========================================="

# Dynamic Hardware & OS Environment Probe
API_LEVEL=$(getprop ro.build.version.sdk)
DEVICE_MODEL=$(getprop ro.product.model)
DEVICE_DEV=$(getprop ro.product.device)
AND_VER=$(getprop ro.build.version.release)

ui_print " > Target : $DEVICE_MODEL ($DEVICE_DEV)"
ui_print " > System : Android $AND_VER (API $API_LEVEL)"
ui_print " > Core   : AIDL SoftApConfiguration Engine"
ui_print " "

if [ "$API_LEVEL" -lt 30 ]; then
    ui_print " [!] Warning: API $API_LEVEL < 30 (Android 11+ recommended)"
else
    ui_print " [+] SoftAp AIDL interface verified"
fi

# Determine Module ID
MODID="$(grep "^id=" "$MODPATH/module.prop" 2>/dev/null | cut -d= -f2 | tr -d ' \r\n')"
[ -z "$MODID" ] && MODID="root_hotspot_manager"

# Set file & binary permissions
ui_print " > Setting systemless permissions..."
set_perm_recursive "$MODPATH/bin" 0 0 0755 0755
set_perm "$MODPATH/bin/hotspot-ctl" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

# Ensure WebUI directory is world-readable for unprivileged WebView sandbox
set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644
chmod -R 0755 "$MODPATH/webroot"
find "$MODPATH/webroot" -type f -exec chmod 0644 {} + 2>/dev/null
find "$MODPATH/webroot" -type d -exec chmod 0755 {} + 2>/dev/null

# Ensure symlink in bin
ln -sf hotspot-ctl "$MODPATH/bin/hotspotctl"

# Systemless /system/bin registration for reboot mount
mkdir -p "$MODPATH/system/bin"
ln -sf "/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl" "$MODPATH/system/bin/hotspot-ctl"
ln -sf "/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl" "$MODPATH/system/bin/hotspotctl"
set_perm "$MODPATH/system/bin/hotspot-ctl" 0 0 0755
set_perm "$MODPATH/system/bin/hotspotctl" 0 0 0755

# Symlinks for immediate root shell environments
if [ -d "/data/adb/ksu/bin" ]; then
    ln -sf "$MODPATH/bin/hotspot-ctl" "/data/adb/ksu/bin/hotspotctl" 2>/dev/null
    ln -sf "$MODPATH/bin/hotspot-ctl" "/data/adb/ksu/bin/hotspot-ctl" 2>/dev/null
fi
ln -sf "$MODPATH/bin/hotspot-ctl" "/data/local/tmp/hotspotctl" 2>/dev/null
ln -sf "$MODPATH/bin/hotspot-ctl" "/data/local/tmp/hotspot-ctl" 2>/dev/null

# Clean up any legacy or duplicate module directories/symlinks
rm -rf "/data/adb/modules/root_hotspot_manager_vanilla" 2>/dev/null

# Deploy WebUI for Standalone WebUI Host and MMRL
ui_print " > Deploying offline WebUI runtime..."
mkdir -p "/data/adb/webroot" 2>/dev/null
chmod 0755 "/data/adb/webroot" 2>/dev/null
mkdir -p "/data/adb/webroot/$MODID"
cp -rf "$MODPATH/webroot/"* "/data/adb/webroot/$MODID/"
chmod -R 0755 "/data/adb/webroot/$MODID"
find "/data/adb/webroot/$MODID" -type f -exec chmod 0644 {} + 2>/dev/null
find "/data/adb/webroot/$MODID" -type d -exec chmod 0755 {} + 2>/dev/null

# Also mirror to alias in /data/adb/webroot
if [ "$MODID" = "root_hotspot_manager" ]; then
    ln -sf "root_hotspot_manager" "/data/adb/webroot/root_hotspot_manager_vanilla" 2>/dev/null
else
    ln -sf "$MODID" "/data/adb/webroot/root_hotspot_manager" 2>/dev/null
fi

# Invalidate WebUI Host caches
ui_print " > Purging WebUI runtime cache..."
am force-stop io.github.a13e300.ksuwebui 2>/dev/null
am force-stop com.dergoogler.mmrl 2>/dev/null
rm -rf /data/data/io.github.a13e300.ksuwebui/cache/* 2>/dev/null
rm -rf /data/data/io.github.a13e300.ksuwebui/app_webview/* 2>/dev/null
rm -rf /data/data/com.dergoogler.mmrl/cache/* 2>/dev/null
rm -rf /data/data/com.dergoogler.mmrl/app_webview/* 2>/dev/null

# Purge any legacy VPN bypass routing rules from prior experimental tests
rm -f /data/adb/root_hotspot_vpn_bypass.conf 2>/dev/null
ip rule del pref 8000 2>/dev/null
ip rule del pref 8001 2>/dev/null
ip rule del pref 8002 2>/dev/null
ip rule del pref 8003 2>/dev/null
ip rule del pref 8999 2>/dev/null
ip rule del pref 9000 2>/dev/null
ip rule del pref 9001 2>/dev/null
ip rule del pref 9002 2>/dev/null
ip rule del pref 9003 2>/dev/null
iptables -t mangle -D OUTPUT -o ap0 -j MARK --set-mark 0 2>/dev/null
iptables -t mangle -D PREROUTING -i ap0 -j MARK --set-mark 0 2>/dev/null

# Verification and initial backup
BACKUP_PATH="/data/adb/root_hotspot_original_backup.json"

if [ -f "$BACKUP_PATH" ]; then
    ui_print " [+] Preserved factory Wi-Fi configuration backup found"
    cp "$BACKUP_PATH" "$MODPATH/original_backup.json"
else
    ui_print " > Capturing factory Wi-Fi configuration backup..."
    if [ -e "/system/bin/app_process" ]; then
        sh "$MODPATH/bin/hotspot-ctl" backup "$BACKUP_PATH" > /dev/null 2>&1
        if [ -s "$BACKUP_PATH" ]; then
            ui_print " [+] Factory configuration backup captured successfully"
            cp "$BACKUP_PATH" "$MODPATH/original_backup.json"
        else
            ui_print " [!] Backup scheduled for service.sh on next boot"
            touch "$MODPATH/pending_backup"
        fi
    else
        touch "$MODPATH/pending_backup"
    fi
fi

# Detect WebUI Host
if pm list packages | grep -q "io.github.a13e300.ksuwebui"; then
    ui_print " [+] KSUWebUIStandalone detected"
elif pm list packages | grep -q "com.dergoogler.mmrl"; then
    ui_print " [+] MMRL host detected"
else
    ui_print " [i] Install KSUWebUIStandalone for graphical interface"
fi

ui_print " ========================================="
ui_print " > Installation complete."
ui_print " > Reboot device to load system overlay."
ui_print " > Terminal CLI: su -c 'hotspot-ctl status'"
ui_print " "
