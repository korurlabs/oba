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
LOGS_DIR="$SCRIPT_DIR/logs"

DO_BUILD=true

# Log functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# True when the Kagi CLI can actually inject this backend's config.
#
# Checks all three failure modes separately so the warning names the real one:
# CLI absent, not authenticated, or a committed kagi.yaml the installed CLI
# cannot resolve. Never exits -- callers fall back to an un-injected run.
kagi_config_available() {
    if ! command -v kagi &> /dev/null; then
        log_warn "Kagi CLI not found. Install: brew install korurlabs/tap/kagi-cli"
        return 1
    fi

    if ! (cd "$BACKEND_DIR" && kagi org current) &> /dev/null; then
        log_warn "Kagi CLI is not authenticated (or no org is selected). Run 'kagi login'."
        return 1
    fi

    # Only stderr is captured; secrets on stdout are discarded.
    local probe_err
    if ! probe_err=$( (cd "$BACKEND_DIR" && kagi pull >/dev/null) 2>&1 ); then
        log_warn "The binding in $BACKEND_DIR/kagi.yaml does not resolve:"
        log_warn "  ${probe_err%%$'\n'*}"
        log_warn "Re-bind it: cd $BACKEND_DIR && kagi setup --path /oba/api --env local"
        return 1
    fi

    return 0
}

