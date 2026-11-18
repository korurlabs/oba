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
COMPOSE_FILE="$BACKEND_DIR/docker-compose.yaml"

# Configuration
BACKEND_PORT=8080
KEYCLOAK_PORT=8086
KEYCLOAK_REALM=sensey

# Frontend apps to start (bash 3.2 compatible - no associative arrays).
APPS="kazan"

# Defaults
SKIP_FRONTEND=false
SKIP_BACKEND=false
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

# Check backend logs for errors. Returns 0 if errors found, 1 if none.
check_backend_errors() {
    local log_file="$LOGS_DIR/backend.log"

    if [ ! -f "$log_file" ]; then
        return 1
    fi

    if grep -q "APPLICATION FAILED TO START\|BeanCreationException\|Failed to configure a DataSource\|Unable to create requested service\|Error creating bean\|Failed to instantiate\|BindException.*Address already in use" "$log_file" 2>/dev/null; then
        return 0
    fi

    if grep -q "\[ERROR\] Failed to execute goal\|Process terminated with exit code\|\[ERROR\].*BUILD FAILURE" "$log_file" 2>/dev/null; then
        return 0
    fi

    # POSIX ERE has no \d -- grep -E reads "\d" as a literal 'd', so the upstream
    # form of this pattern matched "dddd-dd-dd" and never fired on a real log line.
    if grep -E "^[0-9]{4}-[0-9]{2}-[0-9]{2}.*[[:space:]](ERROR|FATAL)[[:space:]]" "$log_file" 2>/dev/null | grep -v "HikariPool.*Connection is not available" | grep -q .; then
        return 0
    fi

    return 1
}

# Display backend error details from logs
show_backend_errors() {
    local log_file="$LOGS_DIR/backend.log"

    echo ""
    log_error "Backend startup failed! Error details:"
    echo "----------------------------------------------"

    if [ ! -f "$log_file" ]; then
        log_error "No backend log at $log_file — the backend never produced output."
        echo "----------------------------------------------"
        echo ""
        return 0
    fi

    if grep -q "APPLICATION FAILED TO START" "$log_file" 2>/dev/null; then
        grep -A 20 "APPLICATION FAILED TO START" "$log_file" | head -25
    elif grep -q "\[ERROR\] Failed to execute goal\|Process terminated with exit code" "$log_file" 2>/dev/null; then
        grep "\[ERROR\]\|\[INFO\] BUILD\|Process terminated\|Caused by:" "$log_file" | tail -25
    elif grep -E "^[0-9]{4}-[0-9]{2}-[0-9]{2}.*[[:space:]](ERROR|FATAL)[[:space:]]" "$log_file" 2>/dev/null | grep -q .; then
        grep -E -B 2 -A 10 "^[0-9]{4}-[0-9]{2}-[0-9]{2}.*[[:space:]](ERROR|FATAL)[[:space:]]" "$log_file" | tail -30
    else
        tail -20 "$log_file"
    fi

    echo "----------------------------------------------"
    echo "Full logs: $log_file"
    echo ""
}

check_backend_process_alive() {
    local pid_file="$LOGS_DIR/.backend.pid"
    [ -f "$pid_file" ] || return 1
    local pid
    pid=$(cat "$pid_file")
    kill -0 "$pid" 2>/dev/null
}

check_frontend_process_alive() {
    local app_name=$1
    local pid_file="$LOGS_DIR/.frontend-${app_name}.pid"
    [ -f "$pid_file" ] || return 1
    local pid
    pid=$(cat "$pid_file")
    kill -0 "$pid" 2>/dev/null
}

