import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Cliente, Cuenta, Verificacion } from '../../core/models';

type TipoFoto = 'identificacion' | 'selfie';

@Component({
  selector: 'app-cliente-detalle',
  imports: [CurrencyPipe, DatePipe, PercentPipe, RouterLink],
  template: `
    <a routerLink="/clientes">← Clientes</a>

    @if (error()) {
      <div class="aviso error" role="alert">{{ error() }}</div>
    }
    @if (exito()) {
      <div class="aviso ok" role="status">{{ exito() }}</div>
    }

    @if (cliente(); as c) {
      <div class="encabezado">
        <div>
          <h1>{{ c.nombreCompleto }}</h1>
          <p class="subtitulo"><span class="mono">{{ c.curp }}</span> · <span class="chip" [class]="c.estadoKyc">KYC {{ c.estadoKyc }}</span></p>
        </div>
      </div>

      <div class="dos-columnas">
        <section class="tarjeta">
          <h2>Datos</h2>
          <dl>
            <dt>RFC</dt><dd class="mono">{{ c.rfc ?? '—' }}</dd>
            <dt>Nacimiento</dt><dd>{{ c.fechaNacimiento | date: 'longDate' : 'UTC' }}</dd>
            <dt>Correo</dt><dd>{{ c.email }}</dd>
            <dt>Teléfono</dt><dd>{{ c.telefono ?? '—' }}</dd>
            <dt>Alta</dt><dd>{{ c.creadoEn | date: 'medium' }}</dd>
          </dl>
        </section>

        @if (c.estadoKyc !== 'VERIFICADO' && puedeOperar) {
          <section class="tarjeta">
            <h2>Verificación biométrica</h2>
            <p class="ayuda">Sube la foto de la identificación (INE) y una selfie. Se comparan y se obtiene un puntaje;
              las imágenes no se almacenan, solo su huella SHA-256.</p>
            <div class="fotos">
              @for (tipo of tipos; track tipo) {
                <label class="foto">
                  @if (previews()[tipo]; as src) {
                    <img [src]="src" [alt]="tipo" />
                  } @else {
                    <span>{{ tipo === 'identificacion' ? 'Identificación' : 'Selfie' }}</span>
                  }
                  <input type="file" accept="image/png,image/jpeg" (change)="elegir(tipo, $event)" />
                </label>
              }
            </div>
            <button class="btn" type="button" (click)="verificar(c.id)"
                    [disabled]="!archivos.identificacion || !archivos.selfie || procesando()">
              {{ procesando() ? 'Verificando…' : 'Verificar identidad' }}
            </button>
          </section>
        }
      </div>

      <section class="tarjeta">
        <div class="encabezado">
          <h2>Cuentas</h2>
          @if (c.estadoKyc === 'VERIFICADO' && puedeOperar) {
            <button class="btn chico" type="button" (click)="abrirCuenta(c.id)" [disabled]="procesando()">+ Abrir cuenta</button>
          }
        </div>
        <div class="tabla-contenedor">
          <table>
            <thead><tr><th>CLABE</th><th>Estado</th><th class="num">Saldo</th><th>Apertura</th></tr></thead>
            <tbody>
              @for (k of cuentas(); track k.id) {
                <tr>
                  <td class="mono">{{ k.clabe }}</td>
                  <td><span class="chip" [class]="k.estado">{{ k.estado }}</span></td>
                  <td class="num">{{ k.saldo | currency: 'MXN' : 'symbol-narrow' }}</td>
                  <td>{{ k.creadoEn | date: 'mediumDate' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="vacio">
                  {{ c.estadoKyc === 'VERIFICADO' ? 'Sin cuentas' : 'Verifica la identidad para poder abrir cuentas' }}
                </td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <section class="tarjeta">
        <h2>Historial de verificaciones</h2>
        <div class="tabla-contenedor">
          <table>
            <thead><tr><th>Fecha</th><th>Resultado</th><th class="num">Puntaje</th><th class="num">Umbral</th><th>Analista</th></tr></thead>
            <tbody>
              @for (v of verificaciones(); track v.id) {
                <tr>
                  <td>{{ v.creadoEn | date: 'medium' }}</td>
                  <td><span class="chip" [class]="v.aprobada ? 'VERIFICADO' : 'RECHAZADO'">{{ v.aprobada ? 'Aprobada' : 'Rechazada' }}</span></td>
                  <td class="num">{{ v.puntaje | percent: '1.1-1' }}</td>
                  <td class="num">{{ v.umbral | percent: '1.0-0' }}</td>
                  <td>{{ v.realizadaPor }}</td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="vacio">Sin verificaciones</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  styles: `
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 8px 18px; margin: 0; }
    dt { color: var(--texto-suave); font-size: .88rem; }
    dd { margin: 0; }
    .fotos { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 14px 0; }
    .foto { aspect-ratio: 3 / 4; border: 2px dashed var(--borde); border-radius: var(--radio); display: grid; place-items: center;
            cursor: pointer; overflow: hidden; color: var(--texto-suave); position: relative; }
    .foto:hover { border-color: var(--acento); }
    .foto img { width: 100%; height: 100%; object-fit: cover; }
    .foto input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
  `
})
export class ClienteDetalle implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly tipos: TipoFoto[] = ['identificacion', 'selfie'];
  protected readonly cliente = signal<Cliente | null>(null);
  protected readonly cuentas = signal<Cuenta[]>([]);
  protected readonly verificaciones = signal<Verificacion[]>([]);
  protected readonly previews = signal<Partial<Record<TipoFoto, string>>>({});
  protected readonly procesando = signal(false);
  protected readonly error = signal('');
  protected readonly exito = signal('');
  protected readonly puedeOperar = this.auth.tieneRol('ADMIN', 'ANALISTA');
  protected archivos: Partial<Record<TipoFoto, File>> = {};

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    Object.values(this.previews()).forEach((url) => url && URL.revokeObjectURL(url));
  }

  private cargar(): void {
    const id = Number(this.id());
    forkJoin({
      cliente: this.api.cliente(id),
      cuentas: this.api.cuentas(id),
      verificaciones: this.api.verificaciones(id)
    }).subscribe({
      next: (r) => {
        this.cliente.set(r.cliente);
        this.cuentas.set(r.cuentas);
        this.verificaciones.set(r.verificaciones);
      },
      error: (e) => this.error.set(mensajeError(e))
    });
  }

  protected elegir(tipo: TipoFoto, evento: Event): void {
    const archivo = (evento.target as HTMLInputElement).files?.[0];
    if (!archivo) return;
    const anterior = this.previews()[tipo];
    if (anterior) URL.revokeObjectURL(anterior);
    this.archivos[tipo] = archivo;
    this.previews.update((p) => ({ ...p, [tipo]: URL.createObjectURL(archivo) }));
  }

  protected verificar(id: number): void {
    this.iniciar();
    this.api.verificar(id, this.archivos.identificacion!, this.archivos.selfie!).subscribe({
      next: (v) => {
        const pct = Math.round(v.puntaje * 1000) / 10;
        this.exito.set(v.aprobada
          ? `Identidad verificada (similitud ${pct}%)`
          : '');
        if (!v.aprobada) this.error.set(`Verificación rechazada: similitud ${pct}% por debajo del umbral`);
        this.archivos = {};
        this.previews.set({});
        this.procesando.set(false);
        this.cargar();
      },
      error: (e) => this.fallo(e)
    });
  }

  protected abrirCuenta(id: number): void {
    this.iniciar();
    this.api.abrirCuenta(id).subscribe({
      next: (k) => {
        this.exito.set(`Cuenta abierta con CLABE ${k.clabe}`);
        this.procesando.set(false);
        this.cargar();
      },
      error: (e) => this.fallo(e)
    });
  }

  private iniciar(): void {
    this.procesando.set(true);
    this.error.set('');
    this.exito.set('');
  }

  private fallo(e: unknown): void {
    this.error.set(mensajeError(e));
    this.procesando.set(false);
  }
}
