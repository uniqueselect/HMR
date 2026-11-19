// Root Hotspot Manager - WebUI Controller
// Target: Android 11+ (API 30+) AOSP / Project Treble GSI / OEM ROMs

const HOTSPOT_CTL = "/data/adb/modules/root_hotspot_manager/bin/hotspot-ctl";

// State Cache
let currentConfig = {
    ssid: "HiFi",
    security_type: 1,
    passphrase: "",
    hidden: false,
    band: 1,
    channel: 0,
    max_clients: 0,
    auto_shutdown: true,
    shutdown_timeout_millis: 600000,
    blocked_clients: []
};

let isRunning = false;

// DOM Elements: Header & Hero
const masterToggle = document.getElementById("masterToggle");
const masterToggleLabel = document.getElementById("masterToggleLabel");
const stateIndicator = document.getElementById("stateIndicator");
const displaySsid = document.getElementById("displaySsid");
const displayBand = document.getElementById("displayBand");
const displaySec = document.getElementById("displaySec");
const displayIp = document.getElementById("displayIp");
const badgeHidden = document.getElementById("badgeHidden");
const btnRefresh = document.getElementById("btnRefresh");
const btnDiag = document.getElementById("btnDiag");

// DOM Elements: Form Fields
const inputSsid = document.getElementById("inputSsid");
const selectSecurity = document.getElementById("selectSecurity");
const inputPassword = document.getElementById("inputPassword");
const btnTogglePass = document.getElementById("btnTogglePass");
const selectBand = document.getElementById("selectBand");
const selectChannel = document.getElementById("selectChannel");
const toggleHiddenSsid = document.getElementById("toggleHiddenSsid");
const toggleAutoShutdown = document.getElementById("toggleAutoShutdown");
const btnApplyConfig = document.getElementById("btnApplyConfig");

// State Variables: NUMERIC SLIDERS & EDIT POPUP
let currentMaxClients = 0;
let currentTimeoutVal = 10;
let currentTimeoutUnit = "min";
let currentEditingMode = null; // "clients" or "timeout"

const sliderMaxClients = document.getElementById("sliderMaxClients");
const valMaxClients = document.getElementById("valMaxClients");
const sliderTimeout = document.getElementById("sliderTimeout");
const valTimeout = document.getElementById("valTimeout");
const tickMin = document.getElementById("tickMin");
const tickMid1 = document.getElementById("tickMid1");
const tickMid2 = document.getElementById("tickMid2");
const tickMid3 = document.getElementById("tickMid3");
const tickMax = document.getElementById("tickMax");

// DOM Elements: EDIT NUMBER POPUP MODAL
const editNumberModal = document.getElementById("editNumberModal");
const editModalTitle = document.getElementById("editModalTitle");
const btnCloseEditModal = document.getElementById("btnCloseEditModal");
const editModalPresetSelect = document.getElementById("editModalPresetSelect");
const btnStepperDec = document.getElementById("btnStepperDec");
const editModalNumberInput = document.getElementById("editModalNumberInput");
const btnStepperInc = document.getElementById("btnStepperInc");
const editModalUnitSelect = document.getElementById("editModalUnitSelect");
const btnCancelEditModal = document.getElementById("btnCancelEditModal");
const btnSaveEditModal = document.getElementById("btnSaveEditModal");

// DOM Elements: Clients & Blocklist
const clientListContainer = document.getElementById("clientListContainer");
const clientCountBadge = document.getElementById("clientCountBadge");
const tabClientCount = document.getElementById("tabClientCount");
const blockedListContainer = document.getElementById("blockedListContainer");
const blockedCountBadge = document.getElementById("blockedCountBadge");
const inputManualMac = document.getElementById("inputManualMac");
const btnManualBlock = document.getElementById("btnManualBlock");

// DOM Elements: Backup & Reversion
const metaOrigSsid = document.getElementById("metaOrigSsid");
const metaOrigSec = document.getElementById("metaOrigSec");
const btnRevertOriginal = document.getElementById("btnRevertOriginal");
const btnExportBackup = document.getElementById("btnExportBackup");

// DOM Elements: GEEKY LIVE TRANSCRIBER SCREENER DOCK
const screenerDock = document.getElementById("screenerDock");
const screenerDisplay = document.getElementById("screenerDisplay");
const screenerText = document.getElementById("screenerText");
const screenerStatusPill = document.getElementById("screenerStatusPill");
const btnFetchLogcatMini = document.getElementById("btnFetchLogcatMini");
const btnCopyLogsMini = document.getElementById("btnCopyLogsMini");
const btnOpenFullscreen = document.getElementById("btnOpenFullscreen");

// DOM Elements: FULLSCREEN TERMINAL MODAL
const fullscreenTerminalModal = document.getElementById("fullscreenTerminalModal");
const fullTerminalConsole = document.getElementById("fullTerminalConsole");
const fsTermStatusBadge = document.getElementById("fsTermStatusBadge");
const btnFsFetchLogcat = document.getElementById("btnFsFetchLogcat");
const fsFetchLabel = document.getElementById("fsFetchLabel");
const btnFsClearTerminal = document.getElementById("btnFsClearTerminal");
const btnFsCopyTerminal = document.getElementById("btnFsCopyTerminal");
const fsCopyTerminalLabel = document.getElementById("fsCopyTerminalLabel");
const btnCloseFullscreen = document.getElementById("btnCloseFullscreen");

// DOM Elements: Modals
const diagModal = document.getElementById("diagModal");
const btnCloseDiag = document.getElementById("btnCloseDiag");
const btnDismissDiag = document.getElementById("btnDismissDiag");
const btnRunCheckAgain = document.getElementById("btnRunCheckAgain");
const diagContent = document.getElementById("diagContent");

const confirmModal = document.getElementById("confirmModal");
const btnCloseConfirm = document.getElementById("btnCloseConfirm");
const btnConfirmCancel = document.getElementById("btnConfirmCancel");
const btnConfirmOk = document.getElementById("btnConfirmOk");

// DOM Elements: Top Bar & About / Support Modal
const brandSubtitle = document.getElementById("brandSubtitle");
const btnAboutSupport = document.getElementById("btnAboutSupport");
const aboutSupportModal = document.getElementById("aboutSupportModal");
const btnCloseAboutModal = document.getElementById("btnCloseAboutModal");
const btnCloseAboutBtn = document.getElementById("btnCloseAboutBtn");
const btnVisitRepo = document.getElementById("btnVisitRepo");
const aboutDeviceText = document.getElementById("aboutDeviceText");
const selectCryptoCoin = document.getElementById("selectCryptoCoin");
const cryptoBadge = document.getElementById("cryptoBadge");
const cryptoAddressDisplay = document.getElementById("cryptoAddressDisplay");
const btnCopyCryptoAddress = document.getElementById("btnCopyCryptoAddress");
const copyCryptoLabel = document.getElementById("copyCryptoLabel");

