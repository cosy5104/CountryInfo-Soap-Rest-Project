#!/usr/bin/env bash
# =============================================================================
# rollback.sh  –  Roll back the Country Info Service to the previous version
# =============================================================================
set -euo pipefail

NAMESPACE="country-info"
DEPLOYMENT="country-info-service"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

command -v kubectl &>/dev/null || error "kubectl is not installed"

info "Rolling back ${DEPLOYMENT} in namespace '${NAMESPACE}'..."
kubectl rollout undo deployment/${DEPLOYMENT} -n "${NAMESPACE}"

info "Waiting for rollback to complete..."
kubectl rollout status deployment/${DEPLOYMENT} -n "${NAMESPACE}" --timeout=120s

info "Current pod status:"
kubectl get pods -n "${NAMESPACE}" -l app=${DEPLOYMENT}

info "✅  Rollback complete."
