#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Configuration
POSTGRES_CONTAINER="oba-postgres"
KEYCLOAK_CONTAINER="oba-keycloak"
AZURITE_CONTAINER="oba-azurite"
DB_NAME="oba"
DB_USER="oba"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOGS_DIR="$SCRIPT_DIR/logs"

# Shared helpers (oba_app_dev_port). Sourced rather than duplicated so the
# port lookup cannot drift between start/stop/status.
# shellcheck source=_common.sh
. "$SCRIPT_DIR/_common.sh"

# No `set -e` here on purpose: this script is a read-only reporter and every
# probe below is expected to fail when a service is down. Aborting on the first
# failed check would stop it printing the rest of the status.

# Detect container runtime
detect_runtime() {
    if command -v docker &> /dev/null && docker ps &> /dev/null; then
        CONTAINER_CMD="docker"
    elif command -v podman &> /dev/null; then
        CONTAINER_CMD="podman"
    else
        CONTAINER_CMD=""
    fi
}

# Check if container is running
is_container_running() {
    local container_name=$1
    if [ -n "$CONTAINER_CMD" ]; then
        $CONTAINER_CMD ps --format '{{.Names}}' 2>/dev/null | grep -q "^${container_name}$"
        return $?
    fi
    return 1
}

# Get container port
get_container_port() {
    local container_name=$1
    if [ -n "$CONTAINER_CMD" ]; then
        $CONTAINER_CMD port "$container_name" 2>/dev/null | head -1 | cut -d: -f2
    fi
}

# Check if service is responding
is_service_up() {
    local url=$1
    curl -s --connect-timeout 3 "$url" &> /dev/null
    return $?
}

# Print status icon
status_icon() {
    if [ "$1" = "running" ]; then
        echo -e "${GREEN}●${NC}"
    elif [ "$1" = "stopped" ]; then
        echo -e "${RED}●${NC}"
    else
        echo -e "${YELLOW}●${NC}"
    fi
}

# Print header
print_header() {
    echo ""
    echo "=============================================="
    echo -e "${BOLD}  Oba Platform - Status${NC}"
    echo "=============================================="
    echo ""
}

# Check infrastructure containers
check_infrastructure() {
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}Infrastructure Services${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    if [ -z "$CONTAINER_CMD" ]; then
        echo -e "  ${RED}No container runtime (docker/podman) available${NC}"
        return
    fi

    printf "  %-20s │ %-10s │ %-8s │ %s\n" "Service" "Status" "Port" "Health"
    echo "  ─────────────────────┼────────────┼──────────┼──────────────"

    # PostgreSQL
    if is_container_running "$POSTGRES_CONTAINER"; then
        local pg_port=$(get_container_port "$POSTGRES_CONTAINER")
        local pg_health="checking..."
        if $CONTAINER_CMD exec "$POSTGRES_CONTAINER" pg_isready -U $DB_USER &> /dev/null; then
            pg_health="${GREEN}healthy${NC}"
        else
            pg_health="${YELLOW}starting${NC}"
        fi
        printf "  %-20s │ $(status_icon running) %-8s │ %-8s │ %b\n" "PostgreSQL" "running" "${pg_port:-5438}" "$pg_health"
    else
        printf "  %-20s │ $(status_icon stopped) %-8s │ %-8s │ %s\n" "PostgreSQL" "stopped" "-" "-"
    fi

    # Keycloak
    if is_container_running "$KEYCLOAK_CONTAINER"; then
        local kc_port=$(get_container_port "$KEYCLOAK_CONTAINER")
        local kc_health="checking..."
        if curl -s --connect-timeout 3 "http://localhost:8086/health/ready" &> /dev/null; then
            kc_health="${GREEN}healthy${NC}"
        else
            kc_health="${YELLOW}starting${NC}"
        fi
        printf "  %-20s │ $(status_icon running) %-8s │ %-8s │ %b\n" "Keycloak" "running" "${kc_port:-8086}" "$kc_health"
    else
        printf "  %-20s │ $(status_icon stopped) %-8s │ %-8s │ %s\n" "Keycloak" "stopped" "-" "-"
    fi

    if is_container_running "$AZURITE_CONTAINER"; then
        local azurite_port=$(get_container_port "$AZURITE_CONTAINER")
        printf "  %-20s │ $(status_icon running) %-8s │ %-8s │ %s\n" "Azurite" "running" "${azurite_port:-10000}" "-"
    else
        printf "  %-20s │ $(status_icon stopped) %-8s │ %-8s │ %s\n" "Azurite" "stopped" "-" "-"
    fi
}

