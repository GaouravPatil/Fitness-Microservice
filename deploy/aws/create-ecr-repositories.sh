#!/usr/bin/env bash
set -euo pipefail

: "${AWS_REGION:?Set AWS_REGION}"
: "${ECR_NAMESPACE:=fitness}"

for service in eureka userservice activityservice aiservice; do
  repository="$ECR_NAMESPACE/$service"
  if aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repository" >/dev/null 2>&1; then
    echo "$repository already exists"
  else
    echo "Creating $repository"
    aws ecr create-repository \
      --region "$AWS_REGION" \
      --repository-name "$repository" \
      --image-scanning-configuration scanOnPush=true \
      --encryption-configuration encryptionType=AES256 \
      >/dev/null
  fi
done
