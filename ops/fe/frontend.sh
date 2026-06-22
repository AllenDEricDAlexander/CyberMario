#!/usr/bin/env bash

# Manage CyberMario frontend dev server with start/stop/restart/status in background with logs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Locate the repository root whether the script is called from ops/fe or copied elsewhere under ops.
find_project_root() {
    local current_dir="${SCRIPT_DIR}"
    while [[ "${current_dir}" != "/" ]]; do
        if [[ -f "${current_dir}/fe/package.json" ]]; then
            echo "${current_dir}"
            return 0
        fi
        current_dir="$(dirname "${current_dir}")"
    done
    return 1
}

PROJECT_ROOT="$(find_project_root)"
FRONTEND_DIR="${PROJECT_ROOT}/fe"
BUN_BIN="${BUN_BIN:-bun}"
VITE_BIN="${FRONTEND_DIR}/node_modules/.bin/vite"
LOG_DIR="${SCRIPT_DIR}/logs"
LOG_FILE="${LOG_DIR}/frontend.log"
FE_ENV_FILE="${FE_ENV_FILE:-${PROJECT_ROOT}/.env}"

# Frontend defaults.
FE_PORT="${FE_PORT:-}"
FE_HOST="${FE_HOST:-}"

load_frontend_env() {
    if [[ ! -f "${FE_ENV_FILE}" ]]; then
        return 0
    fi

    if [[ ! -r "${FE_ENV_FILE}" ]]; then
        echo "Cannot read env file: ${FE_ENV_FILE}"
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
    done < "${FE_ENV_FILE}"
}

normalize_frontend_env() {
    FE_PORT="${FE_PORT:-${VITE_PORT:-5173}}"
    FE_HOST="${FE_HOST:-${VITE_HOST:-0.0.0.0}}"
    if [[ -z "${VITE_BACKEND_PORT:-}" && -n "${BACKEND_PORT:-}" ]]; then
        VITE_BACKEND_PORT="${BACKEND_PORT}"
    elif [[ -z "${VITE_BACKEND_PORT:-}" && -n "${SERVER_PORT:-}" ]]; then
        VITE_BACKEND_PORT="${SERVER_PORT}"
    fi
    export FE_PORT FE_HOST VITE_BACKEND_PORT
}

usage() {
    echo "Usage: $0 {start|stop|restart|status}"
}

# Locate the running frontend by configured port or process command.
get_running_pid() {
    local running
    running="$(lsof -tiTCP:"${FE_PORT}" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "${running}" ]]; then
        echo "${running%%$'\n'*}"
        return 0
    fi

    running="$(pgrep -f "(node|bun).*vite.* --port ${FE_PORT}" 2>/dev/null || true)"
    if [[ -n "${running}" ]]; then
        echo "${running%%$'\n'*}"
        return 0
    fi
    return 1
}

# Start frontend in background and append logs to frontend.log.
start_frontend() {
    if ! command -v "${BUN_BIN}" >/dev/null 2>&1; then
        echo "Bun executable not found. Install Bun first."
        exit 1
    fi

    if [[ ! -x "${VITE_BIN}" ]]; then
        echo "Vite executable not found. Run bun install under ${FRONTEND_DIR} first."
        exit 1
    fi

    mkdir -p "${LOG_DIR}"
    local old_dir
    old_dir="$(pwd)"
    cd "${FRONTEND_DIR}"
    nohup "${BUN_BIN}" run --bun vite --host "${FE_HOST}" --port "${FE_PORT}" </dev/null >> "${LOG_FILE}" 2>&1 &
    local pid=$!
    cd "${old_dir}"
    disown "${pid}" 2>/dev/null || true

    local running_pid
    for _ in {1..20}; do
        running_pid="$(get_running_pid || true)"
        if [[ -z "${running_pid}" ]] && ps -p "${pid}" >/dev/null 2>&1; then
            running_pid="${pid}"
        fi
        if [[ -n "${running_pid}" ]]; then
            echo "frontend started, pid=${running_pid}, log=${LOG_FILE}"
            return 0
        fi
        sleep 1
    done

    echo "frontend failed to start, check ${LOG_FILE}"
    exit 1
}

# Stop frontend by matching process.
stop_frontend() {
    local pid
    pid="$(get_running_pid || true)"
    if [[ -z "${pid}" ]]; then
        echo "frontend is not running"
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

    echo "frontend stopped"
}

# Print process status with current pid if running.
status_frontend() {
    local pid
    pid="$(get_running_pid || true)"
    if [[ -n "${pid}" ]]; then
        echo "frontend is running (pid=${pid}, log=${LOG_FILE})"
    else
        echo "frontend is not running"
    fi
}

ACTION="${1:-}"
case "${ACTION}" in
    start | stop | restart | status)
        load_frontend_env
        normalize_frontend_env
        ;;
esac

case "${ACTION}" in
    start)
        if get_running_pid >/dev/null 2>&1; then
            echo "frontend already running"
            exit 0
        fi
        start_frontend
        ;;
    stop)
        stop_frontend
        ;;
    restart)
        stop_frontend
        start_frontend
        ;;
    status)
        status_frontend
        ;;
    *)
        usage
        exit 1
        ;;
esac
