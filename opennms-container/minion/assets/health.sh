#!/usr/bin/env bash
umask 002

TMP_FILE="/tmp/health"
MINION_HOME="/opt/minion"

${MINION_HOME}/bin/client "health:check | tac ${TMP_FILE}"
grep "Everything is awesome" ${TMP_FILE}
