# Kubernetes Troubleshooting Guide — Country Info Service

## Table of Contents
1. [Quick Diagnostics Checklist](#quick-diagnostics-checklist)
2. [Pod Issues](#pod-issues)
3. [Database (PostgreSQL) Issues](#database-postgresql-issues)
4. [Networking & Service Issues](#networking--service-issues)
5. [SOAP Service Issues](#soap-service-issues)
6. [Performance Issues](#performance-issues)
7. [Secrets & Configuration Issues](#secrets--configuration-issues)
8. [Ingress Issues](#ingress-issues)
9. [Useful kubectl Commands Reference](#useful-kubectl-commands-reference)

---

## Quick Diagnostics Checklist

Run this first for any issue:

```bash
NAMESPACE="country-info"

echo "=== Namespace Resources ==="
kubectl get all -n ${NAMESPACE}

echo "=== Pod Status ==="
kubectl get pods -n ${NAMESPACE} -o wide

echo "=== Recent Events ==="
kubectl get events -n ${NAMESPACE} --sort-by='.lastTimestamp' | tail -20

echo "=== App Logs (last 50 lines) ==="
kubectl logs -l app=country-info-service -n ${NAMESPACE} --tail=50

echo "=== HPA Status ==="
kubectl get hpa -n ${NAMESPACE}
```

---

## Pod Issues

### Problem: Pods stuck in `Pending`

**Symptoms:** `kubectl get pods` shows `STATUS: Pending`

**Diagnosis:**
```bash
kubectl describe pod <pod-name> -n country-info
# Look at the "Events" section at the bottom
```

**Common causes and fixes:**

| Cause | Event message | Fix |
|-------|--------------|-----|
| Insufficient CPU/memory | `Insufficient cpu` | Lower resource requests in `04-deployment.yaml` or add nodes |
| No matching nodes | `0/N nodes are available` | Check node taints and affinity rules |
| PVC not bound | `pod has unbound PVCs` | Check StorageClass: `kubectl get pvc -n country-info` |
| Image pull failure | `ImagePullBackOff` | See below |

---

### Problem: `ImagePullBackOff` or `ErrImagePull`

**Diagnosis:**
```bash
kubectl describe pod <pod-name> -n country-info | grep -A 10 "Events"
```

**Fixes:**
```bash
# Verify the image name in the deployment matches your registry
kubectl get deployment country-info-service -n country-info \
  -o jsonpath='{.spec.template.spec.containers[0].image}'

# For private registries, create an image pull secret
kubectl create secret docker-registry regcred \
  --docker-server=YOUR_REGISTRY \
  --docker-username=YOUR_USERNAME \
  --docker-password=YOUR_PASSWORD \
  --namespace country-info

# Then add imagePullSecrets to 04-deployment.yaml:
# spec:
#   template:
#     spec:
#       imagePullSecrets:
#         - name: regcred
```

---

### Problem: Pods in `CrashLoopBackOff`

**Diagnosis:**
```bash
# Get logs from the crashing pod
kubectl logs <pod-name> -n country-info --previous

# Or stream logs as it tries to start
kubectl logs <pod-name> -n country-info -f
```

**Common causes:**

**1. Cannot connect to database**
```
Caused by: org.postgresql.util.PSQLException: Connection refused
```
Fix: Verify PostgreSQL pod is running and the DB_URL secret is correct:
```bash
kubectl get pods -n country-info | grep postgres
kubectl logs deployment/postgres -n country-info
kubectl get secret country-info-secrets -n country-info \
  -o jsonpath='{.data.DB_URL}' | base64 -d
```

**2. Wrong credentials**
```
FATAL: password authentication failed for user "postgres"
```
Fix: Check that the secret values match PostgreSQL's configured credentials:
```bash
# Decode and verify each secret value
for KEY in DB_URL DB_USERNAME DB_PASSWORD; do
  echo -n "${KEY}: "
  kubectl get secret country-info-secrets -n country-info \
    -o jsonpath="{.data.${KEY}}" | base64 -d
  echo
done
```

**3. OOMKilled (Out of Memory)**
```
kubectl describe pod <pod-name> | grep -i "OOMKilled"
```
Fix: Increase memory limits in `04-deployment.yaml`:
```yaml
resources:
  limits:
    memory: "1Gi"   # increase from 512Mi
```

---

### Problem: Pods in `OOMKilled`

```bash
kubectl describe pod <pod-name> -n country-info | grep -A 5 "Last State"
# Look for: Reason: OOMKilled
```

**Fix:** Tune JVM memory and pod limits together:
```yaml
# In 04-deployment.yaml
resources:
  requests:
    memory: "512Mi"
  limits:
    memory: "1Gi"
# JVM respects -XX:MaxRAMPercentage so it will cap at 75% of 1Gi = 768Mi
```

---

## Database (PostgreSQL) Issues

### Problem: Cannot connect to PostgreSQL

```bash
# Check if Postgres pod is running
kubectl get pods -n country-info | grep postgres

# Check Postgres logs
kubectl logs deployment/postgres -n country-info

# Test connectivity from the app pod
kubectl exec -it deployment/country-info-service -n country-info -- \
  sh -c "nc -zv postgres-service 5432 && echo 'Connected!' || echo 'Failed'"

# Test with psql from a temporary pod
kubectl run psql-test --image=postgres:15-alpine --rm -it \
  --restart=Never -n country-info -- \
  psql -h postgres-service -U postgres -d countrydb
```

### Problem: PostgreSQL PVC not binding

```bash
kubectl get pvc -n country-info
# STATUS should be "Bound"; if "Pending", StorageClass may not exist

kubectl get storageclass
# Note the NAME of your available StorageClass

# Edit 03-postgres.yaml to use the correct storageClassName
# Then re-apply
kubectl apply -f k8s/03-postgres.yaml
```

### Problem: Database schema errors on startup

```bash
# View Hibernate DDL output
kubectl logs deployment/country-info-service -n country-info | grep -i "DDL\|schema\|table"

# Access the database directly to inspect
kubectl exec -it deployment/postgres -n country-info -- \
  psql -U postgres -d countrydb -c "\dt"
```

---

## Networking & Service Issues

### Problem: Service not reachable

```bash
# Verify Service exists and has correct selector
kubectl get svc country-info-service -n country-info
kubectl describe svc country-info-service -n country-info

# Check Endpoints are populated (must not be empty)
kubectl get endpoints country-info-service -n country-info
# If empty, the selector in the Service doesn't match any pod labels

# Compare Service selector vs Pod labels
kubectl get svc country-info-service -n country-info \
  -o jsonpath='{.spec.selector}'
kubectl get pods -n country-info --show-labels
```

### Problem: Port-forward not working

```bash
# Kill any existing port-forward
pkill -f "kubectl port-forward"

# Re-run port-forward
kubectl port-forward svc/country-info-service 8080:80 -n country-info &

# Test
curl -v http://localhost:8080/actuator/health
```

---

## SOAP Service Issues

### Problem: SOAP calls fail with timeout

```bash
# Check if pods can reach the external SOAP endpoint
kubectl exec -it deployment/country-info-service -n country-info -- \
  sh -c "wget -q -O - 'http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL' | head -5"
```

If the above fails, your cluster may block outbound HTTP. Check with your
network admin or configure an egress policy.

**Increase the SOAP timeout:**
Add to `application.properties` via ConfigMap:
```properties
spring.ws.client.connect-timeout=30000
spring.ws.client.read-timeout=30000
```

### Problem: SOAP returns null ISO code

```bash
# Enable DEBUG logging for Spring WS
kubectl set env deployment/country-info-service \
  LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WS=DEBUG \
  -n country-info

# Watch the logs
kubectl logs -f deployment/country-info-service -n country-info | grep -i soap

# Reset logging after debugging
kubectl set env deployment/country-info-service \
  LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WS=INFO \
  -n country-info
```

---

## Performance Issues

### Problem: High latency / slow responses

```bash
# Check CPU and memory usage
kubectl top pods -n country-info
kubectl top nodes

# Check HPA – is it scaling?
kubectl get hpa country-info-hpa -n country-info
kubectl describe hpa country-info-hpa -n country-info

# Manually scale if HPA is too slow to react
kubectl scale deployment/country-info-service --replicas=4 -n country-info
```

### Problem: HPA not scaling

```bash
# Verify metrics-server is installed
kubectl get deployment metrics-server -n kube-system

# If missing, install it
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Check HPA events
kubectl describe hpa country-info-hpa -n country-info | grep -A 10 "Events"
```

---

## Secrets & Configuration Issues

### Problem: Environment variables not set in pods

```bash
# Inspect environment in a running pod
kubectl exec -it deployment/country-info-service -n country-info -- env | grep -E "DB_|SOAP_"

# Verify Secret exists
kubectl get secret country-info-secrets -n country-info

# Verify ConfigMap exists
kubectl get configmap country-info-config -n country-info
kubectl describe configmap country-info-config -n country-info
```

### Update a secret without redeploying

```bash
# Encode the new value
NEW_PASS=$(echo -n "new_password" | base64)

# Patch the secret
kubectl patch secret country-info-secrets -n country-info \
  -p "{\"data\":{\"DB_PASSWORD\":\"${NEW_PASS}\"}}"

# Restart pods to pick up the change
kubectl rollout restart deployment/country-info-service -n country-info
```

---

## Ingress Issues

### Problem: Ingress returns 404 or 502

```bash
# Check Ingress status
kubectl describe ingress country-info-ingress -n country-info

# Check nginx-ingress controller logs
kubectl logs -l app.kubernetes.io/name=ingress-nginx \
  -n ingress-nginx --tail=50

# Verify backend service is reachable
kubectl get svc country-info-service -n country-info
```

### Problem: SSL certificate not provisioning (cert-manager)

```bash
kubectl get certificate -n country-info
kubectl describe certificate country-info-tls -n country-info
kubectl get challenges -n country-info
```

---

## Useful kubectl Commands Reference

```bash
# ── Watching resources ───────────────────────────────────────────────────────
kubectl get pods -n country-info -w                        # Watch pods live
kubectl get events -n country-info --sort-by=lastTimestamp # Sorted events

# ── Logs ─────────────────────────────────────────────────────────────────────
kubectl logs deployment/country-info-service -n country-info -f          # Follow logs
kubectl logs deployment/country-info-service -n country-info --previous  # Crashed pod logs
kubectl logs -l app=country-info-service -n country-info --tail=100      # All replicas

# ── Debugging ────────────────────────────────────────────────────────────────
kubectl exec -it deployment/country-info-service -n country-info -- sh   # Shell into pod
kubectl describe pod <pod-name> -n country-info                           # Full pod details

# ── Scaling ──────────────────────────────────────────────────────────────────
kubectl scale deployment/country-info-service --replicas=3 -n country-info
kubectl rollout restart deployment/country-info-service -n country-info  # Force restart

# ── Rollout management ───────────────────────────────────────────────────────
kubectl rollout history deployment/country-info-service -n country-info
kubectl rollout undo deployment/country-info-service -n country-info
kubectl rollout status deployment/country-info-service -n country-info

# ── Resource usage ───────────────────────────────────────────────────────────
kubectl top pods -n country-info
kubectl top nodes

# ── Port forwarding ──────────────────────────────────────────────────────────
kubectl port-forward svc/country-info-service 8080:80 -n country-info
kubectl port-forward svc/postgres-service 5432:5432 -n country-info

# ── Cleanup ──────────────────────────────────────────────────────────────────
kubectl delete namespace country-info    # Removes everything in the namespace
```
