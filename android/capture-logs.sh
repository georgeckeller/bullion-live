#!/bin/bash
# Script to capture logcat output for Bullion Live debugging

echo "============================================="
echo "  BULLION LIVE LOGCAT CAPTURE"
echo "============================================="
echo ""
echo "This will capture logs filtered for:"
echo "  - MainActivity (tab switching)"
echo "  - Widget (widget click handlers)"
echo "  - StockWidget (single stock widget)"
echo ""
echo "Press Ctrl+C to stop capturing..."
echo ""
echo "============================================="
echo ""

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "ERROR: adb not found in PATH"
    echo ""
    echo "Please install Android SDK Platform Tools:"
    echo "  - Linux: sudo apt-get install android-tools-adb"
    echo "  - macOS: brew install android-platform-tools"
    echo "  - Or download from: https://developer.android.com/studio/releases/platform-tools"
    echo ""
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No Android device connected"
    echo ""
    echo "Please:"
    echo "  1. Connect your device via USB"
    echo "  2. Enable USB debugging in Developer Options"
    echo "  3. Accept the USB debugging prompt on your device"
    echo ""
    echo "Current devices:"
    adb devices
    echo ""
    exit 1
fi

echo "Device connected: $(adb devices | grep 'device$' | head -1 | cut -f1)"
echo "Starting log capture..."
echo ""

# Clear old logs
adb logcat -c

# Capture filtered logs
adb logcat -v time | grep --line-buffered -E "(MainActivity|Widget|StockWidget|AndroidRuntime)" | tee bullion-live-logs-$(date +%Y%m%d-%H%M%S).txt
