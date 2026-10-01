import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ReportType =
  | 'CUSTOM'
  | 'TICKET_SUMMARY'
  | 'SLA_COMPLIANCE'
  | 'AGENT_PERFORMANCE'
  | 'CATEGORY_DISTRIBUTION'
  | 'TREND_ANALYSIS';

export type ExportFormat = 'PDF' | 'EXCEL' | 'CSV';

export interface ReportDto {
  id: string;
  name: string;
  description: string | null;
  ownerId: string;
  reportType: ReportType;
  publicReport: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateReportRequest {
  name: string;
  description?: string;
  reportType: ReportType;
  publicReport?: boolean;
}

export interface ReportResultDto {
  reportType: ReportType;
  from: string;
  to: string;
  generatedAt: string;
  summary: Record<string, number | string>;
  columns: string[];
  rows: Record<string, string | number>[];
}

export interface ScheduledReportDto {
  id: string;
  reportId: string;
  name: string;
  scheduleCron: string;
  timezone: string;
  format: ExportFormat;
  deliveryMethod: string;
  recipients: string[];
  active: boolean;
  lastRunAt: string | null;
  nextRunAt: string | null;
  lastRunStatus: string | null;
  createdAt: string;
}

export interface CreateScheduledReportRequest {
  reportId: string;
  name: string;
  scheduleCron: string;
  timezone?: string;
  format: ExportFormat;
  recipients: string[];
}

export const REPORT_TYPE_LABELS: Record<ReportType, string> = {
  CUSTOM: 'Personalizado',
  TICKET_SUMMARY: 'Resumo de tickets',
  SLA_COMPLIANCE: 'Conformidade de SLA',
  AGENT_PERFORMANCE: 'Desempenho por agente',
  CATEGORY_DISTRIBUTION: 'Distribuição por categoria',
  TREND_ANALYSIS: 'Análise de tendência',
};

@Injectable({ providedIn: 'root' })
export class ReportingService {
  private http = inject(HttpClient);
  private reportsBase = `${environment.apiUrl}/reports`;
  private scheduledBase = `${environment.apiUrl}/scheduled-reports`;

  listReports(): Observable<ReportDto[]> {
    return this.http.get<ReportDto[]>(this.reportsBase);
  }

  createReport(request: CreateReportRequest): Observable<ReportDto> {
    return this.http.post<ReportDto>(this.reportsBase, request);
  }

  deleteReport(id: string): Observable<void> {
    return this.http.delete<void>(`${this.reportsBase}/${id}`);
  }

  overview(days: number): Observable<ReportResultDto> {
    return this.http.get<ReportResultDto>(`${this.reportsBase}/overview`, { params: new HttpParams().set('days', days) });
  }

  runReport(id: string, days: number): Observable<ReportResultDto> {
    return this.http.get<ReportResultDto>(`${this.reportsBase}/${id}/run`, { params: new HttpParams().set('days', days) });
  }

  exportCsv(id: string, days: number): Observable<Blob> {
    return this.http.get(`${this.reportsBase}/${id}/export`, {
      params: new HttpParams().set('days', days),
      responseType: 'blob',
    });
  }

  listScheduled(): Observable<ScheduledReportDto[]> {
    return this.http.get<ScheduledReportDto[]>(this.scheduledBase);
  }

  createScheduled(request: CreateScheduledReportRequest): Observable<ScheduledReportDto> {
    return this.http.post<ScheduledReportDto>(this.scheduledBase, request);
  }

  setScheduledActive(id: string, active: boolean): Observable<ScheduledReportDto> {
    return this.http.patch<ScheduledReportDto>(`${this.scheduledBase}/${id}`, { active });
  }

  deleteScheduled(id: string): Observable<void> {
    return this.http.delete<void>(`${this.scheduledBase}/${id}`);
  }
}
