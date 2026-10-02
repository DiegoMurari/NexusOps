import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type EvidenceSubject = 'TICKET' | 'COMMENT' | 'EVENT';

export interface EvidenceDto {
  id: string;
  /** Nulo enquanto a evidência está só preparada (enviada no formulário de abertura). */
  ticketId: string | null;
  fileName: string;
  fileSize: number;
  mimeType: string;
  subjectType: EvidenceSubject;
  subjectId: string | null;
  /** Interna: só a equipe vê. */
  internal: boolean;
  uploaderId: string;
  scanStatus: string | null;
  createdAt: string;
}

/** Extensões aceitas pelo servidor; o servidor confere de novo o conteúdo. */
export const EVIDENCE_ACCEPT = '.png,.jpg,.jpeg,.gif,.webp,.pdf,.txt,.log,.csv';
export const EVIDENCE_MAX_BYTES = 10 * 1024 * 1024;

@Injectable({ providedIn: 'root' })
export class EvidenceService {
  private http = inject(HttpClient);
  private base = environment.apiUrl;

  list(ticketId: string): Observable<EvidenceDto[]> {
    return this.http.get<EvidenceDto[]>(`${this.base}/tickets/${ticketId}/evidence`);
  }

  /** Anexa ao chamado inteiro, a um comentário ou a um evento (etapa) da timeline. */
  attach(
    ticketId: string,
    file: File,
    link: { subjectType?: EvidenceSubject; subjectId?: string; internal?: boolean } = {},
  ): Observable<EvidenceDto> {
    const body = new FormData();
    body.append('file', file, file.name);
    if (link.subjectType) body.append('subjectType', link.subjectType);
    if (link.subjectId) body.append('subjectId', link.subjectId);
    if (link.internal) body.append('internal', 'true');
    return this.http.post<EvidenceDto>(`${this.base}/tickets/${ticketId}/evidence`, body);
  }

  /** Envia para o formulário de abertura; só vale quando o chamado é criado com o ID devolvido. */
  stage(file: File): Observable<EvidenceDto> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<EvidenceDto>(`${this.base}/evidence/staged`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/evidence/${id}`);
  }

  content(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/evidence/${id}/content`, { responseType: 'blob' });
  }

  /** Baixa pelo navegador usando o token do usuário (um link direto não levaria o cabeçalho). */
  download(item: EvidenceDto): void {
    this.content(item.id).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = item.fileName;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  /** Mensagem de recusa do cliente antes de enviar; nulo se o arquivo é aceitável. */
  clientError(file: File): string | null {
    if (file.size === 0) return 'O arquivo está vazio.';
    if (file.size > EVIDENCE_MAX_BYTES) return 'O arquivo passa de 10 MB.';
    const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
    return EVIDENCE_ACCEPT.split(',').includes('.' + ext) ? null : 'Tipo de arquivo não aceito. Use imagem, PDF ou texto.';
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}
