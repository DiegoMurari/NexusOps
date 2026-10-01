# NexusOps API Design

REST API design with OpenAPI 3.1, DTO standards, error handling, and real-time communication.

## API Overview

| Aspect | Specification |
|--------|---------------|
| Base URL | `https://api.nexusops.com/api/v1` |
| Protocol | HTTPS only |
| Format | JSON (request/response) |
| Authentication | JWT Bearer (RS256) |
| Versioning | URL path (`/api/v1`) |
| Rate Limiting | 2000 req/min per IP (WAF) |
| Pagination | Cursor-based (default) + offset |
| Filtering | Query parameters |
| Sorting | `sort=field,direction` |

## Authentication

### JWT Bearer Token
```
Authorization: Bearer <access_token>
```

### Token Endpoints
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/auth/login` | POST | Username/password + optional MFA |
| `/auth/mfa/verify` | POST | Verify TOTP challenge |
| `/auth/refresh` | POST | Rotate refresh token |
| `/auth/logout` | POST | Single session |
| `/auth/logout-all` | POST | All sessions |

### Login Request
```json
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "agent@nexusops.com",
  "password": "securePassword123",
  "mfaCode": "123456",
  "rememberMe": true
}
```

### Login Response (Success)
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "user": {
    "id": "usr_abc123",
    "username": "agent@nexusops.com",
    "email": "agent@nexusops.com",
    "firstName": "John",
    "lastName": "Doe",
    "roles": ["ROLE_AGENT", "ROLE_TEAM_LEAD"],
    "permissions": ["TICKET:READ:TENANT", "TICKET:WRITE:OWN"],
    "mfaEnabled": true,
    "tenantId": "tenant_xyz789"
  }
}
```

### Login Response (MFA Required)
```json
HTTP 401 Unauthorized
{
  "type": "https://nexusops.com/errors/mfa_required",
  "title": "Unauthorized",
  "status": 401,
  "detail": "MFA verification required",
  "instance": "/api/v1/auth/login",
  "code": "MFA_REQUIRED",
  "mfaChallengeId": "challenge_abc123"
}
```

## Error Handling (RFC 9457)

All errors follow RFC 9457 Problem Details format.

### Standard Error Response
```json
{
  "type": "https://nexusops.com/errors/validation_error",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Request validation failed",
  "instance": "/api/v1/tickets",
  "code": "VALIDATION_ERROR",
  "timestamp": "2024-01-15T10:30:00Z",
  "extensions": {
    "title": "Title is required",
    "description": "Description must be at least 10 characters"
  }
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 422 | Request validation failed |
| `RESOURCE_NOT_FOUND` | 404 | Resource not found |
| `AUTHENTICATION_FAILED` | 401 | Invalid credentials |
| `MFA_REQUIRED` | 401 | MFA challenge required |
| `ACCESS_DENIED` | 403 | Insufficient permissions |
| `MALFORMED_REQUEST` | 400 | Request body malformed |
| `MISSING_PARAMETER` | 400 | Required parameter missing |
| `TYPE_MISMATCH` | 400 | Parameter type mismatch |
| `ENDPOINT_NOT_FOUND` | 404 | Endpoint not found |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
| `RATE_LIMITED` | 429 | Too many requests |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |

### Common HTTP Status Codes

| Status | Description |
|--------|-------------|
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict |
| 422 | Unprocessable Entity |
| 429 | Too Many Requests |
| 500 | Internal Server Error |
| 503 | Service Unavailable |

## Pagination

### Cursor-Based (Default)
```http
GET /api/v1/tickets?cursor=eyJpZCI6MTAwfQ==&size=20
```

```json
{
  "data": [...],
  "pagination": {
    "nextCursor": "eyJpZCI6MTIwfQ==",
    "previousCursor": "eyJpZCI6ODB9",
    "size": 20,
    "hasNext": true,
    "hasPrevious": true
  }
}
```

### Offset-Based (Optional)
```http
GET /api/v1/tickets?page=0&size=20
```

```json
{
  "data": [...],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1500,
    "totalPages": 75,
    "first": true,
    "last": false
  }
