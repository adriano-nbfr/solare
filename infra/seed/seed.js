#!/usr/bin/env node
/*
 * Seed de demonstracao do Solare (Task 16).
 *
 * Popula a tabela single-table do DynamoDB com dados ficticios coerentes para a
 * demo de 5 minutos (LGPD: apenas dados ficticios — NF3): setores, ambientes com
 * hierarquia pai/filho, recursos limitados e ilimitados, e reservas de exemplo
 * que exercitam CONFLITO de horario/margem, HIERARQUIA (pai x filho) e ESTOURO de
 * recurso limitado.
 *
 * Restricoes do ambiente: usa SOMENTE APIs nativas do Node (sem npm/deps). A
 * escrita no DynamoDB e feita via AWS CLI (`aws dynamodb batch-write-item`),
 * sempre com `--profile hackaton` e `--region us-east-1` (regra do projeto).
 *
 * Uso:
 *   node seed.js                       # tabela solare-desenv (padrao)
 *   node seed.js --table solare-homolog
 *   node seed.js --dry-run             # imprime os itens sem escrever
 *   TABELA_SOLARE=solare-desenv node seed.js
 *
 * O id de cada entidade e deterministico (prefixo + sufixo fixo), de modo que
 * reexecutar o seed sobrescreve os mesmos itens (idempotente para a demo).
 */

'use strict';

const { execFileSync } = require('node:child_process');
const { writeFileSync, mkdtempSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');

const PROFILE = 'hackaton';
const REGION = 'us-east-1';

// ---------------------------------------------------------------------------
// Argumentos
// ---------------------------------------------------------------------------
function parseArgs(argv) {
  const args = { table: process.env.TABELA_SOLARE || 'solare-desenv', dryRun: false };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--dry-run') args.dryRun = true;
    else if (a === '--table') args.table = argv[++i];
    else if (a.startsWith('--table=')) args.table = a.slice('--table='.length);
    else if (a === '--help' || a === '-h') args.help = true;
  }
  return args;
}

// ---------------------------------------------------------------------------
// Helpers de AttributeValue (formato wire do DynamoDB)
// ---------------------------------------------------------------------------
const S = (v) => ({ S: String(v) });
const N = (v) => ({ N: String(v) });
const M = (obj) => ({ M: obj });
const L = (arr) => ({ L: arr });

const ISO = DateTimeIso; // formatador local (abaixo)
function DateTimeIso(d) {
  // yyyy-MM-ddTHH:mm:ss (ISO local sem offset — igual ao ChavesSolare do backend)
  const p = (n, w = 2) => String(n).padStart(w, '0');
  return (
    `${d.y}-${p(d.mo)}-${p(d.da)}T${p(d.h)}:${p(d.mi)}:${p(d.s || 0)}`
  );
}

// Data base da demo: amanha, para as reservas caírem no futuro proximo.
function dataBase() {
  const now = new Date();
  const t = new Date(now.getTime() + 24 * 60 * 60 * 1000);
  return { y: t.getFullYear(), mo: t.getMonth() + 1, da: t.getDate() };
}

function dt(base, h, mi) {
  return { y: base.y, mo: base.mo, da: base.da, h, mi, s: 0 };
}

function dataIso(base) {
  const p = (n) => String(n).padStart(2, '0');
  return `${base.y}-${p(base.mo)}-${p(base.da)}`;
}

// ---------------------------------------------------------------------------
// Montadores de itens (chaves identicas as do backend: ChavesSolare)
// ---------------------------------------------------------------------------
function itemSetor(id, nome, sigla, email) {
  return {
    PK: S(`SETOR#${id}`), SK: S('META'), tipo: S('Setor'),
    id: S(id), nome: S(nome), sigla: S(sigla), emailNotificacao: S(email),
  };
}

function itemAmbiente(id, nome, setorId, capacidade, paiId) {
  const it = {
    PK: S(`AMB#${id}`), SK: S('META'), tipo: S('Ambiente'),
    id: S(id), nome: S(nome), setorId: S(setorId), capacidade: N(capacidade),
  };
  if (paiId) {
    it.ambientePaiId = S(paiId);
    it.GSI1PK = S(`PAI#${paiId}`);
    it.GSI1SK = S(`AMB#${id}`);
  }
  return it;
}

function itemRecurso(id, nome, tipoRecurso, quantidadeTotal) {
  const it = {
    PK: S(`REC#${id}`), SK: S('META'), tipo: S('Recurso'),
    id: S(id), nome: S(nome), tipoRecurso: S(tipoRecurso),
  };
  if (tipoRecurso === 'LIMITADO') it.quantidadeTotal = N(quantidadeTotal);
  return it;
}

