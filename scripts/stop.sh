#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOGS_DIR="$SCRIPT_DIR/logs"

BACKEND_PORT=8080

# Defaults
CLEAN_VOLUMES=false

# Log functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Shared helpers (oba_app_dev_port, oba_frontend_ports). Sourced rather
# than duplicated so the port lookup cannot drift between start/stop/status.
# shellcheck source=_common.sh
. "$SCRIPT_DIR/_common.sh"

# Recursively kill a process and all its descendants, children first.
#
# The recorded PID is the 'kagi'/'nohup' wrapper; the real server is a grandchild
# (kagi -> bun -> next-server, kagi -> java). 'pkill -P' only reaches one level,
# so a shallow kill leaves the server orphaned and still holding its port.
# macOS/bash 3.2 compatible -- walk the tree manually via 'pgrep -P'.
kill_tree() {
    local pid=$1
    local child

    for child in $(pgrep -P "$pid" 2>/dev/null); do
        kill_tree "$child"
    done

    # rule-10-no-op -- reason: the process may exit on its own between the pgrep
    # above and this kill (the normal shutdown race). A failure here means it is
    # already gone, which is the outcome we wanted.
    kill "$pid" 2>/dev/null || true
}

# Wait for a PID to actually exit, then escalate to SIGKILL.
#
# 'kill' is asynchronous and the JVM releases its listening socket EARLY in
# shutdown, while its shutdown hooks still run. So a port-only check can report
# "stopped" a beat before the process is really gone. Waiting on the PID too
# makes this script's contract honest: when it returns, the process is gone.
wait_for_pid_exit() {
    local pid=$1
    local attempt=1

    while [ $attempt -le 20 ]; do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 0.5
        attempt=$((attempt + 1))
    done

    if kill -0 "$pid" 2>/dev/null; then
        log_warn "  PID $pid still alive after SIGTERM; sending SIGKILL"
        # rule-10-no-op -- reason: the process can still exit between the check
        # above and this signal; "already gone" is the outcome we want.
        kill -9 "$pid" 2>/dev/null || true
    fi
}

# Wait for a port to actually be released, then escalate to SIGKILL.
#
# A plain 'kill' is asynchronous: the JVM and next-server take a moment to unbind.
# Returning before the port is free makes an immediate re-run of start.sh fail its
# pre-flight check on a port that is about to be released.
wait_for_port_release() {
    local port=$1
    local attempt=1
    local pids

    while [ $attempt -le 20 ]; do
        # rule-10-no-op -- reason: lsof exits non-zero when nothing is listening,
        # which is the success condition here; handled by the emptiness test.
        pids=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null || true)
        [ -z "$pids" ] && return 0
        sleep 0.5
        attempt=$((attempt + 1))
    done

    # Still bound after 10s -- escalate.
    pids=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null || true)
    if [ -n "$pids" ]; then
        log_warn "  Port $port still held after SIGTERM; sending SIGKILL"
        echo "$pids" | xargs kill -9 2>/dev/null || true
        sleep 1
    fi
}

# Safety net: free any dev-server port still held by an orphaned process.
#
# Ports are enumerated from frontend/apps/* (see _common.sh), never hardcoded, so
# a new Oba app on a new port is still cleaned up.
kill_frontend_ports() {
    local port pids
    for port in $(oba_frontend_ports "$FRONTEND_DIR"); do
        # rule-10-no-op -- reason: as above, "nothing listening" is the expected
        # already-stopped case and is handled by the emptiness test below.
        pids=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null || true)
        if [ -n "$pids" ]; then
            for pid in $pids; do
                kill_tree "$pid"
            done
            wait_for_port_release "$port"
            log_info "  Freed port $port (orphaned process)"
        fi
    done
}

