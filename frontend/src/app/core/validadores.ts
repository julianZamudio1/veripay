import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const FORMATO_CURP = new RegExp(
  '^[A-Z][AEIOUX][A-Z]{2}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[HMX]' +
  '(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)' +
  '[B-DF-HJ-NP-TV-Z]{3}[A-Z\\d]\\d$'
);
const DICCIONARIO = '0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ';

/** Mismo algoritmo que el backend (Curp.java): dígito verificador RENAPO. */
export function digitoVerificadorCurp(curp17: string): number {
  let suma = 0;
  for (let i = 0; i < 17; i++) {
    suma += DICCIONARIO.indexOf(curp17[i]) * (18 - i);
  }
  const d = 10 - (suma % 10);
  return d === 10 ? 0 : d;
}

/** Fecha de nacimiento (yyyy-MM-dd) codificada en la CURP, o null si no es válida. */
export function fechaDeCurp(curp: string): string | null {
  if (curp.length < 17) return null;
  const siglo = /\d/.test(curp[16]) ? 1900 : 2000;
  const anio = siglo + Number(curp.substring(4, 6));
  const mes = Number(curp.substring(6, 8));
  const dia = Number(curp.substring(8, 10));
  const fecha = new Date(Date.UTC(anio, mes - 1, dia));
  if (fecha.getUTCMonth() !== mes - 1 || fecha.getUTCDate() !== dia) return null;
  return fecha.toISOString().substring(0, 10);
}

export function esCurpValida(curp: string): boolean {
  const fecha = fechaDeCurp(curp);
  return FORMATO_CURP.test(curp)
    && fecha !== null
    && fecha <= new Date().toISOString().substring(0, 10)   // una fecha de nacimiento futura nunca es válida
    && digitoVerificadorCurp(curp.substring(0, 17)) === Number(curp[17]);
}

export const curpValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const valor = (control.value ?? '').toString().trim().toUpperCase();
  if (!valor) return null;
  return esCurpValida(valor) ? null : { curp: true };
};