function itemReserva(r) {
  const inicioIso = ISO(r.inicio);
  const fimIso = ISO(r.fim);
  const dataIsoStr = `${r.inicio.y}-${String(r.inicio.mo).padStart(2, '0')}-${String(r.inicio.da).padStart(2, '0')}`;
  const sk = `RES#${inicioIso}#${r.id}`;
  const it = {
    PK: S(`AMB#${r.ambienteId}`), SK: S(sk), tipo: S('Reserva'),
    id: S(r.id), ambienteId: S(r.ambienteId),
    inicio: S(inicioIso), fim: S(fimIso), status: S(r.status || 'CONFIRMADA'),
    solicitanteId: S(r.solicitanteId), solicitanteNome: S(r.solicitanteNome),
    finalidade: S(r.finalidade), snp: S(r.snp),
    GSI2PK: S(`SOLIC#${r.solicitanteId}`), GSI2SK: S(sk),
    GSI3PK: S(`DATA#${dataIsoStr}`), GSI3SK: S(sk),
  };
  if (r.recursos && r.recursos.length) {
    it.recursos = L(r.recursos.map((rr) => M({
      recursoId: S(rr.recursoId), quantidade: N(rr.quantidade),
    })));
  }
  return it;
}

function itemUso(u) {
  const inicioIso = ISO(u.inicio);
  const fimIso = ISO(u.fim);
  return {
    PK: S(`REC#${u.recursoId}`), SK: S(`USO#${inicioIso}#${u.reservaId}`),
    tipo: S('UsoRecurso'), recursoId: S(u.recursoId), reservaId: S(u.reservaId),
    quantidade: N(u.quantidade), inicio: S(inicioIso), fim: S(fimIso),
    ambienteId: S(u.ambienteId),
  };
}

// ---------------------------------------------------------------------------
// Dataset de demonstracao
// ---------------------------------------------------------------------------
function construirDataset() {
  const base = dataBase();
  const itens = [];

  // --- Setores (F1) --------------------------------------------------------
  const setorTi = 'setor-ti';
  const setorAdm = 'setor-adm';
  itens.push(itemSetor(setorTi, 'Secretaria de Tecnologia', 'STI', 'sti.notificacoes@exemplo.test'));
  itens.push(itemSetor(setorAdm, 'Secretaria de Administracao', 'SADM', 'sadm.notificacoes@exemplo.test'));

  // --- Ambientes com hierarquia (F2) --------------------------------------
  // Predio (raiz) -> Andar 3 (filho) -> Sala 301 e Sala 302 (netos).
  const predio = 'amb-predio-sede';
  const andar3 = 'amb-andar-3';
  const sala301 = 'amb-sala-301';
  const sala302 = 'amb-sala-302';
  const auditorio = 'amb-auditorio';
  itens.push(itemAmbiente(predio, 'Predio Sede', setorAdm, 500, null));
  itens.push(itemAmbiente(andar3, '3o Andar', setorAdm, 120, predio));
  itens.push(itemAmbiente(sala301, 'Sala de Reuniao 301', setorTi, 12, andar3));
  itens.push(itemAmbiente(sala302, 'Sala de Reuniao 302', setorTi, 8, andar3));
  itens.push(itemAmbiente(auditorio, 'Auditorio Principal', setorAdm, 200, predio));

  // --- Recursos limitados e ilimitados (F3) -------------------------------
  const projetor = 'rec-projetor';     // LIMITADO: 2 unidades
  const notebook = 'rec-notebook';     // LIMITADO: 5 unidades
  const wifi = 'rec-wifi';             // ILIMITADO
  itens.push(itemRecurso(projetor, 'Projetor multimidia', 'LIMITADO', 2));
  itens.push(itemRecurso(notebook, 'Notebook de apoio', 'LIMITADO', 5));
  itens.push(itemRecurso(wifi, 'Acesso Wi-Fi', 'ILIMITADO', null));

  const ana = { id: 'u-ana', nome: 'Ana Souza (ficticia)' };
  const bruno = { id: 'u-bruno', nome: 'Bruno Lima (ficticio)' };
  const carla = { id: 'u-carla', nome: 'Carla Dias (ficticia)' };

  // --- Cenario 1: CONFLITO de horario/margem na mesma sala (RN1-RN3) -------
  // Reserva A na Sala 301 das 09:00 as 10:00. Uma nova reserva 10:15-11:00
  // (dentro da margem de 30 min) deve CONFLITAR na demo ao ser criada via API.
  itens.push(itemReserva({
    id: 'res-301-manha', ambienteId: sala301,
    inicio: dt(base, 9, 0), fim: dt(base, 10, 0),
    solicitanteId: ana.id, solicitanteNome: ana.nome,
    finalidade: 'Planejamento de sprint', snp: 'SNP-DEMO-0001',
    recursos: [{ recursoId: wifi, quantidade: 1 }],
  }));

  // --- Cenario 2: HIERARQUIA (pai ocupado bloqueia filho e vice-versa) -----
  // Reserva no 3o Andar (pai de 301/302) das 14:00 as 16:00. Tentar reservar a
  // Sala 301 (filho) nesse intervalo deve CONFLITAR por hierarquia (RN4-RN6).
  itens.push(itemReserva({
    id: 'res-andar3-tarde', ambienteId: andar3,
    inicio: dt(base, 14, 0), fim: dt(base, 16, 0),
    solicitanteId: bruno.id, solicitanteNome: bruno.nome,
    finalidade: 'Treinamento do andar inteiro', snp: 'SNP-DEMO-0002',
    recursos: [],
  }));

  // --- Cenario 3: ESTOURO de recurso limitado (RN7-RN9) --------------------
  // Projetor tem 2 unidades. Duas reservas simultaneas (11:00-12:00) ja usam as
  // 2 unidades (1 + 1); uma terceira solicitacao do projetor no mesmo intervalo
  // deve ESTOURAR o recurso ao ser criada via API.
  const projInicio = dt(base, 11, 0);
  const projFim = dt(base, 12, 0);
  itens.push(itemReserva({
    id: 'res-301-proj', ambienteId: sala301,
    inicio: projInicio, fim: projFim,
    solicitanteId: carla.id, solicitanteNome: carla.nome,
    finalidade: 'Apresentacao com projetor', snp: 'SNP-DEMO-0003',
    recursos: [{ recursoId: projetor, quantidade: 1 }],
  }));
  itens.push(itemUso({
    recursoId: projetor, reservaId: 'res-301-proj', ambienteId: sala301,
    quantidade: 1, inicio: projInicio, fim: projFim,
  }));
  itens.push(itemReserva({
    id: 'res-302-proj', ambienteId: sala302,
    inicio: projInicio, fim: projFim,
    solicitanteId: ana.id, solicitanteNome: ana.nome,
    finalidade: 'Demonstracao de produto', snp: 'SNP-DEMO-0004',
    recursos: [{ recursoId: projetor, quantidade: 1 }],
  }));
  itens.push(itemUso({
    recursoId: projetor, reservaId: 'res-302-proj', ambienteId: sala302,
    quantidade: 1, inicio: projInicio, fim: projFim,
  }));

  // --- Reserva "limpa" no auditorio (fluxo feliz na grade/cards) -----------
  itens.push(itemReserva({
    id: 'res-auditorio', ambienteId: auditorio,
    inicio: dt(base, 9, 0), fim: dt(base, 11, 0),
    solicitanteId: bruno.id, solicitanteNome: bruno.nome,
    finalidade: 'Palestra institucional', snp: 'SNP-DEMO-0005',
    recursos: [{ recursoId: wifi, quantidade: 1 }],
  }));

  return { itens, base };
}

