#!/usr/bin/env bash
#
# smoke.sh — Verificacao pos-deploy do Solare (NAO e um teste automatizado;
# e um SCRIPT de conferencia manual/CI do fluxo F1–F8 contra o ambiente real).
#
# Exercita a API publicada pelo stack SAM usando curl, com a identidade resolvida
# pelo stub de desenvolvimento (cabecalhos X-Dev-User / X-Dev-Role) — valido fora
# de producao (NF3.7). As operacoes AWS auxiliares usam o profile hackaton.
#
# Pre-requisitos: aws CLI, curl, (opcional) jq. Profile hackaton configurado.
#
# Uso:
#   ./smoke.sh                         # resolve a URL do stack "solare"
#   ./smoke.sh https://xxxx.execute-api.us-east-1.amazonaws.com/desenv
#   BASE_URL=https://... ./smoke.sh
#
set -euo pipefail

PROFILE="hackaton"
REGION="us-east-1"
STACK="${STACK:-solare}"

# ---------------------------------------------------------------------------
# Resolve a URL base: argumento > env BASE_URL > saida ApiEndpoint do stack.
# ---------------------------------------------------------------------------
BASE_URL="${1:-${BASE_URL:-}}"
if [[ -z "${BASE_URL}" ]]; then
  echo "Resolvendo ApiEndpoint do stack '${STACK}' (profile ${PROFILE}, ${REGION})..."
  BASE_URL="$(aws cloudformation describe-stacks \
    --stack-name "${STACK}" \
    --query "Stacks[0].Outputs[?OutputKey=='ApiEndpoint'].OutputValue" \
    --output text --profile "${PROFILE}" --region "${REGION}")"
fi
if [[ -z "${BASE_URL}" || "${BASE_URL}" == "None" ]]; then
  echo "ERRO: nao foi possivel resolver a URL base. Informe-a como argumento." >&2
  exit 1
fi
echo "Base URL: ${BASE_URL}"
echo

# Cabecalhos de identidade (stub) por perfil.
ADMIN=(-H "X-Dev-User: admin.demo" -H "X-Dev-Role: ADMIN")
SOLIC=(-H "X-Dev-User: ana.demo" -H "X-Dev-Role: SOLICITANTE")
ATEND=(-H "X-Dev-User: atendente.demo" -H "X-Dev-Role: ATENDENTE")
JSON=(-H "Content-Type: application/json")

falhas=0
# chamar <descricao> <status-esperado> <metodo> <path> [headers/corpo extra...]
chamar() {
  local desc="$1"; local esperado="$2"; local metodo="$3"; local path="$4"; shift 4
  local http
  http="$(curl -sS -o /tmp/solare_smoke_body -w '%{http_code}' \
    -X "${metodo}" "${BASE_URL}${path}" "$@")" || http="000"
  if [[ "${http}" == "${esperado}" ]]; then
    echo "OK   [${http}] ${desc} — ${metodo} ${path}"
  else
    echo "FALHA[${http}!=${esperado}] ${desc} — ${metodo} ${path}"
    echo "     corpo: $(head -c 300 /tmp/solare_smoke_body)"
    falhas=$((falhas + 1))
  fi
}

echo "== NF1/health =="
chamar "Health check" 200 GET "/api/health"

echo
echo "== F1 Setores (ADMIN) =="
SETOR_BODY='{"nome":"Setor Smoke","sigla":"SMK","emailNotificacao":"smoke@exemplo.test"}'
chamar "Criar setor (ADMIN)" 201 POST "/api/manutencao/setores" "${ADMIN[@]}" "${JSON[@]}" -d "${SETOR_BODY}"
chamar "Listar setores (ADMIN)" 200 GET "/api/manutencao/setores" "${ADMIN[@]}"
chamar "Criar setor sem perfil (negado)" 401 POST "/api/manutencao/setores" "${JSON[@]}" -d "${SETOR_BODY}"

echo
echo "== F2 Ambientes (ADMIN) =="
chamar "Listar ambientes (ADMIN)" 200 GET "/api/manutencao/ambientes" "${ADMIN[@]}"

