import { formatDate } from '@angular/common';
import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';

/** Colombia no tiene horario de verano: siempre UTC-5. Las fechas se guardan en UTC y se muestran así (RN-21). */
export const ZONA_COLOMBIA = '-0500';

@Pipe({ name: 'horaColombia', standalone: true })
export class HoraColombiaPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(valor: string | null | undefined, formato: 'corta' | 'completa' | 'hora' | 'fecha' = 'corta'): string {
    if (!valor) {
      return '—';
    }
    const patrones = { corta: 'd MMM, h:mm a', completa: 'd MMM y, h:mm:ss a', hora: 'h:mm:ss a', fecha: 'd MMM y' };
    return formatDate(valor, patrones[formato], this.locale, ZONA_COLOMBIA);
  }
}

@Pipe({ name: 'duracion', standalone: true })
export class DuracionPipe implements PipeTransform {
  transform(milisegundos: number | null | undefined): string {
    if (milisegundos === null || milisegundos === undefined) {
      return '—';
    }
    if (milisegundos < 1_000) {
      return `${milisegundos} ms`;
    }
    const segundos = Math.round(milisegundos / 1_000);
    if (segundos < 60) {
      return `${segundos} s`;
    }
    const minutos = Math.floor(segundos / 60);
    return `${minutos} min ${String(segundos % 60).padStart(2, '0')} s`;
  }
}
