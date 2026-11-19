#!/system/bin/sh
# Root Hotspot Manager - Safe Uninstallation Script
# Handles automatic restoration of original Android Wi-Fi configuration

BACKUP_ORIGINAL="/data/adb/root_hotspot_original_backup.json"
RESTORE_LOG="/data/adb/root_hotspot_uninstall.log"
SCRIPT_DIR="$(dirname "$0")"

echo "=== Root Hotspot Manager Uninstall Started: $(date) ===" > "$RESTORE_LOG"

if [ -f "$BACKUP_ORIGINAL" ]; then
    echo "Found original configuration backup at $BACKUP_ORIGINAL" >> "$RESTORE_LOG"
    
    # Attempt restoration via Android SoftAp API
    RESTORE_OUTPUT=""
    if [ -x "$SCRIPT_DIR/bin/hotspot-ctl" ]; then
        RESTORE_OUTPUT=$("$SCRIPT_DIR/bin/hotspot-ctl" restore "$BACKUP_ORIGINAL" 2>&1)
        RESTORE_STATUS=$?
    elif [ -x "/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl" ]; then
        RESTORE_OUTPUT=$(/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl restore "$BACKUP_ORIGINAL" 2>&1)
        RESTORE_STATUS=$?
    else
        RESTORE_STATUS=1
        RESTORE_OUTPUT="hotspot-ctl binary not found"
    fi

    echo "Restore command exit code: $RESTORE_STATUS" >> "$RESTORE_LOG"
    echo "Restore output: $RESTORE_OUTPUT" >> "$RESTORE_LOG"

    if [ "$RESTORE_STATUS" -eq 0 ]; then
        echo "[SUCCESS] Original Wi-Fi hotspot settings were successfully restored to Android Wi-Fi service!" >> "$RESTORE_LOG"
        
        # Clean up iptables firewall rules for blocked clients
        if [ -f "/data/adb/root_hotspot_blocked_macs.txt" ]; then
            while read -r mac; do
                [ -n "$mac" ] && iptables -D FORWARD -m mac --mac-source "$mac" -j DROP 2>/dev/null
                [ -n "$mac" ] && iptables -D INPUT -m mac --mac-source "$mac" -j DROP 2>/dev/null
            done < "/data/adb/root_hotspot_blocked_macs.txt"
            rm -f "/data/adb/root_hotspot_blocked_macs.txt"
        fi

        # Clean up legacy VPN bypass config & rules if present
        rm -f "/data/adb/root_hotspot_vpn_bypass.conf"
        ip rule del pref 8000 2>/dev/null
        ip rule del pref 8001 2>/dev/null
        ip rule del pref 8002 2>/dev/null
        ip rule del pref 8003 2>/dev/null
        iptables -t mangle -D OUTPUT -o ap0 -j MARK --set-mark 0 2>/dev/null
        iptables -t mangle -D PREROUTING -i ap0 -j MARK --set-mark 0 2>/dev/null

        # Archive the backup file with timestamp for safety rather than silently deleting it
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        mv "$BACKUP_ORIGINAL" "/data/adb/root_hotspot_original_archived_${TIMESTAMP}.json"
        echo "Original backup archived to /data/adb/root_hotspot_original_archived_${TIMESTAMP}.json" >> "$RESTORE_LOG"
    else
        echo "[CRITICAL WARNING] Restoration failed! Preserving original backup." >> "$RESTORE_LOG"
        echo "The backup file was NOT deleted and remains at $BACKUP_ORIGINAL" >> "$RESTORE_LOG"
        
        # Create a loud warning file in /data/adb/
        cat << 'EOF' > /data/adb/root_hotspot_REVERT_WARNING.txt
====================================================================
HOTSPOT RESTORATION WARNING
====================================================================
During uninstallation of Root Hotspot Manager, the system was unable
to automatically apply your original Wi-Fi hotspot settings back to
the Android Wi-Fi service (likely because system services were
stopped during reboot/uninstallation).

YOUR ORIGINAL SETTINGS ARE SAFE AND HAVE NOT BEEN DELETED:
File location: /data/adb/root_hotspot_original_backup.json

You can verify or manually re-apply these settings if desired.
====================================================================
EOF
    fi
else
    echo "No original backup file found at $BACKUP_ORIGINAL (nothing to revert)." >> "$RESTORE_LOG"
fi

# Clean up module temporary files
rm -f /data/adb/root_hotspot_blocked_macs.txt 2>/dev/null
rm -f /data/adb/hotspot_temp_*.json 2>/dev/null
rm -f /data/adb/root_hotspot_debug.log 2>/dev/null
rm -rf /data/adb/webroot/root_hotspot_manager 2>/dev/null
rm -rf /data/adb/webroot/root_hotspot_manager_vanilla 2>/dev/null
rm -f /data/adb/modules/root_hotspot_manager_vanilla 2>/dev/null

echo "=== Uninstall Completed: $(date) ===" >> "$RESTORE_LOG"
