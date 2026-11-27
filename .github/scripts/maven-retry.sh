#!/usr/bin/env bash
# Retry Maven only when a remote repository or network failure is transient.
# Build/test failures are returned immediately and are never retried.
set -uo pipefail

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 <maven-command> [arguments...]" >&2
  exit 2
fi

max_attempts="${MAVEN_CI_MAX_ATTEMPTS:-4}"
base_delay="${MAVEN_CI_RETRY_BASE_SECONDS:-20}"
if [[ ! "$max_attempts" =~ ^[1-9][0-9]*$ || ! "$base_delay" =~ ^[0-9]+$ ]]; then
  echo "MAVEN_CI_MAX_ATTEMPTS and MAVEN_CI_RETRY_BASE_SECONDS must be positive integers." >&2
  exit 2
fi

transient_pattern='status code: (429|500|502|503|504)|Too Many Requests|Bad Gateway|Service Unavailable|Gateway Timeout|Connection reset|Read timed out|Connect timed out|Remote host closed connection|Temporary failure in name resolution'

for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  log_file="$(mktemp)"
  echo "Maven attempt $attempt/$max_attempts: $*"
  "$@" 2>&1 | tee "$log_file"
  command_status=${PIPESTATUS[0]}

  if ((command_status == 0)); then
    rm -f "$log_file"
    exit 0
  fi

  if ! grep -Eiq "$transient_pattern" "$log_file"; then
    echo "Maven failed with a non-transient error; not retrying." >&2
    rm -f "$log_file"
    exit "$command_status"
  fi

  if ((attempt == max_attempts)); then
    echo "Maven still failed after $max_attempts attempts due to a transient repository/network error." >&2
    rm -f "$log_file"
    exit "$command_status"
  fi

  delay=$((base_delay * attempt))
  echo "Transient Maven repository/network failure detected; retrying in ${delay}s." >&2
  rm -f "$log_file"
  sleep "$delay"
done
