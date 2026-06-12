# Kubernetes Deployment Guide

## Build Application

```bash
mvn clean package
```

## Build Docker Image

```bash
docker build -t country-info-app:latest .
```

## Apply Kubernetes Resources

```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

## Verify Deployment

```bash
kubectl get pods
kubectl get services
```

## Access Application

```bash
kubectl port-forward service/country-info-service 8080:80
```

Application URL:

```text
http://localhost:8080
```