// Cryptocurrency donation addresses
const CRYPTO_DATA = {
    btc: {
        network: "BTC Network (Native SegWit)",
        address: "bc1q7r5hnwsxkz9krrekkwvs0xalapumsjca27su4j"
    },
    bnb: {
        network: "Binance Smart Chain (BEP20)",
        address: "0x5a696aa4e9d9a83b25198151cf37a5c8c4621f34"
    },
    usdc: {
        network: "USDC (ERC20 / Polygon / BEP20)",
        address: "0x5a696aa4e9d9a83b25198151cf37a5c8c4621f34"
    },
    usdt: {
        network: "Tether USDT (TRC20 / BEP20 / Polygon)",
        address: "0x5a696aa4e9d9a83b25198151cf37a5c8c4621f34"
    },
    sol: {
        network: "Solana Network (SOL)",
        address: "0x5a696aa4e9d9a83b25198151cf37a5c8c4621f34"
    },
    matic: {
        network: "Polygon Network (POL / MATIC)",
        address: "0x5a696aa4e9d9a83b25198151cf37a5c8c4621f34"
    },
    trx: {
        network: "Tron Network (TRC20)",
        address: "0x5a696aa4e9d9a83b25198151cf37a5c8c4621f34"
    },
    ltc: {
        network: "Litecoin Network (LTC)",
        address: "0x5a696aa4e9d9a83b25198151cf37a5c8c4621f34"
    }
};

// Tab Navigation Logic
const tabButtons = document.querySelectorAll(".tab-btn");
const tabPanels = document.querySelectorAll(".tab-panel");

function switchTab(targetTabId) {
    tabButtons.forEach(btn => {
        if (btn.dataset.tab === targetTabId) {
            btn.classList.add("active");
        } else {
            btn.classList.remove("active");
        }
    });
    tabPanels.forEach(panel => {
        if (panel.id === targetTabId) {
            panel.classList.add("active");
        } else {
            panel.classList.remove("active");
        }
    });

    // Auto-update connected devices when user views Devices tab
    if (targetTabId === "tab-clients" && isRunning) {
        execShell(`${HOTSPOT_CTL} clients`).then(res => {
            if (res.errno === 0 && res.stdout) {
                try {
                    const data = JSON.parse(res.stdout);
                    if (data.clients) renderConnectedClients(data.clients);
                } catch (ignored) {}
            }
        });
    }
}

tabButtons.forEach(btn => {
    btn.addEventListener("click", () => switchTab(btn.dataset.tab));
});

// Max Clients Handlers
function updateMaxClients(val) {
    const num = isNaN(val) ? 0 : Math.max(0, parseInt(val, 10));
    currentMaxClients = num;
    if (sliderMaxClients) {
        sliderMaxClients.value = Math.min(32, num);
    }
    if (valMaxClients) {
        const text = num === 0 ? "Unlimited" : `${num} client${num > 1 ? "s" : ""}`;
        valMaxClients.innerHTML = `${text} <span class="badge-caret">▾</span>`;
    }
}

if (sliderMaxClients) {
    sliderMaxClients.addEventListener("input", () => {
        updateMaxClients(sliderMaxClients.value);
    });
}

// Auto-Shutdown Handlers
function updateTimeoutSliderRange(unit) {
    if (!sliderTimeout) return;
    if (unit === "sec") {
        sliderTimeout.min = "5";
        sliderTimeout.max = "300";
        sliderTimeout.step = "5";
        if (tickMin) tickMin.textContent = "5 sec";
        if (tickMid1) tickMid1.textContent = "30 sec";
        if (tickMid2) tickMid2.textContent = "60 sec";
        if (tickMid3) tickMid3.textContent = "180 sec";
        if (tickMax) tickMax.textContent = "300 sec";
    } else {
        sliderTimeout.min = "1";
        sliderTimeout.max = "60";
        sliderTimeout.step = "1";
        if (tickMin) tickMin.textContent = "1 min";
        if (tickMid1) tickMid1.textContent = "15 min";
        if (tickMid2) tickMid2.textContent = "30 min";
        if (tickMid3) tickMid3.textContent = "45 min";
        if (tickMax) tickMax.textContent = "60 min";
    }
}

function updateTimeout(val, unit = currentTimeoutUnit, forceEnable = false) {
    currentTimeoutUnit = unit || "min";
    const num = isNaN(val) ? (currentTimeoutUnit === "sec" ? 60 : 10) : Math.max(1, parseInt(val, 10));
    currentTimeoutVal = num;

    if (forceEnable && toggleAutoShutdown) {
        toggleAutoShutdown.checked = true;
    }

    updateTimeoutSliderRange(currentTimeoutUnit);

    if (!toggleAutoShutdown || toggleAutoShutdown.checked) {
        if (sliderTimeout) {
            const maxRange = currentTimeoutUnit === "sec" ? 300 : 60;
            sliderTimeout.value = Math.min(maxRange, num);
        }
        if (valTimeout) {
            valTimeout.innerHTML = `${num} ${currentTimeoutUnit} <span class="badge-caret">▾</span>`;
        }
    } else {
        if (valTimeout) valTimeout.innerHTML = `Disabled <span class="badge-caret">▾</span>`;
    }
}

if (sliderTimeout) {
    sliderTimeout.addEventListener("input", () => {
        updateTimeout(sliderTimeout.value, currentTimeoutUnit, true);
    });
}

if (toggleAutoShutdown) {
    toggleAutoShutdown.addEventListener("change", () => {
        const enabled = toggleAutoShutdown.checked;
        if (valTimeout) {
            const text = enabled ? `${currentTimeoutVal} ${currentTimeoutUnit}` : "Disabled";
            valTimeout.innerHTML = `${text} <span class="badge-caret">▾</span>`;
        }
    });
}

