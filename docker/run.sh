#!/usr/bin/env bash
set -euxo pipefail

OPTS="-http.port=:${HTTP_PORT} -admin.port=:${ADMIN_PORT}"

if [ -n "${WALLET_PROXY_ENABLED+x}" ] && [ "${WALLET_PROXY_ENABLED}" = "true" ];then
  OPTS="${OPTS} -Dhttp.proxyHost=${WALLET_PROXY_HOST} -Dhttp.proxyPort=${WALLET_PROXY_PORT}"
fi
exec ./bin/wallet-daemon ${OPTS} $@
