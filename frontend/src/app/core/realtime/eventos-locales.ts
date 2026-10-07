import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { EventoEnVivo } from '../sistema/models';

/** Fuente de avisos en el propio navegador (datos de ejemplo). Si no se provee, los avisos llegan por WebSocket. */
export const EVENTOS_EN_VIVO_LOCALES = new InjectionToken<Observable<EventoEnVivo>>('EVENTOS_EN_VIVO_LOCALES');
