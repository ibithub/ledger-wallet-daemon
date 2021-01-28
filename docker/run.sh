#!/usr/bin/env bash
set -euxo pipefail

OPTS="-http.port=:${HTTP_PORT} -admin.port=:${ADMIN_PORT}"

if [ -n "${WALLET_PROXY_ENABLED+x}" ] && [ "${WALLET_PROXY_ENABLED}" = "true" ];then
  PROXY_ARGS="-Dhttp.proxyHost=${WALLET_PROXY_HOST} -Dhttp.proxyPort=${WALLET_PROXY_PORT}"
  echo "Starting Wallet Daemon with Proxy enabled : $PROXY_ARGS"
  OPTS="${OPTS} $PROXY_ARGS"
else
  echo "Starting Wallet Daemon with Proxy disabled"
fi

if [ -n "${DD_AGENT_HOST+x}" ];then
  DD_ARGS="-Ddd.agent.host=$DD_AGENT_HOST -Ddd.agent.port=$DD_TRACE_AGENT_PORT -Ddd.profiling.enabled=$DD_PROFILING_ENABLED -Ddd.logs.injection=true -Ddd.trace.sample.rate=1 -Ddd.service=wallet-daemon"
  echo "Starting Wallet Daemon with DATADOG APM support : $DD_ARGS"
  OPTS="${OPTS} $DD_ARGS"
else
  echo "\$DD_AGENT_HOST is empty. Starting without DATADOG APM support"
fi

exec ./bin/wallet-daemon ${OPTS} $@
