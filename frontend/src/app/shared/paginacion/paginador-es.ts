import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/** Textos del paginador en español para todas las tablas. */
@Injectable()
export class PaginadorEnEspanol extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Por página:';
  override nextPageLabel = 'Página siguiente';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primera página';
  override lastPageLabel = 'Última página';
  override getRangeLabel = (pagina: number, tamano: number, total: number): string => {
    if (total === 0 || tamano === 0) {
      return `0 de ${total}`;
    }
    const inicio = pagina * tamano;
    return `${inicio + 1} – ${Math.min(inicio + tamano, total)} de ${total}`;
  };
}
