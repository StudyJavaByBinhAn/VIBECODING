#!/usr/bin/env bash
# Build + (optionally) run the Dental Booking API as a Docker image.
# Không giả định nền tảng deploy cụ thể (VPS/cloud/...) — image build ra chạy được
# ở bất kỳ đâu có Docker, chỉ cần đúng env var (xem .env.example).
#
# Usage:
#   ./deploy.sh build              # chỉ build image
#   ./deploy.sh run                # build + chạy container (đọc .env nếu có)
#   ./deploy.sh run --network host # build + chạy, truyền thêm docker run args

set -euo pipefail

IMAGE_NAME="antijob:latest"
CONTAINER_NAME="antijob-app"

cmd="${1:-build}"
shift || true

echo "==> Building image ${IMAGE_NAME}"
docker build -t "${IMAGE_NAME}" .

if [ "${cmd}" = "build" ]; then
  echo "==> Done. Image: ${IMAGE_NAME}"
  exit 0
fi

if [ "${cmd}" != "run" ]; then
  echo "Unknown command: ${cmd} (dùng 'build' hoặc 'run')" >&2
  exit 1
fi

ENV_FILE_ARGS=()
if [ -f .env ]; then
  ENV_FILE_ARGS=(--env-file .env)
else
  echo "==> Không có .env — dùng default dev (xem .env.example). Nhớ set JWT_SECRET riêng khi deploy thật."
fi

echo "==> Removing old container (nếu có)"
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

echo "==> Starting container ${CONTAINER_NAME}"
docker run -d --name "${CONTAINER_NAME}" \
  -p 8080:8080 \
  "${ENV_FILE_ARGS[@]}" \
  "$@" \
  "${IMAGE_NAME}"

echo "==> Started. Logs: docker logs -f ${CONTAINER_NAME}"
