# Deployment

Production setup: a single server running Docker Compose with PostgreSQL, Redis, the Spring Boot backend, Nginx serving the frontend, Certbot and borgmatic.

## Build prerequisite

The KSeF module depends on `pl.akmf.ksef-sdk:ksef-client`, published on GitHub Packages. GitHub requires authentication for that registry **even for public packages**, so a build without credentials fails with `401 Unauthorized` while resolving dependencies.

Add a server entry to your `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github-ksef</id>
    <username>YOUR_GITHUB_LOGIN</username>
    <password>YOUR_PAT_WITH_read:packages</password>
  </server>
</servers>
```

The token only needs the `read:packages` scope.

## First-time deployment

1. Install Docker.
2. Clone the repository.
3. Create a `.env` file in the project root:
   ```env
   POSTGRES_PASSWORD=your_strong_password
   JWT_SECRET=             # generate with: openssl rand -base64 64
   KSEF_ENCRYPTION_KEY=    # generate with: openssl rand -base64 32  (AES-256 key for KSeF token storage)
   CARD_ENCRYPTION_KEY=    # generate with: openssl rand -base64 32  (AES-256 key for client card data - keep a copy outside the server)
   MFA_ENCRYPTION_KEY=     # generate with: openssl rand -base64 32  (AES-256 key for 2FA secrets)
   CORS_ALLOWED_ORIGINS=https://clinic.example.com
   GITHUB_USERNAME=        # GitHub login (the backend image build pulls ksef-client)
   GITHUB_TOKEN=           # GitHub PAT with read:packages scope

   MAIL_USERNAME=          # optional, SMTP account used to send invoices
   MAIL_PASSWORD=          # optional, SMTP password (for Gmail an App Password)

   BORG_REPO=              # optional, backup repository, e.g. ssh://uXXXX@uXXXX.your-storagebox.de:23/./clinicmanager-borg
   HEALTHCHECKS_URL=       # optional, healthchecks.io ping URL
   ```
4. Run the SSL initialization script (only once):
   ```bash
   ./init-ssl.sh
   ```
5. Open `https://clinic.example.com` and log in with the administrator account created in step 4 during the initialization script run. Enabling 2FA for it is recommended.

## Updating the app

```bash
git pull && ./update.sh
```

## Notes

- SSL certificates are renewed automatically by the Certbot container (checked every 12h); Nginx reloads every 6h to pick up new certificates.
- PostgreSQL and Redis listen on `127.0.0.1` only; reach the database through an SSH tunnel (host port `5433`).
- CORS allowed origins are configured via `CORS_ALLOWED_ORIGINS` in `.env`.
- `init-ssl.sh` must be run before `update.sh` on a fresh server.
- Schema changes go through Liquibase changelogs only.
- Losing `CARD_ENCRYPTION_KEY` means the client card data can no longer be read. Never run `docker compose down -v` - it deletes the database and card file volumes.
- A lost 2FA device on an admin account is reset on the server with `./scripts/reset-2fa.sh <login>`.
