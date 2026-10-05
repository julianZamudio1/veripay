import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService, mensajeError } from '../../core/api.service';
import { InicialesPipe } from '../../core/formato';
import { Guilloche } from '../../core/guilloche';
import { Icono } from '../../core/icono';
import { EventoAuditoria, Pagina } from '../../core/models';

const ENTIDADES: Record<string, string> = {
  CLIENTE: 'Cliente', CUENTA: 'Cuenta', TRANSACCION: 'Transacción', USUARIO: 'Usuario'
};

@Component({
  host: { class: 'entrada' },
  selector: 'app-auditoria',
  imports: [DatePipe, Icono, InicialesPipe, Guilloche],
  template: `
    <h1>Auditoría</h1>
    <p class="subtitulo">Bitácora de operaciones. Cada evento se guarda en la misma transacción que la operación.</p>

    @if (error()) {
      <div class="aviso error" role="alert"><app-icono nombre="alerta" />{{ error() }}</div>
    }

    <section class="tarjeta" aria-labelledby="t-bitacora">
      <h2 id="t-bitacora" class="sr-only">Eventos</h2>
      <div class="tabla-contenedor">
        <table>
          <thead><tr><th scope="col">Fecha</th><th scope="col">Usuario</th><th scope="col">Acción</th><th scope="col">Entidad</th><th scope="col">Detalle</th></tr></thead>
          <tbody>
            @for (e of pagina()?.contenido ?? []; track e.id) {
              <tr>
                <td class="secundario-texto">{{ e.creadoEn | date: 'd MMM y, HH:mm:ss' }}</td>
                <td>
                  <div class="celda-nombre">
                    <span class="avatar chico" aria-hidden="true">{{ e.usuario | iniciales }}</span>{{ e.usuario }}
                  </div>
                </td>
                <td><span class="chip" [class]="claseAccion(e.accion)">{{ etiquetaAccion(e.accion) }}</span></td>
                <td class="secundario-texto">{{ etiquetaEntidad(e.entidad) }}@if (e.entidadId) { <span class="mono"> · {{ e.entidadId.substring(0, 8) }}</span> }</td>
                <td>{{ e.detalle ?? '' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="vacio"><app-guilloche [densidad]="2" detalle="simple" /><p>Sin eventos registrados.</p></td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (pagina(); as p) {
        @if (p.totalPaginas > 1) {
          <nav class="paginador" aria-label="Paginación">
            <span>{{ p.totalElementos }} eventos · página {{ p.pagina + 1 }} de {{ p.totalPaginas }}</span>
            <button class="btn secundario chico" [disabled]="p.pagina === 0" (click)="cargar(p.pagina - 1)">
              <app-icono nombre="flechaIzq" [tamano]="16" />Anterior</button>
            <button class="btn secundario chico" [disabled]="p.pagina + 1 >= p.totalPaginas" (click)="cargar(p.pagina + 1)">
              Siguiente<app-icono nombre="flechaDer" [tamano]="16" /></button>
          </nav>
        }
      }
    </section>
  `,
  styles: `.avatar.chico { width: 26px; height: 26px; font-size: .7rem; }`
})
export class AuditoriaPage implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly pagina = signal<Pagina<EventoAuditoria> | null>(null);
  protected readonly error = signal('');

  ngOnInit(): void {
    this.cargar(0);
  }

  cargar(pagina: number): void {
    this.api.auditoria(pagina).subscribe({
      next: (p) => this.pagina.set(p),
      error: (e) => this.error.set(mensajeError(e))
    });
  }

  /** LOGIN_FALLIDO → "Login fallido" */
  protected etiquetaAccion(accion: string): string {
    const texto = accion.toLowerCase().replace(/_/g, ' ').replace('deposito', 'depósito').replace('kyc', 'KYC');
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  protected etiquetaEntidad(entidad: string): string {
    return ENTIDADES[entidad] ?? entidad;
  }

  /** Color por significado: fallos y bloqueos en rojo, aprobaciones y altas en verde, dinero en azul. */
  protected claseAccion(accion: string): string {
    if (/FALLIDO|RECHAZADO|BLOQUEO/.test(accion) && !accion.startsWith('DESBLOQUEO')) return 'RECHAZADO';
    if (/APROBADO|ALTA|APERTURA|DESBLOQUEO/.test(accion)) return 'VERIFICADO';
    if (/DEPOSITO|RETIRO|TRANSFERENCIA/.test(accion)) return 'TRANSFERENCIA';
    return '';
  }
}
