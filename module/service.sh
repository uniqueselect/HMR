#!/system/bin/sh
# Root Hotspot Manager - Late Boot Service Script
# Runs once after Android boot completes. No persistent daemon or battery drain.

MODDIR="${0%/*}"

# Wait for Android framework to fully boot
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

# Sleep an additional 3 seconds to ensure Wi-Fi service has started
sleep 3

# If initial backup was deferred during recovery flash, take it now
if [ -f "$MODDIR/pending_backup" ] || [ ! -f "/data/adb/root_hotspot_original_backup.json" ]; then
    if [ -x "$MODDIR/bin/hotspot-ctl" ]; then
        "$MODDIR/bin/hotspot-ctl" backup "/data/adb/root_hotspot_original_backup.json" > /dev/null 2>&1
        if [ -s "/data/adb/root_hotspot_original_backup.json" ]; then
            cp "/data/adb/root_hotspot_original_backup.json" "$MODDIR/original_backup.json" 2>/dev/null
            rm -f "$MODDIR/pending_backup"
        fi
    fi
fi

# Re-apply firewall block rules for persistent blocked clients
BLOCKED_FILE="/data/adb/root_hotspot_blocked_macs.txt"
if [ -f "$BLOCKED_FILE" ]; then
    while read -r mac; do
        if [ -n "$mac" ]; then
            iptables -C FORWARD -m mac --mac-source "$mac" -j DROP 2>/dev/null || \
                iptables -I FORWARD 1 -m mac --mac-source "$mac" -j DROP
            iptables -C INPUT -m mac --mac-source "$mac" -j DROP 2>/dev/null || \
                iptables -I INPUT 1 -m mac --mac-source "$mac" -j DROP
        fi
    done < "$BLOCKED_FILE"
fi

exit 0
