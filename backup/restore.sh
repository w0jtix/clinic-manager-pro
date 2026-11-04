#!/bin/bash
set -euo pipefail
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")/.."

DB_NAME="clinicmanager"
REDIS_STALE_KEYS="ksef:access_token ksef:refresh_token"
TS="$(date +%Y%m%d_%H%M%S)"
WORK="/tmp/clinicmanager-restore-$TS"
HOST_RESTORE_DIR="./backup-restore/$TS"
CONTAINER_RESTORE_DIR="/restore/$TS"
WORK_CREATED=0
APP_STOPPED=0

info() {
  echo "==> $*"
}

die() {
  echo "" >&2
  echo "BŁĄD: $*" >&2
  exit 1
}

bx() {
  docker compose exec -T backup "$@"
}

pg_admin() {
  bx bash -c 'PGPASSWORD="$POSTGRES_PASSWORD" exec psql -h postgres -U postgres --no-password -X -q -v ON_ERROR_STOP=1 "$@"' _ "$@"
}

cleanup() {
  if [ "$WORK_CREATED" = "1" ]; then
    bx rm -rf "$WORK" >/dev/null 2>&1 || true
  fi
  if [ "$APP_STOPPED" = "1" ]; then
    info "Uruchamiam ponownie aplikację (spring-boot)..."
    docker compose start spring-boot >/dev/null 2>&1 \
      || echo "UWAGA: nie udało się uruchomić spring-boot - uruchom ręcznie: docker compose start spring-boot" >&2
  fi
}
trap cleanup EXIT

usage() {
  cat <<EOF
Użycie:
  bash backup/restore.sh list
  bash backup/restore.sh restore [ARCHIWUM|latest] [--db test|replace|skip] [--files dir|replace|skip] [--yes]

Bez flag odtwarzanie jest interaktywne. Domyślnie:
  - baza trafia do NOWEJ bazy testowej na serwerze postgres (produkcja nietknięta),
  - pliki kart trafiają do katalogu ./backup-restore/<data>/card-files.
Nadpisanie produkcji (replace) wymaga wpisania słowa NADPISZ albo flagi --yes.
Po podmianie bazy z Redis usuwany jest cache tokenów KSeF ($REDIS_STALE_KEYS).
EOF
}

require_backup() {
  if [ -z "$(docker compose ps --status running -q backup 2>/dev/null)" ]; then
    die "Kontener 'backup' nie działa. Uruchom: docker compose up -d backup"
  fi
}

wait_for_postgres() {
  local i
  for i in $(seq 1 30); do
    if pg_admin -d postgres -tAc "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  die "Baza postgres nie odpowiada (docker compose logs postgres)."
}

archive_names() {
  local out
  if ! out=$(bx borgmatic repo-list --short 2>&1); then
    echo "$out" >&2
    die "Nie udało się odczytać listy archiwów (sprawdź BORG_REPO, BORG_PASSPHRASE, klucz SSH)."
  fi
  printf '%s\n' "$out" | tr -d '\r' | grep -E '^clinicmanager-[0-9]{4}-' || true
}

valid_db_name() {
  [[ "$1" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] || return 1
  case "$1" in
    "$DB_NAME"|postgres|template0|template1) return 1 ;;
  esac
  return 0
}

db_exists() {
  [ "$(pg_admin -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$1'" | tr -d '[:space:]')" = "1" ]
}

choose() {
  local ans
  while :; do
    read -r -p "$1 [1]: " ans || die "Przerwano."
    ans="${ans:-1}"
    case "$ans" in
      1|2|3) echo "$ans"; return 0 ;;
    esac
    echo "Wybierz 1, 2 lub 3." >&2
  done
}

