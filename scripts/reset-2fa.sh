#!/bin/bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")/.."

DB_NAME="clinicmanager"
SEP=$'\x1f'

die() {
  echo "" >&2
  echo "BŁĄD: $*" >&2
  exit 1
}

usage() {
  echo "Użycie: ./scripts/reset-2fa.sh <login>"
}

psql_db() {
  docker compose exec -T postgres psql -U postgres -d "$DB_NAME" --no-password -X -q -v ON_ERROR_STOP=1 "$@"
}

if [ "$#" -ne 1 ] || [ -z "$1" ]; then
  usage
  exit 1
fi
LOGIN="$1"

if ! docker compose ps --status running --services 2>/dev/null | grep -qx postgres; then
  die "Kontener postgres nie działa. Uruchom: docker compose up -d postgres"
fi

INFO="$(psql_db -t -A -F "$SEP" -v login="$LOGIN" <<'SQL'
SELECT u.id,
       u.username,
       COALESCE((SELECT string_agg(r.name, ', ' ORDER BY r.name)
                 FROM users_roles ur JOIN roles r ON r.id = ur.role_id
                 WHERE ur.user_id = u.id), '-'),
       COALESCE(to_char(u.mfa_enabled_at, 'YYYY-MM-DD HH24:MI'), ''),
       CASE WHEN u.mfa_secret_enc IS NOT NULL THEN 1 ELSE 0 END,
       (SELECT count(*) FROM user_backup_codes c
        WHERE c.user_id = u.id AND c.used_at IS NULL),
       (SELECT count(*) FROM trusted_devices d
        WHERE d.user_id = u.id AND d.kind = 'MFA_REMEMBER' AND d.revoked_at IS NULL
          AND d.expires_at > (now() AT TIME ZONE 'Europe/Warsaw'))
FROM users u
WHERE u.username = :'login';
SQL
)"

if [ -z "$INFO" ]; then
  echo "Nie znaleziono użytkownika o loginie: $LOGIN"
  exit 1
fi

IFS="$SEP" read -r USER_ID USERNAME ROLES ENABLED_AT HAS_SECRET BACKUP_CODES DEVICES <<< "$INFO"

if ! [[ "$USER_ID" =~ ^[0-9]+$ ]]; then
  die "Nieoczekiwana odpowiedź bazy danych."
fi

echo ""
echo "Konto:                  $USERNAME (id $USER_ID)"
echo "Role:                   $ROLES"
if [ -n "$ENABLED_AT" ]; then
  echo "Weryfikacja 2FA:        włączona od $ENABLED_AT"
elif [ "$HAS_SECRET" = "1" ]; then
  echo "Weryfikacja 2FA:        niepełna konfiguracja"
else
  echo "Weryfikacja 2FA:        wyłączona"
fi
echo "Kody zapasowe:          $BACKUP_CODES niewykorzystanych"
echo "Zapamiętane urządzenia: $DEVICES aktywnych"
echo ""

if [ -z "$ENABLED_AT" ] && [ "$HAS_SECRET" = "0" ] && [ "$BACKUP_CODES" = "0" ] && [ "$DEVICES" = "0" ]; then
  echo "Użytkownik nie ma włączonej weryfikacji dwuetapowej. Nie ma czego resetować."
  exit 0
fi

if [ ! -t 0 ]; then
  die "Skrypt wymaga terminala do potwierdzenia resetu."
fi

read -r -p "Aby zresetować weryfikację dwuetapową, wpisz login konta: " CONFIRM
if [ "$CONFIRM" != "$USERNAME" ]; then
  die "Wpisany login nie zgadza się z kontem. Przerwano, nic nie zmieniono."
fi

psql_db -v uid="$USER_ID" -v login="$USERNAME" <<'SQL'
BEGIN;
UPDATE users
SET mfa_secret_enc = NULL,
    mfa_enabled_at = NULL
WHERE id = :uid;
DELETE FROM user_backup_codes
WHERE user_id = :uid;
UPDATE trusted_devices
SET revoked_at = (now() AT TIME ZONE 'Europe/Warsaw')
WHERE user_id = :uid
  AND kind = 'MFA_REMEMBER'
  AND revoked_at IS NULL;
INSERT INTO audit_log (entity_type, entity_id, entity_key_trait, action, performed_by, "timestamp",
                       old_value, new_value, changed_fields, ip_address, session_id, device_type, browser_name)
VALUES ('User-2FA-Reset', :uid, :'login', 'SECURITY', 'SYSTEM', (now() AT TIME ZONE 'Europe/Warsaw'),
        NULL, json_build_object('username', :'login', 'by', 'serwer')::text, NULL,
        'UNKNOWN', 'UNKNOWN', 'UNKNOWN', 'Unknown');
COMMIT;
SQL

if ! docker compose exec -T redis redis-cli DEL \
    "mfa:attempts:user:$USER_ID" "mfa:block:user:$USER_ID" \
    "mfa:last-step:user:$USER_ID" "mfa:setup:user:$USER_ID" >/dev/null 2>&1; then
  echo "UWAGA: nie udało się wyczyścić blokady prób w Redis - wygaśnie sama najpóźniej po 15 min."
fi

echo ""
echo "Zresetowano weryfikację dwuetapową użytkownika $USERNAME."
echo "Użytkownik zaloguje się samym hasłem i może ponownie włączyć 2FA w ustawieniach."
