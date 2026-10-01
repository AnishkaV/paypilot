#!/usr/bin/env bash
# Builds PayPilot and starts all four services (macOS / Linux / Git Bash). No Docker needed.
#   ./run-local.sh              build + start
#   ./run-local.sh --skip-build start again without rebuilding
#   ./run-local.sh --stop       stop everything
set -euo pipefail
cd "$(dirname "$0")"

SERVICES=("mock-psp:8083" "ledger-service:8082" "payment-service:8081" "api-gateway:8080")

stop_all() {
  if [[ -f logs/pids.txt ]]; then
    while read -r pid name; do kill "$pid" 2>/dev/null && echo "Stopped $name ($pid)" || true; done < logs/pids.txt
    rm -f logs/pids.txt
  fi
}

if [[ "${1:-}" == "--stop" ]]; then stop_all; exit 0; fi

command -v java >/dev/null || { echo "Java not found. Install JDK 17+ (21 recommended)."; exit 1; }
if [[ "${1:-}" != "--skip-build" ]]; then
  echo "==> Building"
  mvn -B -q -DskipTests package
fi

stop_all
mkdir -p logs data
for entry in "${SERVICES[@]}"; do
  name="${entry%%:*}"
  jar=$(ls "$name"/target/"$name"-*.jar | grep -v original | head -n 1)
  nohup java -Xmx384m -jar "$jar" > "logs/$name.log" 2>&1 &
  echo "$! $name" >> logs/pids.txt
  echo "==> Started $name (pid $!)"
done

for entry in "${SERVICES[@]}"; do
  name="${entry%%:*}"; port="${entry##*:}"
  for _ in $(seq 1 170); do
    curl -fs "http://localhost:$port/actuator/health" | grep -q '"UP"' && break
    sleep 0.7
  done
  curl -fs "http://localhost:$port/actuator/health" | grep -q '"UP"' || { echo "$name failed to start, see logs/$name.log"; exit 1; }
  echo "    $name is up"
done

echo
echo "PayPilot is running: http://localhost:8080   (stop with ./run-local.sh --stop)"
