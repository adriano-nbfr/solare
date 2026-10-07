import { DsParametrosAplicacao } from '@dsmpf/ngx-dsmpf/configuracao';

const API = 'https://xlr09cevv4.execute-api.us-east-1.amazonaws.com/desenv';

const parametrosAplicacao: DsParametrosAplicacao = {
  nome: 'Solare',
  sigla: 'solare',
  logo: { src: 'logo-dsmpf.svg' },
  basePath: '/',
  api: {
    raiz: API + '/api',
    informacaoSistema: API + '/api/public/informacao-sistema',
  },
};

export const environment = {
  production: true,
  parametrosAplicacao,
};