```

## Filtering & Sorting

### Filtering
```http
GET /api/v1/tickets?status=OPEN,IN_PROGRESS&priority=HIGH,CRITICAL&assigneeId=usr_123&createdAfter=2024-01-01
```

### Sorting
```http
GET /api/v1/tickets?sort=createdAt,desc&sort=priority,asc
```

### Search
```http
GET /api/v1/tickets?search=server+down&searchFields=title,description
```

## Standard Response Envelope

### Success Response
```json
{
  "data": { ... },
  "meta": {
    "requestId": "req_abc123",
    "timestamp": "2024-01-15T10:30:00Z"
  },
  "links": {
    "self": "/api/v1/tickets/123",
    "related": "/api/v1/tickets/123/comments"
  }
}
```

### List Response
```json
{
  "data": [...],
  "meta": {
    "requestId": "req_abc123",
    "timestamp": "2024-01-15T10:30:00Z",
    "pagination": { ... }
  },
  "links": {
    "first": "/api/v1/tickets?page=0&size=20",
    "next": "/api/v1/tickets?page=1&size=20",
    "last": "/api/v1/tickets?page=74&size=20"
  }
}
```

## DTO Standards

### Naming Conventions
- Request DTOs: `{Action}{Resource}Request` (e.g., `CreateTicketRequest`)
- Response DTOs: `{Resource}Dto` (e.g., `TicketDto`)
- List items: `{Resource}ListItemDto` (e.g., `TicketListItemDto`)

### Common Fields

#### Base Entity DTO
```typescript
interface BaseEntityDto {
  id: string;
  createdAt: string;        // ISO 8601
  updatedAt: string;        // ISO 8601
  createdBy: string;        // User ID
  updatedBy: string;        // User ID
  version: number;          // Optimistic locking
}
```

#### Ticket DTOs
```typescript
interface TicketListItemDto extends BaseEntityDto {
  ticketNumber: string;     // INC-2024-001
  title: string;
  status: TicketStatus;     // OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED
  priority: Priority;       // LOW, MEDIUM, HIGH, CRITICAL
  category: CategoryRefDto;
  assignee: UserRefDto | null;
  reporter: UserRefDto;
  sla: SlaStatusDto | null;
  createdAt: string;
  updatedAt: string;
}

interface TicketDto extends TicketListItemDto {
  description: string;      // HTML content
  comments: CommentDto[];
  attachments: AttachmentDto[];
  timeEntries: TimeEntryDto[];
  timeline: TimelineEventDto[];
  relatedTickets: RelatedTicketDto[];
  sla: SlaDetailDto | null;
  workflow: WorkflowStatusDto | null;
}

interface CreateTicketRequest {
  title: string;                    // Required, max 200
  description: string;              // Required, max 10000
  type: TicketType;                 // INCIDENT, PROBLEM, CHANGE
  priority: Priority;               // Required
  categoryCode: string;             // Required, pattern: ^[a-zA-Z0-9-_]+$
  assigneeId?: string;              // Optional
  relatedAssetIds?: string[];       // Optional
  relatedTicketIds?: string[];      // Optional
}

interface UpdateTicketRequest {
  title?: string;
  description?: string;
  priority?: Priority;
  categoryCode?: string;
  assigneeId?: string | null;
}
```

## Real-time Communication

### WebSocket (STOMP) - Primary

#### Connection
```
WebSocket: wss://api.nexusops.com/ws/notifications?token=<access_token>
```

#### Subscribe to User Notifications
```
SUBSCRIBE
destination: /user/queue/notifications
```

#### Subscribe to Ticket Updates
```
SUBSCRIBE
destination: /topic/tickets.INC-2024-001.updates
```

#### Notification Message Format
```json
{
  "id": "notif_abc123",
  "type": "TICKET_ASSIGNED",
  "title": "Ticket Assigned",
  "message": "You have been assigned to INC-2024-001",
  "priority": "HIGH",
  "actionUrl": "/tickets/INC-2024-001",
  "createdAt": "2024-01-15T10:30:00Z",
  "read": false
}
```

### Server-Sent Events (SSE) - Fallback

#### Connection
```
GET /api/v1/notifications/stream?token=<access_token>
Accept: text/event-stream
```

#### Event Stream
```
event: notification
data: {"id":"notif_abc123","type":"TICKET_ASSIGNED","title":"Ticket Assigned","message":"You have been assigned to INC-2024-001","priority":"HIGH","actionUrl":"/tickets/INC-2024-001","createdAt":"2024-01-15T10:30:00Z","read":false}

