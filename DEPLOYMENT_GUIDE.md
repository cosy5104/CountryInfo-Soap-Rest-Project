# Kubernetes Deployment Guide — Country Info Service

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Project Overview](#project-overview)
3. [Step 1 — Build the Application](#step-1--build-the-application)
4. [Step 2 — Build & Push the Docker Image](#step-2--build--push-the-docker-image)
5. [Step 3 — Configure Secrets](#step-3--configure-secrets)
6. [Step 4 — Deploy to Kubernetes](#step-4--deploy-to-kubernetes)
7. [Step 5 — Verify the Deployment](#step-5--verify-the-deployment)
8. [Step 6 — Expose via Ingress](#step-6--expose-via-ingress)
9. [Step 7 — Automated Deployment (CI/CD)](#step-7--automated-deployment-cicd)
10. [Updating the Application](#updating-the-application)
11. [Rollback](#rollback)

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java JDK | 17+ | Build the application |
| Maven | 3.9+ | Dependency management & build |
| Docker | 20+ | Container image build & push |
| kubectl | 1.28+ | Kubernetes CLI |
| A Kubernetes cluster | 1.28+ | GKE / EKS / AKS / Minikube / Kind |
| A container registry | — | Docker Hub, ECR, GCR, or ACR |
| nginx-ingress controller | 1.10+ | Route external traffic |

### Verify your tools
```bash
java -version
mvn -version
docker version
kubectl version --client
kubectl cluster-info
```

---

## Project Overview

```
country-info-service/
├── src/                          # Java source code
├── k8s/
│   ├── 00-namespace.yaml         # Kubernetes namespace
│   ├── 01-secret.yaml            # Database credentials (encrypted at rest)
│   ├── 02-configmap.yaml         # Non-sensitive configuration
│   ├── 03-postgres.yaml          # PostgreSQL PVC + Deployment + Service
│   ├── 04-deployment.yaml        # Application Deployment
│   ├── 05-service-ingress.yaml   # ClusterIP Service + Ingress
│   └── 06-hpa.yaml               # Horizontal Pod Autoscaler
├── Dockerfile                    # Multi-stage Docker build
├── deploy.sh                     # One-click deploy script
└── rollback.sh                   # One-click rollback script
```

---

## Step 1 — Build the Application

```bash
cd country-info-service

# Run tests
mvn test

# Build JAR (skip tests if already run)
mvn clean package -DskipTests
```

Confirm the JAR was created:
```bash
ls -lh target/*.jar
# Output: target/country-info-service-1.0.0.jar
```

---

## Step 2 — Build & Push the Docker Image

```bash
# Set your registry
export REGISTRY="docker.io/YOUR_DOCKERHUB_USERNAME"
export IMAGE_TAG="1.0.0"

# Log in to your registry
docker login

# Build the image
docker build -t ${REGISTRY}/country-info-service:${IMAGE_TAG} .

# Verify the image
docker images | grep country-info-service

# Test locally before pushing
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/countrydb \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  ${REGISTRY}/country-info-service:${IMAGE_TAG}

# Push to registry
docker push ${REGISTRY}/country-info-service:${IMAGE_TAG}
```

---

## Step 3 — Configure Secrets

Never commit plaintext secrets to Git. Encode your real values:

```bash
# Encode your actual values
echo -n "your_db_password" | base64
echo -n "your_db_username" | base64
echo -n "jdbc:postgresql://postgres-service:5432/countrydb" | base64
```

Edit `k8s/01-secret.yaml` and replace the placeholder base64 strings with your
encoded values, then apply:

```bash
kubectl apply -f k8s/01-secret.yaml
```

> **Security tip:** In production, use a secrets manager such as
> HashiCorp Vault, AWS Secrets Manager, or Sealed Secrets instead of
> plain Kubernetes Secrets.

---

## Step 4 — Deploy to Kubernetes

### Option A — One-command deploy
```bash
export REGISTRY="docker.io/YOUR_USERNAME"
export IMAGE_TAG="1.0.0"

chmod +x deploy.sh
./deploy.sh
```

### Option B — Manual step-by-step

```bash
NAMESPACE="country-info"

# 1. Namespace
kubectl apply -f k8s/00-namespace.yaml

# 2. Secrets & ConfigMap
kubectl apply -f k8s/01-secret.yaml
kubectl apply -f k8s/02-configmap.yaml

# 3. PostgreSQL
kubectl apply -f k8s/03-postgres.yaml

# Wait for Postgres to be ready
kubectl rollout status deployment/postgres -n ${NAMESPACE} --timeout=120s

# 4. Application
kubectl apply -f k8s/04-deployment.yaml

# Wait for the app to be ready
kubectl rollout status deployment/country-info-service -n ${NAMESPACE} --timeout=180s

# 5. Service & Ingress
kubectl apply -f k8s/05-service-ingress.yaml

# 6. Autoscaler
kubectl apply -f k8s/06-hpa.yaml
```

### Update the image in the deployment
```bash
kubectl set image deployment/country-info-service \
  country-info-service=${REGISTRY}/country-info-service:${IMAGE_TAG} \
  -n country-info
```

---

## Step 5 — Verify the Deployment

```bash
# Check all resources in the namespace
kubectl get all -n country-info

# Expected output shows:
#  pod/country-info-service-xxxx-yyyy  Running
#  pod/postgres-xxxx-yyyy              Running
#  deployment.apps/country-info-service  2/2
#  deployment.apps/postgres              1/1
#  service/country-info-service  ClusterIP
#  service/postgres-service       ClusterIP

# Check pod logs
kubectl logs -l app=country-info-service -n country-info --tail=50

# Check health endpoint (port-forward for local access)
kubectl port-forward svc/country-info-service 8080:80 -n country-info &
curl http://localhost:8080/actuator/health
```

### Test the API locally via port-forward
```bash
# POST — fetch and save a country
curl -X POST http://localhost:8080/api/countries \
  -H "Content-Type: application/json" \
  -d '{"name": "kenya"}'

# GET — list all countries
curl http://localhost:8080/api/countries

# GET — fetch by ID
curl http://localhost:8080/api/countries/1

# PUT — update
curl -X PUT http://localhost:8080/api/countries/1 \
  -H "Content-Type: application/json" \
  -d '{"capitalCity": "Nairobi"}'

# DELETE
curl -X DELETE http://localhost:8080/api/countries/1
```

---

## Step 6 — Expose via Ingress

### Install the nginx-ingress controller (if not already installed)
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml

# Wait for it to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

### Get the external IP of the Ingress
```bash
kubectl get ingress country-info-ingress -n country-info
# NAME                    CLASS   HOSTS                        ADDRESS         PORTS
# country-info-ingress    nginx   country-info.yourdomain.com  34.x.x.x        80
```

### Point your DNS
Create an A record in your DNS provider:
```
country-info.yourdomain.com  →  34.x.x.x (your Ingress external IP)
```

---

## Step 7 — Automated Deployment (CI/CD)

Example GitHub Actions workflow (`.github/workflows/deploy.yml`):

```yaml
name: Build and Deploy
on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run tests
        run: mvn test -B

      - name: Build & push Docker image
        run: |
          docker login -u ${{ secrets.DOCKER_USERNAME }} -p ${{ secrets.DOCKER_PASSWORD }}
          docker build -t ${{ secrets.REGISTRY }}/country-info-service:${{ github.sha }} .
          docker push ${{ secrets.REGISTRY }}/country-info-service:${{ github.sha }}

      - name: Deploy to Kubernetes
        env:
          KUBECONFIG_DATA: ${{ secrets.KUBECONFIG }}
        run: |
          echo "${KUBECONFIG_DATA}" | base64 -d > /tmp/kubeconfig
          export KUBECONFIG=/tmp/kubeconfig
          kubectl set image deployment/country-info-service \
            country-info-service=${{ secrets.REGISTRY }}/country-info-service:${{ github.sha }} \
            -n country-info
          kubectl rollout status deployment/country-info-service -n country-info
```

---

## Updating the Application

```bash
# Build and push new version
export NEW_TAG="1.1.0"
docker build -t ${REGISTRY}/country-info-service:${NEW_TAG} .
docker push ${REGISTRY}/country-info-service:${NEW_TAG}

# Rolling update (zero downtime)
kubectl set image deployment/country-info-service \
  country-info-service=${REGISTRY}/country-info-service:${NEW_TAG} \
  -n country-info

# Watch the rolling update progress
kubectl rollout status deployment/country-info-service -n country-info
```

---

## Rollback

```bash
# Instant rollback to previous version
./rollback.sh

# Or manually
kubectl rollout undo deployment/country-info-service -n country-info

# Roll back to a specific revision
kubectl rollout history deployment/country-info-service -n country-info
kubectl rollout undo deployment/country-info-service --to-revision=2 -n country-info
```
