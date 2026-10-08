import { Injectable, NgZone, inject } from '@angular/core';
import { gsap } from 'gsap';

/** Lo que se anima al aparecer, en el orden en que entra en cascada. */
const ELEMENTOS = '.encabezado, .tarjeta, .kpi, .aviso, .bloque, table.tabla tbody tr, .chip, .panel';
/** Números que cuentan desde 0 al aparecer o al cambiar. */
const NUMEROS = '.kpi .valor, .cuenta';

/**
 * Animaciones con GSAP para toda la aplicación, en un solo lugar: las pantallas no tienen que hacer nada.
 *
 * <ul>
 *   <li>Al cambiar de pantalla, el contenido entra con un deslizamiento suave.</li>
 *   <li>Tarjetas, avisos, indicadores y filas entran en cascada la primera vez que aparecen. Las filas que llegan
 *       después (por ejemplo, una ONU que termina) también entran animadas; las que ya estaban no se repiten.</li>
 *   <li>Los números de los indicadores cuentan desde su valor anterior (o desde 0) hasta el nuevo.</li>
 * </ul>
 * Si el sistema tiene activado "reducir movimiento", no se anima nada.
 */
@Injectable({ providedIn: 'root' })
export class AnimacionesService {
  private readonly zona = inject(NgZone);
  private readonly reducir = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  private readonly vistos = new WeakSet<Element>();
  private readonly valores = new WeakMap<Element, number>();
  /** Lo último que escribió la animación en cada número, para no confundirlo con un cambio de Angular. */
  private readonly escrito = new WeakMap<Element, string>();
  private readonly cuentas = new WeakMap<Element, gsap.core.Tween>();
  private observador?: MutationObserver;
  private pendientes = new Set<Element>();
  private programado = false;

  /** Empieza a observar el contenido principal. */
  observar(raiz: HTMLElement): void {
    if (this.reducir || this.observador) {
      return;
    }
    this.zona.runOutsideAngular(() => {
      this.observador = new MutationObserver((cambios) => {
        for (const cambio of cambios) {
          if (cambio.type === 'characterData') {
            const padre = cambio.target.parentElement;
            if (padre?.matches(NUMEROS) && this.escrito.get(padre) !== cambio.target.textContent) {
              this.pendientes.add(padre);
            }
            continue;
          }
          cambio.addedNodes.forEach((nodo) => {
            if (nodo instanceof HTMLElement) {
              this.pendientes.add(nodo);
            } else if (nodo.parentElement?.matches(NUMEROS)) {
              this.pendientes.add(nodo.parentElement);
            }
          });
        }
        this.programar();
      });
      this.observador.observe(raiz, { childList: true, subtree: true, characterData: true });
      this.pendientes.add(raiz);
      this.programar();
    });
  }

  /** Entrada de una pantalla nueva. */
  entrarPantalla(raiz: HTMLElement): void {
    if (this.reducir) {
      return;
    }
    this.zona.runOutsideAngular(() => gsap.fromTo(raiz, { opacity: 0, y: 10 }, { opacity: 1, y: 0, duration: 0.35, ease: 'power2.out' }));
  }

  /** Un pequeño rebote, para botones y acciones. */
  pulsar(elemento: Element): void {
    if (this.reducir) {
      return;
    }
    this.zona.runOutsideAngular(() =>
      gsap.fromTo(elemento, { scale: 0.96 }, { scale: 1, duration: 0.35, ease: 'back.out(3)', clearProps: 'transform' }));
  }

  private programar(): void {
    if (this.programado) {
      return;
    }
    this.programado = true;
    requestAnimationFrame(() => {
      this.programado = false;
      const lote = [...this.pendientes];
      this.pendientes.clear();
      this.animar(lote);
    });
  }

  private animar(raices: Element[]): void {
    const nuevos: Element[] = [];
    const numeros = new Set<Element>();
    for (const raiz of raices) {
      if (!raiz.isConnected) {
        continue;
      }
      const candidatos = [...(raiz.matches(ELEMENTOS) ? [raiz] : []), ...raiz.querySelectorAll(ELEMENTOS)];
      for (const el of candidatos) {
        if (!this.vistos.has(el)) {
          this.vistos.add(el);
          nuevos.push(el);
        }
      }
      if (raiz.matches(NUMEROS)) {
        numeros.add(raiz);
      }
      raiz.querySelectorAll(NUMEROS).forEach((n) => numeros.add(n));
    }
    if (nuevos.length) {
      // Las filas de tabla, muchas a la vez, entran más rápido que las tarjetas.
      const filas = nuevos.filter((el) => el.tagName === 'TR').slice(0, 40);
      const bloques = nuevos.filter((el) => el.tagName !== 'TR');
      if (bloques.length) {
        gsap.fromTo(bloques, { opacity: 0, y: 14 },
          { opacity: 1, y: 0, duration: 0.45, ease: 'power3.out', stagger: 0.05, clearProps: 'opacity,transform' });
      }
      if (filas.length) {
        gsap.fromTo(filas, { opacity: 0, x: -8 },
          { opacity: 1, x: 0, duration: 0.3, ease: 'power2.out', stagger: 0.025, clearProps: 'opacity,transform' });
      }
    }
    numeros.forEach((n) => this.contar(n as HTMLElement));
  }

  /** Cuenta del valor anterior al nuevo. Solo números enteros sin más texto. */
  private contar(el: HTMLElement): void {
    const texto = el.textContent?.trim() ?? '';
    if (!/^\d{1,7}$/.test(texto) || this.escrito.get(el) === el.textContent) {
      return;
    }
    const destino = Number(texto);
    const desde = this.valores.get(el) ?? 0;
    this.valores.set(el, destino);
    if (desde === destino) {
      return;
    }
    const nodo = el.firstChild;
    if (!(nodo instanceof Text)) {
      return;
    }
    // Si cambia a mitad de una cuenta, se reemplaza la cuenta anterior.
    this.cuentas.get(el)?.kill();
    const estado = { v: desde };
    const escribir = (valor: number) => {
      nodo.data = String(valor);
      this.escrito.set(el, nodo.data);
    };
    this.cuentas.set(el, gsap.to(estado, {
      v: destino,
      duration: Math.min(1.2, 0.4 + Math.abs(destino - desde) * 0.02),
      ease: 'power2.out',
      // Se escribe en el mismo nodo que maneja Angular y se termina en su valor final.
      onUpdate: () => escribir(Math.round(estado.v)),
      onComplete: () => escribir(destino),
    }));
    gsap.fromTo(el, { scale: 0.92 }, { scale: 1, duration: 0.4, ease: 'back.out(2.5)', clearProps: 'transform' });
  }
}
