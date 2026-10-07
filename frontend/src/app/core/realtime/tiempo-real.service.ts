import { Injectable, inject, signal } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import { Observable, filter } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EventoEnVivo } from '../sistema/models';
import { EVENTOS_EN_VIVO_LOCALES } from './eventos-locales';

export type EstadoConexion = 'CONECTANDO' | 'CONECTADO' | 'RECONECTANDO';

/**
 * Avisos en vivo (ESP-08). El backend los publica con NOTIFY y el proceso web los reparte por WebSocket con STOMP en
 * {@code /topic/<canal>}, como en el resto de los proyectos de GuajiraNet. Los canales son {@code lote:<id>},
 * {@code onu-run:<id>}, {@code armado:<usuario>} y {@code sistema}.
 *
 * <p>Un aviso solo dice que algo cambió: la pantalla vuelve a pedir el dato. Así, si se pierde la conexión, al volver
 * se pone al día sola, y el lote nunca se detiene por eso.
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService {
  private readonly locales = inject(EVENTOS_EN_VIVO_LOCALES, { optional: true });
  private readonly estadoConexion = signal<EstadoConexion>(this.locales ? 'CONECTADO' : 'CONECTANDO');
  readonly conexion = this.estadoConexion.asReadonly();

  escuchar(canal: string): Observable<EventoEnVivo> {
    if (this.locales) {
      return this.locales.pipe(filter((evento) => evento.canal === canal));
    }
    return new Observable<EventoEnVivo>((suscriptor) => {
      let suscripcion: StompSubscription | undefined;
      const cliente = new Client({ brokerURL: this.urlWebSocket(), reconnectDelay: 3_000 });
      cliente.onConnect = () => {
        this.estadoConexion.set('CONECTADO');
        suscripcion = cliente.subscribe(`/topic/${canal}`, (mensaje) => suscriptor.next(JSON.parse(mensaje.body)));
        // Al (re)conectar se avisa, para que la pantalla se ponga al día con lo que pasó mientras tanto.
        suscriptor.next({ canal, tipo: 'RECONECTADO', datos: {} });
      };
      cliente.onWebSocketClose = () => this.estadoConexion.set('RECONECTANDO');
      cliente.activate();
      return () => {
        suscripcion?.unsubscribe();
        void cliente.deactivate();
      };
    });
  }

  private urlWebSocket(): string {
    const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocolo}//${location.host}${environment.urlTiempoReal}`;
  }
}