// EDIT NUMBER POPUP MODAL (Centered, Keypad-safe)
function openEditModal(mode) {
    currentEditingMode = mode;
    editModalPresetSelect.innerHTML = "";

    if (mode === "clients") {
        editModalTitle.textContent = "Set Max Clients Limit";
        editModalUnitSelect.style.display = "none";
        editModalNumberInput.min = "0";
        editModalNumberInput.max = "999";
        editModalNumberInput.value = currentMaxClients;

        const presets = [
            { text: "0 — Unlimited (Default)", val: 0 },
            { text: "1 client", val: 1 },
            { text: "2 clients", val: 2 },
            { text: "4 clients", val: 4 },
            { text: "8 clients", val: 8 },
            { text: "10 clients", val: 10 },
            { text: "16 clients", val: 16 },
            { text: "32 clients", val: 32 },
            { text: "Custom Number...", val: "custom" }
        ];

        let matched = false;
        presets.forEach(p => {
            const opt = document.createElement("option");
            opt.value = p.val;
            opt.textContent = p.text;
            if (p.val === currentMaxClients) {
                opt.selected = true;
                matched = true;
            }
            editModalPresetSelect.appendChild(opt);
        });

        if (!matched) {
            editModalPresetSelect.value = "custom";
        }
    } else if (mode === "timeout") {
        editModalTitle.textContent = "Set Auto-Shutdown Delay";
        editModalUnitSelect.style.display = "block";
        editModalUnitSelect.value = currentTimeoutUnit;
        editModalNumberInput.min = "1";
        editModalNumberInput.max = "9999";
        editModalNumberInput.value = currentTimeoutVal;

        const presets = [
            { text: "5 seconds", val: 5, unit: "sec" },
            { text: "10 seconds", val: 10, unit: "sec" },
            { text: "15 seconds", val: 15, unit: "sec" },
            { text: "30 seconds", val: 30, unit: "sec" },
            { text: "1 minute", val: 1, unit: "min" },
            { text: "5 minutes", val: 5, unit: "min" },
            { text: "10 minutes (Default)", val: 10, unit: "min" },
            { text: "15 minutes", val: 15, unit: "min" },
            { text: "30 minutes", val: 30, unit: "min" },
            { text: "60 minutes", val: 60, unit: "min" },
            { text: "Custom Number...", val: "custom", unit: currentTimeoutUnit }
        ];

        let matched = false;
        presets.forEach(p => {
            const opt = document.createElement("option");
            opt.value = p.val;
            opt.textContent = p.text;
            opt.dataset.unit = p.unit;
            if (p.val === currentTimeoutVal && p.unit === currentTimeoutUnit) {
                opt.selected = true;
                matched = true;
            }
            editModalPresetSelect.appendChild(opt);
        });

        if (!matched) {
            editModalPresetSelect.value = "custom";
        }
    }

    editNumberModal.style.display = "flex";
}

function closeEditModal() {
    if (document.activeElement) {
        document.activeElement.blur();
    }
    editNumberModal.style.display = "none";
    currentEditingMode = null;
}

if (valMaxClients) valMaxClients.addEventListener("click", () => openEditModal("clients"));
if (valTimeout) valTimeout.addEventListener("click", () => openEditModal("timeout"));

if (btnCloseEditModal) btnCloseEditModal.addEventListener("click", closeEditModal);
if (btnCancelEditModal) btnCancelEditModal.addEventListener("click", closeEditModal);

if (editModalPresetSelect) {
    editModalPresetSelect.addEventListener("change", () => {
        const sel = editModalPresetSelect.value;
        if (sel === "custom") {
            editModalNumberInput.focus();
        } else {
            const num = parseInt(sel, 10);
            editModalNumberInput.value = num;
            const opt = editModalPresetSelect.selectedOptions[0];
            if (opt && opt.dataset.unit && editModalUnitSelect) {
                editModalUnitSelect.value = opt.dataset.unit;
            }
        }
    });
}

if (editModalNumberInput) {
    editModalNumberInput.addEventListener("input", () => {
        if (editModalPresetSelect) editModalPresetSelect.value = "custom";
    });
}

if (btnStepperDec) {
    btnStepperDec.addEventListener("click", () => {
        let cur = parseInt(editModalNumberInput.value, 10) || 0;
        const min = currentEditingMode === "clients" ? 0 : 1;
        if (cur > min) {
            cur--;
            editModalNumberInput.value = cur;
            if (editModalPresetSelect) editModalPresetSelect.value = "custom";
        }
    });
}

if (btnStepperInc) {
    btnStepperInc.addEventListener("click", () => {
        let cur = parseInt(editModalNumberInput.value, 10) || 0;
        cur++;
        editModalNumberInput.value = cur;
        if (editModalPresetSelect) editModalPresetSelect.value = "custom";
    });
}

if (btnSaveEditModal) {
    btnSaveEditModal.addEventListener("click", () => {
        // 1. Dismiss virtual keyboard immediately
        if (document.activeElement) {
            document.activeElement.blur();
        }

        if (currentEditingMode === "clients") {
            const val = Math.max(0, parseInt(editModalNumberInput.value, 10) || 0);
            updateMaxClients(val);
        } else if (currentEditingMode === "timeout") {
            const val = Math.max(1, parseInt(editModalNumberInput.value, 10) || 10);
            const unit = editModalUnitSelect ? editModalUnitSelect.value : "min";
            updateTimeout(val, unit, true);
        }

        closeEditModal();
        showToast("Value updated");
    });
}

// Terminal & Live Transcriber Logging Utility
let totalLogLines = 0;

let statusResetTimer = null;

function updateTerminalStatusPills(text, pillClass = "idle", autoResetToReady = false) {
    if (statusResetTimer) {
        clearTimeout(statusResetTimer);
        statusResetTimer = null;
    }

    const pills = [screenerStatusPill, fsTermStatusBadge];
    pills.forEach(pill => {
        if (!pill) return;
        pill.textContent = text;
        pill.className = `screener-status-pill ${pillClass}`;
    });

    if (autoResetToReady) {
        statusResetTimer = setTimeout(() => {
            pills.forEach(pill => {
                if (!pill) return;
                pill.textContent = "READY";
                pill.className = "screener-status-pill idle";
            });
        }, 3500);
    }
}

