#!/bin/sh
set -eu
set -f

public_buckets=${MINIO_PUBLIC_BUCKETS-}
private_buckets=${MINIO_PRIVATE_BUCKETS-}

validate_bucket() {
  case "$1" in
    ''|*[!a-z0-9.-]*|.*|*.|-*|*-) printf 'Invalid bucket name: %s\n' "$1" >&2; exit 1 ;;
  esac
  if [ "${#1}" -lt 3 ] || [ "${#1}" -gt 63 ]; then
    printf 'Bucket name must contain 3 to 63 characters: %s\n' "$1" >&2
    exit 1
  fi
}

# Validate all names and permissions before changing any bucket.
IFS=,
for bucket in $public_buckets; do
  validate_bucket "$bucket"
done
for bucket in $private_buckets; do
  validate_bucket "$bucket"
  case ",$public_buckets," in
    *",$bucket,"*) printf 'Bucket cannot be both public and private: %s\n' "$bucket" >&2; exit 1 ;;
  esac
done

mc alias set local http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"

for bucket in $public_buckets; do
  mc mb --ignore-existing "local/$bucket"
  # Public objects can be fetched; anonymous bucket listing stays disabled.
  cat > /tmp/public-object-policy.json <<EOF
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::$bucket/*"]}]}
EOF
  mc anonymous set-json /tmp/public-object-policy.json "local/$bucket"
done

for bucket in $private_buckets; do
  mc mb --ignore-existing "local/$bucket"
  mc anonymous set none "local/$bucket"
done