// ---------------------------------------------------------------------------
// Escrita via AWS CLI (batch-write-item em lotes de 25)
// ---------------------------------------------------------------------------
function escrever(tabela, itens) {
  const lotes = [];
  for (let i = 0; i < itens.length; i += 25) lotes.push(itens.slice(i, i + 25));

  const dir = mkdtempSync(join(tmpdir(), 'solare-seed-'));
  let gravados = 0;
  lotes.forEach((lote, idx) => {
    const payload = {};
    payload[tabela] = lote.map((item) => ({ PutRequest: { Item: item } }));
    const arquivo = join(dir, `lote-${idx}.json`);
    writeFileSync(arquivo, JSON.stringify(payload));
    execFileSync('aws', [
      'dynamodb', 'batch-write-item',
      '--request-items', `file://${arquivo}`,
      '--profile', PROFILE, '--region', REGION,
    ], { stdio: 'inherit' });
    gravados += lote.length;
    console.log(`  lote ${idx + 1}/${lotes.length}: ${lote.length} itens gravados`);
  });
  return gravados;
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------
function main() {
  const args = parseArgs(process.argv);
  if (args.help) {
    console.log('Uso: node seed.js [--table <nome>] [--dry-run]');
    console.log('Profile AWS fixo: hackaton | Regiao: us-east-1');
    return;
  }

  const { itens, base } = construirDataset();
  console.log(`Solare seed -> tabela "${args.table}" (regiao ${REGION}, profile ${PROFILE})`);
  console.log(`Data base das reservas de demo: ${dataIso(base)}`);
  console.log(`Total de itens: ${itens.length}`);

  if (args.dryRun) {
    console.log('\n[--dry-run] itens que seriam gravados:\n');
    console.log(JSON.stringify(itens, null, 2));
    return;
  }

  const gravados = escrever(args.table, itens);
  console.log(`\nConcluido: ${gravados} itens gravados em "${args.table}".`);
  console.log('Cenarios semeados: conflito de horario (Sala 301), hierarquia (3o Andar x salas) e estouro de projetor (2/2 unidades).');
}

main();
