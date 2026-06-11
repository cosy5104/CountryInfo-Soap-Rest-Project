#!/usr/bin/env bash
# =============================================================================
# deploy.sh  –  Build, push, and deploy the Country Info Service to Kubernetes
# =============================================================================
set -euo pipefail

# ─── Configuration ─────────────────────────────────────────────────────────
REGISTRY="${REGISTRY:-your-registry}"          # e.g. docker.io/myuser
IMAGE_NAME="country-info-service"
IMAGE_TAG="${IMAGE_TAG:-1.0.0}"
NAMESPACE="country-info"
K8S_DIR="$(dirname "$0")/k8s"

FULL_IMAGE="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"

# ─── Colour helpers ────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ─── Pre-flight checks ─────────────────────────────────────────────────────
command -v docker  &>/dev/null || error "docker is not installed"
command -v kubectl &>/dev/null || error "kubectl is not installed"
command -v mvn     &>/dev/null || warn  "mvn not found – skipping local test run"

info "Deploying ${FULL_IMAGE} to namespace '${NAMESPACE}'"

# ─── 1. Run tests ──────────────────────────────────────────────────────────
if command -v mvn &>/dev/null; then
  info "Running unit tests..."
  mvn test -B -q || error "Tests failed. Aborting deployment."
  info "All tests passed."
fi

# ─── 2. Build Docker image ─────────────────────────────────────────────────
info "Building Docker image: ${FULL_IMAGE}"
docker build -t "${FULL_IMAGE}" .
info "Docker image built successfully."

# ─── 3. Push image to registry ─────────────────────────────────────────────
info "Pushing image to registry..."
docker push "${FULL_IMAGE}"
info "Image pushed successfully."

# ─── 4. Apply Kubernetes manifests ─────────────────────────────────────────
info "Applying Kubernetes manifests..."

kubectl apply -f "${K8S_DIR}/00-namespace.yaml"
kubectl apply -f "${K8S_DIR}/01-secret.yaml"
kubectl apply -f "${K8S_DIR}/02-configmap.yaml"
kubectl apply -f "${K8S_DIR}/03-postgres.yaml"

info "Waiting for PostgreSQL to be ready..."
kubectl rollout status deployment/postgres -n "${NAMESPACE}" --timeout=120s

kubectl apply -f "${K8S_DIR}/04-deployment.yaml"
kubectl apply -f "${K8S_DIR}/05-service-ingress.yaml"
kubectl apply -f "${K8S_DIR}/06-hpa.yaml"

# ─── 5. Update the image tag (triggers rolling update) ─────────────────────
info "Updating deployment image to ${FULL_IMAGE}..."
kubectl set image deployment/country-info-service \
  country-info-service="${FULL_IMAGE}" \
  -n "${NAMESPACE}"

# ─── 6. Wait for rollout ───────────────────────────────────────────────────
info "Waiting for rollout to complete..."
kubectl rollout status deployment/country-info-service \
  -n "${NAMESPACE}" --timeout=180s

# ─── 7. Verify deployment ──────────────────────────────────────────────────
info "Deployment status:"
kubectl get pods -n "${NAMESPACE}" -l app=country-info-service

info ""
info "✅  Deployment complete!"
info "   Image : ${FULL_IMAGE}"
info "   Pods  : $(kubectl get pods -n "${NAMESPACE}" -l app=country-info-service --no-headers | wc -l | tr -d ' ') running"