# Check for required tools
check_requirements() {
    log_info "Checking requirements..."

    if command -v docker &> /dev/null; then
        CONTAINER_CMD="docker"
        COMPOSE_CMD="docker compose"
    elif command -v podman &> /dev/null; then
        CONTAINER_CMD="podman"
        COMPOSE_CMD="podman-compose"
    else
        log_error "Neither docker nor podman found. Please install one of them."
        exit 1
    fi
    log_info "Using $CONTAINER_CMD for containers"

    if ! command -v java &> /dev/null; then
        log_error "Java not found. Please install Java 25."
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 25 ] 2>/dev/null; then
        if command -v asdf &> /dev/null && asdf where java &> /dev/null; then
            export JAVA_HOME=$(asdf where java)
        elif [[ "$OSTYPE" == "darwin"* ]] && /usr/libexec/java_home -v 25 &> /dev/null; then
            export JAVA_HOME=$(/usr/libexec/java_home -v 25)
        elif [ -z "$JAVA_HOME" ]; then
            export JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
        fi
        log_info "Using Java $JAVA_VERSION: $JAVA_HOME"
    else
        log_warn "Java version $JAVA_VERSION detected. Java 25+ is required."
    fi

    if ! command -v bun &> /dev/null; then
        log_error "bun not found. Please install bun: https://bun.sh"
        exit 1
    fi

    if ! command -v jq &> /dev/null; then
        log_error "jq not found. Please install jq (e.g. brew install jq)."
        exit 1
    fi

    # Maven wrapper preferred, system mvn as fallback.
    MVN_CMD="$BACKEND_DIR/mvnw"
    if [ ! -x "$MVN_CMD" ]; then
        if command -v mvn &> /dev/null; then
            MVN_CMD="mvn"
        else
            log_error "Neither the Maven wrapper (mvnw) nor system 'mvn' was found."
            exit 1
        fi
    fi

    if ! command -v lsof &> /dev/null; then
        log_warn "lsof not found. The pre-flight port check will be skipped."
    fi

    if ! command -v kagi &> /dev/null; then
        log_error "Kagi CLI not found. Install it: brew install korurlabs/tap/kagi-cli"
        exit 1
    fi

    # Every directory launched under 'kagi run' needs its committed binding.
    if [ "$SKIP_BACKEND" = false ] && [ ! -f "$BACKEND_DIR/kagi.yaml" ]; then
        log_error "kagi.yaml not found in backend. Run: cd $BACKEND_DIR && kagi setup"
        exit 1
    fi
    if [ "$SKIP_FRONTEND" = false ]; then
        local app_name
        for app_name in $APPS; do
            if [ ! -f "$FRONTEND_DIR/apps/$app_name/kagi.yaml" ]; then
                log_error "kagi.yaml not found in frontend/apps/$app_name."
                log_error "Run: cd $FRONTEND_DIR/apps/$app_name && kagi setup"
                exit 1
            fi
        done
    fi

    log_success "All requirements met (including Kagi)"
}

# Verify the Kagi session before launching anything that needs secrets.
#
# The backend and every frontend app source their env from Kagi at launch via
# 'kagi run'. Rather than let them start in parallel and each discover a
# missing/expired session on its own, check once up front and stop with clear
# guidance if the user is not logged in.
check_kagi_auth() {
    # PAT auth (CI): the token comes from KAGI_TOKEN, never the keychain -- there
    # is no interactive login to worry about, so skip the check.
    if [ -n "$KAGI_TOKEN" ]; then
        return 0
    fi

    log_info "Verifying Kagi session..."
    local out
    if out=$(kagi org list 2>&1); then
        log_success "Kagi session is valid"
        return 0
    fi

    log_error "You are not logged in to Kagi (or your session has expired)."
    echo "----------------------------------------------"
    echo "$out"
    echo "----------------------------------------------"
    echo "Run 'kagi login' and then re-run this script."
    exit 1
}

