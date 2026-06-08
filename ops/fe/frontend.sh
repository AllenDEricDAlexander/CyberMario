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
VITE_BIN="${FRONTEND_DIR}/node_modules/.bin/vite"
LOG_DIR="${SCRIPT_DIR}/logs"
LOG_FILE="${LOG_DIR}/frontend.log"
PID_FILE="${LOG_DIR}/frontend.pid"

# Frontend defaults.
FE_PORT="${FE_PORT:-5173}"
FE_HOST="${FE_HOST:-0.0.0.0}"

usage() {
    echo "Usage: $0 {start|stop|restart|status}"
}

# If pid file exists and process is alive, print pid.
get_running_pid() {
    if [[ -f "${PID_FILE}" ]]; then
        local file_pid
        file_pid="$(tr -d ' \n\r' < "${PID_FILE}")"
        if [[ -n "${file_pid}" ]] && kill -0 "${file_pid}" 2>/dev/null; then
            echo "${file_pid}"
            return 0
        fi
    fi

    local running
    running="$(lsof -tiTCP:"${FE_PORT}" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "${running}" ]]; then
        echo "${running%%$'\n'*}"
        return 0
    fi

    running="$(pgrep -f "node .*vite.* --port ${FE_PORT}" 2>/dev/null || true)"
    if [[ -n "${running}" ]]; then
        echo "${running%%$'\n'*}"
        return 0
    fi
    return 1
}

# Start frontend in background and append logs to frontend.log.
start_frontend() {
    if [[ ! -x "${VITE_BIN}" ]]; then
        echo "Vite executable not found. Run npm install under ${FRONTEND_DIR} first."
        exit 1
    fi

    mkdir -p "${LOG_DIR}"
    local old_dir
    old_dir="$(pwd)"
    cd "${FRONTEND_DIR}"
    nohup "${VITE_BIN}" --host "${FE_HOST}" --port "${FE_PORT}" </dev/null >> "${LOG_FILE}" 2>&1 &
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
            echo "${running_pid}" > "${PID_FILE}"
            echo "frontend started, pid=${running_pid}, log=${LOG_FILE}"
            return 0
        fi
        sleep 1
    done

    echo "frontend failed to start, check ${LOG_FILE}"
    exit 1
}

# Stop frontend by PID or matching process.
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

    rm -f "${PID_FILE}"
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

case "${1:-}" in
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
