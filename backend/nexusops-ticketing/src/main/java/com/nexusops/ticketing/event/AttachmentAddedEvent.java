package com.nexusops.ticketing.event;

import com.nexusops.shared.event.DomainEvent;

public class AttachmentAddedEvent extends DomainEvent {

    private final String ticketId;
    private final String ticketNumber;
    private final String fileName;
    private final Long fileSize;
    private final String uploaderId;
    private final String tenantId;

    public AttachmentAddedEvent(String aggregateId, long aggregateVersion, String ticketId, String ticketNumber,
                                String fileName, Long fileSize, String uploaderId, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.ticketId = ticketId;
        this.ticketNumber = ticketNumber;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.uploaderId = uploaderId;
        this.tenantId = tenantId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public String getTenantId() {
        return tenantId;
    }
}