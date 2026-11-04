#!/bin/bash
# Sourced by update.sh and init-ssl.sh from the project root. Never print secrets except in backup_confirm_keys.

BACKUP_KEYS_MARKER=".backup-keys-confirmed"

backup_env_get() {
  grep "^$1=" .env 2>/dev/null | head -n1 | cut -d'=' -f2-
}

backup_env_set() {
  if grep -q "^$1=" .env; then
    sed -i "/^$1=/d" .env
  fi
  if [ -s .env ] && [ -n "$(tail -c1 .env)" ]; then
    echo "" >> .env
  fi
  printf '%s=%s\n' "$1" "$2" >> .env
}

backup_configure_env() {
  local repo url

  if [ -z "$(backup_env_get BORG_PASSPHRASE)" ]; then
    echo ""
    echo "==> Brak BORG_PASSPHRASE w .env - generuję nowe hasło szyfrujące kopie zapasowe"
    backup_env_set BORG_PASSPHRASE "$(openssl rand -hex 24)"
    echo "    BORG_PASSPHRASE zapisane w .env"
  fi

  if [ -z "$(backup_env_get BORG_REPO)" ]; then
    echo ""
    echo "==> Brak BORG_REPO w .env (adres repozytorium kopii zapasowych)"
    echo "    Storage Box: ssh://uXXXX@uXXXX.your-storagebox.de:23/./clinicmanager-borg"
    if [ -t 0 ]; then
      while :; do
        read -r -p "Adres repozytorium (Enter = pomiń na razie): " repo || repo=""
        if [[ "$repo" =~ [[:space:]] ]]; then
          echo "    Adres nie może zawierać spacji."
          continue
        fi
        break
      done
      if [ -n "$repo" ]; then
        backup_env_set BORG_REPO "$repo"
        echo "    BORG_REPO zapisane w .env"
      fi
    else
      echo "    Uruchomienie nieinteraktywne - pomijam pytanie."
    fi
  fi

  if [ -z "$(backup_env_get BORG_REPO)" ]; then
    echo ""
    echo "UWAGA: BORG_REPO jest puste - kopie zapasowe NIE są skonfigurowane."
    echo "       Ustaw BORG_REPO w .env (albo uruchom ten skrypt ponownie), żeby je włączyć."
    return 0
  fi

  if ! grep -q "^HEALTHCHECKS_URL=" .env; then
    echo ""
    echo "==> Monitoring kopii zapasowych (healthchecks.io)"
    echo "    Wklej ping URL checka (np. https://hc-ping.com/<uuid>) albo zostaw puste, żeby wyłączyć monitoring."
    if [ -t 0 ]; then
      read -r -p "HEALTHCHECKS_URL (Enter = bez monitoringu): " url || url=""
      if [[ "$url" =~ [[:space:]] ]]; then
        echo "    Adres zawiera spacje - pomijam, ustaw HEALTHCHECKS_URL ręcznie w .env."
      else
        backup_env_set HEALTHCHECKS_URL "$url"
        echo "    HEALTHCHECKS_URL zapisane w .env"
      fi
    else
      echo "    Uruchomienie nieinteraktywne - pomijam pytanie."
    fi
  fi
}

backup_finalize() {
  local out i

  if [ -z "$(backup_env_get BORG_REPO)" ]; then
    echo ""
    echo "UWAGA: BORG_REPO jest puste - pomijam konfigurację kopii zapasowych."
    return 0
  fi

  if [ -z "$(docker compose ps --status running -q backup 2>/dev/null)" ]; then
    echo ""
    echo "UWAGA: kontener 'backup' nie działa - pomijam inicjalizację repozytorium."
    echo "       Sprawdź: docker compose logs backup"
    return 0
  fi

  for i in $(seq 1 30); do
    if docker compose exec -T backup test -f /etc/borgmatic-monitoring.yaml >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  echo ""
  echo "==> Sprawdzam / inicjalizuję repozytorium kopii zapasowych..."
  if ! out=$(docker compose exec -T backup borgmatic repo-create 2>&1); then
    echo "$out"
    echo ""
    echo "UWAGA: nie udało się zainicjalizować repozytorium kopii zapasowych."
    echo "       Sprawdź BORG_REPO, klucz SSH w ./backup/ssh i BORG_PASSPHRASE (dla istniejącego repozytorium)."
    echo "       Aplikacja działa dalej, ale kopie zapasowe nie będą się wykonywać."
    return 0
  fi
  echo "    Repozytorium gotowe."

  if [ "${BACKUP_RESTORE_MODE:-0}" = "1" ]; then
    backup_write_keys_marker
    echo "    Klucze wpisane ręcznie - pomijam ich wyświetlanie."
    backup_offer_restore
  else
    backup_confirm_keys
  fi
}