# Verify each committed binding actually resolves, before anything is launched.
#
# An authenticated CLI still injects nothing if a kagi.yaml uses a binding model
# the installed CLI no longer understands. Probing here turns that into one clear
# message up front instead of N separate failures after the containers are up.
check_kagi_bindings() {
    log_info "Verifying Kagi bindings..."

    local dirs=""
    [ "$SKIP_BACKEND" = false ] && dirs="$BACKEND_DIR"
    if [ "$SKIP_FRONTEND" = false ]; then
        local app_name
        for app_name in $APPS; do
            dirs="$dirs $FRONTEND_DIR/apps/$app_name"
        done
    fi

    local dir probe_err failed=0
    for dir in $dirs; do
        # Only stderr is captured; secrets on stdout are discarded.
        if ! probe_err=$( (cd "$dir" && kagi pull >/dev/null) 2>&1 ); then
            log_error "Binding does not resolve: $dir/kagi.yaml"
            log_error "  ${probe_err%%$'\n'*}"
            failed=$((failed + 1))
        fi
    done

    if [ "$failed" -gt 0 ]; then
        log_error "$failed Kagi binding(s) failed to resolve. Re-bind with:"
        log_error "  cd <dir> && kagi setup --path /oba/<app> --env local"
        exit 1
    fi

    log_success "All Kagi bindings resolve"
}

# Enumerate every host port the compose stack binds, as "service<TAB>ip<TAB>port".
compose_config_raw() {
    (cd "$BACKEND_DIR" && $COMPOSE_CMD config) 2>/dev/null
}