event: heartbeat
data: {}
```

### Auto-fallback Logic (Frontend)
```typescript
// Primary: WebSocket
// On error/close: fallback to SSE
// On SSE error: retry WebSocket after 30s
```

## Ticketing API Examples

### Create Ticket
```http
POST /api/v1/tickets
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Email server down",
  "description": "Users unable to send/receive emails since 09:00",
  "type": "INCIDENT",
  "priority": "HIGH",
  "categoryCode": "EMAIL"
}
```

**Response (201 Created)**
```json
{
  "data": {
    "id": "tkt_abc123",
    "ticketNumber": "INC-2024-001",
    "title": "Email server down",
    "description": "Users unable to send/receive emails since 09:00",
    "type": "INCIDENT",
    "status": "OPEN",
    "priority": "HIGH",
    "category": { "code": "EMAIL", "name": "Email Issues" },
    "assignee": null,
    "reporter": { "id": "usr_123", "name": "John Doe" },
    "sla": { "responseDue": "2024-01-15T13:00:00Z", "resolutionDue": "2024-01-15T17:00:00Z" },
    "createdAt": "2024-01-15T10:00:00Z",
    "updatedAt": "2024-01-15T10:00:00Z"
  },
  "meta": { "requestId": "req_xyz789" }
}
```

### Transition Ticket
```http
POST /api/v1/tickets/tkt_abc123/transition
Authorization: Bearer <token>
Content-Type: application/json

{
  "status": "IN_PROGRESS",
  "comment": "Investigating email server logs"
}
```

### Add Comment
```http
POST /api/v1/tickets/tkt_abc123/comments
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "Found issue in Postfix configuration",
  "internal": false,
  "mentions": ["usr_456"]
}
```

### Upload Attachment (Presigned URL)
```http
POST /api/v1/tickets/tkt_abc123/attachments
Authorization: Bearer <token>
Content-Type: application/json

{
  "fileName": "postfix-logs.txt",
  "contentType": "text/plain",
  "size": 102400
}
```

**Response**
```json
{
  "data": {
    "uploadUrl": "https://nexusops-attachments.s3.amazonaws.com/...?X-Amz-Signature=...",
    "uploadFields": { "key": "...", "policy": "...", "X-Amz-Credential": "..." },
    "attachmentId": "att_abc123"
  }
}
```

*Client uploads directly to S3, then calls:*
```http
POST /api/v1/tickets/tkt_abc123/attachments/att_abc123/complete
Authorization: Bearer <token>
```

### Search Tickets
```http
GET /api/v1/tickets?search=email+server&status=OPEN,IN_PROGRESS&sort=relevance,desc
```

**Response**
```json
{
  "data": [
    {
      "id": "tkt_abc123",
      "ticketNumber": "INC-2024-001",
      "title": "Email server down",
      "highlights": {
        "title": "Email server down",
        "description": "Users unable to send/receive <em>emails</em> since 09:00"
      },
      "status": "OPEN",
      "priority": "HIGH"
    }
  ],
  "meta": { "searchTimeMs": 45 }
}
```

## Asset Management API

### Create Asset
```http
POST /api/v1/assets
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Web Server 01",
  "type": "SERVER",
  "serialNumber": "SN123456",
  "assetTag": "AST-001",
  "specifications": {
    "cpu": "Intel Xeon E5-2680",
    "ram": "64GB",
    "storage": "2TB SSD"
  },
  "locationId": "loc_dc1_rack01"
}
```

### CI Relationship Graph
```http
GET /api/v1/assets/ast_abc123/relationships?depth=2
```

**Response**
```json
{
  "data": {
    "nodes": [
      { "id": "ast_abc123", "label": "Web Server 01", "type": "SERVER", "group": "host" },
      { "id": "ast_def456", "label": "Database 01", "type": "DATABASE", "group": "depends" }
    ],
    "edges": [
      { "from": "ast_abc123", "to": "ast_def456", "label": "DEPENDS_ON", "arrows": "to" }
    ]
  }
}
```

## Knowledge Base API

### Create Article (Draft)
```http
POST /api/v1/articles
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "How to reset email password",
  "content": "<h1>Password Reset</h1><p>Follow these steps...</p>",
  "categoryId": "cat_email",
  "tags": ["email", "password", "self-service"],
  "seo": {
    "metaTitle": "Email Password Reset Guide",
    "metaDescription": "Step-by-step guide to reset your email password",
    "slug": "email-password-reset"
  }
}
```

### Publish Article
```http
POST /api/v1/articles/art_abc123/publish
Authorization: Bearer <token>
Content-Type: application/json

