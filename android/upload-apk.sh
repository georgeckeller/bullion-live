#!/bin/bash
# upload-apk.sh - Build debug APK and upload to Google Drive
#
# USAGE:
#   ./upload-apk.sh              # builds fresh debug APK, then uploads
#   ./upload-apk.sh <apk_path>   # uploads an existing APK file
#
# REQUIRES:
#   - DRIVE_FOLDER_ID env var (loaded from ~/.bullion-drive-config.sh)
#   - gcloud CLI authenticated: gcloud auth login
#   - python3 + requests:       pip3 install requests
#
# CONFIG (stored locally, never committed):
#   ~/.bullion-drive-config.sh should contain:
#     export DRIVE_FOLDER_ID="1jJ8LSufTe8s626gkm737HAEyBf438IIR"

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Load config from ~/.bullion-drive-config.sh if not already set
if [ -f "$HOME/.bullion-drive-config.sh" ]; then
    source "$HOME/.bullion-drive-config.sh"
fi

# Validate required env
if [ -z "$DRIVE_FOLDER_ID" ]; then
    echo "ERROR: DRIVE_FOLDER_ID not set."
    echo "Run: ./setup-drive-folder.sh"
    exit 1
fi

# Get gcloud access token (requires: gcloud auth login)
GCLOUD_TOKEN=$(gcloud auth print-access-token 2>/dev/null)
if [ -z "$GCLOUD_TOKEN" ]; then
    echo "ERROR: gcloud not authenticated. Run:"
    echo "  gcloud auth login"
    exit 1
fi

# Step 1: Determine APK path
if [ -n "$1" ]; then
    APK_PATH="$1"
    if [ ! -f "$APK_PATH" ]; then
        echo "ERROR: APK not found at $APK_PATH"
        exit 1
    fi
    echo "Using provided APK: $APK_PATH"
else
    echo "============================================"
    echo "  Building debug APK..."
    echo "============================================"
    cd "$SCRIPT_DIR"
    ./gradlew assembleDebug
    APK_PATH=$(find "$SCRIPT_DIR/app/build/outputs/apk/debug" -name "*.apk" | head -1)
    if [ -z "$APK_PATH" ]; then
        echo "ERROR: Could not find built APK"
        exit 1
    fi
    echo "Built: $APK_PATH"
fi

APK_FILENAME=$(basename "$APK_PATH")
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)

echo ""
echo "============================================"
echo "  Uploading to Google Drive"
echo "============================================"
echo "  File: $APK_FILENAME ($APK_SIZE)"
echo "  Folder: $DRIVE_FOLDER_ID"
echo ""

# Step 2: Upload via Python using gcloud auth token
python3 - <<PYTHON
import os, json, sys

try:
    import requests
except ImportError:
    print("ERROR: requests not installed. Run: pip3 install requests")
    sys.exit(1)

FOLDER_ID = "$DRIVE_FOLDER_ID"
APK_PATH = "$APK_PATH"
APK_FILENAME = "$APK_FILENAME"
TOKEN = "$GCLOUD_TOKEN"

headers = {"Authorization": f"Bearer {TOKEN}"}
mime = "application/vnd.android.package-archive"

# Check if file already exists in folder
r = requests.get(
    "https://www.googleapis.com/drive/v3/files",
    headers=headers,
    params={"q": f"name='{APK_FILENAME}' and '{FOLDER_ID}' in parents and trashed=false", "fields": "files(id,name)"}
)
files = r.json().get("files", [])

with open(APK_PATH, "rb") as f:
    apk_data = f.read()

if files:
    file_id = files[0]["id"]
    r = requests.patch(
        f"https://www.googleapis.com/upload/drive/v3/files/{file_id}",
        headers={**headers, "Content-Type": mime},
        params={"uploadType": "media"},
        data=apk_data
    )
    print(f"✅ Updated existing file: {APK_FILENAME} (HTTP {r.status_code})")
else:
    meta = json.dumps({"name": APK_FILENAME, "parents": [FOLDER_ID]})
    boundary = "bullionlive_apk_boundary"
    body = (
        f"--{boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{meta}\r\n"
        f"--{boundary}\r\nContent-Type: {mime}\r\n\r\n"
    ).encode() + apk_data + f"\r\n--{boundary}--".encode()
    r = requests.post(
        "https://www.googleapis.com/upload/drive/v3/files",
        headers={**headers, "Content-Type": f"multipart/related; boundary={boundary}"},
        params={"uploadType": "multipart"},
        data=body
    )
    result = r.json()
    print(f"✅ Uploaded new file: {APK_FILENAME} (ID: {result.get('id')})") 

print(f"   https://drive.google.com/drive/folders/{FOLDER_ID}")
PYTHON

echo ""
echo "============================================"
echo "  Done!"
echo "============================================"
