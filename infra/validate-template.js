// Validacao estrutural basica do template SAM (sem SAM CLI disponivel).
const fs = require('fs');
const s = fs.readFileSync(__dirname + '/template.yaml', 'utf8');
const lines = s.split(/\r?\n/);
let semTabs = true;
lines.forEach((l, i) => {
  if (l.indexOf('\t') >= 0) {
    console.log('TAB encontrada na linha ' + (i + 1));
    semTabs = false;
  }
});
const checks = {
  'linhas': lines.length,
  'Transform SAM': /Transform:\s*AWS::Serverless-2016-10-31/.test(s),
  'Resources': /^Resources:/m.test(s),
  'TabelaSolare (DynamoDB)': /TabelaSolare:\s*[\r\n]+\s*Type:\s*AWS::DynamoDB::Table/.test(s),
  'PK/SK keys': /AttributeName: PK/.test(s) && /AttributeName: SK/.test(s),
  'GSI1': /IndexName: GSI1/.test(s),
  'GSI2': /IndexName: GSI2/.test(s),
  'GSI3': /IndexName: GSI3/.test(s),
  'TTL expiraEm': /AttributeName: expiraEm/.test(s) && /TimeToLiveSpecification/.test(s),
  'SSE habilitado': /SSEEnabled: true/.test(s),
  'HTTP API': /AWS::Serverless::HttpApi/.test(s),
  'HealthCheckFunction': /HealthCheckFunction:/.test(s),
  'GET /api/health': /Path:\s*\/api\/health/.test(s) && /Method:\s*GET/.test(s),
  'Runtime java21': /Runtime:\s*java21/.test(s),
  'Stub fora de prod': /STUB_IDENTIDADE_HABILITADO/.test(s),
  // Funcoes de negocio wired na tarefa 16 (todos os endpoints do design).
  'SetorFunction': /SetorFunction:/.test(s),
  'AmbienteFunction': /AmbienteFunction:/.test(s),
  'RecursoFunction': /RecursoFunction:/.test(s),
  'ReservaFunction': /ReservaFunction:/.test(s),
  'DisponibilidadeFunction': /DisponibilidadeFunction:/.test(s),
  'AtendenteFunction': /AtendenteFunction:/.test(s),
  'AssistenteFunction': /AssistenteFunction:/.test(s),
  'NotificacaoFunction': /NotificacaoFunction:/.test(s),
  'rota /api/manutencao/setores': /Path:\s*\/api\/manutencao\/setores\b/.test(s),
  'rota /api/manutencao/ambientes': /Path:\s*\/api\/manutencao\/ambientes\b/.test(s),
  'rota /api/manutencao/recursos': /Path:\s*\/api\/manutencao\/recursos\b/.test(s),
  'rota POST /api/reservas': /Path:\s*\/api\/reservas\s*[\r\n]/.test(s),
  'rota /api/reservas/disponibilidade': /Path:\s*\/api\/reservas\/disponibilidade/.test(s),
  'rota /api/reservas/atendente': /Path:\s*\/api\/reservas\/atendente/.test(s),
  'rota /api/assistente/sugestoes': /Path:\s*\/api\/assistente\/sugestoes/.test(s),
  // IAM de menor privilegio por funcao (NF3.6).
  'Bedrock InvokeModel (so assistente)': (s.match(/^\s*-\s*bedrock:InvokeModel\s*$/gm) || []).length === 1,
  'SES SendEmail (so notificacao)': (s.match(/^\s*-\s*ses:SendEmail\s*$/gm) || []).length === 1,
  'EventBridgePutEvents (reserva/health)': (s.match(/EventBridgePutEventsPolicy/g) || []).length >= 1,
  'sem tabs': semTabs,
};
let tudoOk = true;
for (const [k, v] of Object.entries(checks)) {
  console.log((v === true || typeof v === 'number' ? 'OK  ' : 'FALHA ') + k + ' = ' + v);
  if (v === false) tudoOk = false;
}
console.log('RESULTADO: ' + (tudoOk ? 'TEMPLATE OK' : 'TEMPLATE COM FALHAS'));
process.exit(tudoOk ? 0 : 1);
