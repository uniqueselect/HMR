#!/system/bin/sh
# Root Hotspot Manager - Magisk Action Trigger
# Launches WebUI via KSUWebUIStandalone or MMRL

MODDIR="${0%/*}"
[ ! -f "$MODDIR/module.prop" ] && MODDIR="/data/adb/modules/root_hotspot_manager"
MODID="$(grep "^id=" "$MODDIR/module.prop" 2>/dev/null | cut -d= -f2 | tr -d ' \r\n')"
[ -z "$MODID" ] && MODID="root_hotspot_manager"

echo "Checking WebUI hosts for $MODID..."

# 1. KSUWebUIStandalone (Recommended standalone host for Magisk)
if pm list packages | grep -q "io.github.a13e300.ksuwebui"; then
    echo "Launching KSUWebUIStandalone..."
    am start -n "io.github.a13e300.ksuwebui/.WebUIActivity" -e id "$MODID"
    exit 0
fi

# 2. MMRL (Magisk Module Repo Loader / WebUI host)
if pm list packages | grep -q "com.dergoogler.mmrl"; then
    echo "Launching MMRL WebUI..."
    am start -n "com.dergoogler.mmrl/.ui.activity.webui.WebUIActivity" -e id "$MODID"
    exit 0
fi

# 3. KernelSU Manager (if running in KernelSU environment)
if pm list packages | grep -q "me.weishu.kernelsu"; then
    echo "Launching KernelSU Manager..."
    am start -n "me.weishu.kernelsu/.ui.MainActivity"
    exit 0
fi

# 4. Host not detected
echo "--------------------------------------------------------"
echo "WebUI Host Application Not Found!"
echo "To access the graphical dashboard on Magisk,"
echo "please install KSUWebUIStandalone APK:"
echo "https://github.com/5ec1cff/KsuWebUIStandalone/releases"
echo "--------------------------------------------------------"
echo "Alternatively, you can manage the hotspot via CLI:"
echo "  su -c hotspot-ctl status"
echo "  su -c hotspot-ctl start"
echo "  su -c hotspot-ctl stop"
echo "  su -c hotspot-ctl restore-original"
echo "--------------------------------------------------------"

exit 1
