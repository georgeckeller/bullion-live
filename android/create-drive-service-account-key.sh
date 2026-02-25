#!/bin/bash
# Create and download service account key via gcloud CLI

set -e

if [ -z "$SERVICE_ACCOUNT_EMAIL" ]; then
    echo "Please set SERVICE_ACCOUNT_EMAIL environment variable"
    exit 1
fi

if [ -z "$PROJECT_ID" ]; then
    echo "Please set PROJECT_ID environment variable"
    exit 1
fi

OUTPUT_FILE="../service-accounts/drive-service-account.json"

echo "============================================"
echo "  Create Service Account Key"
echo "============================================"
echo ""
echo "Service Account: $SERVICE_ACCOUNT_EMAIL"
echo "Project: $PROJECT_ID"
echo "Output: $OUTPUT_FILE"
echo ""

# Check if gcloud is authenticated
echo "Checking authentication..."
CURRENT_ACCOUNT=$(gcloud config get-value account 2>/dev/null || echo "")

if [ -z "$CURRENT_ACCOUNT" ]; then
    echo "❌ Not authenticated with gcloud"
    echo ""
    echo "Please authenticate first:"
    echo "  gcloud auth login"
    exit 1
fi

echo "✅ Authenticated as: $CURRENT_ACCOUNT"
echo ""

# Check if service account exists
echo "Checking if service account exists..."
if ! gcloud iam service-accounts describe "$SERVICE_ACCOUNT_EMAIL" --project="$PROJECT_ID" &>/dev/null; then
    echo "❌ Service account not found: $SERVICE_ACCOUNT_EMAIL"
    echo ""
    echo "Creating service account..."
    gcloud iam service-accounts create drive-service-account \
        --display-name="Drive Service Account for Bullion Live" \
        --description="Service account for uploading APKs to Google Drive" \
        --project="$PROJECT_ID" || {
        echo "❌ Failed to create service account"
        echo "Please check if you have the necessary permissions"
        exit 1
    }
    echo "✅ Service account created"
else
    echo "✅ Service account exists"
fi

echo ""

# Create directory if it doesn't exist
mkdir -p "$(dirname "$OUTPUT_FILE")"

# Check if key file already exists
if [ -f "$OUTPUT_FILE" ]; then
    echo "⚠️  Key file already exists: $OUTPUT_FILE"
    read -p "Overwrite? (y/n) [n]: " OVERWRITE
    if [ "$OVERWRITE" != "y" ] && [ "$OVERWRITE" != "Y" ]; then
        echo "Skipping key creation"
        echo ""
        echo "Using existing key file: $OUTPUT_FILE"
        exit 0
    fi
    echo ""
fi

# Create and download the key
echo "Creating new service account key..."
echo "This will create a new key and save it to: $OUTPUT_FILE"
echo ""

gcloud iam service-accounts keys create "$OUTPUT_FILE" \
    --iam-account="$SERVICE_ACCOUNT_EMAIL" \
    --project="$PROJECT_ID" || {
    echo ""
    echo "❌ Failed to create key"
    echo ""
    echo "Possible issues:"
    echo "  1. Insufficient permissions (need roles/iam.serviceAccountKeyAdmin)"
    echo "  2. Service account doesn't exist"
    echo "  3. Too many keys already exist (max 10 per service account)"
    echo ""
    echo "To check existing keys:"
    echo "  gcloud iam service-accounts keys list --iam-account=$SERVICE_ACCOUNT_EMAIL --project=$PROJECT_ID"
    exit 1
}

echo ""
echo "✅ Service account key created successfully!"
echo ""
echo "Key file saved to: $OUTPUT_FILE"
echo ""
echo "Next steps:"
echo "  1. Make sure Drive API is enabled for the project"
echo "  2. Grant the service account necessary permissions"
echo "  3. Test upload: cd android && python3 upload-drive-sa.py ../bullion-live-v1.3.53.apk"
echo ""