invalidate_redis_cache() {
  local deleted
  if [ -z "$(docker compose ps --status running -q redis 2>/dev/null)" ]; then
    echo "UWAGA: kontener redis nie działa - cache tokenów KSeF nie został wyczyszczony." >&2
    echo "       Po uruchomieniu: docker compose exec redis redis-cli DEL $REDIS_STALE_KEYS" >&2
    return 0
  fi
  if deleted="$(docker compose exec -T redis redis-cli DEL $REDIS_STALE_KEYS | tr -d '[:space:]')"; then
    info "Redis: usunięto cache tokenów KSeF ($REDIS_STALE_KEYS), usuniętych kluczy: $deleted."
    info "Redis: blokady logowania (login:*) i unieważnione tokeny JWT (jwt:blacklist:*) zostawione bez zmian."
  else
    echo "UWAGA: nie udało się wyczyścić cache KSeF w Redis - wykonaj: docker compose exec redis redis-cli DEL $REDIS_STALE_KEYS" >&2
  fi
}

cmd_list() {
  require_backup
  info "Archiwa w repozytorium (od najstarszego):"
  bx borgmatic repo-list
}

cmd_restore() {
  local archive="" db_mode="" files_mode="" assume_yes=0
  local names target_db="" old_db="" answer dump tables files_dest="" files_count

  while [ $# -gt 0 ]; do
    case "$1" in
      --db)
        case "${2:-}" in
          test) db_mode=1 ;;
          replace) db_mode=2 ;;
          skip) db_mode=3 ;;
          *) die "Nieznana wartość --db: '${2:-}' (test|replace|skip)." ;;
        esac
        shift 2
        ;;
      --files)
        case "${2:-}" in
          dir) files_mode=1 ;;
          replace) files_mode=2 ;;
          skip) files_mode=3 ;;
          *) die "Nieznana wartość --files: '${2:-}' (dir|replace|skip)." ;;
        esac
        shift 2
        ;;
      --yes)
        assume_yes=1
        shift
        ;;
      -*)
        die "Nieznana opcja: $1"
        ;;
      *)
        archive="$1"
        shift
        ;;
    esac
  done

  if [ -z "$db_mode" ] || [ -z "$files_mode" ] || [ "$assume_yes" = "0" ]; then
    [ -t 0 ] || die "Brak terminala - podaj --db, --files i --yes."
  fi

  require_backup

  info "Odczytuję listę archiwów..."
  names="$(archive_names)"
  [ -n "$names" ] || die "Repozytorium nie zawiera żadnych archiwów."

  if [ -z "$archive" ] && { [ -z "$db_mode" ] || [ -z "$files_mode" ]; }; then
    echo ""
    echo "Dostępne archiwa (od najstarszego):"
    printf '  %s\n' $names
    echo ""
    read -r -p "Archiwum do odtworzenia [latest]: " archive || die "Przerwano."
  fi
  archive="${archive:-latest}"
  if [ "$archive" = "latest" ]; then
    archive="$(printf '%s\n' "$names" | tail -n1)"
  fi
  printf '%s\n' "$names" | grep -Fxq -- "$archive" || die "Nie ma archiwum '$archive'."

  if [ -z "$db_mode" ]; then
    echo ""
    echo "Baza danych:"
    echo "  1) odtwórz do NOWEJ bazy testowej (produkcja nietknięta)"
    echo "  2) zastąp bazę produkcyjną '$DB_NAME' (obecna zostaje zachowana pod inną nazwą)"
    echo "  3) pomiń bazę"
    db_mode="$(choose "Wybór")"
    if [ "$db_mode" = "1" ]; then
      while :; do
        read -r -p "Nazwa bazy testowej [${DB_NAME}_restore_$TS]: " target_db || die "Przerwano."
        target_db="${target_db:-${DB_NAME}_restore_$TS}"
        if valid_db_name "$target_db"; then
          break
        fi
        echo "Niepoprawna nazwa (małe litery, cyfry, _; nie może to być '$DB_NAME', 'postgres' ani 'template*')." >&2
      done
    fi
  fi
  if [ "$db_mode" = "1" ] && [ -z "$target_db" ]; then
    target_db="${DB_NAME}_restore_$TS"
  elif [ "$db_mode" = "2" ]; then
    target_db="${DB_NAME}_restore_$TS"
    old_db="${DB_NAME}_before_restore_$TS"
  fi

  if [ -z "$files_mode" ]; then
    echo ""
    echo "Pliki kart klientów (card_files):"
    echo "  1) wypakuj do katalogu $HOST_RESTORE_DIR/card-files"
    echo "  2) zastąp wolumen card_files (obecna zawartość -> $HOST_RESTORE_DIR/card-files-before-restore.tar)"
    echo "  3) pomiń pliki"
    files_mode="$(choose "Wybór")"
  fi

  [ "$db_mode" != "3" ] || [ "$files_mode" != "3" ] || die "Nic do zrobienia."

  echo ""
  echo "Podsumowanie:"
  echo "  archiwum: $archive"
  case "$db_mode" in
    1) echo "  baza:     nowa baza '$target_db' (produkcja bez zmian)" ;;
    2) echo "  baza:     ZASTĄPIENIE '$DB_NAME' (stara -> '$old_db'), aplikacja zostanie na chwilę zatrzymana" ;;
    3) echo "  baza:     pominięta" ;;
  esac
  case "$files_mode" in
    1) echo "  pliki:    katalog $HOST_RESTORE_DIR/card-files" ;;
    2) echo "  pliki:    ZASTĄPIENIE wolumenu card_files, aplikacja zostanie na chwilę zatrzymana" ;;
    3) echo "  pliki:    pominięte" ;;
  esac
  echo ""

  if [ "$assume_yes" = "0" ]; then
    if [ "$db_mode" = "2" ] || [ "$files_mode" = "2" ]; then
      echo "UWAGA: wybrano nadpisanie danych PRODUKCYJNYCH."
      read -r -p "Wpisz NADPISZ, aby potwierdzić: " answer || die "Przerwano."
      [ "$answer" = "NADPISZ" ] || die "Brak potwierdzenia - przerwano, nic nie zostało zmienione."
    else
      read -r -p "Kontynuować? [t/N]: " answer || die "Przerwano."
      [[ "$answer" =~ ^[TtYy]$ ]] || die "Przerwano, nic nie zostało zmienione."
    fi
  fi

  bx mkdir -p "$WORK"
  WORK_CREATED=1

  if [ "$db_mode" != "3" ]; then
    wait_for_postgres
    echo ""
    info "Wypakowuję zrzut bazy z archiwum..."
    bx mkdir -p "$WORK/db"
    bx borgmatic extract --archive "$archive" --path borgmatic/postgresql_databases --destination "$WORK/db"
    dump="$(bx find "$WORK/db" -type f -path '*/postgresql_databases/*' -name "$DB_NAME" | head -n1 | tr -d '\r')"
    [ -n "$dump" ] || die "W archiwum nie znaleziono zrzutu bazy '$DB_NAME'."

    if db_exists "$target_db"; then
      die "Baza '$target_db' już istnieje - wybierz inną nazwę."
    fi

    info "Tworzę bazę '$target_db' i odtwarzam do niej dane..."
    pg_admin -d postgres -c "CREATE DATABASE \"$target_db\""
    if ! bx bash -c 'set -o pipefail; export PGPASSWORD="$POSTGRES_PASSWORD"; pg_restore -f - "$1" | sed "1,100{/^SET transaction_timeout = 0;\$/d}" | psql -h postgres -U postgres --no-password -X -q -v ON_ERROR_STOP=1 --single-transaction -d "$2" >/dev/null' _ "$dump" "$target_db"; then
      pg_admin -d postgres -c "DROP DATABASE IF EXISTS \"$target_db\"" || true
      die "Odtwarzanie bazy nie powiodło się - baza '$target_db' została usunięta, produkcja bez zmian."
    fi
    tables="$(pg_admin -d "$target_db" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'" | tr -d '[:space:]')"
    info "Baza '$target_db' odtworzona (tabel w schemacie public: $tables)."
  fi

  if [ "$files_mode" != "3" ]; then
    if [ "$files_mode" = "1" ]; then
      files_dest="$CONTAINER_RESTORE_DIR/card-files"
    else
      files_dest="$WORK/card-files"
    fi
    echo ""
    info "Wypakowuję pliki kart z archiwum..."
    bx mkdir -p "$files_dest"
    bx borgmatic extract --archive "$archive" --path data/card-files --strip-components 2 --destination "$files_dest"
    files_count="$(bx find "$files_dest" -type f | wc -l | tr -d '[:space:]')"
    info "Wypakowano plików: $files_count"
  fi

  if [ "$db_mode" = "2" ] || [ "$files_mode" = "2" ]; then
    echo ""
    info "Zatrzymuję aplikację (spring-boot)..."
    docker compose stop spring-boot
    APP_STOPPED=1
  fi

  if [ "$db_mode" = "2" ]; then
    info "Podmieniam bazę produkcyjną..."
    if db_exists "$DB_NAME"; then
      pg_admin -d postgres -tAc "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid()" >/dev/null
      if ! pg_admin -d postgres -c "ALTER DATABASE \"$DB_NAME\" RENAME TO \"$old_db\"; ALTER DATABASE \"$target_db\" RENAME TO \"$DB_NAME\";"; then
        die "Podmiana nie powiodła się - produkcja bez zmian, odtworzone dane są w bazie '$target_db'."
      fi
      info "Baza '$DB_NAME' zastąpiona. Poprzednia wersja zachowana jako '$old_db'."
    else
      pg_admin -d postgres -c "ALTER DATABASE \"$target_db\" RENAME TO \"$DB_NAME\""
      old_db=""
      info "Baza '$DB_NAME' utworzona z archiwum."
    fi
    invalidate_redis_cache
  fi

  if [ "$files_mode" = "2" ]; then
    info "Zabezpieczam obecną zawartość wolumenu card_files..."
    mkdir -p "$HOST_RESTORE_DIR"
    docker compose run --rm --no-deps -T --entrypoint tar spring-boot -C /app/data/card-files -cf - . \
      > "$HOST_RESTORE_DIR/card-files-before-restore.tar"
    info "Podmieniam zawartość wolumenu card_files..."
    bx tar -C "$files_dest" -cf - . \
      | docker compose run --rm --no-deps -T --entrypoint sh spring-boot -c 'find /app/data/card-files -mindepth 1 -delete && tar -C /app/data/card-files -xf -'
    info "Wolumen card_files odtworzony. Poprzednia zawartość: $HOST_RESTORE_DIR/card-files-before-restore.tar"
  fi

  if [ "$APP_STOPPED" = "1" ]; then
    info "Uruchamiam ponownie aplikację (spring-boot)..."
    docker compose start spring-boot
    APP_STOPPED=0
  fi

  echo ""
  echo "Gotowe."
  if [ "$db_mode" = "1" ]; then
    echo "  Baza testowa: $target_db (usuń po weryfikacji: docker compose exec postgres dropdb -U postgres $target_db)"
  fi
  if [ "$db_mode" = "2" ] && [ -n "$old_db" ]; then
    echo "  Stara baza: $old_db (usuń po weryfikacji: docker compose exec postgres dropdb -U postgres $old_db)"
  fi
  if [ "$files_mode" = "1" ]; then
    echo "  Pliki kart: $HOST_RESTORE_DIR/card-files (zaszyfrowane kluczem CARD_ENCRYPTION_KEY)"
  fi
}

case "${1:-}" in
  list) cmd_list ;;
  restore) shift; cmd_restore "$@" ;;
  *) usage; exit 1 ;;
esac
