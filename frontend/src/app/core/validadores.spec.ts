import { digitoVerificadorCurp, esCurpValida, fechaDeCurp } from './validadores';

describe('validadores CURP', () => {
  const completa = (c17: string) => c17 + digitoVerificadorCurp(c17);

  it('acepta una CURP con dígito verificador correcto', () => {
    expect(esCurpValida(completa('GOMA850312HDFRRN0'))).toBeTrue();
  });

  it('rechaza un dígito verificador alterado', () => {
    const curp = completa('GOMA850312HDFRRN0');
    const otro = (Number(curp[17]) + 1) % 10;
    expect(esCurpValida(curp.substring(0, 17) + otro)).toBeFalse();
  });

  it('rechaza fechas inexistentes', () => {
    expect(esCurpValida(completa('GOMA850231HDFRRN0'))).toBeFalse();
  });

  it('deduce el siglo por la homoclave', () => {
    expect(fechaDeCurp(completa('GOMA850312HDFRRN0'))).toBe('1985-03-12');
    expect(fechaDeCurp(completa('RAMC010415HNLMRRA'))).toBe('2001-04-15');
  });

  it('coincide con el dígito calculado por el backend para los datos demo', () => {
    // Mismo algoritmo que Curp.java; si cambia uno, esta prueba y CurpTest deben seguir de acuerdo
    expect(esCurpValida(completa('LOHS920721MJCPRF0'))).toBeTrue();
  });
});
