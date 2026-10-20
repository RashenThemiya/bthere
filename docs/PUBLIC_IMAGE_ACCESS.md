# Public image URL access and 403 diagnosis

`POST /api/v1/uploads/images` authenticates the upload, writes an S3 object, and
returns a stable image URL. That URL must be readable without the application
Bearer token. `PutObject` permission does not grant anonymous `GetObject` access.
The application does not change bucket policies, ACLs or CloudFront configuration.

## Current code and local configuration checked on 2026-10-20

- Images use `service-icons/<uuid>.<ext>` for SUPER_ADMIN and
  `providers/<authenticated-user-id>/images/<uuid>.<ext>` for other users,
  including CUSTOMER. Keep this existing customer prefix in access rules.
- Private documents use `providers/<id>/documents/` and `customers/<id>/documents/`.
  They must not be included in public-image access policies.
- Local configuration uses bucket `jobhube`, region `eu-north-1`, public base URL
  `https://jobhube.s3.eu-north-1.amazonaws.com`. This is a direct S3 endpoint,
  not a configured CDN. Production environment values have not been inspected.
- An anonymous HEAD of that bucket's root returned 403 from AmazonS3 with
  `x-amz-bucket-region: eu-north-1`. Bucket-root access is not an image-object
  test: a correctly configured public image bucket can still reject listing/root access.
- No failing object URL/key or usable AWS identity was available in the debugging
  workspace. Exact object existence, live policy and CloudFront state remain unverified.

## Verify the exact returned object

Use the actual upload response key and URL. Do not substitute an example UUID.
Run AWS commands with an existing authorized operator/EC2 role; do not paste keys,
tokens, private document content or credentials into diagnostics.

```bash
IMAGE_BUCKET=jobhube
IMAGE_REGION=eu-north-1
IMAGE_KEY='providers/ACTUAL_USER_ID/images/ACTUAL_UUID.jpg'
IMAGE_URL='https://jobhube.s3.eu-north-1.amazonaws.com/providers/ACTUAL_USER_ID/images/ACTUAL_UUID.jpg'

# Unsigned HEAD and GET. Inspect status, region, redirect and cache headers.
curl --head --max-time 20 "$IMAGE_URL"
curl --range 0-0 --dump-header - --output /dev/null --max-time 20 "$IMAGE_URL"

# Authenticated object metadata; does not download the photo.
aws s3api head-object --bucket "$IMAGE_BUCKET" --key "$IMAGE_KEY" \
  --region "$IMAGE_REGION" \
  --query '{ContentType:ContentType,ContentLength:ContentLength,LastModified:LastModified,Encryption:ServerSideEncryption,KmsKey:SSEKMSKeyId}'
```

An authenticated successful HEAD confirms the exact key exists and is readable
by that role. An anonymous object 403 alone does not prove existence: without
ListBucket permission, S3 can also return 403 for a missing key. Compare bucket,
region, exact key/case and configured public-base path before changing permissions.

## Inspect S3 access configuration (read-only)

```bash
aws s3api get-bucket-location --bucket "$IMAGE_BUCKET"
aws s3api get-public-access-block --bucket "$IMAGE_BUCKET"
aws s3api get-bucket-policy-status --bucket "$IMAGE_BUCKET"
aws s3api get-bucket-policy --bucket "$IMAGE_BUCKET" --query Policy --output text
aws s3api get-bucket-ownership-controls --bucket "$IMAGE_BUCKET"
aws s3api get-bucket-encryption --bucket "$IMAGE_BUCKET"

IMAGE_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws s3control get-public-access-block --account-id "$IMAGE_ACCOUNT_ID"
```

AccessDenied on a management read is inconclusive; it does not mean the setting
is absent. Check applicable account/organization restrictions and explicit denies.
An ACL-disabled bucket should be managed with bucket/CDN policies; adding
`public-read` to PutObject is not a general fix. SSE-KMS objects require a delivery
configuration compatible with their encryption; do not change encryption blindly.

## Choose the configured delivery path

For direct public S3 delivery, an authorized operator must verify that public
GetObject is permitted for the intended image prefixes and is not blocked by
applicable Public Access Block or explicit deny rules. An image-only bucket policy
statement would scope resources to:

```json
{
  "Effect": "Allow",
  "Principal": "*",
  "Action": "s3:GetObject",
  "Resource": [
    "arn:aws:s3:::jobhube/service-icons/*",
    "arn:aws:s3:::jobhube/providers/*/images/*"
  ]
}
```

This is a review template, not an applied policy or a complete replacement policy.
Merge only after inspecting existing statements and the intended architecture.
Do not grant public access to `jobhube/*`, `providers/*` or private document paths.

For CloudFront delivery, keep S3 private and allow the specific distribution's
OAC/OAI to read only public-image resources. Viewers use the CloudFront/custom
hostname; a private direct S3 URL returning 403 is expected in this design.
Set AWS_S3_PUBLIC_BASE_URL to the verified public CDN base and ensure the origin
path plus returned key resolves to the same S3 object. Inspect distribution
origin, origin access, behavior paths, viewer restrictions and cached 403 responses.
Changing public-base-url affects newly generated URLs; existing profile/media
records containing direct S3 URLs need a separately planned migration if moving to CDN.

S3 CORS does not grant GetObject permissions. An application JWT is not an S3
credential. A temporary presigned URL is not a replacement for the stable public
URL stored as profilePhoto or icon metadata.

## Acceptance check

For the actual failing key, confirm authenticated HEAD exists, then anonymous
GET of the returned public URL is 200 (206 is valid for the range probe), with
the expected image Content-Type. Repeat for a newly uploaded customer image.
Confirm identity/document paths remain inaccessible anonymously. Record whether
the fix was policy, URL/origin mapping, missing object, encryption or cache state.

AWS references:
[403 troubleshooting](https://docs.aws.amazon.com/AmazonS3/latest/userguide/troubleshoot-403-errors.html),
[GetObject missing-key behavior](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html),
[S3 public access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/granting-public-access.html),
[CloudFront origin access](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html).
