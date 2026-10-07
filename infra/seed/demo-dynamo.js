'use strict';
// Orquestra a demo da camada de dados do Solare no DynamoDB real.
// Usa somente AWS CLI (profile hackaton / us-east-1) via execFileSync. Node nativo.
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const LOG = 'c:/Users/usuario/solare/_demo.log';
const TABLE = 'solare-desenv';
const BASE = ['--profile','hackaton','--region','us-east-1'];
let out = [];
function log(s){ out.push(s); fs.writeFileSync(LOG, out.join('\n')); }
function aws(args){
  try { return execFileSync('aws', args.concat(BASE), { encoding:'utf8' }); }
  catch(e){ return (e.stdout||'') + (e.stderr||'') + ' [ERRO] ' + e.message; }
}
function query(label, args){
  const r = aws(['dynamodb','query','--table-name',TABLE].concat(args).concat(['--output','json']));
  let count = '?';
  try { count = JSON.parse(r).Count; } catch(_) {}
  log('\n=== ' + label + ' === (itens: ' + count + ')');
  log(r.length > 4000 ? r.slice(0,4000) + '\n...[truncado]' : r);
}
// 1) Esperar a tabela ACTIVE
log('Aguardando tabela ' + TABLE + ' ficar ACTIVE...');
aws(['dynamodb','wait','table-exists','--table-name',TABLE]);
log('Tabela ACTIVE.');
// 2) Habilitar TTL (campo expiraEm)
log('\nHabilitando TTL (expiraEm)...');
log(aws(['dynamodb','update-time-to-live','--table-name',TABLE,'--time-to-live-specification','Enabled=true,AttributeName=expiraEm']));
// 3) Rodar o seed (reutiliza seed.js existente)
log('\nRodando seed.js...');
try {
  const seedOut = execFileSync('node', ['c:/Users/usuario/solare/infra/seed/seed.js','--table',TABLE], { encoding:'utf8' });
  log(seedOut);
} catch(e){ log('[seed ERRO] ' + (e.stdout||'') + (e.stderr||'') + e.message); }
// 4) Queries dos cenarios de demonstracao
// 4a) Hierarquia: filhos do 3o Andar via GSI1 (PAI#amb-andar-3)
query('CENARIO HIERARQUIA - filhos do 3o Andar (GSI1 PAI#amb-andar-3)',
  ['--index-name','GSI1','--key-condition-expression','GSI1PK = :p',
   '--expression-attribute-values','{":p":{"S":"PAI#amb-andar-3"}}']);
// 4b) Reservas da Sala 301 (particao AMB#amb-sala-301, SK begins_with RES#)
query('RESERVAS da Sala 301 (particao AMB#amb-sala-301)',
  ['--key-condition-expression','PK = :pk AND begins_with(SK, :sk)',
   '--expression-attribute-values','{":pk":{"S":"AMB#amb-sala-301"},":sk":{"S":"RES#"}}']);
// 4c) Usos do projetor (particao REC#rec-projetor, SK begins_with USO#) -> cenario estouro 2/2
query('CENARIO ESTOURO - usos do Projetor (REC#rec-projetor, 2 unidades no total)',
  ['--key-condition-expression','PK = :pk AND begins_with(SK, :sk)',
   '--expression-attribute-values','{":pk":{"S":"REC#rec-projetor"},":sk":{"S":"USO#"}}']);
// 4d) Reservas por solicitante Ana (GSI2 SOLIC#u-ana)
query('RESERVAS da Ana (GSI2 SOLIC#u-ana)',
  ['--index-name','GSI2','--key-condition-expression','GSI2PK = :p',
   '--expression-attribute-values','{":p":{"S":"SOLIC#u-ana"}}']);
log('\n==== DEMO CONCLUIDA ====');
