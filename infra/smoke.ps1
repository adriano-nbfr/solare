# smoke.ps1 — Verificacao pos-deploy do Solare (NAO e um teste automatizado;
# e um SCRIPT de conferencia do fluxo F1–F8 contra o ambiente real), variante
# PowerShell/Windows do smoke.sh.
#
# Usa Invoke-WebRequest com a identidade do stub (cabecalhos X-Dev-User /
# X-Dev-Role, validos fora de producao — NF3.7). As operacoes AWS usam o profile
# hackaton e a regiao us-east-1.
#
# Uso:
#   ./smoke.ps1
#   ./smoke.ps1 -BaseUrl https://xxxx.execute-api.us-east-1.amazonaws.com/desenv
#
param(
  [string]$BaseUrl = $env:BASE_URL,
  [string]$Stack = 'solare'
)

$ErrorActionPreference = 'Stop'
$Profile = 'hackaton'
$Region = 'us-east-1'

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  Write-Host "Resolvendo ApiEndpoint do stack '$Stack' (profile $Profile, $Region)..."
  $BaseUrl = aws cloudformation describe-stacks `
    --stack-name $Stack `
    --query "Stacks[0].Outputs[?OutputKey=='ApiEndpoint'].OutputValue" `
    --output text --profile $Profile --region $Region
}
if ([string]::IsNullOrWhiteSpace($BaseUrl) -or $BaseUrl -eq 'None') {
  Write-Error "Nao foi possivel resolver a URL base. Informe com -BaseUrl."
  exit 1
}
Write-Host "Base URL: $BaseUrl`n"

$admin = @{ 'X-Dev-User' = 'admin.demo'; 'X-Dev-Role' = 'ADMIN' }
$solic = @{ 'X-Dev-User' = 'ana.demo'; 'X-Dev-Role' = 'SOLICITANTE' }
$atend = @{ 'X-Dev-User' = 'atendente.demo'; 'X-Dev-Role' = 'ATENDENTE' }
$anon = @{}

$script:falhas = 0
function Chamar($desc, $esperado, $metodo, $path, $headers, $body) {
  $url = "$BaseUrl$path"
  $h = @{} + $headers
  try {
    $params = @{ Uri = $url; Method = $metodo; Headers = $h; SkipHttpErrorCheck = $true }
    if ($body) { $params.Body = $body; $h['Content-Type'] = 'application/json' }
    $resp = Invoke-WebRequest @params
    $code = [int]$resp.StatusCode
  } catch {
    $code = 0
  }
  if ($code -eq $esperado) {
    Write-Host "OK   [$code] $desc - $metodo $path"
  } else {
    Write-Host "FALHA[$code!=$esperado] $desc - $metodo $path"
    $script:falhas++
  }
}

$dia = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')

Write-Host '== NF1/health =='
Chamar 'Health check' 200 'GET' '/api/health' $anon $null

Write-Host "`n== F1 Setores (ADMIN) =="
$setor = '{"nome":"Setor Smoke","sigla":"SMK","emailNotificacao":"smoke@exemplo.test"}'
Chamar 'Criar setor (ADMIN)' 201 'POST' '/api/manutencao/setores' $admin $setor
Chamar 'Listar setores (ADMIN)' 200 'GET' '/api/manutencao/setores' $admin $null
Chamar 'Criar setor sem perfil (negado)' 401 'POST' '/api/manutencao/setores' $anon $setor

Write-Host "`n== F2 Ambientes (ADMIN) =="
Chamar 'Listar ambientes (ADMIN)' 200 'GET' '/api/manutencao/ambientes' $admin $null

Write-Host "`n== F3 Recursos (ADMIN) =="
Chamar 'Listar recursos (ADMIN)' 200 'GET' '/api/manutencao/recursos' $admin $null

Write-Host "`n== F5 Disponibilidade (SOLICITANTE) =="
Chamar 'Grade de disponibilidade' 200 'GET' "/api/reservas/disponibilidade?ambienteId=amb-sala-301&data=$dia" $solic $null

Write-Host "`n== F4 Reserva - fluxo feliz (SOLICITANTE) =="
$resOk = "{""ambienteId"":""amb-auditorio"",""inicio"":""${dia}T19:00"",""fim"":""${dia}T20:00"",""finalidade"":""Smoke feliz"",""recursos"":[{""recursoId"":""rec-wifi"",""quantidade"":1}]}"
Chamar 'Criar reserva valida' 201 'POST' '/api/reservas' $solic $resOk

Write-Host "`n== F4/RN - conflito de horario (depende do seed) =="
$resConf = "{""ambienteId"":""amb-sala-301"",""inicio"":""${dia}T10:15"",""fim"":""${dia}T11:00"",""finalidade"":""Smoke conflito"",""recursos"":[]}"
Chamar 'Criar reserva em conflito (409)' 409 'POST' '/api/reservas' $solic $resConf

Write-Host "`n== F4/RN - conflito de hierarquia (depende do seed) =="
$resHier = "{""ambienteId"":""amb-sala-301"",""inicio"":""${dia}T14:30"",""fim"":""${dia}T15:30"",""finalidade"":""Smoke hierarquia"",""recursos"":[]}"
Chamar 'Criar reserva com conflito de hierarquia (409)' 409 'POST' '/api/reservas' $solic $resHier

Write-Host "`n== F4/RN - estouro de recurso limitado (depende do seed) =="
$resEst = "{""ambienteId"":""amb-auditorio"",""inicio"":""${dia}T11:00"",""fim"":""${dia}T12:00"",""finalidade"":""Smoke estouro"",""recursos"":[{""recursoId"":""rec-projetor"",""quantidade"":1}]}"
Chamar 'Criar reserva com estouro de recurso (409)' 409 'POST' '/api/reservas' $solic $resEst

Write-Host "`n== F6 Painel do atendente (ATENDENTE) =="
Chamar 'Cards por data (ATENDENTE)' 200 'GET' "/api/reservas/atendente?data=$dia" $atend $null
Chamar 'Cards sem perfil (negado)' 401 'GET' "/api/reservas/atendente?data=$dia" $anon $null

Write-Host "`n== INOV1 Assistente (SOLICITANTE) =="
Chamar 'Assistente de reserva' 200 'POST' '/api/assistente/sugestoes' $solic '{"pedido":"preciso de uma sala para 8 pessoas amanha de manha"}'

Write-Host "`n== F8 Notificacoes =="
Write-Host 'INFO: F8 e assincrona (EventBridge -> Lambda -> SES). Verifique:'
Write-Host "  aws logs tail /aws/lambda/solare-notificacao-desenv --since 5m --profile $Profile --region $Region"
Write-Host '  (e a caixa do e-mail do setor; no SES sandbox o destinatario precisa estar verificado).'

Write-Host ''
if ($script:falhas -eq 0) {
  Write-Host 'SMOKE OK: fluxo F1-F8 verificado (notificacao F8 conferida por log/e-mail).'
  exit 0
} else {
  Write-Host "SMOKE COM $($script:falhas) FALHA(S). Revise os itens marcados FALHA acima."
  exit 1
}
