import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { EtiquetaPipe, InicialesPipe } from '../core/formato';
import { Guilloche } from '../core/guilloche';
import { Icono, NombreIcono } from '../core/icono';
import { Rol } from '../core/models';

interface Opcion {
  ruta: string;
  etiqueta: string;
  icono: NombreIcono;
  roles?: Rol[];
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icono, Guilloche, EtiquetaPipe, InicialesPipe],
  template: `
    <div class="shell" [class.menu-abierto]="menuAbierto()">
      <header class="barra-movil">
        <button class="icono-solo" type="button" (click)="menuAbierto.set(true)"
                aria-label="Abrir menú" [attr.aria-expanded]="menuAbierto()" aria-controls="menu-principal">
          <app-icono nombre="menu" [tamano]="22" />
        </button>
        <span class="marca"><span class="logo"><app-guilloche [densidad]="2" detalle="simple" /></span>VeriPay</span>
      </header>

      <div class="velo" (click)="menuAbierto.set(false)" aria-hidden="true"></div>

      <aside class="sidebar" id="menu-principal">
        <div class="marca">
          <span class="logo"><app-guilloche [densidad]="2" detalle="simple" /></span>
          <span>VeriPay</span>
          <button class="cerrar icono-solo" type="button" (click)="menuAbierto.set(false)" aria-label="Cerrar menú">
            <app-icono nombre="cerrar" />
          </button>
        </div>

        <nav aria-label="Principal">
          @for (op of opcionesVisibles(); track op.ruta) {
            <a [routerLink]="op.ruta" routerLinkActive="activo" ariaCurrentWhenActive="page" (click)="menuAbierto.set(false)">
              <app-icono [nombre]="op.icono" [tamano]="19" />
              {{ op.etiqueta }}
            </a>
          }
        </nav>

        <div class="microtexto" aria-hidden="true"></div>

        <div class="usuario">
          <span class="avatar" aria-hidden="true">{{ auth.usuario()?.nombre | iniciales }}</span>
          <div class="datos">
            <strong>{{ auth.usuario()?.nombre }}</strong>
            <small>{{ auth.usuario()?.rol | etiqueta }}</small>
          </div>
          <button class="icono-solo" type="button" (click)="auth.logout()" aria-label="Cerrar sesión" title="Cerrar sesión">
            <app-icono nombre="salir" />
          </button>
        </div>
      </aside>

      <main id="contenido">
        <router-outlet />
      </main>
    </div>
  `,
  styles: `
    .shell { display: grid; grid-template-columns: 252px 1fr; min-height: 100dvh; }
    .sidebar { background: var(--tinta); color: var(--tinta-texto); padding: 22px 14px 16px; position: sticky; top: 0; height: 100dvh;
               display: flex; flex-direction: column; gap: 4px; }
    .marca { display: flex; align-items: center; gap: 11px; font-weight: 600; font-size: 1.08rem; letter-spacing: -.02em;
             color: var(--tinta-claro); padding: 0 8px 28px; }
    .logo { width: 32px; height: 32px; border-radius: 50%; background: var(--tinta-2); color: var(--lima);
            display: grid; place-items: center; padding: 3px; box-shadow: inset 0 0 0 1px rgb(196 242 90 / .25); }
    .logo app-guilloche { width: 100%; height: 100%; }
    nav { display: flex; flex-direction: column; gap: 2px; flex: 1; }
    nav a { display: flex; align-items: center; gap: 12px; min-height: 42px; padding: 0 12px; border-radius: var(--r-control);
            color: inherit; text-decoration: none; font-weight: 500; position: relative; transition: background-color var(--t), color var(--t); }
    nav a:hover { background: rgb(255 255 255 / .05); color: var(--tinta-claro); }
    nav a.activo { background: rgb(196 242 90 / .09); color: var(--tinta-claro); }
    nav a.activo app-icono { color: var(--lima); }
    nav a.activo::before { content: ""; position: absolute; left: -14px; top: 11px; bottom: 11px; width: 3px; border-radius: 0 3px 3px 0; background: var(--lima); }
    nav a:focus-visible { box-shadow: 0 0 0 2px var(--lima); }
    .microtexto { color: rgb(196 242 90 / .22); margin: 12px 8px; }
    .usuario { display: flex; align-items: center; gap: 10px; padding: 12px 8px 0; border-top: 1px solid rgb(255 255 255 / .07); }
    .usuario .avatar { background: rgb(196 242 90 / .12); color: var(--lima); border-color: transparent; }
    .datos { display: flex; flex-direction: column; min-width: 0; flex: 1; line-height: 1.3; }
    .datos strong { color: var(--tinta-claro); font-weight: 600; font-size: .9rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .datos small { font-size: .78rem; }
    .icono-solo { display: grid; place-items: center; width: 40px; height: 40px; padding: 0; border: 0; border-radius: var(--r-control);
                  background: transparent; color: inherit; cursor: pointer; transition: background-color var(--t), color var(--t); }
    .sidebar .icono-solo:hover { background: rgb(255 255 255 / .07); color: var(--tinta-claro); }
    .cerrar { display: none; margin-left: auto; }
    main { padding: 40px 44px; max-width: 1280px; width: 100%; min-width: 0; }
    .barra-movil, .velo { display: none; }
    @media (max-width: 860px) {
      .shell { grid-template-columns: 1fr; }
      .barra-movil { display: flex; align-items: center; gap: 8px; padding: 8px 12px; position: sticky; top: 0; z-index: 5;
                     background: var(--tinta); color: var(--tinta-claro); }
      .barra-movil .marca { padding: 0; }
      .sidebar { position: fixed; z-index: 20; left: 0; width: 280px; transform: translateX(-100%); transition: transform 260ms var(--curva); }
      .menu-abierto .sidebar { transform: none; }
      .velo { display: block; position: fixed; inset: 0; z-index: 15; background: rgb(4 6 5 / .6); opacity: 0; pointer-events: none; transition: opacity 260ms; }
      .menu-abierto .velo { opacity: 1; pointer-events: auto; }
      .cerrar { display: grid; }
      main { padding: 24px 16px 40px; }
    }
  `
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly menuAbierto = signal(false);

  private readonly opciones: Opcion[] = [
    { ruta: '/tablero', etiqueta: 'Tablero', icono: 'tablero' },
    { ruta: '/clientes', etiqueta: 'Clientes', icono: 'clientes' },
    { ruta: '/cuentas', etiqueta: 'Cuentas', icono: 'cuentas' },
    { ruta: '/transferencias', etiqueta: 'Transferencias', icono: 'transferencias' },
    { ruta: '/auditoria', etiqueta: 'Auditoría', icono: 'auditoria', roles: ['ADMIN', 'AUDITOR'] }
  ];

  protected readonly opcionesVisibles = computed(() =>
    this.opciones.filter((op) => !op.roles || this.auth.tieneRol(...op.roles)));

  @HostListener('document:keydown.escape')
  protected cerrarConEscape(): void {
    this.menuAbierto.set(false);
  }
}
