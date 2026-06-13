#!/usr/bin/env bash

# Manage CyberMario backend with start/stop/restart/status in background with logs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Locate the repository root whether the script is called from ops/be or copied elsewhere under ops.
find_project_root() {
    local current_dir="${SCRIPT_DIR}"
    while [[ "${current_dir}" != "/" ]]; do
        if [[ -x "${current_dir}/be/mvnw" ]]; then
            echo "${current_dir}"
            return 0
        fi
        current_dir="$(dirname "${current_dir}")"
    done
    return 1
}

PROJECT_ROOT="$(find_project_root)"
BACKEND_DIR="${PROJECT_ROOT}/be"
LOG_DIR="${SCRIPT_DIR}/logs"
LOG_FILE="${LOG_DIR}/backend.log"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-${PROJECT_ROOT}/.env}"

# Backend defaults and extension points.
BACKEND_PORT="${BACKEND_PORT:-}"
BACKEND_AUTO_BUILD="${BACKEND_AUTO_BUILD:-1}"

load_backend_env() {
    if [[ ! -f "${BACKEND_ENV_FILE}" ]]; then
        return 0
    fi

    if [[ ! -r "${BACKEND_ENV_FILE}" ]]; then
        echo "Cannot read env file: ${BACKEND_ENV_FILE}"
        exit 1
    fi

    local raw_line trimmed key value delimiter
    while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
        trimmed="${raw_line%%$'\r'}"
        trimmed="${trimmed#"${trimmed%%[![:space:]]*}"}"
        trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"

        [[ -z "${trimmed}" || "${trimmed:0:1}" == "#" ]] && continue

        if [[ "${trimmed}" == export*' ' ]]; then
            trimmed="${trimmed#export }"
            trimmed="${trimmed#"${trimmed%%[![:space:]]*}"}"
        fi

        if [[ "${trimmed}" == *"="* ]]; then
            delimiter="="
        elif [[ "${trimmed}" == *":"* ]]; then
            delimiter=":"
        else
            continue
        fi

        key="${trimmed%%${delimiter}*}"
        value="${trimmed#*${delimiter}}"

        key="${key#"${key%%[![:space:]]*}"}"
        key="${key%"${key##*[![:space:]]}"}"
        value="${value#"${value%%[![:space:]]*}"}"
        value="${value%"${value##*[![:space:]]}"}"

        if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
            continue
        fi

        if [[ "${value}" == "\"${value}" && "${value}" == *"\"" ]]; then
            value="${value:1:${#value}-2}"
        elif [[ "${value}" == "'${value}" && "${value}" == *"'" ]]; then
            value="${value:1:${#value}-2}"
        fi

        export "${key}=${value}"
    done < "${BACKEND_ENV_FILE}"
}

normalize_backend_env() {
    BACKEND_PORT="${BACKEND_PORT:-${SERVER_PORT:-28080}}"
    SERVER_PORT="${SERVER_PORT:-${BACKEND_PORT}}"
    export BACKEND_PORT SERVER_PORT
}

validate_dashscope_api_key() {
    if [[ -z "${AI_DASHSCOPE_API_KEY:-}" && -n "${SPRING_AI_DASHSCOPE_API_KEY:-}" ]]; then
        AI_DASHSCOPE_API_KEY="${SPRING_AI_DASHSCOPE_API_KEY}"
    fi

    if [[ -z "${AI_DASHSCOPE_API_KEY:-}" && -n "${SPRING_AI_DASHSCOPE_CHAT_API_KEY:-}" ]]; then
        AI_DASHSCOPE_API_KEY="${SPRING_AI_DASHSCOPE_CHAT_API_KEY}"
    fi

    export AI_DASHSCOPE_API_KEY

    if [[ -z "${AI_DASHSCOPE_API_KEY:-}" ]]; then
        echo "Missing AI_DASHSCOPE_API_KEY, cannot start backend."
        echo "Set AI_DASHSCOPE_API_KEY (or SPRING_AI_DASHSCOPE_API_KEY / SPRING_AI_DASHSCOPE_CHAT_API_KEY) in your environment or in ${BACKEND_ENV_FILE}."
        exit 1
    fi
}

usage() {
    echo "Usage: $0 {start|stop|restart|status}"
}

# Return the first matching backend jar in be/target.
resolve_backend_jar() {
    local jars=()
    shopt -s nullglob
    jars=("${BACKEND_DIR}/target/mario-"*.jar)
    shopt -u nullglob

    if ((${#jars[@]} == 0)); then
        return 1
    fi

    local jar_file
    for jar_file in "${jars[@]}"; do
        case "${jar_file}" in
            *"-sources.jar" | *"-javadoc.jar" | *"-plain.jar")
                continue
                ;;
            *)
                echo "${jar_file}"
                return 0
                ;;
        esac
    done
    return 1
}

# Check whether the jar can be started directly by java -jar.
is_executable_jar() {
    local jar_file="$1"
    unzip -p "${jar_file}" META-INF/MANIFEST.MF 2>/dev/null | grep -q "^Main-Class:"
}

# Build a runnable jar when auto-build is enabled or when jar is missing.
build_backend() {
    echo "Building backend jar..."
    (cd "${BACKEND_DIR}" && ./mvnw -q -DskipTests package spring-boot:repackage)
}

# Locate the running backend by configured port or process command.
get_running_pid() {
    local running
    running="$(lsof -tiTCP:"${BACKEND_PORT}" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "${running}" ]]; then
        echo "${running%%$'\n'*}"
        return 0
    fi

    local jar_file
    jar_file="$(resolve_backend_jar || true)"
    if [[ -n "${jar_file}" ]]; then
        running="$(pgrep -f "java .*$(basename "${jar_file}")" 2>/dev/null || true)"
        if [[ -n "${running}" ]]; then
            echo "${running%%$'\n'*}"
            return 0
        fi
    fi
    return 1
}

# Start backend in background and append logs to backend.log.
start_backend() {
    local jar_file
    jar_file="$(resolve_backend_jar || true)"

    if [[ "${BACKEND_AUTO_BUILD}" == "1" ]] && { [[ -z "${jar_file}" ]] || ! is_executable_jar "${jar_file}"; }; then
        build_backend
        jar_file="$(resolve_backend_jar || true)"
    fi

    if [[ -z "${jar_file}" ]]; then
        echo "No backend jar found under be/target. Set BACKEND_AUTO_BUILD=1 or provide a pre-built jar."
        exit 1
    fi

    mkdir -p "${LOG_DIR}"
    if is_executable_jar "${jar_file}"; then
        nohup java -jar "${jar_file}" --server.port="${BACKEND_PORT}" \
            </dev/null >> "${LOG_FILE}" 2>&1 &
    else
        echo "Backend jar is not executable. Falling back to spring-boot:run..."
        local old_dir
        old_dir="$(pwd)"
        cd "${BACKEND_DIR}"
        nohup ./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--server.port="${BACKEND_PORT}" \
            </dev/null >> "${LOG_FILE}" 2>&1 &
        cd "${old_dir}"
    fi
    local pid=$!
    disown "${pid}" 2>/dev/null || true

    local running_pid
    for _ in {1..20}; do
        running_pid="$(get_running_pid || true)"
        if [[ -z "${running_pid}" ]] && ps -p "${pid}" >/dev/null 2>&1; then
            running_pid="${pid}"
        fi
        if [[ -n "${running_pid}" ]]; then
            echo "backend started, pid=${running_pid}, log=${LOG_FILE}"
            return 0
        fi
        sleep 1
    done

    echo "backend failed to start, check ${LOG_FILE}"
    exit 1
}

# Stop backend by matching process.
stop_backend() {
    local pid
    pid="$(get_running_pid || true)"
    if [[ -z "${pid}" ]]; then
        echo "backend is not running"
        return 0
    fi

    kill "${pid}"
    for _ in {1..20}; do
        if kill -0 "${pid}" 2>/dev/null; then
            sleep 1
        else
            break
        fi
    done
    if kill -0 "${pid}" 2>/dev/null; then
        kill -9 "${pid}" || true
    fi

    echo "backend stopped"
}

# Print process status with current pid if running.
status_backend() {
    local pid
    pid="$(get_running_pid || true)"
    if [[ -n "${pid}" ]]; then
        echo "backend is running (pid=${pid}, log=${LOG_FILE})"
    else
        echo "backend is not running"
    fi
}

ACTION="${1:-}"
case "${ACTION}" in
    start | stop | restart | status)
        load_backend_env
        normalize_backend_env
        ;;
esac

case "${ACTION}" in
    start)
        if get_running_pid >/dev/null 2>&1; then
            echo "backend already running"
            exit 0
        fi
        validate_dashscope_api_key
        start_backend
        ;;
    stop)
        stop_backend
        ;;
    restart)
        stop_backend
        validate_dashscope_api_key
        start_backend
        ;;
    status)
        status_backend
        ;;
    *)
        usage
        exit 1
        ;;
esac
