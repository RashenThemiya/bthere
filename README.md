# JobHub

Spring Boot REST API starter configured for MySQL.

## Requirements

- Java 17 or newer
- Maven 3.6 or newer
- MySQL 8

## Database configuration

The default connection is:

- URL: `jdbc:mysql://localhost:3306/jobhub`
- Username: `root`
- Password: empty

Override it with environment variables when needed:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/jobhub?createDatabaseIfNotExist=true&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-password"
```

The JDBC URL includes `createDatabaseIfNotExist=true`, so a MySQL user with sufficient permission can create the `jobhub` database automatically.

### Initial super administrator

On the first successful database startup, JobHub creates the default market, the `SUPER_ADMIN` role, and one local super-admin account. Configure its credentials before starting the application:

```powershell
$env:SUPER_ADMIN_USERNAME = "superadmin"
$env:SUPER_ADMIN_EMAIL = "admin@example.com"
$env:SUPER_ADMIN_PASSWORD = "replace-with-a-strong-password"
$env:JWT_SECRET = "replace-with-a-random-secret-at-least-32-characters"
```

The password is stored as a BCrypt hash. Startup fails when the bootstrap is enabled and `SUPER_ADMIN_PASSWORD` is empty. Existing records are reused, so later restarts do not create duplicate accounts. After the first account has been created, bootstrap can be disabled with `SUPER_ADMIN_BOOTSTRAP_ENABLED=false`.

Markets hold country-specific configuration without duplicating global users, roles, or service definitions. A super administrator can add another country using `POST /api/v1/markets`. Each market defines its ISO country code, default currency, IANA timezone, locale, and phone country code.

### Authentication API

Frontend developers should use the complete [Authentication API documentation](docs/AUTH_API.md).
Profile and provider onboarding endpoints are documented in [Profile API documentation](docs/PROFILE_API.md).
Administration endpoints are documented in [Administration API documentation](docs/ADMIN_API.md).

Log in using a username or email as the identifier:

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "identifier": "superadmin",
  "password": "your-password"
}
```

Use the returned access token to retrieve the current account:

```http
GET /api/v1/users/me
Authorization: Bearer <access-token>
```

Google login and registration use a Google ID token obtained by the frontend. Configure the OAuth web client ID as `GOOGLE_CLIENT_ID`, then exchange the token with JobHub:

```http
POST /api/v1/auth/google
Content-Type: application/json

{
  "idToken": "<google-id-token>",
  "accountType": "CUSTOMER"
}
```

For a new Google account, `accountType` may be `CUSTOMER` or `SERVICE_PROVIDER`.
Existing linked accounts keep their current roles. JobHub verifies Google's signature,
issuer, expiration, audience, and verified-email claim before creating or linking the account.

Google and phone OTP authentication are restricted to accounts whose active roles are only `CUSTOMER` and/or `SERVICE_PROVIDER`. Administrative accounts must use username/password login and cannot link Google or use phone OTP.

Email/password registration creates a `PENDING` account and sends a six-digit verification code through Amazon SES. Verify it with:

```http
POST /api/v1/auth/email/verify
Content-Type: application/json

{
  "email": "user@example.com",
  "otp": "123456"
}
```

Resend with `POST /api/v1/auth/email/resend` and an `email` field. Configure a verified SES sender using `AWS_SES_FROM_EMAIL`. The EC2 IAM role requires `ses:SendEmail` in addition to `sns:Publish`. While SES is in its sandbox, recipient addresses must also be verified.

### Phone OTP authentication with AWS SNS

Request an OTP for registration or login:

```http
POST /api/v1/auth/otp/request
Content-Type: application/json

{
  "phoneNumber": "+94771234567",
  "accountType": "CUSTOMER"
}
```

Verify a registration OTP and create the account:

```http
POST /api/v1/auth/otp/verify
Content-Type: application/json

{
  "phoneNumber": "+94771234567",
  "otp": "123456",
  "accountType": "CUSTOMER"
}
```

