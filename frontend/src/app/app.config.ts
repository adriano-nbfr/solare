import { ApplicationConfig } from '@angular/core';
import { provideConfiguracaoBasica } from '@dsmpf/ngx-dsmpf/inicializacao';
import { environment } from '../environments/environment';
import { appRoutes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideConfiguracaoBasica({
      parametrosAplicacao: environment.parametrosAplicacao,
      rotas: {
        primeiroNivel: appRoutes,
        gerarEstruturaPadrao: true
      }
    })
  ]
};
