package com.nexusops.ticketing.dto;

public record QueueMemberDto(
    String userId,
    String name,
    String email,
    boolean userActive,
    String role
) {
}
