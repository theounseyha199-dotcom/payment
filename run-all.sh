#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")"

if [[ -f .env ]]; then
  set -a
  # Local credentials are shell assignments and are inherited by each service.
  source ./.env
  set +a
fi

for command in docker curl setsid java; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    exit 1
  fi
done

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose is required." >&2
  exit 1
fi

if [[ ! -x ./gradlew ]]; then
  echo "The Gradle wrapper is missing or is not executable." >&2
  exit 1
fi

services=(discovery-service user-service product-service order-service payment-service api-gateway)
ports=(8761 8081 8082 8083 8084 8080)
pids=()
log_dir="${RUN_ALL_LOG_DIR:-$PWD/.run/logs}"

cleanup() {
  trap - EXIT INT TERM
  if ((${#pids[@]})); then
    echo "Stopping services..."
    for pid in "${pids[@]}"; do
      kill -TERM -- "-$pid" 2>/dev/null || true
    done
    for pid in "${pids[@]}"; do
      wait "$pid" 2>/dev/null || true
    done
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for port in "${ports[@]}"; do
  if curl --silent --max-time 1 --output /dev/null "http://127.0.0.1:$port/"; then
    echo "Port $port already has an HTTP service. Stop it before running this script." >&2
    exit 1
  fi
done

echo "Starting PostgreSQL..."
docker compose up -d --wait postgres

# Older PostgreSQL volumes may predate one or more services.
for database in userdb productdb orderdb paymentdb keycloakdb; do
  exists=$(docker compose exec -T postgres psql -U practice -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname = '$database'")
  if [[ "$exists" != 1 ]]; then
    docker compose exec -T postgres psql -U practice -d postgres \
      -c "CREATE DATABASE $database"
  fi
done

echo "Starting Keycloak..."
docker compose up -d keycloak
for ((attempt = 0; attempt < 180; attempt++)); do
  if curl --silent --fail --max-time 2 --output /dev/null \
    'http://127.0.0.1:8180/realms/practice/.well-known/openid-configuration'; then
    echo "Keycloak is ready on port 8180"
    break
  fi
  if ((attempt == 179)); then
    echo "Keycloak did not become ready. Check docker compose logs keycloak" >&2
    exit 1
  fi
  sleep 1
done

mkdir -p "$log_dir"

start_service() {
  local service=$1
  echo "Starting $service (log: $log_dir/$service.log)"
  setsid ./gradlew -p "$service" --no-daemon bootRun >"$log_dir/$service.log" 2>&1 &
  pids+=("$!")
}

wait_for_service() {
  local service=$1 port=$2 pid=$3
  local attempt
  for ((attempt = 0; attempt < 180; attempt++)); do
    if curl --silent --max-time 1 --output /dev/null "http://127.0.0.1:$port/"; then
      echo "$service is listening on port $port"
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "$service stopped during startup. Check $log_dir/$service.log" >&2
      tail -n 30 "$log_dir/$service.log" >&2
      return 1
    fi
    sleep 1
  done
  echo "$service did not start within 3 minutes. Check $log_dir/$service.log" >&2
  tail -n 30 "$log_dir/$service.log" >&2
  return 1
}

start_service discovery-service
wait_for_service discovery-service 8761 "${pids[0]}"

for index in 1 2 3 4; do
  start_service "${services[$index]}"
done
for index in 1 2 3 4; do
  wait_for_service "${services[$index]}" "${ports[$index]}" "${pids[$index]}"
done

start_service api-gateway
wait_for_service api-gateway 8080 "${pids[5]}"

# Eureka and the gateway refresh their service lists after the ports open.
ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  status=$(curl --silent --max-time 2 --output /dev/null --write-out '%{http_code}' \
    'http://127.0.0.1:8080/products' || true)
  if [[ "$status" == 200 ]]; then
    ready=true
    break
  fi
  if ! kill -0 "${pids[5]}" 2>/dev/null; then
    echo "api-gateway stopped. Check $log_dir/api-gateway.log" >&2
    exit 1
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo "The gateway could not reach product-service within 60 seconds. Check $log_dir" >&2
  exit 1
fi

echo "All services are running. API: http://localhost:8080  Eureka: http://localhost:8761"
echo "Logs: $log_dir  |  Press Ctrl+C to stop the services."
wait -n "${pids[@]}" || true
echo "A service stopped. Check the logs in $log_dir" >&2
exit 1