# Check application services
check_applications() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}Application Services${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    printf "  %-20s │ %-10s │ %-8s │ %s\n" "Service" "Status" "Port" "URL"
    echo "  ─────────────────────┼────────────┼──────────┼──────────────────────────"

    # Backend
    if is_service_up "http://localhost:8080/actuator/health"; then
        printf "  %-20s │ $(status_icon running) %-8s │ %-8s │ %s\n" "Backend (Spring)" "running" "8080" "http://localhost:8080"
    else
        printf "  %-20s │ $(status_icon stopped) %-8s │ %-8s │ %s\n" "Backend (Spring)" "stopped" "8080" "-"
    fi

    # Frontend apps.
    #
    # This used to report a single "Frontend (Tsunagu)" on port 3008. No such app
    # exists in frontend/apps and nothing binds 3008, so the row was permanently
    # "stopped" regardless of what was actually running. Enumerate the real apps
    # and read each one's port via the shared helper, so this cannot drift again
    # as apps are added or removed -- nor drift from what stop.sh sweeps.
    local app_dir app port
    for app_dir in "$FRONTEND_DIR"/apps/*/; do
        [ -d "$app_dir" ] || continue
        app=$(basename "$app_dir")
        if ! port=$(oba_app_dev_port "$app_dir"); then
            # oba_app_dev_port has already warned with the reason.
            printf "  %-20s │ %-10s │ %-8s │ %s\n" "$app" "unknown" "-" "port unreadable (see warning)"
            continue
        fi
        if is_service_up "http://localhost:$port"; then
            printf "  %-20s │ $(status_icon running) %-8s │ %-8s │ %s\n" "$app" "running" "$port" "http://localhost:$port"
        else
            printf "  %-20s │ $(status_icon stopped) %-8s │ %-8s │ %s\n" "$app" "stopped" "$port" "-"
        fi
    done
}

# Show PID files
check_pid_files() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}Process IDs${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    printf "  %-20s │ %-10s │ %s\n" "Service" "PID" "Status"
    echo "  ─────────────────────┼────────────┼──────────"

    # Backend PID
    if [ -f "$LOGS_DIR/.backend.pid" ]; then
        local backend_pid=$(cat "$LOGS_DIR/.backend.pid")
        if ps -p "$backend_pid" &> /dev/null; then
            printf "  %-20s │ %-10s │ $(status_icon running) %s\n" "Backend" "$backend_pid" "running"
        else
            printf "  %-20s │ %-10s │ $(status_icon stopped) %s\n" "Backend" "$backend_pid" "stale PID"
        fi
    else
        printf "  %-20s │ %-10s │ %s\n" "Backend" "-" "no PID file"
    fi

    # Frontend app PIDs. start.sh writes one .frontend-<app>.pid per app, so
    # enumerate them rather than assuming a single fixed file name.
    local pid_file app_name app_pid
    local found=false
    for pid_file in "$LOGS_DIR"/.frontend-*.pid; do
        [ -f "$pid_file" ] || continue
        found=true
        app_name=$(basename "$pid_file" | sed 's/^\.frontend-//;s/\.pid$//')
        app_pid=$(cat "$pid_file")
        if ps -p "$app_pid" &> /dev/null; then
            printf "  %-20s │ %-10s │ $(status_icon running) %s\n" "$app_name" "$app_pid" "running"
        else
            printf "  %-20s │ %-10s │ $(status_icon stopped) %s\n" "$app_name" "$app_pid" "stale PID"
        fi
    done
    if [ "$found" = false ]; then
        printf "  %-20s │ %-10s │ %s\n" "frontend apps" "-" "no PID files"
    fi
}

# Show useful URLs
show_urls() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}Quick Links${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "  Kazan:        http://localhost:3002"
    echo "  Backend API:  http://localhost:8080"
    echo "  Swagger UI:   http://localhost:8080/swagger-ui.html"
    echo "  Keycloak:     http://localhost:8086"
    echo ""
    echo "  Keycloak Admin: senseyadmin / securepassword123"
}

# Show available commands
show_commands() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}Available Commands${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "  ./scripts/start.sh               Start all services"
    echo "  ./scripts/stop.sh                Stop all services"
    echo "  ./scripts/restart-backend.sh     Restart backend only"
    echo "  ./scripts/reset-db.sh            Reset database"
    echo "  ./scripts/logs-backend.sh        Tail backend logs"
    echo "  ./scripts/logs-frontend.sh       Tail frontend logs"
    echo "  ./scripts/status.sh              Show this status (current)"
}

# Print footer
print_footer() {
    echo ""
    echo "=============================================="
    echo ""
}

# Main execution
main() {
    print_header
    detect_runtime
    check_infrastructure
    check_applications
    check_pid_files
    show_urls
    show_commands
    print_footer
}

# Run main
main "$@"
