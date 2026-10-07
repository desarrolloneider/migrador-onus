import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { filter, finalize, map, switchMap } from 'rxjs';
import { mensajeDeError } from '../../core/api/errores';
import { ConfirmarDialogoComponent, DatosConfirmacion } from '../../shared/dialogos/confirmar-dialogo.component';
import { EstadoChipComponent } from '../../shared/estado-chip/estado-chip.component';
import { GRUPOS_VISTA_PREVIA } from '../../shared/estados';
import { HoraColombiaPipe } from '../../shared/formato/formato.pipes';
import { LotesService } from '../lotes/data/lotes.service';
import { VistaPrevia } from '../lotes/models/lote.model';

/** Probar una sola ONU escogida por su SN, con las mismas revisiones de un lote (ESP-10, ESP-11). */
@Component({
  selector: 'app-prueba-individual',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    MatTooltipModule, EstadoChipComponent, HoraColombiaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <header class="encabezado">
        <div>
          <h1>Prueba individual</h1>
          <p class="subtitulo">Ejecuta el procedimiento sobre una sola ONU, por ejemplo la de laboratorio, sin armar un lote. Pasa por las mismas revisiones; queda registrada como lote de modo "individual".</p>
        </div>
      </header>

      <form class="tarjeta busqueda" (ngSubmit)="buscar()">
        <mat-form-field class="campo-sn">
          <mat-label>SN de la ONU</mat-label>
          <mat-icon matPrefix>qr_code</mat-icon>
          <input matInput [formControl]="sn" placeholder="HWTC1A2B3C4D" autocomplete="off" />
          <mat-hint>En texto (HWTC…) o en hexadecimal (48575443…)</mat-hint>
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="buscando()"><mat-icon>search</mat-icon> Buscar y revisar</button>
      </form>

      @if (buscando()) { <mat-progress-bar mode="indeterminate" /> }
      @if (error()) { <p class="aviso aviso-error" role="alert"><mat-icon>error</mat-icon> {{ error() }}</p> }

      @if (item(); as i) {
        @let v = vista()!;
        <section class="tarjeta resultado">
          <header class="cabecera">
            <div>
              <h2 class="mono">{{ i.sn }}</h2>
              <span class="secundario">Vista previa <span class="mono">{{ v.lote.numero }}</span> · vence {{ v.lote.venceEn | horaColombia: 'hora' }}</span>
            </div>
            <app-estado-chip [presentacion]="grupos[i.grupo]" [texto]="i.grupo === 'ENTRA' ? 'Entra' : i.grupo === 'EXCLUIDA' ? 'Queda fuera' : 'Ya tiene el perfil'" />
          </header>

          <dl class="rejilla-datos">
            <div><dt>IP (SmartOLT)</dt><dd class="mono">{{ i.ip ?? '—' }}</dd></div>
            <div><dt>Cliente</dt><dd>{{ i.cliente ?? '—' }}</dd></div>
            <div><dt>Contrato</dt><dd>{{ i.contrato ?? '—' }}</dd></div>
            <div><dt>Modelo</dt><dd>{{ i.modelo ?? '—' }}</dd></div>
            <div><dt>OLT</dt><dd>{{ i.olt }}</dd></div>
            <div><dt>Tarjeta · PON · ID</dt><dd>{{ i.tarjeta }} · {{ i.pon }} · {{ i.onuId }}</dd></div>
            <div><dt>Perfil actual</dt><dd class="mono">{{ i.perfilActual ?? '—' }}</dd></div>
          </dl>

          @switch (i.grupo) {
            @case ('EXCLUIDA') {
              <div class="aviso aviso-error"><mat-icon>block</mat-icon>
                <div><span class="codigo-motivo">{{ i.codigoMotivo }}</span><p>{{ i.mensaje }}</p><p>No se puede ejecutar hasta que se resuelva.</p></div></div>
            }
            @case ('YA_TIENE_PERFIL') {
              <p class="aviso"><mat-icon>task_alt</mat-icon> <span>Ya tiene GUAJIRANET1: no necesita el procedimiento.</span></p>
            }
            @default {
              @if (v.soloSimulacion) {
                <p class="aviso aviso-simulado"><mat-icon>science</mat-icon> <span>{{ v.motivoSoloSimulacion }}</span></p>
              }
              <div class="acciones">
                <button mat-stroked-button (click)="ejecutar(true)" [disabled]="ejecutando()"><mat-icon>science</mat-icon> Simular</button>
                <button mat-flat-button (click)="ejecutar(false)" [disabled]="ejecutando() || v.soloSimulacion"
                        [matTooltip]="v.soloSimulacion ? 'Falta decidir PC-36: solo se puede simular' : ''"><mat-icon>play_arrow</mat-icon> Ejecutar</button>
              </div>
            }
          }
        </section>
      }
    </div>
  `,
  styles: `
    .busqueda { display: flex; gap: 12px; align-items: flex-start; flex-wrap: wrap; }
    .campo-sn { flex: 1; min-width: 260px; }
    .busqueda button { height: 48px; }
    .resultado { margin-top: 16px; }
    .cabecera { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 16px; }
    .cabecera h2 { margin: 0 0 4px; }
    .secundario { color: var(--mat-sys-on-surface-variant); font-size: 13px; }
    .rejilla-datos { margin-bottom: 16px; }
    .acciones { display: flex; gap: 8px; justify-content: flex-end; }
  `,
})
export class PruebaIndividualComponent {
  private readonly lotesService = inject(LotesService);
  private readonly dialogo = inject(MatDialog);
  private readonly router = inject(Router);
  protected readonly grupos = GRUPOS_VISTA_PREVIA;

  protected readonly sn = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly vista = signal<VistaPrevia | null>(null);
  protected readonly item = computed(() => this.vista()?.items[0] ?? null);
  protected readonly buscando = signal(false);
  protected readonly ejecutando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected buscar(): void {
    const sn = this.sn.value.trim();
    if (!sn) {
      this.error.set('Escribe el SN de la ONU.');
      return;
    }
    this.buscando.set(true);
    this.error.set(null);
    this.vista.set(null);
    this.lotesService.armarPruebaIndividual({ sn }).pipe(finalize(() => this.buscando.set(false))).subscribe({
      next: (vista) => this.vista.set(vista),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }

  protected ejecutar(simulacion: boolean): void {
    const v = this.vista();
    const i = this.item();
    if (!v || !i) return;
    const datos: DatosConfirmacion = simulacion
      ? { titulo: 'Simular esta ONU', icono: 'science', textoConfirmar: 'Simular',
          mensaje: `Vas a simular el cambio del perfil de ${i.sn} en ${i.olt}.`, detalle: 'Lee de verdad en la OLT y no manda ningún comando que cambie algo.' }
      : { titulo: 'Ejecutar en esta ONU', icono: 'play_arrow', textoConfirmar: 'Sí, ejecutar',
          mensaje: `Vas a cambiar el perfil TR-069 de ${i.sn} a GUAJIRANET1 en ${i.olt}, tarjeta ${i.tarjeta}, PON ${i.pon}, ID ${i.onuId}. ¿Confirmas?` };
    this.dialogo.open(ConfirmarDialogoComponent, { width: '500px', data: datos }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => {
        this.ejecutando.set(true);
        return this.lotesService.confirmar(v.lote.id, { simulacion });
      }),
      switchMap((lote) => this.lotesService.listarEjecuciones(lote.id)),
      map((ejecuciones) => ejecuciones[0]),
      finalize(() => this.ejecutando.set(false)),
    ).subscribe({
      // La pantalla abre directamente el detalle de la ONU (ESP-10).
      next: (ejecucion) => void this.router.navigate(['/ejecuciones', ejecucion.id]),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }
}
