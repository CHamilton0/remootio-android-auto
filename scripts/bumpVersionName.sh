#!/bin/bash

# Get the directory where the script is located
script_dir=$(dirname "$(readlink -f "$0")")

# Specify the file containing the version number
version_file="$script_dir/../RemootioAndroidAuto/automotive/build.gradle"

# Check if the version file exists
if [ -e "$version_file" ]; then
    # Read the current version from the file
    current_version=$(grep -Eo 'versionName "([0-9]+\.[0-9]+\.[0-9]+)"' "$version_file" | sed -E 's/versionName "([0-9]+\.[0-9]+\.[0-9]+)"/\1/')

    # Get the current branch name
    current_branch=$(git rev-parse --abbrev-ref HEAD)
    git fetch origin

    # Check the Git history for keywords indicating changes
    if git log --grep='BREAKING\|fix\|feat' "origin/master..$current_branch" | grep -q 'BREAKING\|feat\|fix'; then
        # At least one of the keywords is found in the Git history
        # Increment the minor version
        IFS='.' read -ra version_parts <<< "$current_version"
        ((minor_version=version_parts[1]+1))
        new_version="${version_parts[0]}.$minor_version.${version_parts[2]}"
        # Update the version in the file
        sed -i -E 's|versionName "[0-9]+\.[0-9]+\.[0-9]+"|versionName "'"$new_version"'"|' "$version_file"
        echo "Updated version to $new_version"
        exit 0
    else
        # No changes found, no version increment
        echo "No changes found, version remains $current_version"
        exit 1
    fi
else
    echo "Error: $version_file not found."
    exit 2
fi
