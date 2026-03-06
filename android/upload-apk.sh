#!/bin/bash
# upload-apk.sh - Build debug APK and upload to Google Drive
#
# USAGE: ./upload-apk.sh [apk_path]
#
# If apk_path is omitted, builds a fresh debug APK first.
#
# REQUIRES:
#   - DRIVE_FOLDER_ID  env var (or in ~/.bullion-drive-config.sh)
#   - python3 + google-auth + google-api-python-client packages
#   - Service account key at ../service-accounts/drive-service-account.json
#     (run create-drive-service-account-key.sh once to create it)
#
# SETUP (one-time):
#   pip3 install --quiet google-auth google-auth-httplib2 google-api-python-client
#   ./create-drive-service-account-key.sh   # creates the service account key
#   ./setup-drive-folder.sh                 # notes the DRIVE_FOLDER_ID

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

SERVICE_ACCOUNT_KEY="$PROJECT_ROOT/service-accounts/drive-service-account.json"
if [ ! -f "$SERVICE_ACCOUNT_KEY" ]; then
    echo "ERROR: Service account key not found at $SERVICE_ACCOUNT_KEY"
    echo "Run: ./create-drive-service-account-key.sh"
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

# Step 2: Upload via Python (google-api-python-client)
python3 - <<PYTHON
import sys
import os

try:
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
except ImportError:
    print("ERROR: Missing dependencies. Run:")
    print("  pip3 install google-auth google-auth-httplib2 google-api-python-client")
    sys.exit(1)

SERVICE_ACCOUNT_FILE = "$SERVICE_ACCOUNT_KEY"
FOLDER_ID = "$DRIVE_FOLDER_ID"
APK_PATH = "$APK_PATH"
APK_FILENAME = "$APK_FILENAME"

SCOPES = ["https://www.googleapis.com/auth/drive.file"]

creds = service_account.Credentials.from_service_account_file(
    SERVICE_ACCOUNT_FILE, scopes=SCOPES
)

service = build("drive", "v3", credentials=creds, cache_discovery=False)

# Check if file already exists in folder (to update instead of duplicate)
results = service.files().list(
    q=f"name='{APK_FILENAME}' and '{FOLDER_ID}' in parents and trashed=false",
    fields="files(id, name)"
).execute()
existing = results.get("files", [])

media = MediaFileUpload(APK_PATH, mimetype="application/vnd.android.package-archive", resumable=True)

if existing:
    file_id = existing[0]["id"]
    file = service.files().update(
        fileId=file_id,
        media_body=media
    ).execute()
    print(f"✅ Updated existing file: {APK_FILENAME}")
    print(f"   Drive ID: {file.get('id')}")
else:
    file_metadata = {
        "name": APK_FILENAME,
        "parents": [FOLDER_ID]
    }
    file = service.files().create(
        body=file_metadata,
        media_body=media,
        fields="id, name"
    ).execute()
    print(f"✅ Uploaded new file: {APK_FILENAME}")
    print(f"   Drive ID: {file.get('id')}")

print(f"   Drive folder: https://drive.google.com/drive/folders/{FOLDER_ID}")
PYTHON

echo ""
echo "============================================"
echo "  Done!"
echo "============================================"
