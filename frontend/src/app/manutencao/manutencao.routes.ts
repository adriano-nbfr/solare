import { Routes } from '@angular/router';

/**
 * Rotas de manutenção (cadastros ADMIN): Setores (F1), Ambientes (F2) e
 * Recursos (F3). O acesso ao bloco já é protegido pela guarda de papel ADMIN
 * em `app.routes.ts`.
 */
const routes: Routes = [
  {
    path: 'setores',
    title: 'Setores',
    loadComponent: () => import('./setores/setores').then(m => m.Setores)
  },
  {
    path: 'ambientes',
    title: 'Ambientes',
    loadComponent: () => import('./ambientes/ambientes').then(m => m.Ambientes)
  },
  {
    path: 'recursos',
    title: 'Recursos',
    loadComponent: () => import('./recursos/recursos').then(m => m.Recursos)
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'setores'
  }
];

export default routes;
