#!/bin/bash

# Get the directory where the script is located
script_dir=$(dirname "$(readlink -f "$0")")

# Specify the file containing the version number
version_file="$script_dir/../RemootioAndroidAuto/automotive/build.gradle"

# Check if the file exists
if [ -e "$version_file" ]; then
    # Extract the current version number from the file
    current_version=$(grep -Eo 'versionCode [0-9]+' "$version_file" | awk '{print $2}')

    # Check if the version is a valid integer
    if [[ $current_version =~ ^[0-9]+$ ]]; then
        # Increment the version by 1
        new_version=$((current_version + 1))

        # Update the version in the file using sed
        sed -i "s/versionCode $current_version/versionCode $new_version/" "$version_file"

        echo "Version updated to $new_version in $version_file"
        exit 0
    else
        echo "Error: The current version is not a valid integer."
        exit 1
    fi
else
    echo "Error: $version_file not found."
    exit 2
fi
