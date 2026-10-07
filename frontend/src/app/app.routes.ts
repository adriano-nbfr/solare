import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { DsAppSeguranca } from '@dsmpf/ngx-dsmpf/seguranca';
import { papeis } from './shared/papeis';

/** Rotas de primeiro nível do Solare, com as guardas DsAppSeguranca (NF5.3). */
export const appRoutes = montarRotasPrimeiroNivel();

function montarRotasPrimeiroNivel() {
  const routes: Routes = [];

  routes.push(
    // Cadastros (ADMIN) — F1/F2/F3
    {
      path: 'manutencao',
      loadChildren: () => import('./manutencao/manutencao.routes')
    },
    // Painel do Atendente (ATENDENTE) — F6
    {
      path: 'atendente',
      title: 'Painel do Atendente',
      loadComponent: () =>
        import('./reservas/cards-atendente').then(m => m.CardsAtendenteComponent)
    },
    // Área do Solicitante (autenticado) — F4/F5/F7/INOV1
    {
      path: 'reservas',
      title: 'Minhas reservas',
      loadChildren: () => import('./reservas/reservas.routes')
    },
    {
      path: '',
      pathMatch: 'full',
      loadComponent: () => import('./home/home').then(m => m.Home)
    },
  );

  return routes;
}