The backend automatically registers a new phone number or logs in an existing
matching account. Successful verification returns the normal JobHub access and
refresh tokens.

Configure `OTP_HASH_SECRET`, `AWS_REGION`, and optionally `AWS_SNS_SENDER_ID`. On EC2, attach an instance IAM role that permits `sns:Publish`; do not store permanent AWS keys in the repository. The AWS SDK uses its default credential chain, including EC2 instance-role credentials. New AWS SNS SMS accounts may initially be in the SMS sandbox, where destination numbers must be verified before messages can be sent.

### Database schema

Hibernate creates and updates the MySQL schema directly from the JPA classes under `src/main/java/com/jobhub/entity`. The default setting is `spring.jpa.hibernate.ddl-auto=update`.

## Run

```powershell
mvn spring-boot:run
```

## Test

```powershell
mvn test
```

## Docker

Build the application image:

```powershell
docker build -t jobhub .
```

Run it while connecting to a MySQL server available from the host machine:

```powershell
docker run --rm -p 8080:8080 `
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/jobhub?createDatabaseIfNotExist=true&serverTimezone=UTC" `
  -e DB_USERNAME="root" `
  -e DB_PASSWORD="your-password" `
  jobhub
```

## Docker Hub publishing

The GitHub Actions workflow in `.github/workflows/docker-publish.yml` builds and publishes the image whenever code is pushed to the `main` branch. It publishes these tags:

- `<dockerhub-username>/jobhub:latest`
- `<dockerhub-username>/jobhub:<git-commit-sha>`

Create a `jobhub` repository on Docker Hub, then add these GitHub repository secrets under **Settings > Secrets and variables > Actions**:

- `DOCKERHUB_USERNAME`: your Docker Hub username
- `DOCKERHUB_TOKEN`: a Docker Hub personal access token

You can also start the workflow manually from the repository's **Actions** tab.

## Docker Compose

Copy the example environment file and change its passwords:

```powershell
Copy-Item .env.example .env
```

The Compose configuration expects the existing `distribution-system_default`
Docker network and `distribution-system-mysql-1` MySQL container. Start JobHub:

```powershell
docker compose up -d --build
```

View logs or stop the services:

```powershell
docker compose logs -f jobhub
docker compose down
```

## AWS EC2 deployment

After publishing the image, the same workflow connects to an EC2 instance over SSH, pulls `jobhub:latest`, and replaces the running `jobhub` container.

Add these additional GitHub Actions secrets:

- `AWS_HOST`: the EC2 public IP address or DNS name
- `AWS_PORT`: the SSH port; this is optional and defaults to `22`
- `AWS_USERNAME`: the SSH user, commonly `ubuntu` or `ec2-user`
- `AWS_SSH_KEY`: the complete private SSH key, including its BEGIN and END lines
- `APP_ENV`: the complete multiline production application environment

The workflow creates the deployment directory in the SSH user's home directory:

```bash
cd ~/jobhub
docker compose --env-file image.env ps
docker compose --env-file image.env logs -f jobhub
```

The environment file should contain the production database connection:

```dotenv
DB_URL=jdbc:mysql://your-mysql-host:3306/jobhub?createDatabaseIfNotExist=true&serverTimezone=UTC
DB_USERNAME=your-database-user
DB_PASSWORD=your-database-password
SUPER_ADMIN_USERNAME=superadmin
SUPER_ADMIN_EMAIL=admin@example.com
SUPER_ADMIN_PASSWORD=replace-with-a-strong-password
JWT_SECRET=replace-with-a-random-secret-at-least-32-characters
```

The EC2 security group must allow inbound SSH on port `22` and application traffic on port `8083` from the required clients. If the Docker Hub repository is private, log in to Docker Hub once on the EC2 instance before deploying:

```bash
docker login
```