function logToTerminal(message, type = "info") {
    const now = new Date();
    const timeStr = now.toTimeString().split(" ")[0];

    let prefix = "❯ [INFO]";
    let screenerClass = "";
    let pillText = "READY";
    let pillClass = "idle";
    let autoReset = false;

    const msgStr = String(message || "");
    const firstLine = msgStr.split("\n")[0].trim();
    let screenerLine = firstLine;

    if (type === "error") {
        prefix = "✖ [ERROR]";
        screenerClass = "is-error";
        pillText = "ERROR";
        pillClass = "error";
        autoReset = true;
        screenerLine = firstLine.replace(/^(✖\s*)?(\[ERR(OR)?\]\s*)?/i, "");
    } else if (type === "warn") {
        prefix = "▲ [WARN]";
        screenerClass = "is-warn";
        pillText = "WARN";
        pillClass = "busy";
        screenerLine = firstLine;
    } else if (type === "cmd") {
        prefix = "⚡ [CMD]";
        pillText = "RUNNING";
        pillClass = "busy";
        screenerLine = firstLine;
    } else if (type === "success") {
        prefix = "✔ [SUCCESS]";
        screenerClass = "is-success";
        pillText = "SUCCESS";
        pillClass = "success";
        autoReset = true;
        // Strip duplicate success markers from ticker text
        screenerLine = firstLine.replace(/^(✔\s*)?(\[SUCCESS\]\s*)?/i, "");
    } else if (type === "live") {
        prefix = "● [LIVE]";
        screenerClass = "is-live";
        pillText = "LIVE";
        pillClass = "live";
        screenerLine = firstLine;
    }

    // 1. Append to Fullscreen Console
    if (fullTerminalConsole) {
        fullTerminalConsole.textContent += `[${timeStr}] ${prefix} ${msgStr}\n`;
        fullTerminalConsole.scrollTop = fullTerminalConsole.scrollHeight;
    }

    // 2. Transcribe to Screener Ticker
    if (screenerText) {
        screenerText.textContent = screenerLine;
        screenerText.className = `screener-line-text ${screenerClass}`;
    }

    // 3. Keep Home and Fullscreen badges in sync
    updateTerminalStatusPills(pillText, pillClass, autoReset);
}

// Clipboard Copy with WebView Fallback
async function copyToClipboard(text) {
    if (!text) return false;
    if (navigator.clipboard && window.isSecureContext) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch (e) {}
    }
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";
    textArea.style.left = "-9999px";
    textArea.style.top = "0";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    let ok = false;
    try {
        ok = document.execCommand("copy");
    } catch (err) {}
    document.body.removeChild(textArea);
    return ok;
}

// KernelSU WebUI Bridge
function execShell(cmd) {
    return new Promise((resolve) => {
        // Strip unnecessary su -c wrapper if running directly in ksu / root bridge
        let cleanCmd = cmd ? cmd.trim() : "";
        if (cleanCmd.startsWith('su -c "') && cleanCmd.includes('" 2>/dev/null')) {
            cleanCmd = cleanCmd.substring(7, cleanCmd.indexOf('" 2>/dev/null'));
        } else if (cleanCmd.startsWith('su -c "') && cleanCmd.endsWith('"')) {
            cleanCmd = cleanCmd.substring(7, cleanCmd.length - 1);
        }

        if (window.ksu && typeof window.ksu.exec === "function") {
            const cbName = "ksu_cb_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
            window[cbName] = (errno, stdout, stderr) => {
                delete window[cbName];
                resolve({ errno, stdout, stderr });
            };
            try {
                // ksu.exec already runs in root context! Do NOT invoke su inside ksu.exec
                window.ksu.exec(cleanCmd, "{}", cbName);
            } catch (err) {
                delete window[cbName];
                resolve({ errno: -1, stdout: "", stderr: String(err) });
            }
        } else {
            mockShell(cleanCmd).then(resolve);
        }
    });
}

// Mock Shell for Browser Previews
async function mockShell(cmd) {
    await new Promise(r => setTimeout(r, 150));
    console.log("[MOCK SHELL EXEC]:", cmd);

    if (cmd.includes("status")) {
        return {
            errno: 0,
            stdout: JSON.stringify({
                success: true,
                is_running: isRunning,
                ap_state_name: isRunning ? "ENABLED" : "DISABLED",
                config: currentConfig,
                connected_count: isRunning ? 1 : 0,
                clients: isRunning ? [
                    { ip: "192.168.43.55", mac: "8A:2C:94:11:0B:42", hostname: "Client-Device", interface: "ap0", is_blocked: false }
                ] : [],
                blocked_count: currentConfig.blocked_clients ? currentConfig.blocked_clients.length : 0,
                vpn_bypass_enabled: isVpnBypassActive,
                device_info: {
                    model: "Generic Android",
                    manufacturer: "AOSP",
                    brand: "Google",
                    device: "generic",
                    hardware: "arm64",
                    android_version: "11+",
                    api_level: 30
                }
            }),
            stderr: ""
        };
    } else if (cmd.includes("start")) {
        isRunning = true;
        return { errno: 0, stdout: JSON.stringify({ success: true, ap_state: "ENABLED" }), stderr: "" };
    } else if (cmd.includes("stop")) {
        isRunning = false;
        return { errno: 0, stdout: JSON.stringify({ success: true, ap_state: "DISABLED" }), stderr: "" };
    } else if (cmd.includes("set-b64") || cmd.includes("set")) {
        return { errno: 0, stdout: JSON.stringify({ success: true, message: "Settings applied" }), stderr: "" };
    } else if (cmd.includes("logcat")) {
        return {
            errno: 0,
            stdout: "=== MOCK LOGCAT ===\nwificond: SoftAp started on ap0\nhostapd: ap0: AP-ENABLED\nhostapd: ignore_broadcast_ssid=" + (currentConfig.hidden ? "1" : "0"),
            stderr: ""
        };
    } else if (cmd.includes("restore-original")) {
        currentConfig.hidden = false;
        return { errno: 0, stdout: JSON.stringify({ success: true, message: "Restored factory defaults" }), stderr: "" };
    } else if (cmd.includes("vpn-bypass")) {
        if (cmd.includes("on")) isVpnBypassActive = true;
        else if (cmd.includes("off")) isVpnBypassActive = false;
        return { errno: 0, stdout: JSON.stringify({ success: true, vpn_bypass: isVpnBypassActive }), stderr: "" };
    }
    return { errno: 0, stdout: JSON.stringify({ success: true }), stderr: "" };
}

// Hardware Identity Updater
function updateDeviceInfo(info) {
    if (!info) return;
    const model = (info.model || info.device || "Android Device").trim();
    const brand = (info.manufacturer || info.brand || "").trim();
    const release = info.android_version ? `Android ${info.android_version}` : `API ${info.api_level || 30}`;
    const fullDeviceStr = (brand && !model.toLowerCase().includes(brand.toLowerCase())) ? `${brand} ${model}` : model;

    if (brandSubtitle) {
        brandSubtitle.textContent = `${fullDeviceStr} · ${release} · Root SoftAP`;
    }
    if (aboutDeviceText) {
        aboutDeviceText.textContent = `${fullDeviceStr} (${release}, API ${info.api_level || 30})`;
    }
}

