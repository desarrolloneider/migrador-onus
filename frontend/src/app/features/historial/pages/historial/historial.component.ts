import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, finalize, merge, startWith, switchMap } from 'rxjs';
import { descargarArchivo } from '../../../../core/api/descargas';
import { mensajeDeError } from '../../../../core/api/errores';
import { EstadoChipComponent } from '../../../../shared/estado-chip/estado-chip.component';
import { RESULTADOS_HISTORIAL, SIMULACION } from '../../../../shared/estados';
import { HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { ConfiguracionService } from '../../../configuracion/data/configuracion.service';
import { HistorialService } from '../../data/historial.service';
import { FiltrosHistorial, IntentoOnu, PaginaHistorial, ResultadoHistorial } from '../../models/historial.model';

/** Historial de ONUs: cuáles quedaron corregidas y cuáles no, de todos los lotes (ESP-17). */
@Component({
  selector: 'app-historial',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
    MatPaginatorModule, MatProgressBarModule, EstadoChipComponent, HoraColombiaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>Historial de ONUs</h1>
          <p class="subtitulo">El último resultado de cada ONU en cada paso del procedimiento. Sale de lo que guardan los lotes y no se edita a mano. Los lotes en simulación no cuentan como corregidas.</p>
        </div>
        <div class="acciones"><button mat-stroked-button (click)="exportar()" [disabled]="exportando()"><mat-icon>download</mat-icon> Exportar</button></div>
      </header>

      <div class="pasos" role="tablist" aria-label="Paso del procedimiento">
        @for (p of pasos; track p.valor) {
          <button type="button" role="tab" class="paso" [class.activo]="formulario.controls.tipo.value === p.valor"
                  [attr.aria-selected]="formulario.controls.tipo.value === p.valor" (click)="formulario.controls.tipo.setValue(p.valor)">
            <mat-icon>{{ p.icono }}</mat-icon> {{ p.nombre }}
          </button>
        }
      </div>

      <section class="rejilla-kpi">
        @for (r of resultados; track r) {
          <button type="button" class="kpi tono-{{ presentacion[r].tono }}" [class.activo]="formulario.controls.resultado.value === r" (click)="alternarResultado(r)"
                  [attr.aria-pressed]="formulario.controls.resultado.value === r">
            <span class="etiqueta"><mat-icon>{{ presentacion[r].icono }}</mat-icon> {{ etiqueta(r) }}</span>
            <span class="valor">{{ pagina()?.totales?.[r] ?? '—' }}</span>
          </button>
        }
      </section>

      <form class="filtros" [formGroup]="formulario">
        <mat-form-field class="ancho"><mat-label>SN</mat-label><mat-icon matPrefix>search</mat-icon><input matInput formControlName="sn" /></mat-form-field>
        <mat-form-field>
          <mat-label>OLT</mat-label>
          <mat-select formControlName="olt"><mat-option [value]="null">Todas</mat-option>
            @for (olt of olts(); track olt.id) { <mat-option [value]="olt.codigo">{{ olt.codigo }}</mat-option> }</mat-select>
        </mat-form-field>
        <mat-form-field><mat-label>Tarjeta</mat-label><input matInput formControlName="tarjeta" placeholder="0/1" /></mat-form-field>
        <mat-form-field>
          <mat-label>Resultado</mat-label>
          <mat-select formControlName="resultado"><mat-option [value]="null">Todos</mat-option>
            @for (r of resultados; track r) { <mat-option [value]="r">{{ presentacion[r].etiqueta }}</mat-option> }</mat-select>
        </mat-form-field>
        <mat-form-field><mat-label>Desde</mat-label><input matInput type="date" formControlName="desde" /></mat-form-field>
        <mat-form-field><mat-label>Hasta</mat-label><input matInput type="date" formControlName="hasta" /></mat-form-field>
        <button mat-button type="button" (click)="limpiar()"><mat-icon>filter_alt_off</mat-icon> Limpiar</button>
      </form>

      @if (error()) { <p class="aviso aviso-error"><mat-icon>error</mat-icon> {{ error() }}</p> }
      @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }

      <div class="contenedor-tabla">
        <table class="tabla">
          <thead><tr><th></th><th>SN</th><th>Último resultado</th><th>Posición</th><th>Lote</th><th>Fecha</th><th>Motivo</th></tr></thead>
          <tbody>
            @for (e of pagina()?.entradas ?? []; track e.sn) {
              <tr class="clicable" (click)="alternar(e.sn)" [attr.aria-expanded]="abierto() === e.sn">
                <td><mat-icon class="flecha">{{ abierto() === e.sn ? 'expand_less' : 'expand_more' }}</mat-icon></td>
                <td class="mono">{{ e.sn }}</td>
                <td><app-estado-chip [presentacion]="presentacion[e.ultimoResultado]" />
                  @if (e.ultimoResultado === 'FALLIDA') { <div class="secundario">{{ e.revisada ? 'revisada' : 'sin revisar' }}</div> }</td>
                <td class="secundario">{{ e.olt }} · {{ e.tarjeta ?? '—' }} · PON {{ e.pon ?? '—' }} · ID {{ e.onuId ?? '—' }}</td>
                <td><a class="mono" [routerLink]="['/lotes', e.loteId]" (click)="$event.stopPropagation()">{{ e.loteNumero }}</a></td>
                <td>{{ e.actualizadoEn | horaColombia }}</td>
                <td class="mensaje">@if (e.ultimoResultado === 'FALLIDA' || e.ultimoResultado === 'EXCLUIDA') {
                  <strong class="motivo-titulo">{{ explicar(e.codigo, e.motivo).titulo }}</strong>
                  <span class="motivo-que-hacer">{{ explicar(e.codigo, e.motivo).queHacer }}</span>
                  @if (e.motivo) { <span class="motivo-detalle">Detalle: {{ e.motivo }}</span> }
                }</td>
              </tr>
              @if (abierto() === e.sn) {
                <tr class="detalle-fila"><td colspan="7">
                  <strong>Todos los intentos de esta ONU</strong>
                  <ul class="intentos">
                    @for (i of intentos(); track $index) {
                      <li>
                        <app-estado-chip [presentacion]="presentacion[i.resultado]" compacto />
                        @if (i.simulacion) { <app-estado-chip [presentacion]="simulacion" compacto /> }
                        <span>{{ i.fecha | horaColombia }}</span> · <strong>{{ nombreDePaso(i.tipo) }}</strong> · <a class="mono" [routerLink]="['/lotes', i.loteId]">{{ i.loteNumero }}</a>
                        @if (i.paso) { · paso {{ i.paso }} }
                        @if (i.ejecucionId) { · <a [routerLink]="['/ejecuciones', i.ejecucionId]">ver detalle</a> }
                        @if (i.mensaje && i.resultado !== 'CORREGIDA') { <div class="secundario">{{ i.mensaje }}</div> }
                      </li>
                    } @empty { <li class="secundario">Solo hay registro de la vista previa: nunca se ejecutó.</li> }
                  </ul>
                </td></tr>
              }
            } @empty {
              <tr><td colspan="7" class="vacio">@if (!cargando()) { Ninguna ONU coincide con los filtros. }</td></tr>
            }
          </tbody>
        </table>
      </div>
      <mat-paginator [length]="pagina()?.total ?? 0" [pageSize]="tamano()" [pageIndex]="numeroPagina()" [pageSizeOptions]="[25, 50, 100]"
                     (page)="cambiarPagina($event)" aria-label="Páginas del historial" />
    </div>
  `,
  styles: `
    .mensaje .motivo-titulo { display: block; }
    .mensaje .motivo-que-hacer, .mensaje .motivo-detalle { display: block; font-size: 12px; color: var(--mat-sys-on-surface-variant); }
    .pasos { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 14px; }
    .paso { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 999px; cursor: pointer; font: inherit;
      border: 1px solid var(--mat-sys-outline-variant); background: transparent; color: var(--mat-sys-on-surface); }
    .paso mat-icon { font-size: 18px; width: 18px; height: 18px; flex: 0 0 18px; }
    .paso.activo { background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); border-color: var(--mat-sys-primary); }
    .flecha { color: var(--mat-sys-on-surface-variant); }
    .intentos { list-style: none; padding: 0; margin: 8px 0 0; display: flex; flex-direction: column; gap: 8px; }
    .intentos li { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .intentos .secundario { flex-basis: 100%; }
  `,
})
export class HistorialComponent implements OnInit {
  private readonly historialService = inject(HistorialService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly presentacion = RESULTADOS_HISTORIAL;
  protected readonly simulacion = SIMULACION;
  protected readonly resultados: ResultadoHistorial[] = ['CORREGIDA', 'FALLIDA', 'EXCLUIDA', 'YA_TENIA_PERFIL'];
  protected readonly pasos = [
    { valor: 'VLAN_GESTION', nombre: '1 · Gestión VLAN 46', icono: 'settings_ethernet' },
    { valor: 'PERFIL_TR069', nombre: '2 · Perfil GUAJIRANET1', icono: 'router' },
    { valor: 'SERVICIO_WAN', nombre: '3 · Servicio WAN', icono: 'swap_horiz' },
    { valor: 'TRES_PASOS', nombre: '1 → 2 → 3 · Los 3 pasos', icono: 'double_arrow' },
  ];

  protected nombreDePaso(tipo: string | null | undefined): string {
    return this.pasos.find((p) => p.valor === (tipo ?? 'PERFIL_TR069'))?.nombre ?? '';
  }

  /** El motivo en palabras: qué pasó y qué hacer. El texto técnico queda como detalle. */
  protected explicar(codigo: string | null, motivo: string | null): { titulo: string; queHacer: string } {
    const m = motivo ?? '';
    switch (codigo) {
      case 'EXC_FUERA_DE_LINEA':
        return { titulo: 'Estaba fuera de línea al ejecutar.', queHacer: 'No se tocó. Cuando vuelva a estar en línea, inclúyela en otro lote.' };
      case 'FALLO_SISTEMA_NO_RESPONDE':
        if (/GenieACS/i.test(m)) {
          return { titulo: 'La ONU no se reporta a GenieACS.', queHacer: 'No recibió IP en la VLAN 46: revisa la VLAN 46 en su tarjeta/puerto de la OLT o reiníciala, y vuelve a intentarlo.' };
        }
        if (/sesi[oó]n|cerr[oó]/i.test(m)) {
          return { titulo: 'Se cortó la conexión con la OLT.', queHacer: 'La OLT cerró la sesión antes de terminar. Vuelve a intentarlo en otro lote.' };
        }
        if (/no respondi[oó]/i.test(m)) {
          return { titulo: 'La OLT no respondió a tiempo.', queHacer: 'La OLT estaba ocupada (por ejemplo guardando). Vuelve a intentarlo en unos minutos.' };
        }
        return { titulo: 'Un sistema no respondió.', queHacer: 'Vuelve a intentarlo en otro lote.' };
      case 'FALLO_VERIFICACION':
        return { titulo: 'El cambio no quedó aplicado.', queHacer: 'Al verificar, la OLT no mostró el perfil nuevo. Vuelve a intentarlo o revísala en la OLT.' };
      case 'FALLO_FOTO_PREVIA':
        return { titulo: 'No se pudo leer su estado antes de cambiarla.', queHacer: 'No se tocó. Revisa que exista en el sistema indicado y vuelve a intentarlo.' };
      default:
        if (codigo?.startsWith('EXC_')) {
          return { titulo: 'Quedó por fuera al ejecutar.', queHacer: 'No se tocó. Mira el detalle.' };
        }
        return { titulo: 'Falló.', queHacer: 'Mira el detalle y vuelve a intentarlo.' };
    }
  }

  /** "Ya tenía el perfil" dice lo que corresponde a cada paso. */
  protected etiqueta(r: ResultadoHistorial): string {
    if (r !== 'YA_TENIA_PERFIL') {
      return this.presentacion[r].etiqueta;
    }
    const tipo = this.formulario.controls.tipo.value;
    return tipo === 'VLAN_GESTION' ? 'Ya tenía la VLAN 46' : tipo === 'SERVICIO_WAN' ? 'Ya tenía el servicio' : 'Ya tenía el perfil';
  }

  protected readonly olts = toSignal(inject(ConfiguracionService).listarOlts(), { initialValue: [] });
  protected readonly formulario = inject(FormBuilder).group({
    tipo: ['PERFIL_TR069'], sn: [''], olt: [null as string | null], tarjeta: [''], resultado: [null as ResultadoHistorial | null], desde: [''], hasta: [''],
  });

  protected readonly pagina = signal<PaginaHistorial | null>(null);
  protected readonly numeroPagina = signal(0);
  protected readonly tamano = signal(25);
  protected readonly cargando = signal(true);
  protected readonly exportando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly abierto = signal<string | null>(null);
  protected readonly intentos = signal<IntentoOnu[]>([]);
  private readonly paginacion$ = new Subject<void>();

  ngOnInit(): void {
    merge(this.formulario.valueChanges.pipe(debounceTime(300), startWith(null)), this.paginacion$).pipe(
      switchMap(() => {
        this.cargando.set(true);
        return this.historialService.buscar(this.filtros()).pipe(finalize(() => this.cargando.set(false)));
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (pagina) => this.pagina.set(pagina),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
    this.formulario.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.numeroPagina.set(0));
  }

  protected alternarResultado(resultado: ResultadoHistorial): void {
    const actual = this.formulario.controls.resultado.value;
    this.formulario.controls.resultado.setValue(actual === resultado ? null : resultado);
  }

  protected limpiar(): void {
    this.formulario.reset({ tipo: this.formulario.controls.tipo.value, sn: '', olt: null, tarjeta: '', resultado: null, desde: '', hasta: '' });
  }

  protected cambiarPagina(evento: PageEvent): void {
    this.numeroPagina.set(evento.pageIndex);
    this.tamano.set(evento.pageSize);
    this.paginacion$.next();
  }

  protected alternar(sn: string): void {
    if (this.abierto() === sn) {
      this.abierto.set(null);
      return;
    }
    this.abierto.set(sn);
    this.intentos.set([]);
    this.historialService.intentos(sn).subscribe((intentos) => this.intentos.set(intentos));
  }

  protected exportar(): void {
    this.exportando.set(true);
    this.historialService.exportar(this.filtros()).pipe(finalize(() => this.exportando.set(false))).subscribe({
      next: (archivo) => descargarArchivo(archivo, `historial-onus-${new Date().toISOString().slice(0, 10)}.csv`),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  private filtros(): FiltrosHistorial {
    const v = this.formulario.getRawValue();
    return { tipo: v.tipo, sn: v.sn, olt: v.olt, tarjeta: v.tarjeta, resultado: v.resultado, desde: v.desde, hasta: v.hasta,
      pagina: this.numeroPagina(), tamano: this.tamano() };
  }
}
