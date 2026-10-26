#!/bin/sh
set -eu

read_secret() {
    if [ ! -r "$1" ]; then
        echo "Secret file is not readable: $1" >&2
        exit 1
    fi
    secret_value="$(tr -d '\r\n' < "$1")"
    if [ -z "$secret_value" ]; then
        echo "Secret file is empty: $1" >&2
        exit 1
    fi
    printf '%s' "$secret_value"
}

export AWS_ACCESS_KEY_ID="$(read_secret /run/secrets/seaweedfs_access_key)"
export AWS_SECRET_ACCESS_KEY="$(read_secret /run/secrets/seaweedfs_secret_key)"

# Keep the upstream entrypoint: it fixes /data ownership and drops privileges.
exec /entrypoint.sh "$@"
