#!/bin/bash
set -e

APP_NAME="govcms-admin"
APP_JAR="target/govcms-admin-0.0.1-SNAPSHOT.jar"
APP_HOME="/opt/govcms"
LOG_DIR="/var/log/govcms"
PID_FILE="/var/run/govcms.pid"

export SPRING_PROFILES_ACTIVE=prod
export PROD_DB_PASSWORD="${PROD_DB_PASSWORD:-}"
export GM_SM2_PRIVATE_KEY="${GM_SM2_PRIVATE_KEY:-}"
export GM_SM2_PUBLIC_KEY="${GM_SM2_PUBLIC_KEY:-}"
export GM_SM4_KEY="${GM_SM4_KEY:-}"

start() {
    if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        echo "$APP_NAME is already running (PID: $(cat $PID_FILE))"
        exit 1
    fi

    echo "Starting $APP_NAME..."
    nohup java -jar "$APP_HOME/$APP_JAR" \
        --spring.profiles.active=prod \
        > "$LOG_DIR/stdout.log" 2>&1 &
    echo $! > "$PID_FILE"
    echo "$APP_NAME started with PID $(cat $PID_FILE)"
}

stop() {
    if [ ! -f "$PID_FILE" ] || ! kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        echo "$APP_NAME is not running"
        exit 1
    fi

    echo "Stopping $APP_NAME..."
    kill $(cat "$PID_FILE")
    rm -f "$PID_FILE"
    echo "$APP_NAME stopped"
}

status() {
    if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        echo "$APP_NAME is running (PID: $(cat $PID_FILE))"
    else
        echo "$APP_NAME is not running"
    fi
}

case "${1:-}" in
    start) start ;;
    stop) stop ;;
    restart) stop; sleep 2; start ;;
    status) status ;;
    *) echo "Usage: $0 {start|stop|restart|status}"; exit 1 ;;
esac
