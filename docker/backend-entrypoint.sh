#!/bin/sh
set -eu

# Docker secrets: <VAR>_FILE points to a file whose trimmed content becomes <VAR>.
# The direct variable is kept as a fallback for local runs outside Docker.
file_env() {
    var="$1"
    file_var="${var}_FILE"
    eval "secret_file=\${$file_var:-}"

    if [ -z "${secret_file:-}" ]; then
        return 0
    fi
    if [ ! -r "$secret_file" ]; then
        echo "Secret file for $var is not readable: $secret_file" >&2
        exit 1
    fi

    secret_value="$(tr -d '\r\n' < "$secret_file")"
    if [ -z "$secret_value" ]; then
        echo "Secret file for $var is empty: $secret_file" >&2
        exit 1
    fi

    export "$var=$secret_value"
    unset "$file_var"
}

file_env APP_STORAGE_SEAWEEDFS_ACCESS_KEY
file_env APP_STORAGE_SEAWEEDFS_SECRET_KEY

exec java -jar /app/mis-monedas.jar --spring.config.location=/app/application.yml
