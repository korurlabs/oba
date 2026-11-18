#!/bin/bash
#
# Shared helpers for the Oba dev scripts.
#
# Sourced by start.sh, stop.sh and status.sh -- not executable on its own.
# Keep this file self-contained: status.sh does not define the log_* helpers, so
# nothing here may assume they exist.

# Emit a warning in the same [WARN] style the scripts use, always on stderr so it
# never contaminates a value being captured by command substitution.
_common_warn() {
    if declare -f log_warn > /dev/null 2>&1; then
        log_warn "$1" >&2
    else
        printf '\033[1;33m[WARN]\033[0m %s\n' "$1" >&2
    fi
}

# Print the dev-server port an app binds, read from its own package.json.
#
# Single implementation on purpose: start.sh, stop.sh and status.sh all need this
# and previously carried their own copy of the regex, which is exactly how the
# port lists drifted out of sync in the first place.
#
# Matches the EXACT "dev" script. The previous pattern ('.*"dev".*-p ...') also
# matched any script whose NAME merely contained "dev" -- "dev:doppler",
# "predev" -- and would happily read a port out of the wrong command.
#
# Returns 0 and prints the port on success. On failure it returns 1 and warns
# naming the app: a wrong port silently assumed is precisely the failure class
# rule 10 exists to prevent, so callers must decide what to do rather than
# inheriting a hardcoded guess.
oba_app_dev_port() {
    local app_dir="$1"
    local app
    app=$(basename "$app_dir")
    local pkg="$app_dir/package.json"
    local dev_script=""
    local port=""

    if [ ! -f "$pkg" ]; then
        _common_warn "App '$app': no package.json at $pkg — cannot determine its dev port."
        return 1
    fi

    if command -v jq &> /dev/null; then
        dev_script=$(jq -r '.scripts.dev // empty' "$pkg" 2>/dev/null)
    else
        # Key on the exact "dev" key, never a substring of a longer key name.
        # The quote before 'dev' is what excludes "predev"; the quote after it is
        # what excludes "dev:doppler". Two expressions so this works on both a
        # pretty-printed package.json (key at line start) and a single-line one
        # (key preceded by '{' or ','); head -1 keeps the first hit.
        dev_script=$(sed -n \
            -e 's/^[[:space:]]*"dev"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
            -e 's/.*[{,][[:space:]]*"dev"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
            "$pkg" 2>/dev/null | head -1)
    fi

    if [ -z "$dev_script" ]; then
        _common_warn "App '$app': no \"dev\" script in $pkg — cannot determine its dev port."
        return 1
    fi

    # Accept both the short and long port flags.
    port=$(printf '%s\n' "$dev_script" | sed -n 's/.*[[:space:]]-p[[:space:]]\{1,\}\([0-9]\{1,\}\).*/\1/p' | head -1)
    if [ -z "$port" ]; then
        port=$(printf '%s\n' "$dev_script" | sed -n 's/.*--port[[:space:]]\{1,\}\([0-9]\{1,\}\).*/\1/p' | head -1)
    fi

    if [ -z "$port" ]; then
        _common_warn "App '$app': \"dev\" script pins no numeric port, so it cannot be read statically."
        _common_warn "  dev script: $dev_script"
        _common_warn "  (a port supplied by an env var or left to the framework default is not detectable here)"
        return 1
    fi

    printf '%s\n' "$port"
    return 0
}

# Print every dev port used by the apps under <frontend_dir>/apps.
#
# Enumerated from the filesystem rather than a hardcoded list so a new app on a
# new port is picked up automatically. Apps whose port cannot be read are warned
# about by oba_app_dev_port and skipped.
oba_frontend_ports() {
    local frontend_dir="$1"
    local app_dir
    local port

    for app_dir in "$frontend_dir"/apps/*/; do
        [ -d "$app_dir" ] || continue
        if port=$(oba_app_dev_port "$app_dir"); then
            printf '%s\n' "$port"
        fi
    done
}
