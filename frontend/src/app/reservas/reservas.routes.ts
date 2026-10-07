import { Routes } from '@angular/router';

/**
 * Rotas da área do Solicitante (F5/F4). O acesso ao bloco já é protegido pela
 * guarda de autenticação em `app.routes.ts` (NF5.3); as sub-rotas descrevem as
 * telas disponíveis ao usuário autenticado.
 */
const routes: Routes = [
  {
    path: 'disponibilidade',
    title: 'Disponibilidade de ambientes',
    loadComponent: () =>
      import('./grade-disponibilidade').then((m) => m.GradeDisponibilidadeComponent),
  },
  {
    path: 'assistente',
    title: 'Assistente de reserva',
    loadComponent: () =>
      import('./assistente-reserva').then((m) => m.AssistenteReservaComponent),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'disponibilidade',
  },
];

export default routes;