# Stop all frontend apps
stop_frontend() {
    log_info "Stopping frontend apps..."

    local found=false
    local pid_file app_name pid
    for pid_file in "$LOGS_DIR"/.frontend-*.pid; do
        [ -f "$pid_file" ] || continue
        found=true

        app_name=$(basename "$pid_file" | sed 's/^\.frontend-//;s/\.pid$//')
        pid=$(cat "$pid_file")

        if kill -0 "$pid" 2>/dev/null; then
            kill_tree "$pid"
            wait_for_pid_exit "$pid"
            log_success "  $app_name stopped (PID: $pid)"
        else
            log_warn "  $app_name process not found (PID: $pid)"
        fi
        rm -f "$pid_file"
    done

    if [ "$found" = false ]; then
        log_info "  No frontend PID files; falling back to a port sweep"
    fi

    # Safety net: free any dev-server port still held by an orphaned process.
    kill_frontend_ports
}

# Stop backend
stop_backend() {
    log_info "Stopping backend..."

    if [ -f "$LOGS_DIR/.backend.pid" ]; then
        local pid
        pid=$(cat "$LOGS_DIR/.backend.pid")
        if kill -0 "$pid" 2>/dev/null; then
            kill_tree "$pid"
            wait_for_pid_exit "$pid"
            log_success "Backend stopped (PID: $pid)"
        else
            log_warn "Backend process not found (PID: $pid)"
        fi
        rm -f "$LOGS_DIR/.backend.pid"
    else
        log_info "No backend PID file; falling back to a port sweep"
    fi

    # Fall back to whatever holds the backend port, NOT 'pgrep -f spring-boot:run':
    # other projects in this monorepo (Kagi) also run Spring Boot, and the
    # pattern form would kill their backends too.
    local pids
    # rule-10-no-op -- reason: lsof exits non-zero when nothing is listening on
    # the backend port, which is the expected already-stopped case.
    pids=$(lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN -t 2>/dev/null || true)
    if [ -n "$pids" ]; then
        local p
        for p in $pids; do
            kill_tree "$p"
        done
        log_info "  Freed port $BACKEND_PORT"
    fi
    wait_for_port_release "$BACKEND_PORT"
}

# Stop infrastructure
stop_infrastructure() {
    log_info "Stopping infrastructure services..."

    cd "$BACKEND_DIR"

    local compose_cmd
    if command -v docker &> /dev/null; then
        compose_cmd="docker compose"
    elif command -v podman &> /dev/null; then
        compose_cmd="podman-compose"
    else
        log_warn "Neither docker nor podman found"
        return 0
    fi

    local down_args="down --remove-orphans"
    if [ "$CLEAN_VOLUMES" = true ]; then
        log_warn "Removing containers and volumes (database data will be deleted)..."
        down_args="down -v --remove-orphans"
    fi

    # Report the real outcome: an unconditional success message would hide a
    # teardown that actually failed and left containers running.
    if $compose_cmd $down_args; then
        log_success "Infrastructure services stopped"
    else
        log_error "Failed to stop infrastructure (compose exited non-zero)."
        log_error "Inspect with: cd $BACKEND_DIR && $compose_cmd ps"
        return 1
    fi
}

# Clean up logs and PID files
cleanup_logs() {
    log_info "Cleaning up log files..."
    rm -f "$LOGS_DIR"/backend.log
    rm -f "$LOGS_DIR"/backend-build.log
    rm -f "$LOGS_DIR"/frontend-*.log
    rm -f "$LOGS_DIR"/auth.log
    rm -f "$LOGS_DIR"/frontend.log
    rm -f "$LOGS_DIR"/.backend.pid
    rm -f "$LOGS_DIR"/.frontend-*.pid
    rm -f "$LOGS_DIR"/.frontend.pid
    rm -f "$LOGS_DIR"/.auth.pid
    log_success "Log files cleaned up"
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --clean)
                CLEAN_VOLUMES=true
                shift
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Stops the Oba local development stack."
                echo ""
                echo "Options:"
                echo "  --clean       Remove Docker volumes when stopping (deletes database data)"
                echo "  --help, -h    Show this help message"
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                echo "Use --help for usage information"
                exit 1
                ;;
        esac
    done
}

main() {
    parse_args "$@"

    echo ""
    echo "=============================================="
    echo "  Stopping Oba Development Stack"
    echo "=============================================="
    echo ""

    stop_frontend
    stop_backend
    stop_infrastructure
    cleanup_logs

    echo ""
    log_success "All services stopped"
    echo ""
}

main "$@"
