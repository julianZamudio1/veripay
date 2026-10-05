import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClabePipe, EtiquetaPipe, InicialesPipe } from '../../core/formato';
import { Guilloche } from '../../core/guilloche';
import { Sello } from '../../core/sello';
import { Icono, NombreIcono } from '../../core/icono';
import { Cliente, Cuenta, Verificacion } from '../../core/models';

type TipoFoto = 'identificacion' | 'selfie';

const MAX_BYTES = 5 * 1024 * 1024;

@Component({
  host: { class: 'entrada' },
  selector: 'app-cliente-detalle',
  imports: [CurrencyPipe, DatePipe, PercentPipe, RouterLink, Icono, EtiquetaPipe, ClabePipe, InicialesPipe, Guilloche, Sello],
  template: `
    <a routerLink="/clientes" class="volver"><app-icono nombre="flechaIzq" [tamano]="16" />Clientes</a>

    @if (error()) {
      <div class="aviso error" role="alert"><app-icono nombre="alerta" />{{ error() }}</div>
    }
    @if (exito()) {
      <div class="aviso ok" role="status"><app-icono nombre="checkCirculo" />{{ exito() }}</div>
    }

    @if (cliente(); as c) {
      <header class="cabecera">
        <span class="avatar grande" aria-hidden="true">{{ c.nombreCompleto | iniciales }}</span>
        <div>
          <h1>{{ c.nombreCompleto }}</h1>
          <p class="meta"><span class="mono">{{ c.curp }}</span><span class="chip" [class]="c.estadoKyc">{{ c.estadoKyc | etiqueta }}</span></p>
        </div>
      </header>

      <div class="dos-columnas">
        <section class="tarjeta" aria-labelledby="t-datos">
          <h2 id="t-datos">Datos del cliente</h2>
          <dl>
            <dt>Correo</dt><dd>{{ c.email }}</dd>
            <dt>Teléfono</dt><dd>{{ c.telefono ?? 'Sin registrar' }}</dd>
            <dt>Nacimiento</dt><dd>{{ c.fechaNacimiento | date: 'longDate' : 'UTC' }}</dd>
            <dt>RFC</dt><dd [class.mono]="c.rfc">{{ c.rfc ?? 'Sin registrar' }}</dd>
            <dt>Alta</dt><dd>{{ c.creadoEn | date: 'medium' }}</dd>
          </dl>
        </section>

        @if (c.estadoKyc !== 'VERIFICADO' && puedeOperar) {
          <section class="tarjeta" aria-labelledby="t-kyc">
            <h2 id="t-kyc">Verificar identidad</h2>
            <p class="ayuda">Se lee la CURP de la INE y se compara el rostro con una selfie. Las imágenes no se guardan: solo su huella SHA-256.</p>
            <div class="fotos">
              @for (f of fotos; track f.tipo) {
                <label class="foto" [class.con-imagen]="previews()[f.tipo]">
                  @if (previews()[f.tipo]; as src) {
                    <img [src]="src" [alt]="'Vista previa: ' + f.titulo" />
                    <span class="cambiar">Cambiar</span>
                  } @else {
                    <app-icono [nombre]="f.icono" [tamano]="28" />
                    <strong>{{ f.titulo }}</strong>
                    <span class="ayuda">JPG o PNG · máx. 5 MB</span>
                  }
                  <input type="file" accept="image/png,image/jpeg" (change)="elegir(f.tipo, $event)"
                         [attr.aria-label]="'Elegir ' + f.titulo" />
                </label>
              }
            </div>
            <button class="btn ancho" type="button" (click)="verificar(c.id)"
                    [disabled]="!archivos.identificacion || !archivos.selfie || procesando()">
              @if (procesando()) { Verificando… } @else { <app-icono nombre="escudo" />Verificar identidad }
            </button>
          </section>
        } @else if (c.estadoKyc === 'VERIFICADO') {
          <section class="tarjeta verificado" aria-labelledby="t-kyc-ok">
            <app-sello [tamano]="84" />
            <h2 id="t-kyc-ok">Identidad verificada</h2>
            <p class="ayuda">El cliente puede abrir cuentas y operar.</p>
          </section>
        }
      </div>

      <section class="tarjeta" aria-labelledby="t-cuentas">
        <div class="encabezado">
          <h2 id="t-cuentas">Cuentas</h2>
          @if (c.estadoKyc === 'VERIFICADO' && puedeOperar) {
            <button class="btn chico" type="button" (click)="abrirCuenta(c.id)" [disabled]="procesando()">
              <app-icono nombre="mas" [tamano]="16" />Abrir cuenta
            </button>
          }
        </div>
        <div class="tabla-contenedor">
          <table>
            <thead><tr><th scope="col">CLABE</th><th scope="col">Estado</th><th scope="col" class="num">Saldo</th><th scope="col">Apertura</th></tr></thead>
            <tbody>
              @for (k of cuentas(); track k.id) {
                <tr>
                  <td class="mono">{{ k.clabe | clabe }}</td>
                  <td><span class="chip" [class]="k.estado">{{ k.estado | etiqueta }}</span></td>
                  <td class="monto">{{ k.saldo | currency: 'MXN' : 'symbol-narrow' }}</td>
                  <td class="secundario-texto">{{ k.creadoEn | date: 'd MMM y' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="vacio">
                  <app-guilloche [densidad]="2" detalle="simple" />
                  <p>{{ c.estadoKyc === 'VERIFICADO' ? 'Sin cuentas todavía.' : 'Verifica la identidad para poder abrir cuentas.' }}</p>
                </td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <section class="tarjeta" aria-labelledby="t-hist">
        <h2 id="t-hist">Historial de verificaciones</h2>
        <div class="tabla-contenedor">
          <table>
            <thead><tr><th scope="col">Fecha</th><th scope="col">Resultado</th><th scope="col">CURP en la INE</th><th scope="col">Rostro</th><th scope="col">Analista</th></tr></thead>
            <tbody>
              @for (v of verificaciones(); track v.id) {
                <tr>
                  <td class="secundario-texto">{{ v.creadoEn | date: 'd MMM y, HH:mm' }}</td>
                  <td><span class="chip" [class]="v.aprobada ? 'APROBADA' : 'RECHAZADO'">{{ v.aprobada ? 'Aprobada' : 'Rechazada' }}</span></td>
                  <td>
                    @if (v.curpCoincide === null) {
                      <span class="secundario-texto">No se leyó</span>
                    } @else if (v.curpCoincide) {
                      <span class="verif ok"><app-icono nombre="check" [tamano]="14" />Coincide</span>
                    } @else {
                      <span class="verif mal"><app-icono nombre="cerrar" [tamano]="14" />Otra persona</span>
                      <div class="mono ayuda">{{ v.curpIne }}</div>
                    }
                  </td>
                  <td>
                    <div class="puntaje" [attr.aria-label]="'Similitud ' + (v.puntaje | percent: '1.0-1') + ', umbral ' + (v.umbral | percent: '1.0-0')">
                      <span class="pista"><span class="valor" [class.bajo]="!v.rostroCoincide" [style.width.%]="porcentaje(v.puntaje)"></span>
                        <span class="umbral" [style.left.%]="porcentaje(v.umbral)"></span></span>
                      <span class="num">{{ v.puntaje | percent: '1.1-1' }}</span>
                    </div>
                  </td>
                  <td>{{ v.realizadaPor }}</td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="vacio"><p>Sin verificaciones.</p></td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    } @else if (!error()) {
      <div class="cabecera"><span class="avatar grande"></span><div style="flex: 1"><span class="esqueleto" style="width: 40%; height: 26px"></span></div></div>
      <div class="tarjeta"><span class="esqueleto" style="width: 60%"></span></div>
    }
  `,
  styles: `
    .cabecera { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
    .meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 4px 0 0; color: var(--texto-suave); }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 12px 24px; margin: 0; }
    dt { color: var(--texto-suave); font-size: .88rem; }
    dd { margin: 0; overflow-wrap: anywhere; }
    .fotos { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 14px 0 16px; }
    .foto { aspect-ratio: 4 / 3; border: 1.5px dashed var(--borde-fuerte); border-radius: var(--r-tarjeta); position: relative; overflow: hidden;
            display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; text-align: center;
            color: var(--texto-suave); cursor: pointer; transition: border-color var(--transicion), background-color var(--transicion); }
    .foto strong { color: var(--texto); font-weight: 500; }
    .foto:hover { border-color: var(--acento-texto); background: var(--acento-suave); }
    .foto:focus-within { border-color: var(--texto); box-shadow: var(--anillo); }
    .foto.con-imagen { border-style: solid; }
    .foto img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
    .cambiar { position: absolute; bottom: 8px; background: rgba(0,0,0,.65); color: #fff; font-size: .78rem; padding: 3px 10px; border-radius: 999px; }
    .foto input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .ancho { width: 100%; }
    .verificado { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
    .verificado h2 { margin: 16px 0 4px; }
    .verif { display: inline-flex; align-items: center; gap: 5px; font-weight: 500; }
    .verif.ok { color: var(--ok); }
    .verif.mal { color: var(--error); }
    .puntaje { display: flex; align-items: center; gap: 10px; }
    .pista { position: relative; width: 120px; height: 6px; border-radius: 3px; background: var(--superficie-2); }
    .valor { position: absolute; left: 0; top: 0; bottom: 0; border-radius: 3px; background: var(--ok); }
    .valor.bajo { background: var(--error); }
    .umbral { position: absolute; top: -3px; bottom: -3px; width: 2px; background: var(--texto-suave); }
    @media (max-width: 480px) { .fotos { grid-template-columns: 1fr; } }
  `
})
export class ClienteDetalle implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly fotos: { tipo: TipoFoto; titulo: string; icono: NombreIcono }[] = [
    { tipo: 'identificacion', titulo: 'Foto de la INE', icono: 'identificacion' },
    { tipo: 'selfie', titulo: 'Selfie', icono: 'selfie' }
  ];
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
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    if (!archivo) return;
    // Validación inmediata en el navegador; el servidor vuelve a validar
    if (!['image/png', 'image/jpeg'].includes(archivo.type) || archivo.size > MAX_BYTES) {
      this.error.set(`"${archivo.name}" debe ser JPG o PNG de máximo 5 MB`);
      campo.value = '';
      return;
    }
    this.error.set('');
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
        if (v.aprobada) {
          this.exito.set(`Identidad verificada: la CURP de la INE coincide y la similitud del rostro es ${pct}%`);
        } else {
          const motivos: string[] = [];
          if (v.curpCoincide === false) motivos.push(`la CURP de la INE (${v.curpIne}) no es la del cliente`);
          if (!v.rostroCoincide) motivos.push(`la similitud del rostro (${pct}%) está debajo del umbral de ${Math.round(v.umbral * 100)}%`);
          this.error.set(`Verificación rechazada: ${motivos.join(' y ')}`);
        }
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

  /** La similitud coseno va de -1 a 1; la barra solo dibuja la parte positiva. */
  protected porcentaje(valor: number): number {
    return Math.max(0, Math.min(1, valor)) * 100;
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