// Fetch Status & Render UI
async function refreshStatus(showToastOnSuccess = false) {
    const cmd = `${HOTSPOT_CTL} status`;
    const res = await execShell(cmd);

    if (res.errno !== 0 && !res.stdout) {
        logToTerminal(`Status query failed: ${res.stderr || "Process error"}`, "error");
        showToast("Error querying hotspot status", "error");
        return;
    }

    try {
        const data = JSON.parse(res.stdout);
        if (data.config) {
            currentConfig = Object.assign(currentConfig, data.config);
        }
        isRunning = Boolean(data.is_running);

        if (data.device_info) {
            updateDeviceInfo(data.device_info);
        }

        renderHotspotHero(data);
        renderConnectedClients(data.clients || []);
        renderBlockedClients(currentConfig.blocked_clients || []);

        if (isRunning) {
            startClientAutoPoll();
        } else {
            stopClientAutoPoll();
        }

        if (showToastOnSuccess) {
            showToast("Hotspot status refreshed");
        }
    } catch (e) {
        logToTerminal(`JSON parse error on status response:\n${res.stdout}`, "warn");
    }
}

// Render Main Hero Card & Form
function renderHotspotHero(data) {
    displaySsid.textContent = currentConfig.ssid || "HiFi";

    const bandLabel = currentConfig.band === 2 ? "5.0 GHz" : "2.4 GHz";
    let secLabel = "WPA2-PSK";
    if (currentConfig.security_type === 0) secLabel = "Open";
    else if (currentConfig.security_type >= 3) secLabel = "WPA3-SAE";
    else if (currentConfig.security_type === 2) secLabel = "WPA3 Transition";

    if (displayBand) displayBand.textContent = bandLabel;
    if (displaySec) displaySec.textContent = secLabel;

    if (isRunning) {
        stateIndicator.textContent = "ACTIVE";
        stateIndicator.className = "state-pill state-on";
        displayIp.textContent = (data && data.ap_ip && data.ap_ip !== "Stopped") ? data.ap_ip : "Active";
        masterToggle.checked = true;
        masterToggleLabel.textContent = "ON";
    } else {
        stateIndicator.textContent = "OFFLINE";
        stateIndicator.className = "state-pill state-off";
        displayIp.textContent = "Stopped";
        masterToggle.checked = false;
        masterToggleLabel.textContent = "OFF";
    }

    // Hidden Badge
    if (currentConfig.hidden) {
        badgeHidden.textContent = "Hidden";
        badgeHidden.className = "badge-mini is-hidden";
    } else {
        badgeHidden.textContent = "Broadcast";
        badgeHidden.className = "badge-mini";
    }

    // Form inputs (if not currently focused)
    if (document.activeElement !== inputSsid) inputSsid.value = currentConfig.ssid || "";
    if (document.activeElement !== inputPassword) inputPassword.value = currentConfig.passphrase || "";
    selectSecurity.value = currentConfig.security_type !== undefined ? currentConfig.security_type : "1";
    selectBand.value = currentConfig.band || "1";
    selectChannel.value = currentConfig.channel || "0";
    toggleHiddenSsid.checked = Boolean(currentConfig.hidden);
    toggleAutoShutdown.checked = currentConfig.auto_shutdown !== undefined ? currentConfig.auto_shutdown : true;

    // Sliders & Numeric Inputs
    if (document.activeElement !== sliderMaxClients && currentEditingMode !== "clients") {
        const mc = currentConfig.max_clients || 0;
        updateMaxClients(mc);
    }

    if (document.activeElement !== sliderTimeout && currentEditingMode !== "timeout") {
        const ms = currentConfig.shutdown_timeout_millis !== undefined ? currentConfig.shutdown_timeout_millis : 600000;
        if (currentConfig.auto_shutdown === false) {
            toggleAutoShutdown.checked = false;
            valTimeout.innerHTML = 'Disabled <span class="badge-caret">▾</span>';
        } else if (ms > 0 && ms < 60000) {
            const secs = Math.round(ms / 1000);
            updateTimeout(secs, "sec");
        } else {
            const mins = Math.max(1, Math.round(ms / 60000));
            updateTimeout(mins, "min");
        }
    }

    // Passphrase field visibility
    const isSecOpen = selectSecurity.value === "0";
    document.getElementById("passphraseGroup").style.display = isSecOpen ? "none" : "flex";

    // Backup meta preview
    metaOrigSsid.textContent = currentConfig.ssid || "HiFi";
    metaOrigSec.textContent = secLabel;
}

// Render Connected Devices List
function renderConnectedClients(clients) {
    const clientsCount = clients.length;
    clientCountBadge.textContent = `${clientsCount} Active`;
    tabClientCount.textContent = clientsCount.toString();

    if (!clients || clients.length === 0) {
        clientListContainer.innerHTML = `
            <div class="empty-state-mini">
                <div class="pulse-beacon-mini"></div>
                <span>No devices connected</span>
            </div>
        `;
        return;
    }

    let html = "";
    clients.forEach((c) => {
        const isBlocked = c.is_blocked || false;
        html += `
            <div class="client-row-mini">
                <div class="client-mini-info">
                    <span class="client-mini-name" title="${escapeHtml(c.hostname || "")}">${escapeHtml(c.hostname || "android@device")}</span>
                    <span class="client-mini-details">${escapeHtml(c.ip || "")} · ${escapeHtml(c.mac || "")}</span>
                </div>
                <div>
                    ${isBlocked 
                        ? `<button class="btn-block-mini" onclick="unblockMac('${c.mac}')">Unblock</button>` 
                        : `<button class="btn-block-mini" onclick="blockMac('${c.mac}')">Block</button>`
                    }
                </div>
            </div>
        `;
    });
    clientListContainer.innerHTML = html;
}

// Render Blocked Devices List
function renderBlockedClients(macs) {
    const count = macs ? macs.length : 0;
    blockedCountBadge.textContent = `${count} Blocked`;

    if (!macs || macs.length === 0) {
        blockedListContainer.innerHTML = `<div class="empty-blocked-mini">No blocked devices</div>`;
        return;
    }

    let html = "";
    macs.forEach((mac) => {
        html += `
            <div class="client-row-mini">
                <span class="client-mini-name">${escapeHtml(mac)}</span>
                <button class="btn-block-mini" onclick="unblockMac('${mac}')">Remove</button>
            </div>
        `;
    });
    blockedListContainer.innerHTML = html;
}

// Lightweight Connected Device Auto-Poll (Active ONLY when Hotspot is ON)
let clientPollTimer = null;
let isPollingClients = false;

