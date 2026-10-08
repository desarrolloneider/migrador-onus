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
      let cerrando = false;
      cliente.onConnect = () => {
        this.caidos.delete(cliente);
        this.actualizarEstado();
        try {
          suscripcion = cliente.subscribe(`/topic/${canal}`, (mensaje) => {
          try {
            suscriptor.next(JSON.parse(mensaje.body));
          } catch {
            // Un aviso mal formado se ignora: igual se vuelve a pedir el dato con el sondeo.
            suscriptor.next({ canal, tipo: 'SONDEO', datos: {} });
          }
          });
        } catch (error) {
          console.warn('No se pudo escuchar el canal en vivo', canal, error);
        }
        // Al (re)conectar se avisa, para que la pantalla se ponga al día con lo que pasó mientras tanto.
        suscriptor.next({ canal, tipo: 'RECONECTADO', datos: {} });
      };
      // Cerrar a propósito (al salir de la pantalla) no es una caída.
      cliente.onWebSocketClose = () => {
        if (!cerrando) {
          this.caidos.add(cliente);
          this.actualizarEstado();
        }
      };
      // Si el navegador no deja abrir el WebSocket, la pantalla sigue funcionando con el sondeo: nunca se corta.
      try {
        cliente.activate();
      } catch (error) {
        console.warn('No se pudo abrir la conexión en vivo; se sigue con consultas periódicas.', error);
        this.caidos.add(cliente);
        this.actualizarEstado();
      }
      // El proceso ejecutor todavía no publica avisos: mientras tanto la pantalla vuelve a pedir el dato cada 4 segundos.
      const sondeo = setInterval(() => suscriptor.next({ canal, tipo: 'SONDEO', datos: {} }), 4_000);
      return () => {
        cerrando = true;
        this.caidos.delete(cliente);
        this.actualizarEstado();
        clearInterval(sondeo);
        try {
          suscripcion?.unsubscribe();
          void cliente.deactivate();
        } catch {
          // Ya estaba cerrada.
        }
      };
    });
  }

  /** Las conexiones abiertas por una pantalla que se cayeron y están reintentando. */
  private readonly caidos = new Set<Client>();

  private actualizarEstado(): void {
    this.estadoConexion.set(this.caidos.size > 0 ? 'RECONECTANDO' : 'CONECTADO');
  }

  private urlWebSocket(): string {
    const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocolo}//${location.host}${environment.urlTiempoReal}`;
  }
}
