#!/usr/bin/env bash
# Usage:
#   ./verify-cve.sh                 build locally (needs ~10GB Docker memory) and scan
#   ./verify-cve.sh <image-ref>     scan a CI-built image (copy "image=..." from the dev deploy run)
set -euo pipefail
PORT=8080
NAME="reops-event-proxy"

if [[ $# -ge 1 ]]; then
  IMAGE="$1"
  echo "== docker pull $IMAGE =="
  gcloud auth configure-docker europe-north1-docker.pkg.dev --quiet
  docker pull --platform linux/amd64 "$IMAGE"
  PLATFORM=(--platform linux/amd64)
else
  IMAGE="$NAME"
  echo "== docker build (native image, slow, needs ~10GB Docker memory) =="
  docker build --pull -t "$IMAGE" .
  PLATFORM=()
fi

echo "== docker run (may exit without Kafka/env; logs are informational) =="
docker run --rm -d "${PLATFORM[@]}" -p "$PORT:$PORT" --name "$NAME" "$IMAGE" || true
sleep 5
curl -sI "localhost:$PORT/internal/health/liveness" || true
echo "== container logs =="
docker logs "$NAME" 2>&1 | tail -30 || true
docker stop "$NAME" >/dev/null 2>&1 || true

echo "== grype scan =="
grype "${PLATFORM[@]}" "$IMAGE" -o table
