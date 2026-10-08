import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { filter, finalize, forkJoin, switchMap } from 'rxjs';
import { mensajeDeError } from '../../../../core/api/errores';
import { ConfirmarDialogoComponent, DatosConfirmacion } from '../../../../shared/dialogos/confirmar-dialogo.component';
import { HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { ConfiguracionService } from '../../data/configuracion.service';
import { Configuracion, Olt, Parametro, PruebaDeConexion } from '../../models/configuracion.model';

/** Parámetros, OLT y tarjetas permitidas, sin tocar el programa (ESP-15, RNF-11). */
@Component({
  selector: 'app-configuracion',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatChipsModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressBarModule, MatSelectModule, MatTooltipModule, HoraColombiaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>Configuración</h1>
          <p class="subtitulo">Cada cambio muestra el valor anterior y el nuevo, pide confirmación y queda en la auditoría. Un lote se ejecuta completo con los parámetros con que se confirmó.</p>
        </div>
      </header>

      @if (!configuracion()) { <mat-progress-bar mode="indeterminate" /> }
      @if (bloqueo(); as lote) {
        <p class="aviso aviso-advertencia"><mat-icon>lock</mat-icon>
          <span>No se puede cambiar la configuración mientras el lote <a routerLink="/inicio" class="mono">{{ lote }}</a> está en ejecución (RN-28).</span></p>
      }
      @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }

      @for (grupo of grupos(); track grupo.nombre) {
        <section class="tarjeta">
          <h2><mat-icon>{{ iconoGrupo(grupo.nombre) }}</mat-icon> {{ grupo.nombre }}</h2>
          <div class="parametros">
            @for (p of grupo.parametros; track p.clave) {
              <div class="parametro" [class.editando]="editando() === p.clave">
                <div class="texto">
                  <div class="nombre">{{ p.nombre }} <span class="referencia">{{ p.referencia }}</span></div>
                  <div class="descripcion">{{ p.descripcion }}</div>
                  @if (p.actualizadoPor) { <div class="cambio">Último cambio: {{ p.actualizadoPor }}, {{ p.actualizadoEn | horaColombia }}</div> }
                </div>
                @if (editando() === p.clave) {
                  <div class="editor">
                    @switch (p.tipo) {
                      @case ('OPCION') {
                        <mat-form-field><mat-label>Valor</mat-label>
                          <mat-select [(ngModel)]="borrador"><mat-option [value]="null">Sin valor</mat-option>
                            @for (o of p.opciones ?? []; track o.valor) { <mat-option [value]="o.valor">{{ o.etiqueta }}</mat-option> }</mat-select></mat-form-field>
                      }
                      @case ('ENTERO') {
                        <mat-form-field><mat-label>Valor</mat-label><input matInput type="number" [min]="p.minimo" [max]="p.maximo" [(ngModel)]="borrador" />
                          @if (p.unidad) { <span matTextSuffix>{{ p.unidad }}</span> }
                          <mat-hint>Entre {{ p.minimo }} y {{ p.maximo }}</mat-hint></mat-form-field>
                      }
                      @default {
                        <mat-form-field><mat-label>Valor</mat-label><input matInput [(ngModel)]="borrador" /></mat-form-field>
                      }
                    }
                    <div class="botones">
                      <button mat-button (click)="editando.set(null)">Cancelar</button>
                      <button mat-flat-button (click)="guardar(p)">Guardar</button>
                    </div>
                  </div>
                } @else {
                  <div class="valor">
                    <span [class.sin-valor]="p.valor === null">{{ mostrar(p) }}</span>
                    @if (p.editable) {
                      <button mat-icon-button (click)="editar(p)" [disabled]="!!bloqueo()" [attr.aria-label]="'Cambiar ' + p.nombre"
                              [matTooltip]="bloqueo() ? 'Hay un lote en ejecución' : 'Cambiar'"><mat-icon>edit</mat-icon></button>
                    } @else {
                      <mat-icon class="fijo" matTooltip="No se cambia desde la pantalla">lock</mat-icon>
                    }
                  </div>
                }
              </div>
            }
          </div>
        </section>
      }

      <section class="bloque olts">
        <h2><mat-icon>dns</mat-icon> OLT: acceso por consola y tarjetas permitidas</h2>
        <p class="nota">Solo se entra a las tarjetas de esta lista (RN-14). Las credenciales de cada OLT viven en el servidor y nunca se ven ni se cambian aquí (RN-25).</p>
        <div class="rejilla-olts">
          @for (olt of olts(); track olt.id) {
            <article class="tarjeta">
              <header class="cabecera-olt">
                <div><h3 class="mono">{{ olt.codigo }}</h3><span class="nota">{{ olt.nombre }}</span></div>
                @if (olt.accesoConfigurado) {
                  <span class="acceso tono-exito"><mat-icon>lan</mat-icon> {{ olt.protocolo }} {{ olt.direccion }}:{{ olt.puerto }}</span>
                } @else {
                  <span class="acceso tono-error"><mat-icon>link_off</mat-icon> Sin acceso por consola</span>
                }
              </header>
              <div class="consola">
                <div class="fila-acceso">
                  <mat-form-field class="campo-ip">
                    <mat-label>IP de gestión</mat-label>
                    <input matInput placeholder="10.11.x.x" [ngModel]="accesoDe(olt).direccion" (ngModelChange)="escribirAcceso(olt, 'direccion', $event)"
                           [disabled]="!!bloqueo()" autocomplete="off" />
                  </mat-form-field>
                  <mat-form-field class="campo-puerto">
                    <mat-label>Puerto SSH</mat-label>
                    <input matInput type="number" [ngModel]="accesoDe(olt).puerto" (ngModelChange)="escribirAcceso(olt, 'puerto', $event)"
                           [disabled]="!!bloqueo()" />
                  </mat-form-field>
                </div>
                <div class="botones-acceso">
                  @if (accesoCambiado(olt)) {
                    <button mat-flat-button (click)="guardarAcceso(olt)" [disabled]="ocupada()[olt.id]"><mat-icon>save</mat-icon> Guardar acceso</button>
                  }
                  <button mat-stroked-button (click)="probar(olt)" [disabled]="!olt.direccion || accesoCambiado(olt) || ocupada()[olt.id]"
                          [matTooltip]="accesoCambiado(olt) ? 'Guarda primero' : 'Entra por SSH y solo lee los perfiles; no cambia nada'">
                    <mat-icon>network_check</mat-icon> Probar conexión</button>
                </div>
                @if (ocupada()[olt.id]) { <mat-progress-bar mode="indeterminate" /> }
                <p class="nota huella">
                  <mat-icon>fingerprint</mat-icon>
                  @if (olt.huellaSsh) { <span>Huella fijada: <code>{{ olt.huellaSsh }}</code></span> }
                  @else { <span>Sin huella fijada: prueba la conexión y fíjala.</span> }
                </p>
                @if (pruebas()[olt.id]; as p) {
                  <div class="resultado" [class.bien]="p.conecto && p.huellaCoincide" [class.mal]="!p.conecto">
                    <p><mat-icon>{{ !p.conecto ? 'error' : p.huellaCoincide ? 'check_circle' : 'help' }}</mat-icon> {{ p.mensaje }}</p>
                    @if (p.conecto && !p.huellaCoincide && p.huellaPresentada) {
                      <p class="nota">La OLT presentó la huella <code>{{ p.huellaPresentada }}</code>. Si es la OLT correcta, fíjala.</p>
                      <button mat-flat-button (click)="fijarHuella(olt, p.huellaPresentada)"><mat-icon>fingerprint</mat-icon> Fijar esta huella</button>
                    }
                    @if (p.perfiles.length) {
                      <div class="perfiles">@for (perfil of p.perfiles; track perfil) { <span class="chip-perfil mono">{{ perfil }}</span> }</div>
                    }
                  </div>
                }
              </div>
              @if (olt.protocolo === 'TELNET') {
                <p class="aviso aviso-advertencia pequeno"><mat-icon>warning</mat-icon> <span>Solo Telnet: el acceso se restringe por red y queda como riesgo aceptado por escrito (PC-35).</span></p>
              }
              <mat-chip-set aria-label="Tarjetas permitidas">
                @for (t of tarjetasDe(olt); track t) {
                  <mat-chip [removable]="!bloqueo()" (removed)="quitarTarjeta(olt, t)">{{ t }}
                    @if (!bloqueo()) { <button matChipRemove [attr.aria-label]="'Quitar ' + t"><mat-icon>cancel</mat-icon></button> }
                  </mat-chip>
                } @empty {
                  <span class="nota sin-tarjetas">Sin tarjetas: ninguna ONU de esta OLT entra a un lote (PC-39).</span>
                }
              </mat-chip-set>
              <div class="agregar">
                <mat-form-field>
                  <mat-label>Agregar tarjeta</mat-label>
                  <input matInput placeholder="0/2" [ngModel]="nuevaTarjeta()[olt.id] ?? ''" (ngModelChange)="escribirTarjeta(olt.id, $event)"
                         (keydown.enter)="agregarTarjeta(olt)" [disabled]="!!bloqueo()" />
                </mat-form-field>
                <button mat-stroked-button (click)="agregarTarjeta(olt)" [disabled]="!!bloqueo()"><mat-icon>add</mat-icon> Agregar</button>
              </div>
              @if (cambiado(olt)) {
                <div class="botones">
                  <button mat-button (click)="deshacer(olt)">Deshacer</button>
                  <button mat-flat-button (click)="guardarTarjetas(olt)">Guardar tarjetas</button>
                </div>
              }
            </article>
          }
        </div>
      </section>
    </div>
  `,
  styles: `
    .parametros { display: flex; flex-direction: column; }
    .parametro { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 16px; padding: 12px 0; border-top: 1px solid var(--mat-sys-outline-variant); align-items: center; }
    .parametro:first-child { border-top: 0; padding-top: 0; }
    .parametro.editando { grid-template-columns: 1fr; }
    .nombre { font-weight: 500; }
    .referencia { font-size: 11.5px; padding: 1px 6px; border-radius: 6px; background: var(--mat-sys-surface-container-high); color: var(--mat-sys-on-surface-variant); margin-left: 6px; }
    .descripcion, .cambio, .nota { color: var(--mat-sys-on-surface-variant); font-size: 13px; line-height: 1.45; margin-top: 2px; }
    .cambio { font-size: 12px; }
    .valor { display: flex; align-items: center; gap: 4px; font-weight: 600; white-space: nowrap; }
    .sin-valor { color: var(--color-aviso); }
    .fijo { color: var(--mat-sys-on-surface-variant); margin: 0 10px; }
    .editor { display: flex; gap: 12px; align-items: flex-start; flex-wrap: wrap; }
    .editor mat-form-field { min-width: 260px; }
    .botones { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }
    .olts { margin-top: 24px; }
    .rejilla-olts { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; margin-top: 12px; }
    .rejilla-olts .tarjeta + .tarjeta { margin-top: 0; }
    .cabecera-olt { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
    .cabecera-olt h3 { margin: 0; font-size: 16px; }
    .acceso { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px; background: var(--fondo-tono); color: var(--color-tono); font-size: 12.5px; height: fit-content; }
    .acceso mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .pequeno { font-size: 12.5px; padding: 8px 10px; }
    .sin-tarjetas { color: var(--color-aviso); }
    .consola { display: flex; flex-direction: column; gap: 6px; padding-bottom: 12px; margin-bottom: 12px; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .fila-acceso { display: flex; gap: 8px; }
    .campo-ip { flex: 1; min-width: 0; }
    .campo-puerto { width: 110px; }
    .botones-acceso { display: flex; gap: 8px; flex-wrap: wrap; }
    .huella { display: flex; gap: 6px; align-items: center; margin: 4px 0 0; word-break: break-all; }
    .huella mat-icon { font-size: 18px; width: 18px; height: 18px; flex: none; }
    code { font-family: var(--fuente-mono); font-size: 11.5px; }
    .resultado { border-radius: 10px; padding: 8px 10px; background: var(--mat-sys-surface-container-high); font-size: 13px; }
    .resultado.bien { background: var(--fondo-exito); }
    .resultado.mal { background: color-mix(in srgb, var(--mat-sys-error) 12%, transparent); }
    .resultado p { display: flex; gap: 6px; align-items: flex-start; margin: 2px 0 6px; word-break: break-all; }
    .resultado p mat-icon { font-size: 18px; width: 18px; height: 18px; flex: none; }
    .perfiles { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 4px; }
    .chip-perfil { font-size: 11.5px; padding: 2px 8px; border-radius: 999px; background: var(--mat-sys-surface-container-highest); }
    .agregar { display: flex; gap: 8px; align-items: flex-start; margin-top: 12px; }
    .agregar mat-form-field { width: 160px; }
    .agregar button { height: 48px; }
  `,
})
export class ConfiguracionComponent implements OnInit {
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly dialogo = inject(MatDialog);
  private readonly avisos = inject(MatSnackBar);

  protected readonly configuracion = signal<Configuracion | null>(null);
  protected readonly olts = signal<Olt[]>([]);
  protected readonly editando = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly borradorTarjetas = signal<Record<number, string[]>>({});
  protected readonly nuevaTarjeta = signal<Record<number, string>>({});
  protected borrador: string | number | null = null;
  protected readonly borradorAcceso = signal<Record<number, { direccion: string; puerto: number | null }>>({});
  protected readonly pruebas = signal<Record<number, PruebaDeConexion>>({});
  protected readonly ocupada = signal<Record<number, boolean>>({});

  protected readonly bloqueo = computed(() => this.configuracion()?.bloqueadaPorLote ?? null);
  protected readonly grupos = computed(() => {
    const porGrupo = new Map<string, Parametro[]>();
    for (const p of this.configuracion()?.parametros ?? []) {
      porGrupo.set(p.grupo, [...(porGrupo.get(p.grupo) ?? []), p]);
    }
    return [...porGrupo.entries()].map(([nombre, parametros]) => ({ nombre, parametros }));
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected iconoGrupo(grupo: string): string {
    return ({ Procedimiento: 'rule', Lotes: 'inventory_2', 'Ejecución': 'play_circle', Acceso: 'lock_person' } as Record<string, string>)[grupo] ?? 'tune';
  }

  protected mostrar(p: Parametro): string {
    if (p.valor === null) return 'Sin valor';
    if (p.tipo === 'OPCION') return p.opciones?.find((o) => o.valor === p.valor)?.etiqueta ?? String(p.valor);
    return p.unidad ? `${p.valor} ${p.unidad}` : String(p.valor);
  }

  protected editar(p: Parametro): void {
    this.borrador = p.valor;
    this.editando.set(p.clave);
  }

  protected guardar(p: Parametro): void {
    const nuevo = this.borrador === '' ? null : this.borrador;
    const vistaNueva = this.mostrar({ ...p, valor: nuevo });
    this.dialogo.open(ConfirmarDialogoComponent, {
      width: '480px',
      data: { titulo: `Cambiar ${p.nombre.toLowerCase()}`, icono: 'edit', textoConfirmar: 'Guardar cambio',
        mensaje: `Valor anterior: ${this.mostrar(p)}. Valor nuevo: ${vistaNueva}.`,
        detalle: 'Las vistas previas armadas antes de este cambio ya no se podrán confirmar: habrá que armarlas de nuevo (RN-36).' } satisfies DatosConfirmacion,
    }).afterClosed().pipe(filter(Boolean), switchMap(() => this.configuracionService.cambiarParametro(p.clave, { valor: nuevo }))).subscribe({
      next: () => {
        this.editando.set(null);
        this.avisos.open(`${p.nombre}: ${vistaNueva}.`, 'Cerrar', { duration: 4_000 });
        this.cargar();
      },
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  protected accesoDe(olt: Olt): { direccion: string; puerto: number | null } {
    return this.borradorAcceso()[olt.id] ?? { direccion: olt.direccion ?? '', puerto: olt.puerto ?? 22 };
  }

  protected escribirAcceso(olt: Olt, campo: 'direccion' | 'puerto', valor: string | number | null): void {
    const actual = this.accesoDe(olt);
    this.borradorAcceso.update((b) => ({ ...b, [olt.id]: { ...actual, [campo]: campo === 'puerto' ? (valor === '' || valor === null ? null : Number(valor)) : String(valor ?? '') } }));
  }

  protected accesoCambiado(olt: Olt): boolean {
    const b = this.borradorAcceso()[olt.id];
    return !!b && (b.direccion.trim() !== (olt.direccion ?? '') || (b.direccion.trim() !== '' && b.puerto !== olt.puerto));
  }

  private marcarOcupada(oltId: number, valor: boolean): void {
    this.ocupada.update((o) => ({ ...o, [oltId]: valor }));
  }

  private reemplazarOlt(actualizada: Olt): void {
    this.olts.update((lista) => lista.map((o) => (o.id === actualizada.id ? actualizada : o)));
  }

  protected guardarAcceso(olt: Olt): void {
    const { direccion, puerto } = this.accesoDe(olt);
    this.marcarOcupada(olt.id, true);
    this.configuracionService.cambiarAcceso(olt.id, { direccion: direccion.trim() || null, puerto })
      .pipe(finalize(() => this.marcarOcupada(olt.id, false))).subscribe({
        next: (actualizada) => {
          this.reemplazarOlt(actualizada);
          this.borradorAcceso.update((b) => { const { [olt.id]: _q, ...resto } = b; return resto; });
          this.pruebas.update((p) => { const { [olt.id]: _q, ...resto } = p; return resto; });
          this.avisos.open(`Acceso de ${olt.codigo} guardado. Ahora prueba la conexión.`, 'Cerrar', { duration: 4000 });
        },
        error: (e: unknown) => this.avisos.open(mensajeDeError(e), 'Cerrar', { duration: 6000 }),
      });
  }

  protected probar(olt: Olt): void {
    this.marcarOcupada(olt.id, true);
    this.configuracionService.probarConexion(olt.id).pipe(finalize(() => this.marcarOcupada(olt.id, false))).subscribe({
      next: (r) => this.pruebas.update((p) => ({ ...p, [olt.id]: r })),
      error: (e: unknown) => this.avisos.open(mensajeDeError(e), 'Cerrar', { duration: 6000 }),
    });
  }

  protected fijarHuella(olt: Olt, huella: string): void {
    this.marcarOcupada(olt.id, true);
    this.configuracionService.fijarHuella(olt.id, huella).pipe(
      switchMap(() => this.configuracionService.listarOlts()),
      finalize(() => this.marcarOcupada(olt.id, false)),
    ).subscribe({
      next: (lista) => {
        this.olts.set(lista);
        this.avisos.open(`Huella de ${olt.codigo} fijada. Probando de nuevo…`, 'Cerrar', { duration: 3000 });
        this.probar(olt);
      },
      error: (e: unknown) => this.avisos.open(mensajeDeError(e), 'Cerrar', { duration: 6000 }),
    });
  }

  protected tarjetasDe(olt: Olt): string[] {
    return this.borradorTarjetas()[olt.id] ?? olt.tarjetasPermitidas;
  }

  protected escribirTarjeta(oltId: number, valor: string): void {
    this.nuevaTarjeta.update((actual) => ({ ...actual, [oltId]: valor }));
  }

  protected agregarTarjeta(olt: Olt): void {
    const tarjeta = (this.nuevaTarjeta()[olt.id] ?? '').trim();
    if (!/^\d{1,2}\/\d{1,2}$/.test(tarjeta)) {
      this.error.set(`Tarjeta inválida: "${tarjeta}". El formato es 0/n.`);
      return;
    }
    this.error.set(null);
    const actuales = this.tarjetasDe(olt);
    if (!actuales.includes(tarjeta)) {
      this.borradorTarjetas.update((b) => ({ ...b, [olt.id]: [...actuales, tarjeta].sort() }));
    }
    this.escribirTarjeta(olt.id, '');
  }

  protected quitarTarjeta(olt: Olt, tarjeta: string): void {
    this.borradorTarjetas.update((b) => ({ ...b, [olt.id]: this.tarjetasDe(olt).filter((t) => t !== tarjeta) }));
  }

  protected cambiado(olt: Olt): boolean {
    const borrador = this.borradorTarjetas()[olt.id];
    return !!borrador && borrador.join(',') !== olt.tarjetasPermitidas.join(',');
  }

  protected deshacer(olt: Olt): void {
    this.borradorTarjetas.update((b) => {
      const { [olt.id]: _quitado, ...resto } = b;
      return resto;
    });
  }

  protected guardarTarjetas(olt: Olt): void {
    const nuevas = this.tarjetasDe(olt);
    this.dialogo.open(ConfirmarDialogoComponent, {
      width: '480px',
      data: { titulo: `Tarjetas de ${olt.codigo}`, icono: 'dns', textoConfirmar: 'Guardar tarjetas',
        mensaje: `Antes: ${olt.tarjetasPermitidas.join(', ') || 'ninguna'}. Ahora: ${nuevas.join(', ') || 'ninguna'}.`,
        detalle: 'Las ONUs de tarjetas que no estén en la lista quedarán fuera de los lotes nuevos (EXC_TARJETA_NO_PERMITIDA).' } satisfies DatosConfirmacion,
    }).afterClosed().pipe(filter(Boolean), switchMap(() => this.configuracionService.cambiarTarjetas(olt.id, { tarjetas: nuevas }))).subscribe({
      next: (actualizada) => {
        this.olts.update((lista) => lista.map((o) => (o.id === actualizada.id ? actualizada : o)));
        this.deshacer(actualizada);
        this.avisos.open(`Tarjetas de ${actualizada.codigo} guardadas.`, 'Cerrar', { duration: 4_000 });
      },
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  private cargar(): void {
    forkJoin([this.configuracionService.consultar(), this.configuracionService.listarOlts()]).subscribe({
      next: ([configuracion, olts]) => {
        this.configuracion.set(configuracion);
        this.olts.set(olts);
      },
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }
}