function startClientAutoPoll() {
    stopClientAutoPoll();
    if (!isRunning) return;

    clientPollTimer = setInterval(async () => {
        // Battery saving & quiet guard: only poll if hotspot is ON, page is visible, and no request in-flight
        if (!isRunning || document.hidden || isPollingClients) return;
        isPollingClients = true;
        try {
            const res = await execShell(`${HOTSPOT_CTL} clients`);
            if (res.errno === 0 && res.stdout) {
                const data = JSON.parse(res.stdout);
                if (data.clients) {
                    renderConnectedClients(data.clients);
                }
                if (data.ap_ip && isRunning && displayIp && displayIp.textContent !== data.ap_ip) {
                    displayIp.textContent = data.ap_ip;
                }
            }
        } catch (ignored) {
        } finally {
            isPollingClients = false;
        }
    }, 3500);
}

function stopClientAutoPoll() {
    if (clientPollTimer) {
        clearInterval(clientPollTimer);
        clientPollTimer = null;
    }
    isPollingClients = false;
}

document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
        stopClientAutoPoll();
    } else if (isRunning) {
        startClientAutoPoll();
        execShell(`${HOTSPOT_CTL} clients`).then(res => {
            if (res.errno === 0 && res.stdout) {
                try {
                    const data = JSON.parse(res.stdout);
                    if (data.clients) renderConnectedClients(data.clients);
                    if (data.ap_ip && isRunning && displayIp) displayIp.textContent = data.ap_ip;
                } catch (ignored) {}
            }
        });
    }
});

// Master Toggle (Start/Stop Hotspot)
masterToggle.addEventListener("change", async () => {
    const shouldStart = masterToggle.checked;
    masterToggleLabel.textContent = shouldStart ? "STARTING..." : "STOPPING...";
    logToTerminal(`Action: ${shouldStart ? "START" : "STOP"} Hotspot (Wi-Fi client STA remains OFF)`, "cmd");

    const cmd = shouldStart ? `${HOTSPOT_CTL} start` : `${HOTSPOT_CTL} stop`;
    const res = await execShell(cmd);

    if (res.errno === 0) {
        isRunning = shouldStart;
        if (shouldStart) {
            startClientAutoPoll();
        } else {
            stopClientAutoPoll();
            renderConnectedClients([]);
        }
        logToTerminal(`Hotspot successfully ${shouldStart ? "started" : "stopped"}.\n${res.stdout || ""}`, "success");
        showToast(shouldStart ? "Hotspot active: Connected devices can access Wi-Fi." : "Hotspot stopped.", shouldStart ? "success" : "info", 6500);
    } else {
        const err = res.stderr || res.stdout || "Command error";
        logToTerminal(`Failed to ${shouldStart ? "start" : "stop"} hotspot:\n${err}`, "error");
        showToast("Action failed: " + err, "error", 6500);
        masterToggle.checked = !shouldStart;

        // Auto-fetch logcat on failure
        const logRes = await execShell(`${HOTSPOT_CTL} log | tail -n 25`);
        if (logRes.stdout) {
            logToTerminal("System logcat snippet:\n" + logRes.stdout, "warn");
        }
    }
    await refreshStatus();
});

// Security selector change listener
selectSecurity.addEventListener("change", () => {
    const isSecOpen = selectSecurity.value === "0";
    document.getElementById("passphraseGroup").style.display = isSecOpen ? "none" : "flex";
});

// Password visibility toggle
btnTogglePass.addEventListener("click", () => {
    if (inputPassword.type === "password") {
        inputPassword.type = "text";
        btnTogglePass.style.color = "var(--accent-emerald)";
    } else {
        inputPassword.type = "password";
        btnTogglePass.style.color = "var(--text-muted)";
    }
});

// Apply Configuration
btnApplyConfig.addEventListener("click", async () => {
    const ssid = inputSsid.value.trim();
    if (!ssid) {
        showToast("SSID cannot be empty", "error");
        return;
    }

    const secType = parseInt(selectSecurity.value, 10);
    const pass = inputPassword.value;
    if (secType !== 0 && pass && pass.length < 8) {
        showToast("Password must be at least 8 characters", "error");
        return;
    }

    const maxClientsVal = currentMaxClients;
    const isAutoShutdown = toggleAutoShutdown.checked;
    const timeoutUnit = currentTimeoutUnit;
    const timeoutVal = currentTimeoutVal;
    const timeoutMillisVal = isAutoShutdown ? (timeoutUnit === "sec" ? timeoutVal * 1000 : timeoutVal * 60 * 1000) : 0;

    const payload = {
        ssid: ssid,
        security_type: secType,
        passphrase: secType === 0 ? "" : pass,
        band: parseInt(selectBand.value, 10),
        channel: parseInt(selectChannel.value, 10),
        hidden: toggleHiddenSsid.checked,
        auto_shutdown: isAutoShutdown,
        max_clients: maxClientsVal,
        shutdown_timeout_millis: timeoutMillisVal
    };

    const jsonStr = JSON.stringify(payload);
    const b64 = btoa(unescape(encodeURIComponent(jsonStr)));

    const delayDesc = isAutoShutdown ? `${timeoutVal} ${timeoutUnit}` : "disabled";
    const mcDesc = maxClientsVal === 0 ? "unlimited" : maxClientsVal;
    logToTerminal(`Applying settings: hidden=${payload.hidden}, max_clients=${mcDesc}, delay=${delayDesc}, ssid="${payload.ssid}"`, "cmd");

    const cmd = `${HOTSPOT_CTL} set-b64 ${b64}`;
    const res = await execShell(cmd);

    if (res.errno === 0) {
        logToTerminal(`Configuration applied successfully:\n${res.stdout || ""}`, "success");
        showToast("Hotspot settings successfully updated!", "success");
        setTimeout(async () => {
            await refreshStatus(false);
        }, 1200);
    } else {
        const err = res.stderr || res.stdout || "Unknown error";
        logToTerminal(`FAILED to apply configuration:\n${err}`, "error");
        showToast("Failed to apply configuration", "error");

        const logRes = await execShell(`${HOTSPOT_CTL} log | tail -n 25`);
        if (logRes.stdout) {
            logToTerminal("System logcat snippet:\n" + logRes.stdout, "warn");
        }
    }
});

// Block Device Action
window.blockMac = async function(mac) {
    if (!mac) return;
    showToast(`Blocking device ${mac}...`);
    logToTerminal(`Blocking MAC: ${mac}`, "cmd");
    const cmd = `${HOTSPOT_CTL} block "${mac}"`;
    const res = await execShell(cmd);
    if (res.errno === 0) {
        logToTerminal(`Device ${mac} blocked successfully.`, "success");
        showToast(`Device ${mac} blocked`);
        await refreshStatus(false);
    } else {
        logToTerminal(`Failed to block ${mac}:\n${res.stderr || res.stdout}`, "error");
        showToast("Block failed", "error");
    }
};

