#!/usr/bin/env bash
set -euxo pipefail

OPTS="-http.port=:${HTTP_PORT} -admin.port=:${ADMIN_PORT}"

if [ -n "${WALLET_PROXY_ENABLED+x}" ] && [ "${WALLET_PROXY_ENABLED}" = "true" ];then
  OPTS="${OPTS} -Dhttp.proxyHost=${WALLET_PROXY_HOST} -Dhttp.proxyPort=${WALLET_PROXY_PORT}"
fi
exec ./bin/wallet-daemon ${OPTS} $@

export DD_SERVICE=nrt-wallet-daemon
export DD_LOGS_INJECTION=true
export DD_AGENT_HOST=172.17.0.1
export DD_PROFILING_ENABLED=true
export DD_TRACE_DEBUG=true