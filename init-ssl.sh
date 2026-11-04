#!/bin/bash
set -e

DOMAIN="clinic.example.com"

if [ ! -f .env ]; then
  echo "ERROR: .env file not found in project root. See README for required variables."
  exit 1
fi

BACKUP_RESTORE_MODE=0
if [ -f ./backup/setup-backup.sh ]; then
  source ./backup/setup-backup.sh
  backup_ask_restore_mode || exit 1
fi

if ! grep -q "^KSEF_ENCRYPTION_KEY=" .env; then
  echo "=== KSEF_ENCRYPTION_KEY missing in .env — generating a fresh AES-256 key ==="
  echo "KSEF_ENCRYPTION_KEY=$(openssl rand -base64 32)" >> .env
elif [ -z "$(grep "^KSEF_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)" ]; then
  echo "=== KSEF_ENCRYPTION_KEY empty in .env — generating a fresh AES-256 key ==="
  KEY=$(openssl rand -base64 32)
  sed -i "s|^KSEF_ENCRYPTION_KEY=.*|KSEF_ENCRYPTION_KEY=${KEY}|" .env
fi

if ! grep -q "^CARD_ENCRYPTION_KEY=" .env; then
  echo "=== CARD_ENCRYPTION_KEY missing in .env - generating a fresh AES-256 key ==="
  echo "CARD_ENCRYPTION_KEY=$(openssl rand -base64 32)" >> .env
elif [ -z "$(grep "^CARD_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)" ]; then
  echo "=== CARD_ENCRYPTION_KEY empty in .env - generating a fresh AES-256 key ==="
  KEY=$(openssl rand -base64 32)
  sed -i "s|^CARD_ENCRYPTION_KEY=.*|CARD_ENCRYPTION_KEY=${KEY}|" .env
fi
echo "    IMPORTANT: back up CARD_ENCRYPTION_KEY outside this server - losing it makes client questionnaires unreadable."

if ! grep -q "^MFA_ENCRYPTION_KEY=" .env; then
  echo "=== MFA_ENCRYPTION_KEY missing in .env - generating a fresh AES-256 key ==="
  echo "MFA_ENCRYPTION_KEY=$(openssl rand -base64 32)" >> .env
elif [ -z "$(grep "^MFA_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)" ]; then
  echo "=== MFA_ENCRYPTION_KEY empty in .env - generating a fresh AES-256 key ==="
  KEY=$(openssl rand -base64 32)
  sed -i "s|^MFA_ENCRYPTION_KEY=.*|MFA_ENCRYPTION_KEY=${KEY}|" .env
fi

if ! grep -q "^JWT_SECRET=" .env; then
  echo "=== JWT_SECRET missing in .env — generating a fresh secret ==="
  echo "JWT_SECRET=$(openssl rand -base64 48)" >> .env
elif [ -z "$(grep "^JWT_SECRET=" .env | cut -d'=' -f2-)" ]; then
  echo "=== JWT_SECRET empty in .env — generating a fresh secret ==="
  SECRET=$(openssl rand -base64 48)
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${SECRET}|" .env
fi

if ! grep -q "^GITHUB_USERNAME=" .env || [ -z "$(grep "^GITHUB_USERNAME=" .env | cut -d'=' -f2-)" ]; then
  echo ""
  echo "=== GITHUB_USERNAME missing in .env ==="
  echo "    Needed to authenticate with GitHub Packages while building the backend"
  echo "    (the ksef-client dependency is hosted there)."
  read -p "Enter your GitHub username: " GH_USER
  if [ -z "$GH_USER" ]; then
    echo "Aborted. GITHUB_USERNAME is required to build the backend."
    exit 1
  fi
  if grep -q "^GITHUB_USERNAME=" .env; then
    sed -i "s|^GITHUB_USERNAME=.*|GITHUB_USERNAME=${GH_USER}|" .env
  else
    echo "GITHUB_USERNAME=${GH_USER}" >> .env
  fi
fi

if ! grep -q "^GITHUB_TOKEN=" .env || [ -z "$(grep "^GITHUB_TOKEN=" .env | cut -d'=' -f2-)" ]; then
  echo ""
  echo "=== GITHUB_TOKEN missing in .env ==="
  echo "    A GitHub Personal Access Token with the 'read:packages' scope is required"
  echo "    to download the ksef-client dependency during the backend build."
  read -s -p "Enter your GitHub token (input hidden): " GH_TOKEN
  echo ""
  if [ -z "$GH_TOKEN" ]; then
    echo "Aborted. GITHUB_TOKEN is required to build the backend."
    exit 1
  fi
  if grep -q "^GITHUB_TOKEN=" .env; then
    sed -i "s|^GITHUB_TOKEN=.*|GITHUB_TOKEN=${GH_TOKEN}|" .env
  else
    echo "GITHUB_TOKEN=${GH_TOKEN}" >> .env
  fi
