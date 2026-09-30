import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tablero' },
      { path: 'tablero', title: 'Tablero · VeriPay',
        loadComponent: () => import('./pages/tablero/tablero').then((m) => m.TableroPage) },
      { path: 'clientes', title: 'Clientes · VeriPay',
        loadComponent: () => import('./pages/clientes/clientes-lista').then((m) => m.ClientesLista) },
      { path: 'clientes/nuevo', title: 'Nuevo cliente · VeriPay',
        loadComponent: () => import('./pages/clientes/cliente-nuevo').then((m) => m.ClienteNuevo) },
      { path: 'clientes/:id', title: 'Cliente · VeriPay',
        loadComponent: () => import('./pages/clientes/cliente-detalle').then((m) => m.ClienteDetalle) },
      { path: 'cuentas', title: 'Cuentas · VeriPay',
        loadComponent: () => import('./pages/cuentas/cuentas').then((m) => m.CuentasPage) },
      { path: 'transferencias', title: 'Transferencias · VeriPay',
        loadComponent: () => import('./pages/transferencias/transferencias').then((m) => m.TransferenciasPage) },
      { path: 'auditoria', title: 'Auditoría · VeriPay',
        loadComponent: () => import('./pages/auditoria/auditoria').then((m) => m.AuditoriaPage) }
    ]
  },
  { path: '**', redirectTo: '' }
];