{
  "scheduledAt": "2024-01-20T09:00:00Z"
}
```

### Public Article Access
```http
GET /api/v1/public/articles/email-password-reset
```

## SLA API

### Create SLA Definition
```http
POST /api/v1/sla/definitions
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Critical Incident SLA",
  "description": "For critical incidents affecting all users",
  "appliesTo": {
    "types": ["INCIDENT"],
    "priorities": ["CRITICAL"],
    "categories": [],
    "customerTiers": ["PREMIUM", "ENTERPRISE"]
  },
  "responseTimeMinutes": 15,
  "resolutionTimeMinutes": 240,
  "calendarId": "cal_business_hours"
}
```

## Reporting API

### Execute Report
```http
POST /api/v1/reports/rpt_abc123/execute
Authorization: Bearer <token>
Content-Type: application/json

{
  "parameters": {
    "startDate": "2024-01-01",
    "endDate": "2024-01-31",
    "groupBy": "category"
  }
}
```

### Export Report
```http
POST /api/v1/reports/rpt_abc123/export
Authorization: Bearer <token>
Content-Type: application/json

{
  "format": "EXCEL",
  "parameters": { "startDate": "2024-01-01", "endDate": "2024-01-31" }
}
```

## Webhook API

### Create Webhook
```http
POST /api/v1/webhooks
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Ticket Created Webhook",
  "targetUrl": "https://external-system.com/webhooks/ticket-created",
  "secret": "hmac-secret-key",
  "events": ["TICKET_CREATED", "TICKET_UPDATED", "TICKET_CLOSED"],
  "retryPolicy": {
    "maxAttempts": 5,
    "initialIntervalSeconds": 60,
    "multiplier": 2,
    "maxIntervalSeconds": 3600
  }
}
```

### Webhook Payload
```json
{
  "id": "evt_abc123",
  "eventType": "TICKET_CREATED",
  "timestamp": "2024-01-15T10:00:00Z",
  "payload": {
    "ticket": { ... }
  },
  "signature": "sha256=..."
}
```

## Rate Limiting

### Headers
```http
X-RateLimit-Limit: 2000
X-RateLimit-Remaining: 1995
X-RateLimit-Reset: 1705315200
Retry-After: 60
```

### Exceeded Response (429)
```json
{
  "type": "https://nexusops.com/errors/rate_limited",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded. Try again in 60 seconds.",
  "code": "RATE_LIMITED",
  "extensions": {
    "retryAfter": 60
  }
}
```

## OpenAPI Documentation

### Access
- Swagger UI: `https://api.nexusops.com/swagger-ui.html`
- OpenAPI Spec: `https://api.nexusops.com/v3/api-docs`
- Redoc: `https://api.nexusops.com/redoc.html`

### Tags (Grouping)
| Tag | Description |
|-----|-------------|
| Authentication | Login, MFA, tokens |
| Users | User management |
| Roles & Permissions | RBAC/ABAC |
| Tenants | Multi-tenancy |
| Feature Flags | Feature toggles |
| Tickets | Core ticketing |
| Comments | Ticket comments |
| Attachments | File uploads |
| Time Tracking | Time entries |
| Assets | CMDB |
| CI Relationships | Dependency graph |
| Licenses | Software compliance |
| Knowledge Base | Articles, categories |
| Public KB | Public portal |
| SLA | Definitions, compliance |
| Notifications | Real-time, email |
| Reports | Analytics, exports |
| Dashboards | Visualizations |
| Webhooks | Outbound events |
| Integrations | Jira, Slack, Teams |
| Administration | System settings |

## SDKs (Future)

| Language | Package | Status |
|----------|---------|--------|
| TypeScript | `@nexusops/api-client` | Planned |
| Java | `com.nexusops:api-client` | Planned |
| Python | `nexusops-api` | Planned |
| Go | `github.com/nexusops/go-client` | Planned |

## Versioning & Deprecation

### Version Header
```
Accept: application/vnd.nexusops.v1+json
```

### Deprecation Headers
```http
Deprecation: true
Sunset: Sat, 01 Jan 2025 00:00:00 GMT
Link: <https://api.nexusops.com/api/v2/tickets>; rel="successor-version"
```

### Changelog
Available at: `https://api.nexusops.com/changelog`