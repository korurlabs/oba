#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DB_CONTAINER="oba-postgres"

# Flags
FORCE_MODE=false
SKIP_BACKEND=false

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_DIR="$ROOT_DIR/backend"
LOGS_DIR="$SCRIPT_DIR/logs"

# Log functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --force|-f)
                FORCE_MODE=true
                shift
                ;;
            --skip-backend|-s)
                SKIP_BACKEND=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# Show help
show_help() {
    echo ""
    echo "Usage: ./scripts/reset-db.sh [OPTIONS]"
    echo ""
    echo "Resets the database by removing the PostgreSQL container and volume,"
    echo "then recreates it. Optionally restarts the backend for Flyway migrations."
    echo ""
    echo "Options:"
    echo "  --force, -f         Skip confirmation prompt"
    echo "  --skip-backend, -s  Skip automatic backend restart"
    echo "  --help, -h          Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./scripts/reset-db.sh                    # Reset and restart backend"
    echo "  ./scripts/reset-db.sh --force            # Reset without confirmation"
    echo "  ./scripts/reset-db.sh --skip-backend     # Reset only, don't restart backend"
    echo ""
}

# Detect container runtime
detect_runtime() {
    if command -v docker &> /dev/null && docker ps &> /dev/null; then
        CONTAINER_CMD="docker"
    elif command -v podman &> /dev/null; then
        CONTAINER_CMD="podman"
    else
        log_error "Neither docker nor podman found or running"
        exit 1
    fi
    log_info "Using container runtime: $CONTAINER_CMD"
}

# Detect compose command
detect_compose() {
    if command -v docker-compose &> /dev/null; then
        COMPOSE_CMD="docker-compose"
    elif command -v docker &> /dev/null; then
        COMPOSE_CMD="docker compose"
    else
        COMPOSE_CMD="podman-compose"
    fi
}

# Compose project name, derived the same way start.sh derives it.
#
# Prints nothing when it cannot be determined. Deliberately NOT defaulting to a
# guess: this script deletes volumes, and a wrong-but-plausible project name
# would target another stack's data volume.
compose_project_name() {
    (cd "$BACKEND_DIR" && $COMPOSE_CMD config) 2>/dev/null | awk '$1 == "name:" { print $2; exit }'
}

# Confirm action
confirm_reset() {
    echo ""
    echo -e "${YELLOW}WARNING: This will DELETE ALL DATA in the database!${NC}"
    echo -e "${YELLOW}The PostgreSQL container and volume will be removed and recreated.${NC}"
    echo ""
    read -p "Are you sure you want to continue? (yes/no): " confirm

    if [ "$confirm" != "yes" ]; then
        log_info "Operation cancelled"
        exit 0
    fi
}