compose_host_ports() {
    awk '
        /^services:/ { in_services = 1; next }
        /^[^ ]/      { in_services = 0 }
        in_services && /^  [A-Za-z0-9._-]+:[[:space:]]*$/ {
            svc = $1; sub(/:$/, "", svc); in_ports = 0; next
        }
        in_services && /^    ports:[[:space:]]*$/ { in_ports = 1; next }
        in_services && in_ports && /^    [A-Za-z0-9._-]+:/ { in_ports = 0 }
        in_ports && /^      - mode:/ { host_ip = ""; next }
        in_ports && /^        host_ip:/ { host_ip = $2; next }
        in_ports && /^        published:/ {
            published = $2; gsub(/"/, "", published)
            printf "%s\t%s\t%s\n", svc, (host_ip == "" ? "0.0.0.0" : host_ip), published
            next
        }
    '
}

# Compose project name. Prints nothing when it cannot be derived -- deliberately
# NOT defaulting to a guess, so callers can tell "not our container" from
# "ownership could not be determined".
compose_project_name() {
    awk '$1 == "name:" { print $2; exit }'
}

container_publishing_port() {
    $CONTAINER_CMD ps --format '{{.Names}}|{{.Ports}}' 2>/dev/null \
        | awk -F'|' -v pat=":$1->" 'index($2, pat) { print $1; exit }'
}

port_is_taken() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

report_port_conflict() {
    local svc="$1"
    local host_ip="$2"
    local port="$3"
    local owner_name="$4"
    local owner_project="$5"
    local inspect_failed="$6"
    local our_project="$7"

    echo ""
    log_error "Port ${host_ip}:${port} (compose service '${svc}') is already in use."

    if [ -n "$owner_name" ] && [ "$inspect_failed" = true ]; then
        # Ownership is UNKNOWN, not foreign. Say so, otherwise a user goes hunting
        # for a phantom conflict when the port is in fact held by this very stack.
        log_error "  Held by container '${owner_name}', but '$CONTAINER_CMD inspect' failed on it,"
        log_error "  so it could NOT be checked against this stack${our_project:+ (project '${our_project}')}."
        log_error "  This may be a false alarm. Verify with:"
        log_error "    $CONTAINER_CMD inspect ${owner_name}"
        return 0
    fi

    if [ -n "$owner_name" ]; then
        if [ -n "$owner_project" ]; then
            log_error "  Held by container '${owner_name}' from compose project '${owner_project}'."
        else
            log_error "  Held by container '${owner_name}'."
        fi
        log_error "  Fix: $CONTAINER_CMD stop ${owner_name}"
        return 0
    fi

    local holder pid cmd
    holder=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk 'NR > 1 { print $2 "|" $1; exit }')
    if [ -n "$holder" ]; then
        pid=${holder%%|*}
        cmd=${holder##*|}
        log_error "  Held by process '${cmd}' (PID ${pid}) — not a container."
        log_error "  Inspect: ps -p ${pid} -o command="
        log_error "  Fix: stop that process, or change the host port for '${svc}' in"
        log_error "       $COMPOSE_FILE"
    else
        log_error "  Could not identify the owner (another user's process?)."
        log_error "  Inspect: sudo lsof -nP -iTCP:${port} -sTCP:LISTEN"
    fi
}

# Pre-flight: verify every host port the stack needs is free BEFORE 'compose up'.
#
# Without this, compose starts most of the stack and then fails on the one
# conflicting publish with a raw daemon error that names neither the service nor
# the process holding the port, leaving a half-started stack behind.
check_ports() {
    log_info "Checking host ports required by the infrastructure stack..."

    if ! command -v lsof &> /dev/null; then
        log_warn "lsof not available — skipping the pre-flight port check."
        return 0
    fi

    local raw
    raw=$(compose_config_raw)
    if [ -z "$raw" ]; then
        log_error "'$COMPOSE_CMD config' produced no output in $BACKEND_DIR."
        log_error "The compose file is unreadable or invalid, so ports cannot be checked."
        log_error "Inspect: cd $BACKEND_DIR && $COMPOSE_CMD config"
        exit 1
    fi

    local ports
    ports=$(printf '%s\n' "$raw" | compose_host_ports)

    # An empty parse must never be treated as "nothing to check". If the resolved
    # config publishes ports but the parser extracted none, the output shape has
    # changed underneath us and this whole pre-flight check has silently become a
    # no-op -- the exact failure it exists to prevent. Fail loudly instead.
    if [ -z "$ports" ]; then
        if printf '%s\n' "$raw" | grep -q "^[[:space:]]*published:"; then
            log_error "Parsed ZERO host ports, but '$COMPOSE_CMD config' DOES publish ports."
            log_error "The port parser no longer matches the compose output format."
            log_error "Fix compose_host_ports() in $SCRIPT_DIR/start.sh"
            exit 1
        fi
        log_info "No host ports are published by this stack — nothing to pre-flight."
        return 0
    fi

    local project
    project=$(printf '%s\n' "$raw" | compose_project_name)
    if [ -z "$project" ]; then
        log_warn "Could not derive the compose project name from $COMPOSE_FILE."
        log_warn "Containers belonging to THIS stack may be reported as conflicts below."
    fi

    local conflicts=0
    local svc host_ip port owner_name owner_project inspect_failed

    while IFS="$(printf '\t')" read -r svc host_ip port; do
        [ -n "$port" ] || continue

        case "$port" in
            *[!0-9]*)
                log_warn "Service '$svc' publishes '$port', which is not a single port — not checked."
                continue
                ;;
        esac

        port_is_taken "$port" || continue

        # A port held by one of OUR OWN containers is fine: compose reuses it.
        owner_name=$(container_publishing_port "$port")
        owner_project=""
        inspect_failed=false
        if [ -n "$owner_name" ]; then
            # An existing container with no compose label yields an empty string
            # and exit 0, so a non-zero exit really does mean inspect FAILED.
            if ! owner_project=$($CONTAINER_CMD inspect \
                -f '{{index .Config.Labels "com.docker.compose.project"}}' "$owner_name" 2>/dev/null); then
                inspect_failed=true
                owner_project=""
            fi
        fi
        if [ -n "$owner_project" ] && [ -n "$project" ] && [ "$owner_project" = "$project" ]; then
            continue
        fi

        conflicts=$((conflicts + 1))
        report_port_conflict "$svc" "$host_ip" "$port" "$owner_name" "$owner_project" "$inspect_failed" "$project"
    done <<EOF
$ports
EOF

    if [ "$conflicts" -gt 0 ]; then
        echo ""
        log_error "$conflicts port conflict(s) found — not starting the stack."
        exit 1
    fi

    log_success "All required host ports are available"
}

