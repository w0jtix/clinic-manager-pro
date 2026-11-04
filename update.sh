#!/bin/bash
set -e

if [ ! -f ./nginx/dhparam.pem ]; then
  echo "SSL not initialized. Run ./init-ssl.sh first."
  exit 1
fi

if [ ! -f .env ]; then
  echo ".env not found. See README for required variables."
  exit 1
fi

if ! grep -q "^KSEF_ENCRYPTION_KEY=" .env || [ -z "$(grep "^KSEF_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)" ]; then
  echo ""
  echo "==> KSEF_ENCRYPTION_KEY is missing in .env"
  echo "    This AES-256 key encrypts KSeF auth tokens stored in the database."
  echo "    WARNING: if KSeF credentials were previously saved with a different key,"
  echo "    they will become unreadable and must be re-entered via PUT /api/ksef/credentials."
  echo ""
  read -p "Generate a new key and add it to .env? [y/N]: " ANSWER
  if [[ "$ANSWER" =~ ^[Yy]$ ]]; then
    KEY=$(openssl rand -base64 32)
    if grep -q "^KSEF_ENCRYPTION_KEY=" .env; then
      sed -i "s|^KSEF_ENCRYPTION_KEY=.*|KSEF_ENCRYPTION_KEY=${KEY}|" .env
    else
      echo "KSEF_ENCRYPTION_KEY=${KEY}" >> .env
    fi
    echo "New KSEF_ENCRYPTION_KEY written to .env"
  else
    echo "Aborted. Set KSEF_ENCRYPTION_KEY manually in .env and re-run ./update.sh"
    exit 1
  fi
fi

if ! grep -q "^CARD_ENCRYPTION_KEY=" .env || [ -z "$(grep "^CARD_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)" ]; then
  echo ""
  echo "==> CARD_ENCRYPTION_KEY is missing in .env"
  echo "    This AES-256 key encrypts client card questionnaire answers stored in the database."
  echo "    WARNING: if answers were saved with a different key, they will become unreadable."
  echo "    If this server already has questionnaire answers, restore the original key from your backup instead."
  echo ""
  read -p "Generate a new key and add it to .env? [y/N]: " ANSWER
  if [[ "$ANSWER" =~ ^[Yy]$ ]]; then
    KEY=$(openssl rand -base64 32)
    if grep -q "^CARD_ENCRYPTION_KEY=" .env; then
      sed -i "s|^CARD_ENCRYPTION_KEY=.*|CARD_ENCRYPTION_KEY=${KEY}|" .env
    else
      echo "CARD_ENCRYPTION_KEY=${KEY}" >> .env
    fi
    echo "New CARD_ENCRYPTION_KEY written to .env"
    echo "Copy CARD_ENCRYPTION_KEY from .env to a safe place outside the server (password manager)."
  else
    echo "Aborted. Set CARD_ENCRYPTION_KEY manually in .env and re-run ./update.sh"
    exit 1
  fi
fi

if ! grep -q "^MFA_ENCRYPTION_KEY=" .env || [ -z "$(grep "^MFA_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)" ]; then
  echo "==> MFA_ENCRYPTION_KEY missing in .env - generating a fresh AES-256 key"
  echo "    It encrypts 2FA secrets. If accounts already had 2FA with another key,"
  echo "    reset them with ./scripts/reset-2fa.sh <login>."
  KEY=$(openssl rand -base64 32)
  if grep -q "^MFA_ENCRYPTION_KEY=" .env; then
    sed -i "s|^MFA_ENCRYPTION_KEY=.*|MFA_ENCRYPTION_KEY=${KEY}|" .env
  else
    echo "MFA_ENCRYPTION_KEY=${KEY}" >> .env
  fi
fi

if ! grep -q "^JWT_SECRET=" .env ||[ -z "$(grep "^JWT_SECRET=" .env | cut -d'=' -f2-)" ]; then
  echo "==> JWT_SECRET missing in .env — generating a fresh secret"
  SECRET=$(openssl rand -base64 48)
  if grep -q "^JWT_SECRET=" .env; then
    sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${SECRET}|" .env
  else
    echo "JWT_SECRET=${SECRET}" >> .env
  fi
fi

if ! grep -q "^GITHUB_USERNAME=" .env || [ -z "$(grep "^GITHUB_USERNAME=" .env | cut -d'=' -f2-)" ]; then
  echo ""
  echo "==> GITHUB_USERNAME is missing in .env"
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
  echo "GITHUB_USERNAME written to .env"
fi

if ! grep -q "^GITHUB_TOKEN=" .env || [ -z "$(grep "^GITHUB_TOKEN=" .env | cut -d'=' -f2-)" ]; then
  echo ""
  echo "==> GITHUB_TOKEN is missing in .env"
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
  echo "GITHUB_TOKEN written to .env"
fi

if ! grep -q "^MAIL_USERNAME=" .env || [ -z "$(grep "^MAIL_USERNAME=" .env | cut -d'=' -f2-)" ]    || ! grep -q "^MAIL_PASSWORD=" .env || [ -z "$(grep "^MAIL_PASSWORD=" .env | cut -d'=' -f2-)" ]; then
  echo ""
  echo "==> MAIL_USERNAME / MAIL_PASSWORD missing in .env"
  echo "    Used to email named invoices to clients (SMTP, defaults to smtp.gmail.com:587)."
  echo "    For Gmail this must be an App Password, not the account password."
  echo "    Leave empty to skip - the app will run, but sending invoices will fail."
  if [ -t 0 ]; then
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
  else
    echo "    Non-interactive run - skipping the prompt and continuing."
  fi
  echo ""
fi

if [ -f ./backup/setup-backup.sh ]; then
  source ./backup/setup-backup.sh
  backup_configure_env || true
fi

echo "Pulling latest code..."
git pull

echo "Building and starting containers..."
export GITHUB_USERNAME="$(grep "^GITHUB_USERNAME=" .env | cut -d'=' -f2-)"
export GITHUB_TOKEN="$(grep "^GITHUB_TOKEN=" .env | cut -d'=' -f2-)"
source ./scripts/first-admin.sh
docker compose up --build -d

if declare -F backup_finalize >/dev/null; then
  backup_finalize || true
fi

echo "Pruning old images..."
docker system prune -a -f

echo "Done!"
