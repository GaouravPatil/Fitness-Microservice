# GitLab CI and Argo CD

This directory deploys the backend services and their stateful dependencies to
EKS. The frontend remains on Cloudflare Pages at `https://aura.gaourav.space`.

```text
GitLab CI
  test backend + frontend
  build four Spring Boot images
  push immutable tags to ECR
  update Kustomize image tags in GitHub
        │
        ▼
Argo CD watches GitHub
        │
        ▼
EKS: eureka, userservice, activityservice, aiservice,
     postgres, mongodb, kafka, ALB ingress
```

> **Warning:** the Postgres, MongoDB, and Kafka manifests are single-replica
> development/staging baselines. They do not provide HA, backups, point-in-time
> recovery, or TLS between the application and Kafka. For production, use RDS,
> DocumentDB, and MSK (or the corresponding Kubernetes operators) before
> carrying real user data.

## 1. Prerequisites

- EKS cluster with the VPC CNI and EBS CSI driver
- AWS Load Balancer Controller installed with IRSA
- ACM public certificate for `api.gaourav.space` in the EKS region
- Four ECR repositories
- EKS node role permission to pull from ECR
  (`AmazonEC2ContainerRegistryReadOnly`) or an `imagePullSecret`
- Argo CD installed in the `argocd` namespace
- GitHub repository access for Argo CD and the GitOps token
- A GitLab project or mirror for running `.gitlab-ci.yml`
- A GitLab Runner with privileged mode enabled for Docker-in-Docker

GitLab CI does not run from a GitHub-only repository. Import the GitHub repo
into GitLab, or configure the GitLab project as a mirror of GitHub. The pipeline
then pushes image tags back to GitHub because Argo CD watches the GitHub repo.

## 2. Create ECR repositories

```bash
export AWS_REGION=ap-south-1
export ECR_NAMESPACE=fitness
./deploy/aws/create-ecr-repositories.sh
```

The pipeline expects this registry:

```text
<ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/fitness/<service>
```

## 3. Create the GitLab AWS role

1. Create an IAM OIDC provider for `https://gitlab.com` if it does not exist.
2. Create an IAM role trusted by GitLab:
   - Use `deploy/aws/gitlab-oidc-trust-policy.json`.
   - Replace the account, group, and project placeholders.
3. Attach `deploy/aws/ecr-push-policy.json` to the role.
4. Set these masked GitLab CI/CD variables:

```text
ECR_REGISTRY=<ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com
ECR_NAMESPACE=fitness
AWS_REGION=ap-south-1
AWS_ROLE_ARN=arn:aws:iam::<ACCOUNT_ID>:role/gitlab-fitness-ci
GITOPS_TOKEN=<GitHub fine-grained PAT with contents read/write>
GITOPS_REPO=GaouravPatil/Fitness-Microservice
GITOPS_BRANCH=main
```

If you cannot use OIDC yet, set `AWS_ACCESS_KEY_ID` and
`AWS_SECRET_ACCESS_KEY` as protected variables instead of `AWS_ROLE_ARN`. Use
OIDC as soon as possible; static keys are a credential-leak risk.

`GITOPS_TOKEN` is optional. Without it, the deploy job is skipped and Argo CD
will not automatically receive new image tags.

## 4. Create the Kubernetes Secret

Create the Secret in each namespace after replacing every placeholder:

```bash
kubectl -n fitness-prod create secret generic fitness-secrets \
  --from-literal=JWT_PRIVATE_KEY='...' \
  --from-literal=JWT_PUBLIC_KEY='...' \
  --from-literal=INTERNAL_API_KEYS='...' \
  --from-literal=INTERNAL_API_KEY='...' \
  --from-literal=DB_USERNAME='fitness' \
  --from-literal=DB_PASSWORD='...' \
  --from-literal=MONGO_ROOT_USERNAME='fitness' \
  --from-literal=MONGO_ROOT_PASSWORD='...' \
  --from-literal=EUREKA_USERNAME='eureka' \
  --from-literal=EUREKA_PASSWORD='...' \
  --from-literal=KAFKA_SASL_USERNAME='fitnessapp' \
  --from-literal=KAFKA_SASL_PASSWORD='...' \
  --from-literal=AI_API_KEY=''
```

