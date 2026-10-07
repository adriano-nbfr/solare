import { DsParametrosAplicacao } from '@dsmpf/ngx-dsmpf/configuracao';
import { DsParametrosSeguranca } from '@dsmpf/ngx-dsmpf/seguranca';
import { papeis } from '../app/shared/papeis';

// Parâmetros específicos da aplicação Solare.
const parametrosAplicacao: DsParametrosAplicacao = {
  nome: 'Solare',
  sigla: 'solare',
  logo: {
    src: 'logo-dsmpf.svg',
  },
  basePath: '/',
  api: {
    raiz: '/api',
    informacaoSistema: '/api/public/informacao-sistema',
  },
};

// Endpoints de segurança compatíveis com o padrão DSMPF (NF5.2),
// expostos pelo BFF em `/api/__seguranca/*`.
const parametrosSeguranca: DsParametrosSeguranca = {
  papelAdmin: papeis.PAPEL_ADMIN,
  api: {
    relativoBasePath: true,
    usuarioAutenticado: '/api/__seguranca/usuario',
    atuacoes: '/api/__seguranca/atuacoes-json',
    papeis: '/api/__seguranca/papeis',
    atuacaoCorrente: '/api/__seguranca/atuacoes/atual',
    atuacaoEscolha: '/api/__seguranca/atuacoes',
    logout: 'logout',
  },
  csrf: {
    endpointToken: '/api/__seguranca/csrf',
  },
};

/** Parâmetros de ambiente usados na configuração da aplicação. */
export const environment = {
  production: false,
  parametrosAplicacao,
  parametrosSeguranca,
};