// Unblock Device Action
window.unblockMac = async function(mac) {
    if (!mac) return;
    showToast(`Unblocking device ${mac}...`);
    logToTerminal(`Unblocking MAC: ${mac}`, "cmd");
    const cmd = `${HOTSPOT_CTL} unblock "${mac}"`;
    const res = await execShell(cmd);
    if (res.errno === 0) {
        logToTerminal(`Device ${mac} unblocked successfully.`, "success");
        showToast(`Device ${mac} unblocked`);
        await refreshStatus(false);
    } else {
        logToTerminal(`Failed to unblock ${mac}:\n${res.stderr || res.stdout}`, "error");
        showToast("Unblock failed", "error");
    }
};

// Manual MAC Block Listener
btnManualBlock.addEventListener("click", () => {
    const val = inputManualMac.value.trim().toUpperCase();
    if (!val || !val.match(/^([0-9A-F]{2}[:-]){5}([0-9A-F]{2})$/i)) {
        showToast("Please enter a valid MAC (e.g. AA:BB:CC:DD:EE:FF)", "error");
        return;
    }
    window.blockMac(val);
    inputManualMac.value = "";
});

// Factory Revert Action
btnRevertOriginal.addEventListener("click", () => {
    confirmModal.style.display = "flex";
});

btnConfirmOk.addEventListener("click", async () => {
    confirmModal.style.display = "none";
    logToTerminal("Executing factory restoration: hotspot-ctl restore-original", "cmd");

    const cmd = `${HOTSPOT_CTL} restore-original`;
    const res = await execShell(cmd);

    if (res.errno === 0) {
        logToTerminal(`Factory Wi-Fi defaults restored successfully.\n${res.stdout || ""}`, "success");
        showToast("Wi-Fi settings reverted to factory defaults!");
        await refreshStatus(false);
    } else {
        logToTerminal(`Revert failed:\n${res.stderr || res.stdout}`, "error");
        showToast("Revert failed: Check Terminal logs", "error");
    }
});

btnCloseConfirm.addEventListener("click", () => {
    confirmModal.style.display = "none";
});
btnConfirmCancel.addEventListener("click", () => {
    confirmModal.style.display = "none";
});

