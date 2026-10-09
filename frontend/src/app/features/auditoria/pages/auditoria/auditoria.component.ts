import { JsonPipe, KeyValuePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { DuracionPipe, HoraColombiaPipe } from '../../../../shared/formato/formato.pipes';
import { AuditoriaService } from '../../data/auditoria.service';
import { ACCIONES_AUDITORIA, FiltrosAuditoria, PaginaAuditoria, ResultadoIntegridad, TipoActor } from '../../models/auditoria.model';

/** Auditoría: buscar, exportar y verificar integridad (ESP-13). Inalterable: no hay forma de editarla desde aquí. */
@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, JsonPipe, KeyValuePipe, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatPaginatorModule, MatProgressBarModule, HoraColombiaPipe, DuracionPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>Auditoría</h1>
          <p class="subtitulo">Todo lo que hace la herramienta y sus usuarios: quién, qué, sobre cuál ONU, en qué OLT, qué comando, qué respondió y cuándo. Cada evento está encadenado al anterior con su huella; no se puede cambiar ni borrar.</p>
        </div>
        <div class="acciones">
          <button mat-stroked-button (click)="verificar()" [disabled]="verificando()"><mat-icon>verified_user</mat-icon> Verificar integridad</button>
          <button mat-stroked-button (click)="exportar()" [disabled]="exportando()"><mat-icon>download</mat-icon> Exportar</button>
        </div>
      </header>

      @if (verificando()) { <mat-progress-bar mode="indeterminate" /> }
      @if (integridad(); as r) {
        <p class="aviso" [class.aviso-exito]="r.intacta" [class.aviso-error]="!r.intacta" role="status">
          <mat-icon>{{ r.intacta ? 'verified_user' : 'gpp_bad' }}</mat-icon> <span>{{ r.mensaje }}</span></p>
      }
      @if (error()) { <p class="aviso aviso-error"><mat-icon>error</mat-icon> {{ error() }}</p> }

      <form class="filtros" [formGroup]="formulario">
        <mat-form-field><mat-label>Desde</mat-label><input matInput type="date" formControlName="desde" /></mat-form-field>
        <mat-form-field><mat-label>Hasta</mat-label><input matInput type="date" formControlName="hasta" /></mat-form-field>
        <mat-form-field><mat-label>Usuario</mat-label><input matInput formControlName="usuario" /></mat-form-field>
        <mat-form-field><mat-label>Lote</mat-label><input matInput formControlName="lote" placeholder="LOT-2026-…" /></mat-form-field>
        <mat-form-field><mat-label>SN</mat-label><input matInput formControlName="sn" /></mat-form-field>
        <mat-form-field><mat-label>OLT o sistema</mat-label><input matInput formControlName="sistema" /></mat-form-field>
        <mat-form-field>
          <mat-label>Paso</mat-label>
          <mat-select formControlName="paso"><mat-option [value]="null">Todos</mat-option>
            @for (p of pasos; track p) { <mat-option [value]="p">{{ p }}</mat-option> }</mat-select>
        </mat-form-field>
        <mat-form-field class="ancho">
          <mat-label>Acción</mat-label>
          <mat-select formControlName="accion"><mat-option [value]="null">Todas</mat-option>
            @for (grupo of acciones | keyvalue: sinOrden; track grupo.key) {
              <mat-optgroup [label]="grupo.key">@for (a of grupo.value; track a) { <mat-option [value]="a">{{ a }}</mat-option> }</mat-optgroup>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Resultado</mat-label>
          <mat-select formControlName="resultado"><mat-option [value]="null">Todos</mat-option>
            @for (r of resultados; track r) { <mat-option [value]="r">{{ r }}</mat-option> }</mat-select>
        </mat-form-field>
        <button mat-button type="button" (click)="limpiar()"><mat-icon>filter_alt_off</mat-icon> Limpiar</button>
      </form>

      @if (cargando()) { <mat-progress-bar mode="indeterminate" /> }
      <div class="contenedor-tabla">
        <table class="tabla">
          <thead><tr><th>Hora</th><th>Quién</th><th>Acción</th><th>Lote</th><th>SN</th><th>Paso</th><th>OLT o sistema</th><th>Resultado</th><th>Mensaje</th></tr></thead>
          <tbody>
            @for (e of pagina()?.eventos ?? []; track e.id) {
              <tr class="clicable" (click)="alternar(e.id)" [attr.aria-expanded]="abierto() === e.id">
                <td class="hora">{{ e.ocurridoEn | horaColombia: 'completa' }}</td>
                <td class="sin-corte"><span class="quien"><span class="avatar-actor" [class.sistema]="e.actorTipo !== 'USUARIO'"><mat-icon [attr.aria-label]="actores[e.actorTipo]">{{ iconoActor(e.actorTipo) }}</mat-icon></span> {{ e.usuario ?? actores[e.actorTipo] }}</span></td>
                <td><span class="accion tono-{{ estiloAccion(e.accion).tono }}" [attr.title]="e.accion"><mat-icon>{{ estiloAccion(e.accion).icono }}</mat-icon>{{ legible(e.accion) }}</span></td>
                <td class="sin-corte">@if (e.loteId) { <a class="mono" [routerLink]="['/lotes', e.loteId]" (click)="$event.stopPropagation()">{{ e.loteNumero }}</a> }</td>
                <td class="mono">{{ e.sn ?? '' }}</td>
                <td>@if (e.paso) { <span class="paso">{{ e.paso }}</span> }</td>
                <td>{{ e.sistema ?? '' }}</td>
                <td>@if (e.resultado) { <span [class]="'resultado r-' + e.resultado">{{ e.resultado }}</span> }</td>
                <td class="mensaje">{{ e.mensaje ?? '' }}</td>
              </tr>
              @if (abierto() === e.id) {
                <tr class="detalle-fila"><td colspan="9">
                  <dl class="rejilla-datos">
                    <div><dt>Id del evento</dt><dd class="mono">{{ e.id }}</dd></div>
                    <div><dt>Tipo de actor</dt><dd>{{ actores[e.actorTipo] }}</dd></div>
                    <div><dt>IP de origen</dt><dd class="mono">{{ e.ipOrigen ?? '—' }}</dd></div>
                    <div><dt>Duración</dt><dd>{{ e.duracionMs | duracion }}</dd></div>
                    <div><dt>Código</dt><dd class="mono">{{ e.codigo ?? '—' }}</dd></div>
                  </dl>
                  <div class="json">
                    <div><strong>Petición</strong><pre class="consola">{{ e.peticion | json }}</pre></div>
                    <div><strong>Respuesta</strong><pre class="consola">{{ e.respuesta | json }}</pre></div>
                  </div>
                </td></tr>
              }
            } @empty {
              <tr><td colspan="9" class="vacio">@if (!cargando()) { Ningún evento coincide con los filtros. }</td></tr>
            }
          </tbody>
        </table>
      </div>
      <mat-paginator [length]="pagina()?.total ?? 0" [pageSize]="tamano()" [pageIndex]="numeroPagina()" [pageSizeOptions]="[50, 100, 200]"
                     (page)="cambiarPagina($event)" aria-label="Páginas de la auditoría" />
    </div>
  `,
  styles: `
    .hora, .sin-corte { white-space: nowrap; }
    .hora { font-variant-numeric: tabular-nums; }
    .quien { display: inline-flex; align-items: center; gap: 8px; }
    .avatar-actor { width: 26px; height: 26px; border-radius: 50%; display: grid; place-items: center; flex: none;
      background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
    .avatar-actor.sistema { background: var(--mat-sys-surface-container-highest); color: var(--mat-sys-on-surface-variant); }
    .avatar-actor mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .accion { display: inline-flex; align-items: center; gap: 6px; padding: 3px 10px 3px 8px; border-radius: 999px; font-size: 12px; font-weight: 600;
      white-space: nowrap; background: var(--fondo-tono, var(--mat-sys-surface-container-highest)); color: var(--color-tono, inherit); }
    .accion mat-icon { font-size: 15px; width: 15px; height: 15px; }
    .paso { font-family: var(--fuente-mono); font-size: 12px; padding: 1px 6px; border-radius: 6px; background: var(--mat-sys-surface-container-highest); }
    .resultado { display: inline-block; font-size: 11.5px; font-weight: 700; padding: 2px 8px; border-radius: 6px; letter-spacing: .02em;
      background: var(--mat-sys-surface-container-highest); }
    .r-FALLIDO, .r-FALLIDA, .r-FALLO, .r-ROTA, .r-RECHAZADA, .r-BLOQUEADO { background: var(--fondo-error); }
    .r-OK, .r-INTACTA, .r-TERMINADO, .r-REVISADA, .r-COMPLETADA { background: var(--fondo-exito); }
    .r-SIMULADO, .r-SIMULADA { background: var(--fondo-simulado); }
    .r-FALLIDO, .r-FALLIDA, .r-ROTA, .r-RECHAZADA, .r-BLOQUEADO { color: var(--color-error); }
    .r-OK, .r-INTACTA, .r-TERMINADO, .r-REVISADA { color: var(--color-exito); }
    .r-SIMULADO, .r-SIMULADA { color: var(--color-simulado); }
    .r-FALLO { color: var(--color-error); }
    .r-COMPLETADA { color: var(--color-exito); }
    .mensaje { max-width: 420px; }
    .json { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px; }
    .json pre { max-height: 260px; }
    @media (max-width: 800px) { .json { grid-template-columns: 1fr; } }
  `,
})
export class AuditoriaComponent implements OnInit {
  /** Ícono y color de cada acción, por familia, para leer la tabla de un vistazo. */
  protected estiloAccion(accion: string): { icono: string; tono: string } {
    const a = accion ?? '';
    if (/FALLID|RECHAZ|BLOQUE|ROTA|FALLO/.test(a)) return { icono: 'error', tono: 'error' };
    if (/COMPLETAD|REVISAD|RESUELT/.test(a)) return { icono: 'task_alt', tono: 'exito' };
    if (/SIMULAD/.test(a)) return { icono: 'science', tono: 'simulado' };
    if (/SESION|CLAVE|CREDENCIAL/.test(a)) return { icono: 'login', tono: 'info' };
    if (/USUARIO/.test(a)) return { icono: 'manage_accounts', tono: 'info' };
    if (/EJECUTOR/.test(a)) return { icono: 'memory', tono: 'neutro' };
    if (/VISTA_PREVIA|LOTE/.test(a)) return { icono: 'inventory_2', tono: 'progreso' };
    if (/COMANDO|PASO/.test(a)) return { icono: 'terminal', tono: 'neutro' };
    if (/PARAMETRO|TARJETAS|ACCESO_OLT|HUELLA/.test(a)) return { icono: 'settings', tono: 'aviso' };
    if (/EXCLUIDA/.test(a)) return { icono: 'block', tono: 'aviso' };
    return { icono: 'bolt', tono: 'neutro' };
  }

  /** SESION_INICIADA → "Sesión iniciada". */
  protected legible(accion: string): string {
    const texto = (accion ?? '').toLowerCase().replace(/_/g, ' ')
      .replace(/\bsesion\b/, 'sesión').replace(/\bonu\b/, 'ONU').replace(/\bolt\b/, 'OLT').replace(/\bparametro\b/, 'parámetro');
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  private readonly auditoriaService = inject(AuditoriaService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly acciones = ACCIONES_AUDITORIA;
  protected readonly pasos = ['P01', 'P02', 'P03', 'P04', 'P05', 'P06', 'P07', 'P08'];
  protected readonly resultados = ['OK', 'FALLIDO', 'SIMULADO', 'RECHAZADA', 'INTACTA', 'ROTA'];
  protected readonly actores: Record<TipoActor, string> = { USUARIO: 'Usuario', PROCESO_EJECUTOR: 'Proceso ejecutor', CONSOLA: 'Consola del servidor' };
  protected readonly sinOrden = () => 0;
  protected readonly formulario = inject(FormBuilder).group({
    desde: [''], hasta: [''], usuario: [''], lote: [''], sn: [''], sistema: [''],
    paso: [null as string | null], accion: [null as string | null], resultado: [null as string | null],
  });

  protected readonly pagina = signal<PaginaAuditoria | null>(null);
  protected readonly numeroPagina = signal(0);
  protected readonly tamano = signal(50);
  protected readonly cargando = signal(true);
  protected readonly verificando = signal(false);
  protected readonly exportando = signal(false);
  protected readonly integridad = signal<ResultadoIntegridad | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly abierto = signal<number | null>(null);
  private readonly paginacion$ = new Subject<void>();

  ngOnInit(): void {
    merge(this.formulario.valueChanges.pipe(debounceTime(300), startWith(null)), this.paginacion$).pipe(
      switchMap(() => {
        this.cargando.set(true);
        return this.auditoriaService.buscar(this.filtros()).pipe(finalize(() => this.cargando.set(false)));
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (pagina) => this.pagina.set(pagina),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
    this.formulario.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.numeroPagina.set(0));
  }

  protected iconoActor(actor: TipoActor): string {
    return actor === 'USUARIO' ? 'person' : actor === 'PROCESO_EJECUTOR' ? 'memory' : 'terminal';
  }

  protected alternar(id: number): void {
    this.abierto.set(this.abierto() === id ? null : id);
  }

  protected limpiar(): void {
    this.formulario.reset({ desde: '', hasta: '', usuario: '', lote: '', sn: '', sistema: '', paso: null, accion: null, resultado: null });
  }

  protected cambiarPagina(evento: PageEvent): void {
    this.numeroPagina.set(evento.pageIndex);
    this.tamano.set(evento.pageSize);
    this.paginacion$.next();
  }

  protected verificar(): void {
    this.verificando.set(true);
    this.integridad.set(null);
    this.auditoriaService.verificarIntegridad().pipe(finalize(() => this.verificando.set(false))).subscribe({
      next: (resultado) => {
        this.integridad.set(resultado);
        this.paginacion$.next();
      },
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  protected exportar(): void {
    this.exportando.set(true);
    this.auditoriaService.exportar(this.filtros()).pipe(finalize(() => this.exportando.set(false))).subscribe({
      next: (archivo) => descargarArchivo(archivo, `auditoria-${new Date().toISOString().slice(0, 10)}.csv`),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  private filtros(): FiltrosAuditoria {
    return { ...this.formulario.getRawValue(), pagina: this.numeroPagina(), tamano: this.tamano() };
  }
}
