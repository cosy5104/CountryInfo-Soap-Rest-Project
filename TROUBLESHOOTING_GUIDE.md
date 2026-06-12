# Kubernetes Troubleshooting Guide

## Check Pod Status

```bash
kubectl get pods
```

## View Pod Logs

```bash
kubectl logs <pod-name>
```

## Describe Pod

```bash
kubectl describe pod <pod-name>
```

## Verify Service

```bash
kubectl get svc
```

## Verify Deployment

```bash
kubectl get deployments
```

## Restart Deployment

```bash
kubectl rollout restart deployment/country-info-app
```

## Common Issues

### Database Connection Failure

Verify datasource environment variables and database availability.

### CrashLoopBackOff

Inspect pod logs:

```bash
kubectl logs <pod-name>
```

### Service Unreachable

Verify service and pod labels match.

### Image Pull Failure

Confirm image name and registry access.
