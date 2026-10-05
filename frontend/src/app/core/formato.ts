import { Pipe, PipeTransform } from '@angular/core';

/** Etiquetas legibles para los enums del API (PENDIENTE → Pendiente, DEPOSITO → Depósito). */
const ETIQUETAS: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  VERIFICADO: 'Verificado',
  RECHAZADO: 'Rechazado',
  ACTIVA: 'Activa',
  BLOQUEADA: 'Bloqueada',
  DEPOSITO: 'Depósito',
  RETIRO: 'Retiro',
  TRANSFERENCIA: 'Transferencia',
  ADMIN: 'Administrador',
  ANALISTA: 'Analista',
  AUDITOR: 'Auditor'
};

@Pipe({ name: 'etiqueta' })
export class EtiquetaPipe implements PipeTransform {
  transform(valor: string | null | undefined): string {
    return valor ? (ETIQUETAS[valor] ?? valor) : '';
  }
}

/**
 * CLABE en grupos banco · plaza · cuenta · control: 646 180 26710574619 5.
 * Sin CLABE (depósito o retiro en efectivo) muestra "Ventanilla".
 */
@Pipe({ name: 'clabe' })
export class ClabePipe implements PipeTransform {
  transform(valor: string | null | undefined): string {
    if (!valor) return 'Ventanilla';
    return valor.length === 18
      ? `${valor.slice(0, 3)} ${valor.slice(3, 6)} ${valor.slice(6, 17)} ${valor.slice(17)}`
      : valor;
  }
}

/** Iniciales para el avatar: "Sofía López Hernández" → "SL" */
@Pipe({ name: 'iniciales' })
export class InicialesPipe implements PipeTransform {
  transform(nombre: string | null | undefined): string {
    if (!nombre) return '';
    const partes = nombre.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes[1]?.[0] ?? '')).toUpperCase();
  }
}
