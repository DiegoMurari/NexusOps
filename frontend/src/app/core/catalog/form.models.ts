/** Contrato do formulário dinâmico do tópico (espelha FormDefinitionDto do backend). */

export type FormFieldType = 'TEXT' | 'TEXTAREA' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'SELECT' | 'MULTISELECT' | 'LOCATION';
export type FormPrefill = 'USER_LOCATION' | 'USER_PHONE' | 'USER_DEPARTMENT' | 'USER_JOB_TITLE';
export type EvidenceMode = 'NONE' | 'OPTIONAL' | 'REQUIRED';

export interface FormOption {
  value: string;
  label: string;
}

export interface FormVisibleWhen {
  field: string;
  equals: string;
}

export interface FormField {
  key: string;
  label: string;
  type: FormFieldType;
  required: boolean;
  helpText?: string | null;
  placeholder?: string | null;
  options?: FormOption[];
  minLength?: number | null;
  maxLength?: number | null;
  min?: number | null;
  max?: number | null;
  pattern?: string | null;
  defaultValue?: unknown;
  visibleWhen?: FormVisibleWhen | null;
  prefill?: FormPrefill | null;
}

export interface FormEvidence {
  mode: EvidenceMode;
  hint?: string | null;
}

export interface FormDefinition {
  fields: FormField[];
  evidence: FormEvidence;
}

export type FormVersionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface TopicFormVersion {
  id: string;
  topicId: string;
  version: number;
  status: FormVersionStatus;
  definition: FormDefinition;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  publishedAt: string | null;
  publishedBy: string | null;
}

export interface TicketForm {
  versionId: string;
  version: number;
  definition: FormDefinition;
  answers: Record<string, unknown>;
}

export const FIELD_TYPE_LABELS: Record<FormFieldType, string> = {
  TEXT: 'Texto curto',
  TEXTAREA: 'Texto longo',
  NUMBER: 'Número',
  DATE: 'Data',
  BOOLEAN: 'Sim / não',
  SELECT: 'Escolha única',
  MULTISELECT: 'Escolha múltipla',
  LOCATION: 'Localidade',
};

export function emptyDefinition(): FormDefinition {
  return { fields: [], evidence: { mode: 'NONE', hint: null } };
}

/** Chave estável a partir do rótulo: "Qual o equipamento?" -> "qualOEquipamento". */
export function keyFromLabel(label: string): string {
  const words = label.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-zA-Z0-9]+/g, ' ').trim().split(' ').filter(Boolean);
  if (words.length === 0) return '';
  const camel = words.map((w, i) => i === 0 ? w.toLowerCase() : w[0].toUpperCase() + w.slice(1).toLowerCase()).join('');
  const key = /^[a-z]/.test(camel) ? camel : 'campo' + camel[0].toUpperCase() + camel.slice(1);
  return key.slice(0, 40);
}