Repeat for `fitness-dev` if you deploy the dev environment. The JWT values are
the same base64-encoded PKCS8/X509 values described in the root README.

Use URL-safe database passwords because the MongoDB credentials are embedded in
a URI. The referenced example Secret is `deploy/k8s/base/secrets.example.yaml`;
never commit the real Secret file.

## 5. Bootstrap Argo CD

Install Argo CD if needed:

```bash
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update
helm upgrade --install argocd argo/argo-cd \
  --namespace argocd \
  --create-namespace
```

Apply the project and the ApplicationSet:

```bash
kubectl apply -f deploy/argocd/project.yaml
kubectl apply -f deploy/argocd/applicationset.yaml
```

This creates `fitness-dev` and `fitness-prod` Applications. Remove the `dev`
generator from the ApplicationSet if you only want production. Both manifests
hard-code `https://github.com/GaouravPatil/Fitness-Microservice.git`; update
`repoURL` and the AppProject `sourceRepos` entry if you use a fork.

If the GitHub repository is private, register it with Argo CD:

```bash
argocd repo add https://github.com/GaouravPatil/Fitness-Microservice.git \
  --username <github-user> \
  --password <github-token>
```

## 6. Configure the ALB ingress

1. Create an ACM public certificate for `api.gaourav.space`.
2. Replace `REPLACE_WITH_ACM_CERTIFICATE_ARN` in
   `deploy/k8s/overlays/prod/ingress-patch.yaml`.
3. Ensure the AWS Load Balancer Controller is running.
4. Ensure the public subnets have the tags required by the controller:
   `kubernetes.io/role/elb=1` and the cluster ownership tag.
5. Let Argo CD sync, then point Cloudflare `api.gaourav.space` at the ALB DNS
   name as a proxied CNAME.
6. Set Cloudflare SSL mode to **Full (strict)** and enable **Always Use HTTPS**.

The ALB uses the free ACM public certificate. You do not need a Cloudflare
Origin Certificate. The ALB security group should allow `443` from Cloudflare
only. Do not expose Eureka, Postgres, MongoDB, or Kafka through a public Service.

## 7. Pipeline flow

On every default-branch push:

1. `test:backend` runs `mvn verify` and stores the four executable JARs.
2. `test:frontend` runs lint, typecheck, and the production build.
3. `validate:manifests` renders both Kustomize overlays.
4. `publish:images` builds `deploy/docker/Dockerfile.backend` four times and
   pushes tags using `$CI_COMMIT_SHORT_SHA`.
5. `gitops:update` updates both Kustomize overlays with the new image tags,
   commits to GitHub, and pushes.
6. Argo CD detects the commit and syncs the affected Applications.

The Docker-in-Docker publish job requires a privileged GitLab Runner. If your
runner does not allow privileged containers, replace it with a rootless build
tool such as Kaniko or Buildah.

Images use immutable commit tags. To roll back, revert the GitOps commit or set
the overlay tag to an earlier SHA and push.

## 8. Validation

Render both overlays locally:

```bash
kubectl kustomize deploy/k8s/overlays/dev
kubectl kustomize deploy/k8s/overlays/prod
```

After Argo CD sync:

```bash
kubectl -n argocd get applications
kubectl -n fitness-prod get pods,svc,ingress,pvc
kubectl -n fitness-prod rollout status deployment/eureka
kubectl -n fitness-prod rollout status deployment/userservice
kubectl -n fitness-prod rollout status deployment/activityservice
kubectl -n fitness-prod rollout status deployment/aiservice
```

Then verify the public API path:

```bash
curl -i https://api.gaourav.space/api/users/login
```
