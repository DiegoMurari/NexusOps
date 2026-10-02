package com.nexusops.platform.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceTest {

    @Test
    void escapeLikeMakesWildcardsLiteral() {
        assertThat(AuditService.escapeLike("100%_ok")).isEqualTo("100\\%\\_ok");
    }

    @Test
    void escapeLikeEscapesTheEscapeCharacterFirst() {
        assertThat(AuditService.escapeLike("a\\b")).isEqualTo("a\\\\b");
    }
}
