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
          <p class="subtitulo">El último resultado de cada ONU que la herramienta intentó corregir. Sale de lo que guardan los lotes y no se edita a mano. Los lotes en simulación no cuentan como corregidas.</p>
        </div>
        <div class="acciones"><button mat-stroked-button (click)="exportar()" [disabled]="exportando()"><mat-icon>download</mat-icon> Exportar</button></div>
      </header>

      <section class="rejilla-kpi">
        @for (r of resultados; track r) {
          <button type="button" class="kpi tono-{{ presentacion[r].tono }}" [class.activo]="formulario.controls.resultado.value === r" (click)="alternarResultado(r)"
                  [attr.aria-pressed]="formulario.controls.resultado.value === r">
            <span class="etiqueta"><mat-icon>{{ presentacion[r].icono }}</mat-icon> {{ presentacion[r].etiqueta }}</span>
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
                <td class="mensaje">@if (e.codigo) { <span class="codigo-motivo">{{ e.codigo }}</span> } {{ e.motivo ?? '' }}</td>
              </tr>
              @if (abierto() === e.sn) {
                <tr class="detalle-fila"><td colspan="7">
                  <strong>Todos los intentos de esta ONU</strong>
                  <ul class="intentos">
                    @for (i of intentos(); track $index) {
                      <li>
                        <app-estado-chip [presentacion]="presentacion[i.resultado]" compacto />
                        @if (i.simulacion) { <app-estado-chip [presentacion]="simulacion" compacto /> }
                        <span>{{ i.fecha | horaColombia }}</span> · <a class="mono" [routerLink]="['/lotes', i.loteId]">{{ i.loteNumero }}</a>
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
  protected readonly olts = toSignal(inject(ConfiguracionService).listarOlts(), { initialValue: [] });
  protected readonly formulario = inject(FormBuilder).group({
    sn: [''], olt: [null as string | null], tarjeta: [''], resultado: [null as ResultadoHistorial | null], desde: [''], hasta: [''],
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
    this.formulario.reset({ sn: '', olt: null, tarjeta: '', resultado: null, desde: '', hasta: '' });
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
    return { sn: v.sn, olt: v.olt, tarjeta: v.tarjeta, resultado: v.resultado, desde: v.desde, hasta: v.hasta,
      pagina: this.numeroPagina(), tamano: this.tamano() };
  }
}
