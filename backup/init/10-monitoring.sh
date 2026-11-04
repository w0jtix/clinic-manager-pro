#!/bin/bash

TARGET=/etc/borgmatic-monitoring.yaml

if [ -n "$HEALTHCHECKS_URL" ]; then
  cat > "$TARGET" <<'EOF'
healthchecks:
    ping_url: ${HEALTHCHECKS_URL}
    send_logs: false
    states:
        - start
        - finish
        - fail
EOF
  echo "[clinicmanager] Healthchecks monitoring enabled"
else
  echo '{}' > "$TARGET"
  echo "[clinicmanager] HEALTHCHECKS_URL is empty - monitoring disabled"
fi