// Export JSON Backup
btnExportBackup.addEventListener("click", () => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(currentConfig, null, 2));
    const downloadAnchor = document.createElement("a");
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `hotspot_config_${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
    showToast("Configuration snapshot exported");
});

// GEEKY SCREENER & FULLSCREEN TERMINAL CONTROLS
async function handleFetchLogcat() {
    showToast("Fetching live hostapd & Wi-Fi logcat...");
    const origFsLabel = fsFetchLabel ? fsFetchLabel.textContent : "Fetch";
    const origMiniLabel = btnFetchLogcatMini ? btnFetchLogcatMini.textContent : "Logcat";

    if (fsFetchLabel) fsFetchLabel.textContent = "Fetching...";
    if (btnFetchLogcatMini) btnFetchLogcatMini.textContent = "Fetching...";
    if (btnFsFetchLogcat) btnFsFetchLogcat.disabled = true;
    if (btnFetchLogcatMini) btnFetchLogcatMini.disabled = true;

    logToTerminal("Executing live system logcat fetch (SoftAp, Hostapd, Arp, ConfigStore)...", "live");

    try {
        const cmd = `${HOTSPOT_CTL} logcat`;
        const res = await execShell(cmd);
        let logContent = (res && res.stdout) ? res.stdout.trim() : "";

        // Fallback: If helper output was somehow empty, stream native Android logcat via su directly
        if (!logContent) {
            const rawRes = await execShell("logcat -d -t 60 -b main -b system 2>/dev/null || logcat -d -t 60");
            if (rawRes && rawRes.stdout && rawRes.stdout.trim()) {
                logContent = "=== ANDROID LOGCAT RAW FALLBACK ===\n" + rawRes.stdout.trim();
            }
        }

        if (!logContent) {
            logContent = (res && res.stderr) ? res.stderr.trim() : "(No recent events in circular log buffer)";
        }

        logToTerminal(logContent, "live");
        updateTerminalStatusPills("LIVE", "live");
        showToast("Logcat report updated successfully");
    } catch (err) {
        logToTerminal("Logcat fetch exception: " + (err.message || err), "error");
        showToast("Failed to fetch logcat", "error");
    } finally {
        if (fsFetchLabel) fsFetchLabel.textContent = origFsLabel;
        if (btnFetchLogcatMini) btnFetchLogcatMini.textContent = origMiniLabel;
        if (btnFsFetchLogcat) btnFsFetchLogcat.disabled = false;
        if (btnFetchLogcatMini) btnFetchLogcatMini.disabled = false;
        updateTerminalStatusPills("LIVE", "live");
    }
}

async function copyTerminalLogs(btnLabelElem) {
    if (!fullTerminalConsole) return;
    const text = fullTerminalConsole.textContent;
    const ok = await copyToClipboard(text);
    if (ok) {
        if (btnLabelElem) {
            const orig = btnLabelElem.textContent;
            btnLabelElem.textContent = "Copied!";
            setTimeout(() => { btnLabelElem.textContent = orig; }, 2000);
        }
        showToast("Console logs copied to clipboard!");
    } else {
        showToast("Select text to copy", "warn");
    }
}

function openFullscreenTerminal() {
    if (!fullscreenTerminalModal) return;
    fullscreenTerminalModal.classList.add("is-active");
    if (fullTerminalConsole) {
        fullTerminalConsole.scrollTop = fullTerminalConsole.scrollHeight;
        fullTerminalConsole.focus();
    }
}

function closeFullscreenTerminal() {
    if (!fullscreenTerminalModal) return;
    fullscreenTerminalModal.classList.remove("is-active");
}

// Screener Dock event handlers
if (screenerDisplay) {
    screenerDisplay.addEventListener("click", openFullscreenTerminal);
}

if (btnOpenFullscreen) {
    btnOpenFullscreen.addEventListener("click", openFullscreenTerminal);
}

if (btnFetchLogcatMini) {
    btnFetchLogcatMini.addEventListener("click", handleFetchLogcat);
}

if (btnCopyLogsMini) {
    btnCopyLogsMini.addEventListener("click", () => copyTerminalLogs(btnCopyLogsMini));
}

// Fullscreen Modal event handlers
if (btnCloseFullscreen) {
    btnCloseFullscreen.addEventListener("click", closeFullscreenTerminal);
}

if (btnFsFetchLogcat) {
    btnFsFetchLogcat.addEventListener("click", handleFetchLogcat);
}

if (btnFsCopyTerminal) {
    btnFsCopyTerminal.addEventListener("click", () => copyTerminalLogs(fsCopyTerminalLabel));
}

if (btnFsClearTerminal) {
    btnFsClearTerminal.addEventListener("click", () => {
        if (fullTerminalConsole) {
            fullTerminalConsole.textContent = "Console cleared.\n";
            totalLogLines = 1;
        }
        if (screenerText) {
            screenerText.textContent = "Console cleared. Ready.";
            screenerText.className = "screener-line-text";
        }
        updateTerminalStatusPills("READY", "idle");
    });
}

// Self-Test Diagnostics Modal
btnDiag.addEventListener("click", async () => {
    diagModal.style.display = "flex";
    diagContent.innerHTML = `
        <div class="empty-state-mini">
            <div class="pulse-beacon-mini"></div>
            <span>Testing SoftApConfiguration API...</span>
        </div>
    `;

    const cmd = `${HOTSPOT_CTL} check`;
    logToTerminal("Running API self-check: hotspotctl check", "cmd");
    const res = await execShell(cmd);

    try {
        const d = JSON.parse(res.stdout);
        logToTerminal(`Check complete: API ${d.api_level}, Root=${d.is_root}, WifiBinder=${d.wifi_service_ok}, SoftApApi=${d.softap_api_ok}`, "success");
        const hwStr = (d.device_brand ? d.device_brand + " " : "") + (d.device_model || d.device_device || "Android Device");
        diagContent.innerHTML = `
            <div class="diag-row">
                <span>Hardware</span>
                <strong>${escapeHtml(hwStr)}</strong>
            </div>
            <div class="diag-row">
                <span>API Level</span>
                <strong class="${d.api_level >= 30 ? "diag-ok" : "diag-fail"}">${d.api_level} (Android ${escapeHtml(d.android_version || "11+")})</strong>
            </div>
            <div class="diag-row">
                <span>Root</span>
                <strong class="${d.is_root ? "diag-ok" : "diag-fail"}">${d.is_root ? "Active (UID 0)" : "Denied"}</strong>
            </div>
            <div class="diag-row">
                <span>Wi-Fi Binder</span>
                <strong class="${d.wifi_service_ok ? "diag-ok" : "diag-fail"}">${d.wifi_service_ok ? "Available" : "Unreachable"}</strong>
            </div>
            <div class="diag-row">
                <span>SoftAP API</span>
                <strong class="${d.softap_api_ok ? "diag-ok" : "diag-fail"}">${d.softap_api_ok ? "Permitted" : "Restricted"}</strong>
            </div>
        `;
    } catch (e) {
        diagContent.innerHTML = `
            <div class="diag-row">
                <span>Result</span>
                <span class="diag-fail">Error parsing output</span>
            </div>
        `;
    }
});

btnCloseDiag.addEventListener("click", () => {
    diagModal.style.display = "none";
});
btnDismissDiag.addEventListener("click", () => {
    diagModal.style.display = "none";
});
btnRunCheckAgain.addEventListener("click", () => btnDiag.click());

// About & Support Modal Controls
function openAboutSupportModal() {
    if (!aboutSupportModal) return;
    aboutSupportModal.style.display = "flex";
}

function closeAboutSupportModal() {
    if (!aboutSupportModal) return;
    aboutSupportModal.style.display = "none";
}

if (btnAboutSupport) {
    btnAboutSupport.addEventListener("click", openAboutSupportModal);
}
if (btnCloseAboutModal) {
    btnCloseAboutModal.addEventListener("click", closeAboutSupportModal);
}
if (btnCloseAboutBtn) {
    btnCloseAboutBtn.addEventListener("click", closeAboutSupportModal);
}

if (selectCryptoCoin) {
    selectCryptoCoin.addEventListener("change", () => {
        const coin = selectCryptoCoin.value;
        const info = CRYPTO_DATA[coin] || CRYPTO_DATA.btc;
        if (cryptoBadge) cryptoBadge.textContent = info.network;
        if (cryptoAddressDisplay) cryptoAddressDisplay.textContent = info.address;
    });
}

if (btnCopyCryptoAddress) {
    btnCopyCryptoAddress.addEventListener("click", async () => {
        const coin = selectCryptoCoin ? selectCryptoCoin.value : "btc";
        const info = CRYPTO_DATA[coin] || CRYPTO_DATA.btc;
        const ok = await copyToClipboard(info.address);
        if (ok) {
            if (copyCryptoLabel) {
                const orig = copyCryptoLabel.textContent;
                copyCryptoLabel.textContent = "Copied!";
                setTimeout(() => { copyCryptoLabel.textContent = orig; }, 2000);
            }
            showToast(`Copied ${coin.toUpperCase()} address!`);
        } else {
            showToast("Failed to copy address", "error");
        }
    });
}

if (btnVisitRepo) {
    btnVisitRepo.addEventListener("click", async () => {
        const repoUrl = "https://github.com/uniqueselect/HMR";
        logToTerminal(`Opening GitHub repository in external browser: ${repoUrl}`, "cmd");
        showToast("Opening GitHub in default browser...");

        // Fire native Android Activity Intent VIEW so device default browser or app chooser opens
        const cmd = `am start -a android.intent.action.VIEW -d "${repoUrl}" 2>/dev/null || cmd activity start -a android.intent.action.VIEW -d "${repoUrl}"`;
        const res = await execShell(cmd);

        if (res.errno !== 0) {
            // Browser preview fallback
            try {
                window.open(repoUrl, "_blank");
            } catch (e) {
                window.location.href = repoUrl;
            }
        }
    });
}

btnRefresh.addEventListener("click", () => {
    logToTerminal("Manual refresh requested", "cmd");
    refreshStatus(true);
});

// Toast Utility - Clean, single toast that stays visible for ~6.5 seconds so user can read at a glance
function showToast(message, type = "info", durationMs = 6500) {
    const container = document.getElementById("toastContainer");
    if (!container) return;
    container.innerHTML = ""; // Clear any active toast immediately so multiple toasts never stack
    const toast = document.createElement("div");
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = "0";
        setTimeout(() => toast.remove(), 250);
    }, durationMs);
}

function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

// Initial Boot
window.addEventListener("DOMContentLoaded", () => {
    updateTerminalStatusPills("READY", "idle");
    logToTerminal("Root Hotspot Manager v1.2.4 initialized. Ready.", "info");
    refreshStatus();
});