# Start infrastructure services
start_infrastructure() {
    check_ports

    log_info "Starting infrastructure services..."
    cd "$BACKEND_DIR"

    if [ "$CLEAN_VOLUMES" = true ]; then
        log_warn "Removing existing containers and volumes (database data will be deleted)..."
        $COMPOSE_CMD down -v
        log_success "Containers and volumes removed"
    fi

    $COMPOSE_CMD up -d

    log_success "Infrastructure services started"
}

wait_for_postgres() {
    log_info "Waiting for PostgreSQL to be ready..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if $CONTAINER_CMD exec oba-postgres pg_isready -U oba &> /dev/null; then
            echo ""
            log_success "PostgreSQL is ready"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    echo ""
    log_error "PostgreSQL did not become ready in time"
    exit 1
}

# Wait for Keycloak to be ready.
#
# Probe the realm's OIDC discovery document, not /health/ready: in 'start-dev'
# Keycloak serves health on the (unpublished) management port 9000, so a plain
# curl against 8086/health/ready gets a 404 and still exits 0 -- it reported
# "ready" the moment the port accepted a connection, before the realm import had
# finished. 'curl -f' against the realm endpoint only succeeds once the realm is
# actually imported and serving.
wait_for_keycloak() {
    log_info "Waiting for Keycloak (realm '$KEYCLOAK_REALM') to be ready..."
    local max_attempts=60
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -sf --connect-timeout 3 \
            "http://localhost:$KEYCLOAK_PORT/realms/$KEYCLOAK_REALM/.well-known/openid-configuration" &> /dev/null; then
            echo ""
            log_success "Keycloak is ready"
            return 0
        fi
        echo -n "."
        sleep 3
        attempt=$((attempt + 1))
    done

    echo ""
    log_error "Keycloak did not become ready in time"
    log_error "Check: $CONTAINER_CMD logs oba-keycloak"
    exit 1
}

# Build backend
build_backend() {
    cd "$BACKEND_DIR"
    log_info "Building backend ($MVN_CMD clean package -DskipTests)..."
    if "$MVN_CMD" clean package -DskipTests -q > "$LOGS_DIR/backend-build.log" 2>&1; then
        log_success "Backend built"
    else
        log_error "Backend build failed. See $LOGS_DIR/backend-build.log"
        tail -n 50 "$LOGS_DIR/backend-build.log"
        exit 1
    fi
}

