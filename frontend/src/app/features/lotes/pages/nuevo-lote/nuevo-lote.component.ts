import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Subject, Subscription, catchError, finalize, merge, of, switchMap, tap } from 'rxjs';
import { mensajeDeError } from '../../../../core/api/errores';
import { AuthService } from '../../../../core/auth/auth.service';
import { TiempoRealService } from '../../../../core/realtime/tiempo-real.service';
import { ConfiguracionService } from '../../../configuracion/data/configuracion.service';
import { Olt } from '../../../configuracion/models/configuracion.model';
import { LotesService } from '../../data/lotes.service';
import { OnusDeOlt } from '../../models/lote.model';

/** Armar un lote: marca y, si se quiere, OLT, tarjeta y puerto PON (ESP-03). */
@Component({
  selector: 'app-nuevo-lote',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatCheckboxModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <a class="volver" routerLink="/inicio"><mat-icon>arrow_back</mat-icon> Inicio</a>
      <header class="encabezado">
        <div>
          <h1>Nuevo lote</h1>
          <p class="subtitulo">Aquí se arma el cambio de perfil TR-069 a GUAJIRANET1, para las ONUs con GUAJIRANET (perfil 2) y las que no tienen perfil. La herramienta busca las ONUs en SmartOLT, lee su perfil en la OLT, las cruza con Wispro por IP y las separa en tres grupos. Nada cambia en ningún sistema hasta que confirmes la vista previa.</p>
        </div>
      </header>

      <nav class="asistente" aria-label="Pasos para armar un lote">
        @for (paso of pasos(); track paso.numero; let ultimo = $last) {
          <div class="paso" [class.hecho]="paso.estado === 'hecho'" [class.actual]="paso.estado === 'actual'">
            <span class="circulo">
              @if (paso.estado === 'hecho') { <mat-icon>check</mat-icon> } @else { {{ paso.numero }} }
            </span>
            <span class="paso-texto"><strong>{{ paso.titulo }}</strong><small>{{ paso.ayuda }}</small></span>
          </div>
          @if (!ultimo) { <span class="conector" [class.hecho]="paso.estado === 'hecho'"></span> }
        }
      </nav>
      <p class="aviso siguiente"><mat-icon>{{ siguiente().icono }}</mat-icon> <span>{{ siguiente().texto }}</span></p>

      <div class="disposicion">
        <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="armar()">
          <h2><mat-icon>tune</mat-icon> Qué ONUs</h2>
          <mat-form-field class="ancho-completo">
            <mat-label>Marca</mat-label>
            <mat-select formControlName="marca">
              <mat-option value="TODAS">Todas las marcas</mat-option>
              @for (m of marcas; track m.valor) { <mat-option [value]="m.valor">{{ m.nombre }}</mat-option> }
            </mat-select>
            <mat-hint>En la fase 1 solo Huawei; todos los modelos usan los mismos parámetros.</mat-hint>
          </mat-form-field>

          <mat-form-field class="ancho-completo">
            <mat-label>OLT (opcional)</mat-label>
            <mat-select formControlName="olt">
              <mat-option [value]="null">Todas las OLT</mat-option>
              @for (olt of olts(); track olt.id) {
                <mat-option [value]="olt.codigo">
                  {{ olt.codigo }} · {{ olt.nombre }}
                  @if (!olt.accesoConfigurado) { <span class="nota-opcion">— sin acceso por consola</span> }
                  @else if (olt.tarjetasPermitidas.length === 0) { <span class="nota-opcion">— sin tarjetas permitidas</span> }
                </mat-option>
              }
            </mat-select>
          </mat-form-field>

          <div class="fila">
            <mat-form-field>
              <mat-label>Tarjeta (opcional)</mat-label>
              <mat-select formControlName="tarjeta">
                <mat-option [value]="null">Todas las permitidas</mat-option>
                @for (tarjeta of tarjetas(); track tarjeta) {
                  <mat-option [value]="tarjeta">{{ tarjeta }}</mat-option>
                }
              </mat-select>
              <mat-hint>{{ ayudaTarjeta() }}</mat-hint>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Puerto PON (opcional)</mat-label>
              <input matInput type="number" min="0" formControlName="pon" />
              <mat-hint>{{ formulario.controls.tarjeta.value ? 'Número del puerto en la tarjeta' : 'Primero escoge una tarjeta' }}</mat-hint>
            </mat-form-field>
          </div>

          @if (error()) {
            <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p>
          }

          @if (armando()) {
            <div class="avance" role="status">
              <mat-progress-bar mode="indeterminate" />
              <ul>
                @for (mensaje of avance(); track $index; let ultimo = $last) {
                  <li [class.actual]="ultimo"><mat-icon>{{ ultimo ? 'pending' : 'check' }}</mat-icon> {{ mensaje }}</li>
                }
              </ul>
            </div>
          }

          <div class="acciones">
            <button mat-flat-button type="submit" [disabled]="armando()"><mat-icon>preview</mat-icon> Armar vista previa</button>
          </div>
        </form>

        <aside class="tarjeta guia">
          <h2><mat-icon>info</mat-icon> Cómo se arma</h2>
          <ol>
            <li><strong>SmartOLT:</strong> SN, posición, estado e IP de cada ONU. Si la descarga queda incompleta, no se arma nada.</li>
            <li><strong>La OLT:</strong> el perfil TR-069 real. Entran las que tienen GUAJIRANET y las que no tienen perfil; las de GUAJIRANET1 van a "ya tienen el perfil" y las de otro perfil quedan fuera.</li>
            <li><strong>Wispro, por IP:</strong> la IP de SmartOLT tiene que estar en un solo contrato.</li>
            <li><strong>Tres grupos:</strong> entran, quedan fuera (cada una con su motivo) y ya tienen el perfil.</li>
            <li><strong>Máximo {{ maximo }} ONUs por lote</strong> al empezar. Si hay más, usa los filtros.</li>
            <li><strong>La vista previa vence a los 30 minutos</strong> sin confirmar y queda descartada, sin borrar nada.</li>
          </ol>
        </aside>
      </div>

      @if (oltElegida(); as olt) {
        <section class="tarjeta onus-olt">
          <div class="encabezado-onus">
            <h2><mat-icon>lan</mat-icon> ONUs de {{ olt }}</h2>
            <button mat-button type="button" (click)="recargarOnus()" [disabled]="cargandoOnus()"><mat-icon>refresh</mat-icon> Actualizar</button>
          </div>
          <p class="subtitulo">Estado según SmartOLT y perfil TR-069 leído directamente en la OLT. Solo lectura: aquí no se cambia nada.</p>

          @if (cargandoOnus()) {
            <div class="avance" role="status"><mat-progress-bar mode="indeterminate" /><p>Leyendo SmartOLT y la OLT…</p></div>
          } @else if (errorOnus()) {
            <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ errorOnus() }}</p>
          } @else if (onusOlt(); as datos) {
            @if (!datos.perfilesLeidos) {
              <p class="aviso"><mat-icon>info</mat-icon> No se pudo leer el perfil en la OLT (sin acceso por consola o no respondió). Sus ONUs quedarán fuera del lote.</p>
            }
            <div class="filtros">
              <div class="chips" role="group" aria-label="Filtrar por perfil">
                <button type="button" class="chip" [class.activo]="filtroPerfil() === null" (click)="filtroPerfil.set(null)">
                  Todos <span class="cuenta">{{ datos.onus.length }}</span>
                </button>
                @for (p of perfiles(); track p.nombre) {
                  <button type="button" class="chip" [class.activo]="filtroPerfil() === p.nombre" (click)="filtroPerfil.set(p.nombre)">
                    {{ p.nombre }} <span class="cuenta">{{ p.cantidad }}</span>
                  </button>
                }
              </div>
              <mat-form-field class="estado" subscriptSizing="dynamic">
                <mat-label>Estado</mat-label>
                <mat-select [value]="filtroEstado()" (selectionChange)="filtroEstado.set($event.value)">
                  <mat-option value="TODOS">Todos</mat-option>
                  <mat-option value="EN_LINEA">En línea</mat-option>
                  <mat-option value="FUERA">Fuera de línea</mat-option>
                </mat-select>
              </mat-form-field>
            </div>
            <div class="barra-seleccion">
              <span class="resumen">{{ visibles().length }} de {{ datos.onus.length }} ONUs
                @if (seleccionadas().size) { · <strong>{{ seleccionadas().size }} seleccionadas</strong> }</span>
              <span class="acciones-seleccion">
                <button mat-button type="button" (click)="seleccionarVisibles()" [disabled]="!seleccionablesVisibles().length">
                  <mat-icon>select_all</mat-icon> Seleccionar las {{ seleccionablesVisibles().length }} visibles</button>
                @if (seleccionadas().size) {
                  <button mat-button type="button" (click)="limpiarSeleccion()"><mat-icon>deselect</mat-icon> Quitar selección</button>
                  <button mat-flat-button type="button" (click)="armar()" [disabled]="armando()">
                    <mat-icon>preview</mat-icon> Armar vista previa con las {{ seleccionadas().size }}</button>
                }
              </span>
            </div>
            @if (seleccionadas().size) {
              <p class="aviso"><mat-icon>info</mat-icon> <span>Se armará la vista previa solo con las seleccionadas
                ({{ perfilesSeleccionados() }}). Cada una pasa por las mismas reglas: en línea, IP en un solo contrato de Wispro y tarjeta permitida.
                Las que ya tienen GUAJIRANET1 no se pueden seleccionar.</span></p>
            }
            <div class="contenedor-tabla">
              <table class="tabla">
                <thead><tr>
                  <th class="casilla"><mat-checkbox [checked]="todasVisiblesSeleccionadas()" [indeterminate]="algunasVisiblesSeleccionadas()"
                      (change)="$event.checked ? seleccionarVisibles() : quitarVisibles()" aria-label="Seleccionar las visibles" /></th>
                  <th>Posición</th><th>SN</th><th>Modelo</th><th>Estado</th><th>Perfil TR-069</th><th>IP</th>
                </tr></thead>
                <tbody>
                  @for (o of visibles(); track o.tarjeta + '/' + o.pon + ':' + o.onuId) {
                    <tr [class.seleccionada]="seleccionadas().has(o.sn)">
                      <td class="casilla"><mat-checkbox [checked]="seleccionadas().has(o.sn)" [disabled]="!seleccionable(o)"
                          (change)="alternar(o.sn)" [attr.aria-label]="'Seleccionar ' + o.sn" /></td>
                      <td class="mono sin-corte">{{ o.tarjeta }}/{{ o.pon }} · ONT {{ o.onuId }}</td>
                      <td class="mono">{{ o.sn }}</td>
                      <td>{{ o.modelo ?? '—' }}</td>
                      <td><span class="estado-onu" [class.en-linea]="o.enLinea">{{ o.enLinea ? 'En línea' : 'Fuera de línea' }}</span></td>
                      <td><span class="perfil" [class.origen]="o.perfil === perfilOrigen" [class.destino]="o.perfil === perfilDestino">{{ o.perfil ?? 'Sin perfil' }}</span></td>
                      <td class="mono">{{ o.ip ?? '—' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="7" class="vacio">No hay ONUs con esos filtros.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .asistente { display: flex; align-items: center; gap: 8px; margin: 4px 0 16px; flex-wrap: wrap; }
    .paso { display: flex; align-items: center; gap: 10px; opacity: .65; transition: opacity .25s ease; }
    .paso.actual, .paso.hecho { opacity: 1; }
    .circulo { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; font-weight: 700; flex: none;
      border: 2px solid var(--mat-sys-outline); color: var(--mat-sys-on-surface-variant); transition: all .25s ease; }
    .paso.actual .circulo { border-color: var(--mat-sys-primary); background: var(--mat-sys-primary); color: var(--mat-sys-on-primary);
      box-shadow: 0 0 0 6px color-mix(in srgb, var(--mat-sys-primary) 20%, transparent); }
    .paso.hecho .circulo { border-color: var(--color-exito); background: var(--color-exito); color: var(--mat-sys-surface); }
    .circulo mat-icon { font-size: 20px; width: 20px; height: 20px; }
    .paso-texto { display: flex; flex-direction: column; line-height: 1.25; }
    .paso-texto small { color: var(--mat-sys-on-surface-variant); font-size: 12px; }
    .conector { flex: 1; min-width: 24px; height: 2px; background: var(--mat-sys-outline-variant); border-radius: 1px; }
    .conector.hecho { background: var(--color-exito); }
    .siguiente { margin-bottom: 16px; }
    @media (max-width: 760px) { .paso-texto small { display: none; } .conector { min-width: 12px; } }
    .onus-olt { margin-top: 16px; }
    .encabezado-onus { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
    .encabezado-onus h2 { margin: 0; }
    .filtros { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; margin: 12px 0 4px; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .chip { border: 1px solid var(--mat-sys-outline-variant); background: transparent; color: var(--mat-sys-on-surface); border-radius: 999px;
      padding: 6px 12px; font: inherit; font-size: 13px; cursor: pointer; display: inline-flex; gap: 8px; align-items: center; }
    .chip.activo { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); border-color: transparent; }
    .chip .cuenta { font-weight: 600; }
    .estado { width: 180px; }
    .resumen { font-size: 13px; color: var(--mat-sys-on-surface-variant); margin: 4px 0 8px; }
    .contenedor-tabla { overflow-x: auto; max-height: 520px; overflow-y: auto; }
    .estado-onu { font-size: 12.5px; padding: 2px 8px; border-radius: 999px; background: var(--mat-sys-surface-container-highest); white-space: nowrap; }
    .estado-onu.en-linea { background: color-mix(in srgb, #2e7d32 22%, transparent); }
    .perfil { font-family: var(--fuente-mono, monospace); font-size: 12.5px; }
    .perfil.origen { color: var(--mat-sys-error); font-weight: 600; }
    .perfil.destino { color: #2e9d4a; font-weight: 600; }
    .vacio { text-align: center; color: var(--mat-sys-on-surface-variant); }
    .barra-seleccion { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px; margin: 4px 0 8px; }
    .acciones-seleccion { display: flex; flex-wrap: wrap; gap: 8px; }
    .casilla { width: 40px; }
    tr.seleccionada td { background: color-mix(in srgb, var(--mat-sys-primary) 10%, transparent); }
    .disposicion { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .disposicion { grid-template-columns: 1fr; } }
    form { display: flex; flex-direction: column; gap: 16px; }
    .fila { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    @media (max-width: 520px) { .fila { grid-template-columns: 1fr; } }
    .nota-opcion { color: var(--mat-sys-on-surface-variant); font-size: 12px; }
    .acciones { display: flex; justify-content: flex-end; }
    .avance ul { list-style: none; padding: 0; margin: 12px 0 0; display: flex; flex-direction: column; gap: 6px; }
    .avance li { display: flex; align-items: center; gap: 8px; color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    .avance li mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--color-exito); }
    .avance li.actual { color: var(--mat-sys-on-surface); font-weight: 500; }
    .avance li.actual mat-icon { color: var(--color-progreso); }
    .guia ol { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 10px; line-height: 1.45; }
  `,
})
export class NuevoLoteComponent {
  private readonly lotesService = inject(LotesService);
  private readonly tiempoReal = inject(TiempoRealService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly maximo = 50;

  /** Marcas de ONU vistas en SmartOLT. El cambio lo hace la OLT Huawei, así que todas pueden entrar. */
  protected readonly marcas = [
    { valor: 'Huawei', nombre: 'Huawei' }, { valor: 'ZTE', nombre: 'ZTE' }, { valor: 'Optictimes', nombre: 'Optictimes' },
    { valor: 'FiberHome', nombre: 'FiberHome' }, { valor: 'TP-Link', nombre: 'TP-Link' }, { valor: 'Genérica GPON', nombre: 'Genérica GPON' },
  ];
  protected readonly formulario = inject(FormBuilder).group({
    marca: ['TODAS', Validators.required],
    olt: [null as string | null],
    tarjeta: [{ value: null as string | null, disabled: true }],
    pon: [{ value: null as number | null, disabled: true }, Validators.min(0)],
  });
  protected readonly olts = toSignal(inject(ConfiguracionService).listarOlts(), { initialValue: [] as Olt[] });
  private readonly oltElegida = toSignal(this.formulario.controls.olt.valueChanges, { initialValue: null });
  protected readonly tarjetas = computed(() => this.olts().find((o) => o.codigo === this.oltElegida())?.tarjetasPermitidas ?? []);
  protected readonly ayudaTarjeta = computed(() => {
    if (!this.oltElegida()) return 'Primero escoge una OLT';
    return this.tarjetas().length ? 'Solo se ofrecen las tarjetas permitidas de esa OLT' : 'Esa OLT no tiene tarjetas permitidas: sus ONUs quedarán fuera';
  });
  /** El perfil que se cambia y el nuevo, para resaltarlos en la tabla. */
  protected readonly perfilOrigen = 'GUAJIRANET';
  protected readonly perfilDestino = 'GUAJIRANET1';
  protected readonly onusOlt = signal<OnusDeOlt | null>(null);
  protected readonly cargandoOnus = signal(false);
  protected readonly errorOnus = signal<string | null>(null);
  protected readonly filtroPerfil = signal<string | null>(null);
  protected readonly filtroEstado = signal<'TODOS' | 'EN_LINEA' | 'FUERA'>('TODOS');
  private readonly tarjetaElegida = toSignal(this.formulario.controls.tarjeta.valueChanges, { initialValue: null });
  private readonly ponElegido = toSignal(this.formulario.controls.pon.valueChanges, { initialValue: null });
  private readonly recarga = new Subject<string>();
  protected readonly perfiles = computed(() =>
    Object.entries(this.onusOlt()?.porPerfil ?? {}).map(([nombre, cantidad]) => ({ nombre, cantidad })));
  protected readonly visibles = computed(() => {
    const perfil = this.filtroPerfil();
    const estado = this.filtroEstado();
    const tarjeta = this.tarjetaElegida();
    const pon = this.ponElegido();
    return (this.onusOlt()?.onus ?? []).filter((o) =>
      (perfil === null || (o.perfil ?? 'Sin perfil') === perfil)
      && (estado === 'TODOS' || (estado === 'EN_LINEA') === o.enLinea)
      && (!tarjeta || o.tarjeta === tarjeta)
      && (pon === null || pon === undefined || String(pon) === '' || o.pon === Number(pon)));
  });
  /** SN de las ONUs escogidas en la tabla. */
  protected readonly seleccionadas = signal<Set<string>>(new Set());
  protected readonly seleccionablesVisibles = computed(() => this.visibles().filter((o) => this.seleccionable(o)));
  protected readonly todasVisiblesSeleccionadas = computed(() => {
    const visibles = this.seleccionablesVisibles();
    return visibles.length > 0 && visibles.every((o) => this.seleccionadas().has(o.sn));
  });
  protected readonly algunasVisiblesSeleccionadas = computed(() =>
    !this.todasVisiblesSeleccionadas() && this.seleccionablesVisibles().some((o) => this.seleccionadas().has(o.sn)));
  /** Los perfiles de las seleccionadas, que son los que el lote va a cambiar. */
  private readonly perfilesDeLaSeleccion = computed(() => {
    const elegidas = this.seleccionadas();
    const perfiles = new Set((this.onusOlt()?.onus ?? []).filter((o) => elegidas.has(o.sn)).map((o) => o.perfil ?? 'SIN_PERFIL'));
    return [...perfiles];
  });
  protected readonly perfilesSeleccionados = computed(() =>
    this.perfilesDeLaSeleccion().map((p) => (p === 'SIN_PERFIL' ? 'sin perfil' : p)).join(' y '));
  protected readonly armando = signal(false);
  /** Los pasos del asistente, según lo que ya se hizo. */
  protected readonly pasos = computed(() => {
    const olt = !!this.oltElegida();
    const conOnus = this.seleccionadas().size > 0 || (olt && !!this.onusOlt());
    const armando = this.armando();
    const estado = (hecho: boolean, actual: boolean): 'hecho' | 'actual' | 'pendiente' => (hecho ? 'hecho' : actual ? 'actual' : 'pendiente');
    return [
      { numero: 1, titulo: 'OLT', ayuda: 'Dónde están las ONUs', estado: estado(olt, !olt) },
      { numero: 2, titulo: 'ONUs', ayuda: 'Filtra o selecciona', estado: estado(conOnus && armando, olt && !armando) },
      { numero: 3, titulo: 'Vista previa', ayuda: 'Revisa qué entra', estado: estado(false, armando) },
      { numero: 4, titulo: 'Ejecutar', ayuda: 'Simular o cambiar', estado: estado(false, false) },
    ];
  });
  /** Qué hacer ahora, en una frase. */
  protected readonly siguiente = computed(() => {
    if (this.armando()) {
      return { icono: 'hourglass_top', texto: 'Armando la vista previa: leyendo SmartOLT, la OLT y Wispro…' };
    }
    if (!this.oltElegida()) {
      return { icono: 'lan', texto: 'Paso 1: escoge la OLT. Abajo aparecerán sus ONUs con su estado y su perfil TR-069.' };
    }
    if (this.seleccionadas().size) {
      return { icono: 'checklist', texto: `Paso 2: tienes ${this.seleccionadas().size} ONU(s) seleccionadas. Presiona "Armar vista previa con las ${this.seleccionadas().size}".` };
    }
    return { icono: 'filter_alt', texto: 'Paso 2: filtra por tarjeta o puerto, o selecciona ONUs en la tabla de abajo (sin perfil y GUAJIRANET). Luego arma la vista previa.' };
  });
  protected readonly avance = signal<string[]>([]);
  protected readonly error = signal<string | null>(null);

  constructor() {
    const { olt, tarjeta, pon } = this.formulario.controls;
    olt.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      tarjeta.reset(null);
      valor ? tarjeta.enable() : tarjeta.disable();
    });
    // Al escoger una OLT se leen sus ONUs con su perfil real; escoger otra cancela la lectura anterior.
    merge(olt.valueChanges, this.recarga).pipe(
      tap(() => {
        this.onusOlt.set(null);
        this.errorOnus.set(null);
        this.filtroPerfil.set(null);
        this.seleccionadas.set(new Set());
      }),
      switchMap((codigo) => {
        if (!codigo) {
          return of(null);
        }
        this.cargandoOnus.set(true);
        return this.lotesService.onusDeOlt(codigo).pipe(
          catchError((error: unknown) => {
            this.errorOnus.set(mensajeDeError(error));
            return of(null);
          }),
          finalize(() => this.cargandoOnus.set(false)),
        );
      }),
      takeUntilDestroyed(),
    ).subscribe((datos) => this.onusOlt.set(datos));
    tarjeta.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      pon.reset(null);
      valor ? pon.enable() : pon.disable();
    });
  }

  /** Las que ya tienen el perfil nuevo no se seleccionan: no hay nada que cambiarles. */
  protected seleccionable(o: { perfil: string | null }): boolean {
    return o.perfil?.toUpperCase() !== this.perfilDestino;
  }

  protected alternar(sn: string): void {
    this.seleccionadas.update((actual) => {
      const nueva = new Set(actual);
      if (!nueva.delete(sn)) {
        nueva.add(sn);
      }
      return nueva;
    });
  }

  protected seleccionarVisibles(): void {
    this.seleccionadas.update((actual) => new Set([...actual, ...this.seleccionablesVisibles().map((o) => o.sn)]));
  }

  protected quitarVisibles(): void {
    const visibles = new Set(this.seleccionablesVisibles().map((o) => o.sn));
    this.seleccionadas.update((actual) => new Set([...actual].filter((sn) => !visibles.has(sn))));
  }

  protected limpiarSeleccion(): void {
    this.seleccionadas.set(new Set());
  }

  protected recargarOnus(): void {
    const codigo = this.oltElegida();
    if (codigo) {
      this.recarga.next(codigo);
    }
  }

  protected armar(): void {
    if (this.formulario.invalid) {
      return;
    }
    const valores = this.formulario.getRawValue();
    this.error.set(null);
    this.avance.set([]);
    this.armando.set(true);
    const avance: Subscription = this.tiempoReal.escuchar(`armado:${this.auth.usuario()?.usuario}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((evento) => this.avance.update((lista) => [...lista, String(evento.datos['mensaje'] ?? '')]));
    this.lotesService.armarVistaPrevia({
      marca: valores.marca ?? 'TODAS',
      olt: valores.olt ?? null,
      tarjeta: valores.tarjeta ?? null,
      pon: valores.pon === null || valores.pon === undefined || String(valores.pon) === '' ? null : Number(valores.pon),
      perfiles: this.seleccionadas().size ? this.perfilesDeLaSeleccion() : null,
      sns: this.seleccionadas().size ? [...this.seleccionadas()] : null,
    }).pipe(finalize(() => {
      this.armando.set(false);
      avance.unsubscribe();
    })).subscribe({
      next: (vista) => void this.router.navigate(['/lotes', vista.lote.id, 'vista-previa']),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }
}
