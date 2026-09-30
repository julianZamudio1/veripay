import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService, mensajeError } from '../../core/api.service';
import { EventoAuditoria, Pagina } from '../../core/models';

@Component({
  selector: 'app-auditoria',
  imports: [DatePipe],
  template: `
    <h1>Auditoría</h1>
    <p class="subtitulo">Bitácora de todas las operaciones (se registra en la misma transacción que la operación)</p>

    @if (error()) {
      <div class="aviso error" role="alert">{{ error() }}</div>
    }

    <div class="tarjeta">
      <div class="tabla-contenedor">
        <table>
          <thead><tr><th>Fecha</th><th>Usuario</th><th>Acción</th><th>Entidad</th><th>Detalle</th></tr></thead>
          <tbody>
            @for (e of pagina()?.contenido ?? []; track e.id) {
              <tr>
                <td>{{ e.creadoEn | date: 'medium' }}</td>
                <td>{{ e.usuario }}</td>
                <td><strong>{{ e.accion }}</strong></td>
                <td class="mono">{{ e.entidad }}{{ e.entidadId ? ' #' + e.entidadId.substring(0, 8) : '' }}</td>
                <td>{{ e.detalle ?? '' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="vacio">Sin eventos</td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (pagina(); as p) {
        @if (p.totalPaginas > 1) {
          <div class="paginador">
            <button class="btn secundario chico" [disabled]="p.pagina === 0" (click)="cargar(p.pagina - 1)">Anterior</button>
            <span>Página {{ p.pagina + 1 }} de {{ p.totalPaginas }}</span>
            <button class="btn secundario chico" [disabled]="p.pagina + 1 >= p.totalPaginas" (click)="cargar(p.pagina + 1)">Siguiente</button>
          </div>
        }
      }
    </div>
  `
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
}