# Resolve the built Spring Boot jar, ignoring Maven's *.jar.original and any
# sources/javadoc artifacts.
backend_jar() {
    ls -t "$BACKEND_DIR"/target/*.jar 2>/dev/null \
        | grep -v -- '-sources\.jar$\|-javadoc\.jar$' \
        | head -1
}

# Kill a PID and everything it spawned.
#
# The recorded PID is the wrapper (`nohup kagi run ...`); the JVM that actually
# holds port 8080 is a Maven-forked grandchild, so killing only the recorded PID
# leaves 8080 bound and the restart below then reports "already running" against
# the OLD process -- i.e. the restart silently does nothing.
kill_tree() {
    local pid="$1"
    local child

    for child in $(pgrep -P "$pid" 2>/dev/null); do
        kill_tree "$child"
    done

    # rule-10-no-op -- reason: the process may exit on its own between the pgrep
    # above and this kill (the normal shutdown race). A failure here means it is
    # already gone, which is the outcome we wanted.
    kill "$pid" 2>/dev/null || true
}

# Stop backend
stop_backend() {
    log_info "Stopping backend..."

    if [ -f "$LOGS_DIR/.backend.pid" ]; then
        local pid
        pid=$(cat "$LOGS_DIR/.backend.pid")
        if kill -0 "$pid" 2>/dev/null; then
            kill_tree "$pid"
            log_success "Backend stopped (PID: $pid)"
        else
            log_warn "Backend process not found (PID: $pid)"
        fi
        rm -f "$LOGS_DIR/.backend.pid"
        return 0
    fi

    # Fall back to whatever holds the backend port, NOT `pgrep -f spring-boot:run`:
    # other projects in this monorepo (Kagi) also run Spring Boot via Maven,
    # and the pattern form would kill their backends too.
    if ! command -v lsof &> /dev/null; then
        log_warn "No PID file and lsof unavailable — cannot locate the backend to stop."
        return 0
    fi

    local pids
    # rule-10-no-op -- reason: lsof exits non-zero when nothing is listening on
    # 8080, which is the expected "backend already stopped" case; it is handled
    # by the emptiness test below rather than being an error.
    pids=$(lsof -nP -iTCP:8080 -sTCP:LISTEN -t 2>/dev/null || true)

    if [ -n "$pids" ]; then
        local p
        for p in $pids; do
            kill_tree "$p"
        done
        log_success "Backend stopped (port 8080)"
    else
        log_warn "No backend process found"
    fi
}

# Check backend logs for errors (returns 0 if errors found)
check_backend_errors() {
    local log_file="$LOGS_DIR/backend.log"
    [ ! -f "$log_file" ] && return 1
    if grep -q "APPLICATION FAILED TO START\|BeanCreationException\|Failed to configure a DataSource\|Unable to create requested service\|Error creating bean\|Failed to instantiate\|BindException.*Address already in use" "$log_file" 2>/dev/null; then
        return 0
    fi
    if grep -q "\[ERROR\] Failed to execute goal\|Process terminated with exit code\|\[ERROR\].*BUILD FAILURE" "$log_file" 2>/dev/null; then
        return 0
    fi
    # POSIX ERE has no \d or \s -- grep -E read the previous "\d{4}" as a literal
    # 'd', so this check matched "dddd-dd-dd" and never fired on a real log line.
    if grep -E "^[0-9]{4}-[0-9]{2}-[0-9]{2}.*[[:space:]](ERROR|FATAL)[[:space:]]" "$log_file" 2>/dev/null | grep -v "HikariPool.*Connection is not available" | grep -q .; then
        return 0
    fi
    return 1
}

show_backend_errors() {
    local log_file="$LOGS_DIR/backend.log"
    echo ""
    log_error "Backend startup failed! Error details:"
    echo "----------------------------------------------"
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
    [ ! -f "$LOGS_DIR/.backend.pid" ] && return 1
    local pid=$(cat "$LOGS_DIR/.backend.pid")
    kill -0 "$pid" 2>/dev/null
}

# Start backend
start_backend() {
    log_info "Starting backend..."
    cd "$BACKEND_DIR"

    # Check if backend is already running
    if curl -s --connect-timeout 2 http://localhost:8080/actuator/health &> /dev/null; then
        log_warn "Backend is already running on port 8080"
        return 0
    fi

    # Detect Java 25 on macOS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # rule-10-no-op -- reason: absence of a Java 25 JDK is a supported case
        # here; the emptiness of java_home_25 is tested immediately below and the
        # script proceeds with the ambient JAVA_HOME instead.
        local java_home_25
        java_home_25=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
        if [ -n "$java_home_25" ]; then
            export JAVA_HOME="$java_home_25"
            log_info "Using Java 25: $JAVA_HOME"
        fi
    fi

    # Maven wrapper preferred, system mvn as fallback (matching start.sh).
    local mvn_cmd="$BACKEND_DIR/mvnw"
    if [ ! -x "$mvn_cmd" ]; then
        if ! command -v mvn &> /dev/null; then
            log_error "Neither the Maven wrapper (mvnw) nor system 'mvn' is available."
            exit 1
        fi
        mvn_cmd="mvn"
    fi

    # --no-build previously changed nothing: BOTH branches ran the identical
    # './mvnw spring-boot:run', and the message referenced -Dspring-boot.run.skip
    # (which would skip the RUN, not the build). The flag now genuinely differs:
    # the default repackages the jar, --no-build reuses the existing one.
    if [ "$DO_BUILD" = true ]; then
        log_info "Building backend ($mvn_cmd clean package -DskipTests)..."
        if ! "$mvn_cmd" clean package -DskipTests -q > "$LOGS_DIR/backend-build.log" 2>&1; then
            log_error "Backend build failed. See $LOGS_DIR/backend-build.log"
            tail -n 50 "$LOGS_DIR/backend-build.log"
            exit 1
        fi
        log_success "Backend built"
    else
        log_info "Skipping build (--no-build); reusing the existing jar."
    fi

    local jar_file
    jar_file=$(backend_jar)
    if [ -z "$jar_file" ] || [ ! -f "$jar_file" ]; then
        log_error "Backend jar not found in $BACKEND_DIR/target"
        log_error "Re-run without --no-build to produce it."
        exit 1
    fi

    # Launched under 'kagi run' so its config is injected from the Kagi
    # environment bound by backend/kagi.yaml. Running the packaged jar rather
    # than 'spring-boot:run' keeps the process tree shallow (kagi -> java), so
    # stop.sh can reliably kill it.
    # CorsConfig allows localhost and *.localhost origins only when
    # sensey.cors.allow-local-origins is on, so the local run turns it on here;
    # 'kagi run' passes the parent environment to java.
    export SENSEY_CORS_ALLOW_LOCAL_ORIGINS="${SENSEY_CORS_ALLOW_LOCAL_ORIGINS:-true}"
    if kagi_config_available; then
        log_info "Config: Kagi (via 'kagi run', binding: $BACKEND_DIR/kagi.yaml)"
        nohup kagi run -- java -jar "$jar_file" > "$LOGS_DIR/backend.log" 2>&1 &
    else
        log_warn "Starting backend WITHOUT Kagi-injected config — ENCRYPTION_SECRET has"
        log_warn "no default in application.properties, so startup will likely fail."
        nohup java -jar "$jar_file" > "$LOGS_DIR/backend.log" 2>&1 &
    fi

    echo $! > "$LOGS_DIR/.backend.pid"
    log_info "Backend starting with PID: $(cat "$LOGS_DIR/.backend.pid")"
    log_info "Logs: $LOGS_DIR/backend.log"
}

# Wait for backend to be ready
wait_for_backend() {
    log_info "Waiting for backend to be ready..."
    local max_attempts=120
    local attempt=1
    local error_check_interval=2

    while [ $attempt -le $max_attempts ]; do
        if curl -s --connect-timeout 2 http://localhost:8080/actuator/health &> /dev/null; then
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

parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --no-build)
                DO_BUILD=false
                shift
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Stops the backend and starts it again via Maven wrapper."
                echo "Use this when only backend code changed (frontend has HMR; DB/Keycloak unchanged)."
                echo ""
                echo "Options:"
                echo "  --no-build   Skip Maven rebuild (faster restart if no code changes)"
                echo "  --help, -h   Show this help"
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                echo "Use --help for usage"
                exit 1
                ;;
        esac
    done
}

main() {
    parse_args "$@"
    mkdir -p "$LOGS_DIR"

    stop_backend
    start_backend
    wait_for_backend

    log_success "Backend restarted. API: http://localhost:8080"
}

main "$@"