# Resolve the built Spring Boot jar, ignoring Maven's *.jar.original and any
# sources/javadoc artifacts.
backend_jar() {
    ls -t "$BACKEND_DIR"/target/*.jar 2>/dev/null \
        | grep -v -- '-sources\.jar$\|-javadoc\.jar$' \
        | head -1
}

# Start backend
#
# Launched under 'kagi run' so its config -- datasource, Keycloak, Azure,
# the AI provider keys and ENCRYPTION_SECRET (which has no default in
# application.properties) -- is injected from the Kagi environment bound by
# backend/kagi.yaml. Running the packaged jar rather than 'spring-boot:run'
# keeps the process tree shallow (kagi -> java), so stop.sh can reliably kill it.
start_backend() {
    log_info "Starting backend..."
    cd "$BACKEND_DIR"

    if curl -s --connect-timeout 2 "http://localhost:$BACKEND_PORT/actuator/health" &> /dev/null; then
        log_warn "Backend is already running on port $BACKEND_PORT"
        return 0
    fi

    local jar_file
    jar_file=$(backend_jar)
    if [ -z "$jar_file" ] || [ ! -f "$jar_file" ]; then
        log_error "Backend jar not found in $BACKEND_DIR/target"
        log_error "The build step should have produced it — see $LOGS_DIR/backend-build.log"
        exit 1
    fi
    log_info "Config: Kagi (via 'kagi run', binding: $BACKEND_DIR/kagi.yaml)"

    # CorsConfig allows localhost and *.localhost origins only when
    # sensey.cors.allow-local-origins is on, so the local run turns it on here;
    # 'kagi run' passes the parent environment to java.
    SENSEY_CORS_ALLOW_LOCAL_ORIGINS="${SENSEY_CORS_ALLOW_LOCAL_ORIGINS:-true}" \
        nohup kagi run -- java -jar "$jar_file" > "$LOGS_DIR/backend.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$LOGS_DIR/.backend.pid"

    log_info "Backend starting with PID: $pid"
    log_info "Logs available at: $LOGS_DIR/backend.log"
}

wait_for_backend() {
    log_info "Waiting for backend to be ready..."
    local max_attempts=120
    local attempt=1
    local error_check_interval=2

    while [ $attempt -le $max_attempts ]; do
        if curl -s --connect-timeout 2 "http://localhost:$BACKEND_PORT/actuator/health" &> /dev/null; then
            echo ""
            log_success "Backend is ready"
            return 0
        fi

        if [ $((attempt % error_check_interval)) -eq 0 ]; then
            if ! check_backend_process_alive; then
                echo ""
                log_error "Backend process died unexpectedly!"
                show_backend_errors
                exit 1
            fi
            if check_backend_errors; then
                echo ""
                show_backend_errors
                exit 1
            fi
        fi

        echo -n "."
        sleep 3
        attempt=$((attempt + 1))
    done

    echo ""
    log_error "Backend did not become ready in time."
    show_backend_errors
    exit 1
}

# Confirm Flyway actually applied migrations.
#
# The backend answering /actuator/health is not proof the schema exists: with
# ddl-auto=validate a broken migration chain fails the context, but a silently
# skipped Flyway run would leave an empty database that only breaks later, at the
# first query. Check the schema history table directly.
verify_flyway() {
    log_info "Verifying Flyway migrations..."

    local applied
    applied=$($CONTAINER_CMD exec oba-postgres psql -U oba -d oba -tAc \
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success;" 2>/dev/null || echo "0")

    if [ "$applied" -gt 0 ] 2>/dev/null; then
        log_success "Flyway applied $applied migration(s)"
        return 0
    fi

    log_error "Flyway has applied NO migrations — the database schema is missing."
    log_error "The backend is up but every query against it will fail."
    log_error "Check the backend log for Flyway errors: $LOGS_DIR/backend.log"
    exit 1
}

# Seed development data (organization; users are auto-created on first login).
#
# Runs AFTER the backend is up, because it depends on tables that Flyway creates.
seed_dev_data() {
    log_info "Checking if seed data is needed..."

    local user_count
    user_count=$($CONTAINER_CMD exec oba-postgres psql -U oba -d oba -tAc \
        "SELECT COUNT(*) FROM shrine.users;" 2>/dev/null || echo "0")

    if [ "$user_count" -gt 0 ] 2>/dev/null; then
        log_info "Users already exist ($user_count), skipping seed data"
        return 0
    fi

    log_info "Seeding development data..."

    local seed_output
    if seed_output=$($CONTAINER_CMD exec -i oba-postgres psql -v ON_ERROR_STOP=1 -U oba -d oba \
        < "$SCRIPT_DIR/seed-dev-data.sql" 2>&1); then
        log_success "Development data seeded (org: Sensey)"
    else
        log_error "Seed data failed:"
        echo "$seed_output" | tail -10
        exit 1
    fi
}

# Link any unlinked users to the default org
link_users_to_org() {
    local org_id="b0000000-0000-0000-0000-000000000001"
    local output

    if ! output=$($CONTAINER_CMD exec oba-postgres psql -U oba -d oba -q -c "
        UPDATE shrine.users
        SET default_organization_id = '$org_id'
        WHERE default_organization_id IS NULL;

        INSERT INTO shrine.organization_members (id, organization_id, user_id, role, status)
        SELECT gen_random_uuid(), '$org_id', u.id, 'OWNER', 'ACTIVE'
        FROM shrine.users u
        WHERE NOT EXISTS (
            SELECT 1 FROM shrine.organization_members om
            WHERE om.user_id = u.id AND om.organization_id = '$org_id'
        );
    " 2>&1); then
        # Not fatal: a fresh database has no users to link yet, and the backend
        # links them on first login. Surfaced rather than swallowed so a genuine
        # schema/permission problem is visible instead of silently skipped.
        log_warn "Could not link users to the default org:"
        echo "$output" | tail -5
    fi
}

install_frontend_deps() {
    log_info "Installing frontend dependencies..."
    (cd "$FRONTEND_DIR" && bun install)
    log_success "Frontend dependencies installed"
}

# Start a single frontend app under 'kagi run'.
start_frontend_app() {
    local app_name=$1
    local app_dir="$FRONTEND_DIR/apps/$app_name"
    local port
    port=$(oba_app_dev_port "$app_dir") || {
        log_error "Cannot determine the dev port for '$app_name' (see the warning above)."
        exit 1
    }

    # Use -f so a half-dead server that answers the TCP connection but returns an
    # error status is NOT mistaken for a healthy app.
    if curl -sf --connect-timeout 2 -o /dev/null "http://localhost:$port" 2>/dev/null; then
        log_warn "  $app_name is already running on port $port"
        return 0
    fi

    # Safety: never read a stale .env.local from disk -- config comes from Kagi at
    # runtime. A leftover file from an older workflow silently shadows the
    # injected values (Next.js loads .env.local over the process env for
    # NEXT_PUBLIC_* at build time), producing an app pointed at the wrong API.
    rm -f "$app_dir/.env.local"

    cd "$app_dir"
    nohup kagi run -- bun run dev > "$LOGS_DIR/frontend-${app_name}.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$LOGS_DIR/.frontend-${app_name}.pid"

    log_info "  $app_name starting on port $port (PID: $pid)"
}

# Wait for a frontend app to come up, catching kagi/bun failures early.
wait_for_frontend_app() {
    local app_name=$1
    local app_dir="$FRONTEND_DIR/apps/$app_name"
    local port
    port=$(oba_app_dev_port "$app_dir") || return 0
    local max_attempts=60
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        local http_code
        # rule-10-no-op -- reason: while the dev server is still booting, curl
        # exits 7 (connection refused). That is the expected polling state, not an
        # error, and is handled by the "000"/empty test below. Without the guard,
        # `set -e` aborts this script on the very first poll (observed: exit 7).
        http_code=$(curl -s -o /dev/null --connect-timeout 2 -w "%{http_code}" "http://localhost:$port" 2>/dev/null || true)
        if [ "$http_code" != "000" ] && [ -n "$http_code" ]; then
            log_success "  $app_name is ready on port $port (HTTP $http_code)"
            return 0
        fi

        if ! check_frontend_process_alive "$app_name"; then
            log_error "$app_name failed to start (the launch exited immediately)"
            echo "----------------------------------------------"
            tail -n 25 "$LOGS_DIR/frontend-${app_name}.log"
            echo "----------------------------------------------"
            exit 1
        fi

        sleep 2
        attempt=$((attempt + 1))
    done

    log_error "$app_name did not become ready in time."
    tail -n 25 "$LOGS_DIR/frontend-${app_name}.log"
    exit 1
}

# Start all frontend apps, then wait for them.
start_frontend() {
    log_info "Starting frontend apps ($APPS)..."

    local app_name
    for app_name in $APPS; do
        start_frontend_app "$app_name"
    done

    for app_name in $APPS; do
        wait_for_frontend_app "$app_name"
    done

    log_info "All frontend apps ready. Logs at: $LOGS_DIR/frontend-*.log"
}

print_summary() {
    echo ""
    echo "=============================================="
    echo -e "${GREEN}Oba Development Stack is Running!${NC}"
    echo "=============================================="
    echo ""
    echo "Frontend Apps:"
    local app_name port
    for app_name in $APPS; do
        port=$(oba_app_dev_port "$FRONTEND_DIR/apps/$app_name" 2>/dev/null) || port="?"
        printf "  %-12s http://localhost:%s\n" "$app_name:" "$port"
    done
    echo ""
    echo "Backend & Infrastructure:"
    echo "  Backend API:  http://localhost:$BACKEND_PORT"
    echo "  Swagger UI:   http://localhost:$BACKEND_PORT/swagger-ui.html"
    echo "  Keycloak:     http://localhost:$KEYCLOAK_PORT"
    echo ""
    echo "Config source:"
    echo "  Kagi (via 'kagi run')"
    echo "  Each app directory carries a committed kagi.yaml binding, so a bare"
    echo "  'kagi run' resolves that app's secrets from the cwd alone."
    echo ""
    echo "Login Credentials:"
    echo "  Username: senseyadmin"
    echo "  Password: securepassword123"
    echo ""
    echo "Logs:"
    echo "  Backend:      $LOGS_DIR/backend.log"
    for app_name in $APPS; do
        printf "  %-13s %s\n" "${app_name}:" "$LOGS_DIR/frontend-${app_name}.log"
    done
    echo ""
    echo "To stop all services: ./scripts/stop.sh"
    echo "To check status:      ./scripts/status.sh"
    echo "=============================================="
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --app)
                if [ -z "${2:-}" ]; then
                    log_error "--app requires an app name"
                    exit 1
                fi
                if [ ! -d "$FRONTEND_DIR/apps/$2" ]; then
                    log_error "Unknown frontend app: '$2'"
                    log_error "Available apps: $(ls -1 "$FRONTEND_DIR/apps" 2>/dev/null | tr '\n' ' ')"
                    exit 1
                fi
                APPS="$2"
                shift 2
                ;;
            --all-apps)
                APPS=$(ls -1 "$FRONTEND_DIR/apps" 2>/dev/null | tr '\n' ' ')
                shift
                ;;
            --no-frontend)
                SKIP_FRONTEND=true
                shift
                ;;
            --no-backend)
                SKIP_BACKEND=true
                shift
                ;;
            --clean)
                CLEAN_VOLUMES=true
                shift
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Starts the Oba local development stack:"
                echo "  - PostgreSQL, Keycloak, Azurite (Docker)"
                echo "  - Backend (Spring Boot jar, config injected from Kagi via 'kagi run')"
                echo "  - Frontend apps (Next.js via 'kagi run -- bun run dev')"
                echo ""
                echo "Options:"
                echo "  --app NAME      Start only NAME (default: kazan)"
                echo "  --all-apps      Start every app under frontend/apps"
                echo "  --no-frontend   Skip starting frontend apps"
                echo "  --no-backend    Skip starting the backend"
                echo "  --clean         Remove Docker volumes before starting (deletes DB data)"
                echo "  --help, -h      Show this help message"
                echo ""
                echo "Available apps: $(ls -1 "$FRONTEND_DIR/apps" 2>/dev/null | tr '\n' ' ')"
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

    mkdir -p "$LOGS_DIR"

    echo ""
    echo "=============================================="
    echo "  Oba Platform - Local Development"
    echo "  (Frontend apps: $APPS)"
    echo "=============================================="
    echo ""

    check_requirements
    check_kagi_auth
    check_kagi_bindings

    start_infrastructure
    wait_for_postgres
    wait_for_keycloak

    if [ "$SKIP_FRONTEND" = false ]; then
        install_frontend_deps
    fi

    if [ "$SKIP_BACKEND" = false ]; then
        build_backend
        start_backend
        wait_for_backend
        verify_flyway
        seed_dev_data
        link_users_to_org
    fi

    if [ "$SKIP_FRONTEND" = false ]; then
        start_frontend
    fi

    print_summary
}

main "$@"