backup_card_key_valid() {
  local key="$1" body
  [[ "$key" =~ ^[A-Za-z0-9+/]+={0,2}$ ]] || return 1
  body="${key%%=*}"
  [ $(( ${#body} % 4 )) -ne 1 ] || return 1
  if [ "$key" != "$body" ] && [ $(( ${#key} % 4 )) -ne 0 ]; then
    return 1
  fi
  case $(( ${#body} * 3 / 4 )) in
    16|24|32) return 0 ;;
  esac
  return 1
}

backup_read_secret() {
  local name="$1" label="$2" validator="${3:-}" invalid_msg="${4:-}" current first second prompt

  current="$(backup_env_get "$name")"
  while :; do
    prompt="$label: "
    if [ -n "$current" ]; then
      prompt="$label (Enter = zostaw wartość z .env): "
    fi
    read -r -s -p "$prompt" first || { echo ""; return 1; }
    echo ""
    first="${first//[[:space:]]/}"
    if [ -z "$first" ]; then
      if [ -z "$current" ]; then
        echo "    Wartość nie może być pusta."
        continue
      fi
      first="$current"
    else
      read -r -s -p "Powtórz $label: " second || { echo ""; return 1; }
      echo ""
      second="${second//[[:space:]]/}"
      if [ "$first" != "$second" ]; then
        echo "    Wartości się różnią - spróbuj jeszcze raz."
        continue
      fi
    fi
    if [ -n "$validator" ] && ! "$validator" "$first"; then
      echo "    $invalid_msg"
      continue
    fi
    backup_env_set "$name" "$first"
    return 0
  done
}

backup_prompt_repo_required() {
  local repo current

  current="$(backup_env_get BORG_REPO)"
  echo "    Storage Box: ssh://uXXXX@uXXXX.your-storagebox.de:23/./clinicmanager-borg"
  while :; do
    read -r -p "Adres repozytorium BORG_REPO${current:+ [$current]}: " repo || return 1
    repo="${repo:-$current}"
    if [ -n "$repo" ] && ! [[ "$repo" =~ [[:space:]] ]]; then
      break
    fi
    echo "    Adres jest wymagany w trybie odtwarzania (bez spacji)."
  done
  backup_env_set BORG_REPO "$repo"
}

backup_repo_ssh_target() {
  local repo="$1"
  case "$repo" in
    ssh://*)
      repo="${repo#ssh://}"
      repo="${repo%%/*}"
      echo "${repo%:*}"
      ;;
    *@*:*)
      echo "${repo%%:*}"
      ;;
    *)
      return 1
      ;;
  esac
}

backup_prepare_ssh_key() {
  local target answer key="backup/ssh/id_ed25519"

  if ! target="$(backup_repo_ssh_target "$(backup_env_get BORG_REPO)")"; then
    echo "    Repozytorium lokalne - klucz SSH nie jest potrzebny."
    return 0
  fi

  mkdir -p backup/ssh
  chmod 700 backup/ssh
  if [ ! -f "$key" ]; then
    ssh-keygen -q -t ed25519 -N "" -C clinicmanager-backup -f "$key"
    echo "    Wygenerowano nowy klucz SSH: $key"
  elif [ ! -f "$key.pub" ]; then
    ssh-keygen -y -f "$key" > "$key.pub"
  fi
  chmod 600 "$key"

  echo ""
  echo "    Klucz SSH NIE musi być tym starym. Wystarczy dodać ten klucz publiczny do Storage Box"
  echo "    (stary klucz można później usunąć w panelu Hetzner)."
  echo ""
  echo "    Instalacja z tego serwera (poprosi o hasło do Storage Box):"
  echo "      ssh-copy-id -p 23 -s -i $key.pub $target"
  echo "    albo w panelu Hetzner (Storage Box -> SSH keys) wklej:"
  echo "      $(cat "$key.pub")"
  echo ""
  read -r -p "Uruchomić teraz ssh-copy-id? [T/n]: " answer || answer="n"
  if ! [[ "$answer" =~ ^[Nn]$ ]]; then
    ssh-copy-id -p 23 -s -i "$key.pub" "$target" \
      || echo "    ssh-copy-id nie powiodło się - zainstaluj klucz ręcznie (panel Hetzner)."
  fi
  read -r -p "Naciśnij Enter, gdy klucz jest zainstalowany na Storage Box..." answer || true
}

backup_ask_restore_mode() {
  local answer

  BACKUP_RESTORE_MODE=0

  if [ ! -t 0 ]; then
    if [ -z "$(backup_env_get CARD_ENCRYPTION_KEY)" ]; then
      echo "BŁĄD: brak terminala i brak CARD_ENCRYPTION_KEY w .env."
      echo "      Nie generuję nowego klucza, bo to może być odtwarzanie istniejącej instalacji."
      echo "      Uruchom skrypt w terminalu albo wpisz CARD_ENCRYPTION_KEY do .env."
      return 1
    fi
    return 0
  fi

  echo ""
  read -r -p "Czy odtwarzasz istniejącą instalację z kopii zapasowej (masz klucze)? [t/N]: " answer || answer=""
  if ! [[ "$answer" =~ ^[Tt]$ ]]; then
    return 0
  fi

  BACKUP_RESTORE_MODE=1
  echo ""
  echo "==> Tryb odtwarzania. Wpisz klucze (wpisywane znaki nie są wyświetlane)."
  backup_read_secret BORG_PASSPHRASE "BORG_PASSPHRASE" || return 1
  backup_read_secret CARD_ENCRYPTION_KEY "CARD_ENCRYPTION_KEY" backup_card_key_valid \
    "Niepoprawny klucz: oczekiwany Base64 klucza AES o długości 16, 24 lub 32 bajtów (zwykle 44 znaki zakończone '=')." \
    || return 1
  echo ""
  backup_prompt_repo_required || return 1
  backup_prepare_ssh_key
}

backup_restore_verify() {
  local out choice count

  [ "${BACKUP_RESTORE_MODE:-0}" = "1" ] || return 0

  while :; do
    echo ""
    echo "==> Sprawdzam dostęp do repozytorium kopii zapasowych..."
    if out=$(docker compose run --rm --no-deps -T --entrypoint bash backup \
        -c 'bash /custom-cont-init.d/10-monitoring.sh >/dev/null && borgmatic repo-list --short' 2>&1); then
      count="$(printf '%s\n' "$out" | grep -cE '^clinicmanager-[0-9]{4}-' || true)"
      echo "    Dostęp OK. Archiwów w repozytorium: $count"
      if [ "$count" = "0" ]; then
        echo "    UWAGA: repozytorium jest puste - nie będzie czego odtworzyć."
      fi
      return 0
    fi

    echo "$out" | tail -n 15
    echo ""
    if printf '%s' "$out" | grep -qi "passphrase"; then
      echo "    Wygląda na błędne BORG_PASSPHRASE."
    elif printf '%s' "$out" | grep -qi "permission denied"; then
      echo "    Brak dostępu SSH - klucz nie jest zainstalowany na Storage Box albo zły użytkownik."
    elif printf '%s' "$out" | grep -qiE "does not exist|not a valid repository"; then
      echo "    Pod tym adresem nie ma repozytorium - sprawdź BORG_REPO."
    fi
    echo "Nie udało się otworzyć repozytorium. Co dalej?"
    echo "  1) wpisz ponownie BORG_PASSPHRASE"
    echo "  2) zmień BORG_REPO"
    echo "  3) spróbuj ponownie (np. po instalacji klucza SSH)"
    echo "  4) przerwij"
    read -r -p "Wybór [3]: " choice || return 1
    case "${choice:-3}" in
      1) backup_read_secret BORG_PASSPHRASE "BORG_PASSPHRASE" || return 1 ;;
      2) backup_prompt_repo_required || return 1; backup_prepare_ssh_key ;;
      3) ;;
      4) return 1 ;;
      *) echo "    Wybierz 1-4." ;;
    esac
  done
}

backup_offer_restore() {
  local answer

  echo ""
  echo "Baza i pliki kart na tym serwerze zostaną zastąpione najnowszą kopią."
  echo "Obecny stan (świeża instalacja) zostanie zachowany obok: baza clinicmanager_before_restore_<data>"
  echo "i ./backup-restore/<data>/card-files-before-restore.tar."
  read -r -p "Odtworzyć teraz najnowszą kopię? [T/n]: " answer || answer="n"
  if [[ "$answer" =~ ^[Nn]$ ]]; then
    echo "Pominięto. Później: bash backup/restore.sh restore latest --db replace --files replace"
    return 0
  fi
  if bash ./backup/restore.sh restore latest --db replace --files replace --yes; then
    echo ""
    echo "Odtworzenie zakończone."
    echo "  - JWT_SECRET jest nowy: wszyscy użytkownicy muszą zalogować się ponownie."
    echo "  - Jeśli KSEF_ENCRYPTION_KEY jest nowy, token KSeF trzeba wprowadzić ponownie w ustawieniach."
    echo "  - Jeśli MFA_ENCRYPTION_KEY jest nowy, 2FA kont trzeba zresetować: ./scripts/reset-2fa.sh <login>."
  else
    echo ""
    echo "UWAGA: odtwarzanie nie powiodło się. Aplikacja działa na świeżej bazie."
    echo "       Spróbuj ponownie: bash backup/restore.sh restore latest --db replace --files replace"
  fi
}

backup_write_keys_marker() {
  local borg_key card_key

  borg_key="$(backup_env_get BORG_PASSPHRASE)"
  card_key="$(backup_env_get CARD_ENCRYPTION_KEY)"
  printf 'borg=%s\ncard=%s\n' \
    "$(printf '%s' "$borg_key" | sha256sum | cut -d' ' -f1)" \
    "$(printf '%s' "$card_key" | sha256sum | cut -d' ' -f1)" > "$BACKUP_KEYS_MARKER"
  chmod 600 "$BACKUP_KEYS_MARKER"
}

backup_clear_screen() {
  clear 2>/dev/null || printf '\033[H\033[2J'
  printf '\033[3J'
}

backup_confirm_keys() {
  local borg_key card_key expected answer

  borg_key="$(backup_env_get BORG_PASSPHRASE)"
  card_key="$(backup_env_get CARD_ENCRYPTION_KEY)"
  expected="borg=$(printf '%s' "$borg_key" | sha256sum | cut -d' ' -f1)
card=$(printf '%s' "$card_key" | sha256sum | cut -d' ' -f1)"

  if [ -f "$BACKUP_KEYS_MARKER" ] && [ "$(cat "$BACKUP_KEYS_MARKER")" = "$expected" ]; then
    return 0
  fi

  if [ ! -t 0 ] || [ ! -t 1 ]; then
    echo ""
    echo "UWAGA: klucze kopii zapasowych nie zostały potwierdzone (brak terminala)."
    echo "       Zostaną pokazane przy następnym interaktywnym uruchomieniu skryptu."
    return 0
  fi

  echo ""
  echo "=================================================================="
  if [ -f "$BACKUP_KEYS_MARKER" ]; then
    echo "  Co najmniej jeden klucz zmienił się od ostatniego potwierdzenia."
    echo ""
  fi
  echo "  Zapisz na papierze oba klucze poniżej (dokładnie, znak po znaku)."
  echo "  Bez nich nie da się odtworzyć kopii zapasowej ani odczytać kart klientów."
  echo ""
  echo "  BORG_PASSPHRASE=$borg_key"
  echo ""
  echo "  CARD_ENCRYPTION_KEY=$card_key"
  echo "=================================================================="
  echo ""

  while :; do
    if ! read -r -p "Wpisz SAVED, gdy klucze są zapisane: " answer; then
      backup_clear_screen
      echo "Przerwano - klucze nie zostały potwierdzone, zostaną pokazane ponownie."
      return 0
    fi
    if [ "$answer" = "SAVED" ]; then
      break
    fi
    echo "Wpisz dokładnie: SAVED"
  done

  backup_clear_screen

  backup_write_keys_marker
  echo "Klucze potwierdzone. Ekran wyczyszczony."
}
