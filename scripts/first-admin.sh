#!/bin/bash

FIRST_ADMIN_MIN_PASSWORD_LENGTH=12

first_admin_user_count() {
  docker compose up -d postgres >/dev/null
  for _ in $(seq 1 30); do
    if docker compose exec -T postgres pg_isready -U postgres -d clinicmanager >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  docker compose exec -T postgres psql -U postgres -d clinicmanager -tAc "SELECT count(*) FROM users" 2>/dev/null | tr -d '[:space:]' || true
}

first_admin_prompt() {
  local username password confirm answer

  while true; do
    read -r -p "Login pierwszego administratora: " username
    username="$(echo "$username" | xargs)"
    if [ -z "$username" ]; then
      echo "Login nie może być pusty."
      continue
    fi
    if [ "$username" = "admin" ]; then
      read -r -p "Login \"admin\" jest łatwy do odgadnięcia. Użyć mimo to? [y/N]: " answer
      [ "$answer" = "y" ] || [ "$answer" = "Y" ] || continue
    fi
    break
  done

  while true; do
    read -r -s -p "Hasło (min. ${FIRST_ADMIN_MIN_PASSWORD_LENGTH} znaków): " password
    echo
    if [ "${#password}" -lt "$FIRST_ADMIN_MIN_PASSWORD_LENGTH" ]; then
      echo "Hasło jest za krótkie."
      continue
    fi
    read -r -s -p "Powtórz hasło: " confirm
    echo
    if [ "$password" != "$confirm" ]; then
      echo "Hasła się różnią."
      continue
    fi
    break
  done

  export ADMIN_INITIAL_USERNAME="$username"
  export ADMIN_INITIAL_PASSWORD="$password"
}

FIRST_ADMIN_COUNT="$(first_admin_user_count)"
if [ -z "$FIRST_ADMIN_COUNT" ] || [ "$FIRST_ADMIN_COUNT" = "0" ]; then
  echo "==> Baza nie ma jeszcze użytkowników - tworzenie pierwszego administratora"
  if [ ! -t 0 ]; then
    echo "Brak terminala do wpisania danych. Uruchom skrypt interaktywnie."
    exit 1
  fi
  first_admin_prompt
  echo "Administrator \"${ADMIN_INITIAL_USERNAME}\" zostanie utworzony przy starcie aplikacji."
else
  unset ADMIN_INITIAL_USERNAME ADMIN_INITIAL_PASSWORD
fi
