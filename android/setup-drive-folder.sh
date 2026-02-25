#!/bin/bash
# Setup script to find or create "APK uploads" folder in Google Drive

set -e

FOLDER_NAME="APK uploads"

if [ -z "$SERVICE_ACCOUNT_EMAIL" ]; then
    read -p "Enter local Service Account Email: " SERVICE_ACCOUNT_EMAIL
    if [ -z "$SERVICE_ACCOUNT_EMAIL" ]; then
        echo "Service Account Email is required."
        exit 1
    fi
fi

echo "============================================"
echo "  Setup Drive Folder: $FOLDER_NAME"
echo "============================================"
echo ""

echo "To set up the Drive folder:"
echo ""
echo "1. Go to Google Drive: https://drive.google.com"
echo ""
echo "2. Create a folder named: '$FOLDER_NAME'"
echo "   (or use an existing folder with this name)"
echo ""
echo "3. Right-click the folder → Share"
echo ""
echo "4. Add this email: $SERVICE_ACCOUNT_EMAIL"
echo "   Give 'Editor' permission"
echo "   Uncheck 'Notify people'"
echo "   Click Share"
echo ""
echo "5. Get the folder ID from the URL:"
echo "   URL format: https://drive.google.com/drive/folders/FOLDER_ID"
echo "   Copy the FOLDER_ID part"
echo ""
echo "6. Set it as:"
echo "   export DRIVE_FOLDER_ID=YOUR_FOLDER_ID_HERE"
echo ""
echo "7. Or add it to ~/.bashrc or ~/.zshrc to make it permanent:"
echo "   echo 'export DRIVE_FOLDER_ID=YOUR_FOLDER_ID_HERE' >> ~/.bashrc"
echo ""

read -p "Do you have the folder ID? Enter it now (or press Enter to skip): " FOLDER_ID

if [ -n "$FOLDER_ID" ]; then
    echo ""
    echo "Setting DRIVE_FOLDER_ID..."
    export DRIVE_FOLDER_ID="$FOLDER_ID"
    
    # Optionally save to config file
    echo ""
    read -p "Save to config file for future use? (y/n) [y]: " SAVE_CONFIG
    
    if [ -z "$SAVE_CONFIG" ] || [ "$SAVE_CONFIG" = "y" ] || [ "$SAVE_CONFIG" = "Y" ]; then
        CONFIG_FILE="$HOME/.bullion-drive-config.sh"
        echo "export DRIVE_FOLDER_ID=$FOLDER_ID" > "$CONFIG_FILE"
        echo "✅ Saved to $CONFIG_FILE"
        echo ""
        echo "To use in future, run:"
        echo "  source $CONFIG_FILE"
        echo ""
    fi
    
    echo "✅ DRIVE_FOLDER_ID is now set to: $FOLDER_ID"
    echo ""
    echo "You can now upload APK:"
    echo "  cd /home/georg/LinuxCode/bullion-live/android"
    echo "  ./upload-drive-sa-folder.sh ../bullion-live-v1.3.54.apk"
    echo ""
else
    echo ""
    echo "⚠️  No folder ID provided"
    echo ""
    echo "Please follow the steps above, then run this script again or set:"
    echo "  export DRIVE_FOLDER_ID=YOUR_FOLDER_ID"
    echo ""
fi
