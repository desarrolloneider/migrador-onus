import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';
import { mensajeDeError } from '../../../../core/api/errores';
import { AuthService } from '../../../../core/auth/auth.service';
import { TiempoRealService } from '../../../../core/realtime/tiempo-real.service';
import { ConfiguracionService } from '../../../configuracion/data/configuracion.service';
import { Olt } from '../../../configuracion/models/configuracion.model';
import { LotesService } from '../../data/lotes.service';

/** Armar un lote: marca y, si se quiere, OLT, tarjeta y puerto PON (ESP-03). */
@Component({
  selector: 'app-nuevo-lote',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <a class="volver" routerLink="/inicio"><mat-icon>arrow_back</mat-icon> Inicio</a>
      <header class="encabezado">
        <div>
          <h1>Nuevo lote</h1>
          <p class="subtitulo">La herramienta busca en SmartOLT las ONUs de la marca y los filtros, las cruza con Wispro por IP y las separa en tres grupos. Nada cambia en ningún sistema hasta que confirmes la vista previa.</p>
        </div>
      </header>

      <div class="disposicion">
        <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="armar()">
          <h2><mat-icon>tune</mat-icon> Qué ONUs</h2>
          <mat-form-field class="ancho-completo">
            <mat-label>Marca</mat-label>
            <mat-select formControlName="marca">
              <mat-option value="HUAWEI">Huawei</mat-option>
              <mat-option value="OTRAS" disabled>Otras marcas (todavía sin procedimiento)</mat-option>
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
            <li><strong>SmartOLT:</strong> SN, posición, estado, IP y perfil de cada ONU. Si la descarga queda incompleta, no se arma nada.</li>
            <li><strong>Wispro, por IP:</strong> la IP de SmartOLT tiene que estar en un solo contrato.</li>
            <li><strong>Tres grupos:</strong> entran, quedan fuera (cada una con su motivo) y ya tienen el perfil.</li>
            <li><strong>Máximo {{ maximo }} ONUs por lote</strong> al empezar. Si hay más, usa los filtros.</li>
            <li><strong>La vista previa vence a los 30 minutos</strong> sin confirmar y queda descartada, sin borrar nada.</li>
          </ol>
        </aside>
      </div>
    </div>
  `,
  styles: `
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

  protected readonly formulario = inject(FormBuilder).group({
    marca: ['HUAWEI', Validators.required],
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
  protected readonly armando = signal(false);
  protected readonly avance = signal<string[]>([]);
  protected readonly error = signal<string | null>(null);

  constructor() {
    const { olt, tarjeta, pon } = this.formulario.controls;
    olt.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      tarjeta.reset(null);
      valor ? tarjeta.enable() : tarjeta.disable();
    });
    tarjeta.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      pon.reset(null);
      valor ? pon.enable() : pon.disable();
    });
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
      marca: valores.marca ?? 'HUAWEI',
      olt: valores.olt ?? null,
      tarjeta: valores.tarjeta ?? null,
      pon: valores.pon === null || valores.pon === undefined || String(valores.pon) === '' ? null : Number(valores.pon),
    }).pipe(finalize(() => {
      this.armando.set(false);
      avance.unsubscribe();
    })).subscribe({
      next: (vista) => void this.router.navigate(['/lotes', vista.lote.id, 'vista-previa']),
      error: (error: unknown) => this.error.set(mensajeDeError(error)),
    });
  }
}
