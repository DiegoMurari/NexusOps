import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AssetService, AssetType, LocationDto } from '../../../core/asset/asset.service';

@Component({
  selector: 'app-asset-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule],
  template: `
    <h1 class="page-title">Criar Asset</h1>

    <form class="card form" [formGroup]="form" (ngSubmit)="onSubmit()">
      <label class="field">
        <span class="field-label">Nome*</span>
        <input type="text" formControlName="name" placeholder="Ex: Dell Latitude 5420" />
        @if (form.controls['name'].touched && form.controls['name'].invalid) {
          <span class="field-error">Informe um nome (até 255 caracteres)</span>
        }
      </label>

      <label class="field">
        <span class="field-label">Descrição</span>
        <textarea formControlName="description" rows="3" placeholder="Detalhes adicionais sobre o asset…"></textarea>
      </label>

      <div class="field-row">
        <label class="field">
          <span class="field-label">Tipo*</span>
          <select formControlName="type">
            <option value="HARDWARE">Hardware</option>
            <option value="SOFTWARE">Software</option>
            <option value="CLOUD">Nuvem</option>
            <option value="VIRTUAL">Virtual</option>
            <option value="NETWORK">Rede</option>
            <option value="STORAGE">Armazenamento</option>
            <option value="PERIPHERAL">Periférico</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">Tag do asset</span>
          <input type="text" formControlName="assetTag" placeholder="Gerada automaticamente se vazia" />
        </label>
      </div>

      <div class="field-row">
        <label class="field">
          <span class="field-label">Fabricante</span>
          <input type="text" formControlName="manufacturer" placeholder="Ex: Dell, HP, Cisco…" />
        </label>

        <label class="field">
          <span class="field-label">Modelo</span>
          <input type="text" formControlName="model" />
        </label>
      </div>

      <div class="field-row">
        <label class="field">
          <span class="field-label">Nº de série</span>
          <input type="text" formControlName="serialNumber" />
        </label>

        <label class="field">
          <span class="field-label">Localização</span>
          <select formControlName="locationId">
            <option value="">— Nenhuma —</option>
            @for (loc of locations(); track loc.id) {
              <option [value]="loc.id">{{ loc.name }}</option>
            }
          </select>
        </label>
      </div>

      @if (error()) {
        <div class="form-error">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }

      <div class="form-actions">
        <button type="button" class="btn-secondary" (click)="cancel()">Cancelar</button>
        <button type="submit" class="btn-primary" [disabled]="form.invalid || submitting()">
          @if (submitting()) {
            <mat-icon class="spin">progress_activity</mat-icon>
          } @else {
            <mat-icon>check</mat-icon>
          }
          <span>Criar Asset</span>
        </button>
      </div>
    </form>
  `,
  styles: [`
    :host { display: block; }

    .page-title {
      margin: 0 0 20px;
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text);
    }

    .form {
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-width: 640px;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .field-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }

    .field-label {
      font-size: 12.5px;
      font-weight: 600;
      color: var(--text-muted);
    }

    input, textarea, select {
      font-family: var(--sans);
      font-size: 13px;
      color: var(--text);
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: var(--radius-s);
      padding: 9px 12px;
      outline: none;

      &:focus { border-color: var(--accent); }
      &::placeholder { color: var(--text-faint); }
    }

    textarea { resize: vertical; font-family: var(--sans); }

    .field-error {
      font-size: 11.5px;
      color: var(--critical);
    }

    .form-error {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      border-radius: var(--radius-s);
      background: var(--critical-soft);
      color: var(--critical);
      font-size: 12.5px;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
      margin-top: 4px;
    }

    .btn-primary, .btn-secondary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 38px;
      padding: 0 18px;
      border-radius: var(--radius-s);
      font-weight: 500;
      font-size: 13px;
      border: none;
      cursor: pointer;

      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }

    .btn-primary {
      background: var(--accent);
      color: #fff;

      &:hover:not(:disabled) { filter: brightness(1.08); }
      &:disabled { opacity: 0.5; cursor: default; }
    }

    .btn-secondary {
      background: var(--surface-2);
      color: var(--text);
      border: 1px solid var(--border);

      &:hover { background: var(--surface-3); }
    }

    .spin {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
  `]
})
export class AssetCreateComponent implements OnInit {
  form: FormGroup;

  submitting = signal(false);
  error = signal<string | null>(null);
  locations = signal<LocationDto[]>([]);

  constructor(
    private fb: FormBuilder,
    private assetService: AssetService,
    private router: Router
  ) {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      description: [''],
      type: ['HARDWARE' as AssetType, Validators.required],
      assetTag: [''],
      manufacturer: [''],
      model: [''],
      serialNumber: [''],
      locationId: [''],
    });
  }

  ngOnInit(): void {
    this.assetService.listLocations().subscribe({
      next: locations => this.locations.set(locations),
      error: () => {}
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.submitting.set(true);
    this.error.set(null);

    const value = this.form.getRawValue();
    this.assetService.create({
      name: value.name,
      description: value.description || undefined,
      type: value.type,
      assetTag: value.assetTag || undefined,
      manufacturer: value.manufacturer || undefined,
      model: value.model || undefined,
      serialNumber: value.serialNumber || undefined,
      locationId: value.locationId || undefined,
    }).subscribe({
      next: asset => {
        this.router.navigate(['/assets', asset.id]);
      },
      error: err => {
        this.submitting.set(false);
        this.error.set(err?.error?.detail ?? 'Não foi possível criar o asset. Tente novamente.');
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/assets']);
  }
}