# Stop and remove postgres container + its named data volume
#
# `docker rm -v` removes only ANONYMOUS volumes. The postgres data lives in the
# compose-managed NAMED volume `<project>_postgres_data`, which `rm -v` leaves
# untouched -- so the previous version of this function recreated the container
# on top of the existing data directory and reported "Database Reset Complete!"
# without having reset anything. The named volumes are now read off the container
# before it is removed and deleted explicitly.
reset_postgres() {
    log_info "Resolving PostgreSQL data volume(s)..."
    local volumes
    volumes=$($CONTAINER_CMD inspect -f \
        '{{range .Mounts}}{{if eq .Type "volume"}}{{.Name}} {{end}}{{end}}' \
        "$DB_CONTAINER" 2>/dev/null || echo "")

    if [ -n "$volumes" ]; then
        log_info "Data volume(s): $volumes"
    else
        log_warn "Could not resolve a named volume from '$DB_CONTAINER' (already removed?)."
        log_info "Falling back to the compose-derived volume name..."

        local project
        project=$(compose_project_name)

        # Validate BEFORE anything destructive runs. An empty project name would
        # otherwise produce the bare volume name "_postgres_data", which either
        # matches nothing or -- worse -- matches something that is not ours.
        if [ -z "$project" ]; then
            log_error "Could not derive the compose project name from $BACKEND_DIR."
            log_error "Refusing to guess a volume name; NOTHING has been removed."
            log_error "Inspect: cd $BACKEND_DIR && $COMPOSE_CMD config | head -1"
            log_error "Then remove the volume manually, e.g.:"
            log_error "  $CONTAINER_CMD volume ls | grep postgres_data"
            exit 1
        fi

        volumes="${project}_postgres_data"
        log_info "Derived data volume: $volumes (compose project '$project')"
    fi

    log_info "Stopping PostgreSQL container..."
    # rule-10-no-op -- reason: the container is legitimately absent when resetting
    # a stack that was never started; that is the desired end state, not an error.
    # A genuine failure surfaces at the `volume rm` / `compose up` steps below.
    $CONTAINER_CMD stop "$DB_CONTAINER" &> /dev/null || true

    log_info "Removing PostgreSQL container..."
    # rule-10-no-op -- reason: same as above -- "already removed" is success here.
    $CONTAINER_CMD rm -fv "$DB_CONTAINER" &> /dev/null || true

    log_info "Removing PostgreSQL data volume(s)..."
    local vol
    local removed=0
    for vol in $volumes; do
        local rm_output
        if rm_output=$($CONTAINER_CMD volume rm "$vol" 2>&1); then
            log_success "Removed volume: $vol"
            removed=$((removed + 1))
        elif ! $CONTAINER_CMD volume inspect "$vol" &> /dev/null; then
            log_info "Volume already absent: $vol"
            removed=$((removed + 1))
        else
            # The volume still exists and could not be removed -- almost always
            # another container still using it. Failing loudly here is the whole
            # point: continuing would silently reuse the old database.
            log_error "Could not remove volume '$vol':"
            echo "$rm_output" | tail -3
            log_error "Something is still using it. Stop the stack first:"
            log_error "  cd $BACKEND_DIR && $COMPOSE_CMD down"
            exit 1
        fi
    done

    if [ "$removed" -eq 0 ]; then
        log_error "No data volume was removed — the database would NOT have been reset."
        exit 1
    fi

    log_info "Recreating PostgreSQL container..."
    cd "$BACKEND_DIR"
    $COMPOSE_CMD up -d postgres

    log_success "PostgreSQL container and data volume recreated (database is empty)"
}

# Wait for postgres to be ready
wait_for_postgres() {
    log_info "Waiting for PostgreSQL to be ready..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if $CONTAINER_CMD exec "$DB_CONTAINER" pg_isready -U oba &> /dev/null; then
            log_success "PostgreSQL is ready"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    log_error "PostgreSQL did not become ready in time"
    exit 1
}

# Restart backend for Flyway migrations
restart_backend() {
    log_info "Restarting backend for Flyway migrations..."
    bash "$SCRIPT_DIR/restart-backend.sh"
}

# Print next steps (when backend was skipped)
print_next_steps_skipped() {
    echo ""
    echo "=============================================="
    echo -e "${GREEN}Database Reset Complete!${NC}"
    echo "=============================================="
    echo ""
    echo "Next steps:"
    echo "  1. Start the backend to run Flyway migrations:"
    echo "     ./scripts/restart-backend.sh"
    echo ""
    echo "=============================================="
}

# Main execution
main() {
    echo ""
    echo "=============================================="
    echo "  Oba Platform - Database Reset"
    echo "=============================================="
    echo ""

    parse_args "$@"

    if [ "$FORCE_MODE" != true ]; then
        confirm_reset
    fi

    detect_runtime
    detect_compose
    reset_postgres
    wait_for_postgres

    if [ "$SKIP_BACKEND" = true ]; then
        print_next_steps_skipped
    else
        echo ""
        log_info "Starting backend for Flyway migrations..."
        restart_backend
    fi
}

# Run main
main "$@"