echo
echo "== F3 Recursos (ADMIN) =="
chamar "Listar recursos (ADMIN)" 200 GET "/api/manutencao/recursos" "${ADMIN[@]}"

echo
echo "== F5 Disponibilidade (SOLICITANTE) =="
DIA="$(date -u -d '+1 day' +%F 2>/dev/null || date -u -v+1d +%F)"
chamar "Grade de disponibilidade" 200 GET \
  "/api/reservas/disponibilidade?ambienteId=amb-sala-301&data=${DIA}" "${SOLIC[@]}"

echo
echo "== F4 Reserva — fluxo feliz (SOLICITANTE) =="
RES_OK="{\"ambienteId\":\"amb-auditorio\",\"inicio\":\"${DIA}T19:00\",\"fim\":\"${DIA}T20:00\",\"finalidade\":\"Smoke feliz\",\"recursos\":[{\"recursoId\":\"rec-wifi\",\"quantidade\":1}]}"
chamar "Criar reserva valida" 201 POST "/api/reservas" "${SOLIC[@]}" "${JSON[@]}" -d "${RES_OK}"

echo
echo "== F4/RN — conflito de horario (depende do seed) =="
RES_CONF="{\"ambienteId\":\"amb-sala-301\",\"inicio\":\"${DIA}T10:15\",\"fim\":\"${DIA}T11:00\",\"finalidade\":\"Smoke conflito\",\"recursos\":[]}"
chamar "Criar reserva em conflito (409)" 409 POST "/api/reservas" "${SOLIC[@]}" "${JSON[@]}" -d "${RES_CONF}"

echo
echo "== F4/RN — conflito de hierarquia (depende do seed) =="
RES_HIER="{\"ambienteId\":\"amb-sala-301\",\"inicio\":\"${DIA}T14:30\",\"fim\":\"${DIA}T15:30\",\"finalidade\":\"Smoke hierarquia\",\"recursos\":[]}"
chamar "Criar reserva com conflito de hierarquia (409)" 409 POST "/api/reservas" "${SOLIC[@]}" "${JSON[@]}" -d "${RES_HIER}"

echo
echo "== F4/RN — estouro de recurso limitado (depende do seed) =="
RES_EST="{\"ambienteId\":\"amb-auditorio\",\"inicio\":\"${DIA}T11:00\",\"fim\":\"${DIA}T12:00\",\"finalidade\":\"Smoke estouro\",\"recursos\":[{\"recursoId\":\"rec-projetor\",\"quantidade\":1}]}"
chamar "Criar reserva com estouro de recurso (409)" 409 POST "/api/reservas" "${SOLIC[@]}" "${JSON[@]}" -d "${RES_EST}"

echo
echo "== F6 Painel do atendente (ATENDENTE) =="
chamar "Cards por data (ATENDENTE)" 200 GET "/api/reservas/atendente?data=${DIA}" "${ATEND[@]}"
chamar "Cards sem perfil (negado)" 401 GET "/api/reservas/atendente?data=${DIA}"

echo
echo "== INOV1 Assistente (SOLICITANTE) =="
chamar "Assistente de reserva" 200 POST "/api/assistente/sugestoes" "${SOLIC[@]}" "${JSON[@]}" \
  -d '{"pedido":"preciso de uma sala para 8 pessoas amanha de manha"}'

echo
echo "== F8 Notificacoes =="
echo "INFO: F8 e assincrona (EventBridge -> Lambda -> SES). Verifique:"
echo "  aws logs tail /aws/lambda/solare-notificacao-desenv --since 5m --profile ${PROFILE} --region ${REGION}"
echo "  (e a caixa do e-mail do setor; no SES sandbox o destinatario precisa estar verificado)."

echo
if [[ "${falhas}" -eq 0 ]]; then
  echo "SMOKE OK: fluxo F1–F8 verificado (notificacao F8 conferida por log/e-mail)."
  exit 0
else
  echo "SMOKE COM ${falhas} FALHA(S). Revise os itens marcados FALHA acima."
  exit 1
fi