fi

if ! grep -q "^MAIL_USERNAME=" .env || [ -z "$(grep "^MAIL_USERNAME=" .env | cut -d'=' -f2-)" ]    || ! grep -q "^MAIL_PASSWORD=" .env || [ -z "$(grep "^MAIL_PASSWORD=" .env | cut -d'=' -f2-)" ]; then
  echo ""
  echo "=== MAIL_USERNAME / MAIL_PASSWORD missing in .env ==="
  echo "    Used to email named invoices to clients (SMTP, defaults to smtp.gmail.com:587)."
  echo "    For Gmail this must be an App Password, not the account password."
  echo "    Leave empty to skip - the app will run, but sending invoices will fail."
  read -p "SMTP username (email address), or press Enter to skip: " MAIL_USER
  if [ -n "$MAIL_USER" ]; then
    read -s -p "SMTP password (input hidden): " MAIL_PASS
    echo ""
    if grep -q "^MAIL_USERNAME=" .env; then
      sed -i "s|^MAIL_USERNAME=.*|MAIL_USERNAME=${MAIL_USER}|" .env
    else
      echo "MAIL_USERNAME=${MAIL_USER}" >> .env
    fi
    if grep -q "^MAIL_PASSWORD=" .env; then
      sed -i "s|^MAIL_PASSWORD=.*|MAIL_PASSWORD=${MAIL_PASS}|" .env
    else
      echo "MAIL_PASSWORD=${MAIL_PASS}" >> .env
    fi
    echo "MAIL_USERNAME / MAIL_PASSWORD written to .env"
  else
    echo "Skipped. Set MAIL_USERNAME and MAIL_PASSWORD in .env later to enable invoice emails."
  fi
fi

if declare -F backup_configure_env >/dev/null; then
  backup_configure_env || true
  export GITHUB_USERNAME="$(grep "^GITHUB_USERNAME=" .env | cut -d'=' -f2-)"
  export GITHUB_TOKEN="$(grep "^GITHUB_TOKEN=" .env | cut -d'=' -f2-)"
  if ! backup_restore_verify; then
    echo "Przerwano. Klucze i BORG_REPO zostały w .env - uruchom ./init-ssl.sh ponownie."
    exit 1
  fi
fi

read -p "Enter your email for Let's Encrypt notifications: " EMAIL

echo "=== Generating SSL certificate (standalone) ==="
docker run --rm -p 80:80 \
  -v clinicmanagerpro_letsencrypt:/etc/letsencrypt \
  certbot/certbot certonly --standalone \
  --email $EMAIL \
  --agree-tos \
  --no-eff-email \
  -d $DOMAIN

echo "=== Generating dhparam (this may take a moment) ==="
if [ ! -f ./nginx/dhparam.pem ]; then
  openssl dhparam -out ./nginx/dhparam.pem 2048
fi

echo "=== Starting full stack with SSL ==="
export GITHUB_USERNAME="$(grep "^GITHUB_USERNAME=" .env | cut -d'=' -f2-)"
export GITHUB_TOKEN="$(grep "^GITHUB_TOKEN=" .env | cut -d'=' -f2-)"
source ./scripts/first-admin.sh
docker compose up -d

echo "=== Switching renewal method from standalone to webroot ==="
RENEWAL_CONF="/etc/letsencrypt/renewal/${DOMAIN}.conf"
docker run --rm --entrypoint /bin/sh \
  -v clinicmanagerpro_letsencrypt:/etc/letsencrypt \
  certbot/certbot -c "
    sed -i 's|^authenticator = standalone|authenticator = webroot|' ${RENEWAL_CONF}
    grep -q '^webroot_path' ${RENEWAL_CONF} || sed -i '/^authenticator = webroot/a webroot_path = /var/www/certbot,' ${RENEWAL_CONF}
    grep -q '\[\[webroot_map\]\]' ${RENEWAL_CONF} || printf '\n[[webroot_map]]\n${DOMAIN} = /var/www/certbot\n' >> ${RENEWAL_CONF}
  "

echo "=== Validating webroot renewal (dry-run against staging) ==="
sleep 5
docker compose run --rm --entrypoint certbot certbot renew --dry-run \
  || echo "WARN: renewal dry-run failed — check that nginx serves /.well-known/acme-challenge/ before the next renewal."

if declare -F backup_finalize >/dev/null; then
  backup_finalize || true
fi

echo "=== DONE! App available at https://$DOMAIN ==="
