import { Injectable, signal, computed, inject, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';

export interface Notification {
  id: string;
  title: string;
  message: string;
  type: 'info' | 'warning' | 'error' | 'success';
  read: boolean;
  createdAt: string;
  actionUrl?: string;
}

interface NotificationDto {
  id: string;
  templateKey: string | null;
  subject: string | null;
  content: string | null;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  status: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

interface PageResponse<T> {
  content: T[];
}

function toNotification(dto: NotificationDto): Notification {
  return {
    id: dto.id,
    title: dto.subject ?? dto.templateKey ?? 'Notificação',
    message: dto.content ?? '',
    type: dto.priority === 'URGENT' || dto.priority === 'HIGH' ? 'warning' : 'info',
    read: dto.read,
    createdAt: dto.createdAt
  };
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private authService = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  private http = inject(HttpClient);

  private _notifications = signal<Notification[]>([]);
  private _unreadCount = signal(0);
  private ws: WebSocket | null = null;
  private sse: EventSource | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  notifications = computed(() => this._notifications());
  unreadCount = computed(() => this._unreadCount());

  connect(): void {
    if (this.ws || this.sse) {
      return;
    }

    this.tryWebSocket();
  }

  private tryWebSocket(): void {
    const token = this.authService.getAccessToken();
    if (!token) {
      this.fallbackToSSE();
      return;
    }

    const wsUrl = `${environment.wsUrl}/notifications?token=${token}`;
    this.ws = new WebSocket(wsUrl);

    this.ws.onopen = () => {
      this.reconnectAttempts = 0;
    };

    this.ws.onmessage = (event) => {
      try {
        const notification = JSON.parse(event.data) as Notification;
        this.addNotification(notification);
      } catch {
      }
    };

    this.ws.onerror = () => {
    };

    this.ws.onclose = () => {
      this.ws = null;
      this.scheduleReconnect();
    };
  }

  private fallbackToSSE(): void {
    const token = this.authService.getAccessToken();
    if (!token) return;

    const sseUrl = `${environment.apiUrl}/notifications/stream?token=${token}`;
    this.sse = new EventSource(sseUrl);

    this.sse.onmessage = (event) => {
      try {
        const notification = JSON.parse(event.data) as Notification;
        this.addNotification(notification);
      } catch {
      }
    };

    this.sse.onerror = () => {
      this.sse?.close();
      this.sse = null;
    };
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.fallbackToSSE();
      return;
    }

    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);

    setTimeout(() => {
      if (this.authService.isAuthenticated()) {
        this.tryWebSocket();
      }
    }, delay);
  }

  disconnect(): void {
    this.ws?.close();
    this.ws = null;
    this.sse?.close();
    this.sse = null;
  }

  private addNotification(notification: Notification): void {
    this._notifications.update(list => [notification, ...list.slice(0, 99)]);
    if (!notification.read) {
      this._unreadCount.update(c => c + 1);
    }
  }

  markAsRead(id: string): void {
    const wasUnread = this._notifications().find(n => n.id === id && !n.read) !== undefined;
    this._notifications.update(list =>
      list.map(n => n.id === id ? { ...n, read: true } : n)
    );
    if (wasUnread) {
      this._unreadCount.update(c => Math.max(0, c - 1));
    }
    this.http.post(`${environment.apiUrl}/notifications/${id}/read`, {}).subscribe({
      error: () => {}
    });
  }

  markAllAsRead(): void {
    this._notifications.update(list =>
      list.map(n => ({ ...n, read: true }))
    );
    this._unreadCount.set(0);
    this.http.post(`${environment.apiUrl}/notifications/read-all`, {}).subscribe({
      error: () => {}
    });
  }

  removeNotification(id: string): void {
    this._notifications.update(list => {
      const notification = list.find(n => n.id === id);
      if (notification && !notification.read) {
        this._unreadCount.update(c => Math.max(0, c - 1));
      }
      return list.filter(n => n.id !== id);
    });
  }

  loadNotifications(): void {
    this.http.get<PageResponse<NotificationDto>>(`${environment.apiUrl}/notifications`, { params: { size: '20' } }).subscribe({
      next: page => {
        const notifications = page.content.map(toNotification);
        this._notifications.set(notifications);
        this._unreadCount.set(notifications.filter(n => !n.read).length);
      },
      error: () => {}
    });
  }
}