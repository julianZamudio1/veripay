export type Rol = 'ADMIN' | 'ANALISTA' | 'AUDITOR';
export type EstadoKyc = 'PENDIENTE' | 'VERIFICADO' | 'RECHAZADO';
export type EstadoCuenta = 'ACTIVA' | 'BLOQUEADA';
export type TipoTransaccion = 'DEPOSITO' | 'RETIRO' | 'TRANSFERENCIA';

export interface Sesion {
  token: string;
  expira: string;
  username: string;
  nombre: string;
  rol: Rol;
}

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface Cliente {
  id: number;
  curp: string;
  rfc: string | null;
  nombre: string;
  apellidoPaterno: string;
  apellidoMaterno: string | null;
  nombreCompleto: string;
  fechaNacimiento: string;
  email: string;
  telefono: string | null;
  estadoKyc: EstadoKyc;
  creadoEn: string;
}

export interface AltaCliente {
  curp: string;
  rfc?: string | null;
  nombre: string;
  apellidoPaterno: string;
  apellidoMaterno?: string | null;
  fechaNacimiento: string;
  email: string;
  telefono?: string | null;
}

export interface Verificacion {
  id: number;
  puntaje: number;
  umbral: number;
  aprobada: boolean;
  huellaIdentificacion: string;
  huellaSelfie: string;
  realizadaPor: string;
  creadoEn: string;
}

export interface Cuenta {
  id: number;
  clabe: string;
  clienteId: number;
  titular: string;
  saldo: number;
  moneda: string;
  estado: EstadoCuenta;
  creadoEn: string;
}

export interface Transaccion {
  id: number;
  folio: string;
  tipo: TipoTransaccion;
  clabeOrigen: string | null;
  clabeDestino: string | null;
  monto: number;
  concepto: string | null;
  estado: string;
  realizadaPor: string;
  creadoEn: string;
}

export interface Tablero {
  clientesTotal: number;
  clientesPendientes: number;
  clientesVerificados: number;
  clientesRechazados: number;
  cuentas: number;
  saldoTotal: number;
  transaccionesHoy: number;
  volumenHoy: number;
}

export interface EventoAuditoria {
  id: number;
  usuario: string;
  accion: string;
  entidad: string;
  entidadId: string | null;
  detalle: string | null;
  creadoEn: string;
}

/** Error del API en formato RFC 9457 (application/problem+json). */
export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  codigo: string;
  timestamp: string;
  campos?: Record<string, string>;
}
