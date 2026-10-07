#!/usr/bin/env bash
#
# bootstrap.sh — Comando unico de ponta a ponta do Solare.
#
# Faz: build do backend (Maven) -> sam build -> sam deploy -> seed de demo ->
# smoke de verificacao. Toda operacao AWS usa o profile hackaton e us-east-1.
#
# Pre-requisitos: Java 21 + Maven, AWS SAM CLI, AWS CLI, Node, curl.
# Credenciais do profile hackaton configuradas.
#
# Uso:
#   ./bootstrap.sh                 # ambiente desenv (padrao)
#   AMBIENTE=homolog ./bootstrap.sh
#   ./bootstrap.sh --no-seed       # pula o seed de demo
#   ./bootstrap.sh --no-smoke      # pula o smoke
#   ./bootstrap.sh --guided        # primeiro deploy interativo
#
set -euo pipefail

PROFILE="hackaton"
REGION="us-east-1"
STACK="${STACK:-solare}"
AMBIENTE="${AMBIENTE:-desenv}"
TABELA="${TABELA_SOLARE:-solare-${AMBIENTE}}"

DO_SEED=1; DO_SMOKE=1; GUIDED=0
for arg in "$@"; do
  case "$arg" in
    --no-seed) DO_SEED=0 ;;
    --no-smoke) DO_SMOKE=0 ;;
    --guided) GUIDED=1 ;;
    *) echo "arg ignorado: $arg" ;;
  esac
done

AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "${AQUI}/.." && pwd)"

echo "=== 1/5 Build do backend (Maven, fat jar) ==="
mvn -q -f "${RAIZ}/backend/pom.xml" -DskipTests package

echo "=== 2/5 sam build ==="
sam build --template "${AQUI}/template.yaml"

echo "=== 3/5 sam deploy (profile ${PROFILE}, ${REGION}, Ambiente=${AMBIENTE}) ==="
if [[ "${GUIDED}" -eq 1 ]]; then
  sam deploy --guided --profile "${PROFILE}" --region "${REGION}" \
    --parameter-overrides "Ambiente=${AMBIENTE}"
else
  sam deploy --profile "${PROFILE}" --region "${REGION}" \
    --no-confirm-changeset --no-fail-on-empty-changeset \
    --parameter-overrides "Ambiente=${AMBIENTE}"
fi

if [[ "${DO_SEED}" -eq 1 ]]; then
  echo "=== 4/5 Seed de demonstracao (tabela ${TABELA}) ==="
  node "${AQUI}/seed/seed.js" --table "${TABELA}"
else
  echo "=== 4/5 Seed pulado (--no-seed) ==="
fi

if [[ "${DO_SMOKE}" -eq 1 ]]; then
  echo "=== 5/5 Smoke de verificacao (F1-F8) ==="
  BASE_URL="$(aws cloudformation describe-stacks --stack-name "${STACK}" \
    --query "Stacks[0].Outputs[?OutputKey=='ApiEndpoint'].OutputValue" \
    --output text --profile "${PROFILE}" --region "${REGION}")"
  bash "${AQUI}/smoke.sh" "${BASE_URL}"
else
  echo "=== 5/5 Smoke pulado (--no-smoke) ==="
fi

echo "=== bootstrap concluido ==="
