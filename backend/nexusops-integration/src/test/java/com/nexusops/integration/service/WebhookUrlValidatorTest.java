package com.nexusops.integration.service;

import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    @Test
    void acceptsPublicHttpsUrl() {
        assertThat(WebhookUrlValidator.validate(" https://hooks.example.com/path?a=1 "))
            .isEqualTo("https://hooks.example.com/path?a=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://hooks.example.com/x",
        "ftp://hooks.example.com/x",
        "https://user:pass@hooks.example.com/x",
        "https://localhost/x",
        "https://intranet/x",
        "https://db.internal/x",
        "https://printer.local/x",
        "https://127.0.0.1/x",
        "https://10.1.2.3/x",
        "https://192.168.0.5/x",
        "https://172.16.0.1/x",
        "https://169.254.169.254/latest/meta-data",
        "https://0.0.0.0/x",
        "https://[::1]/x",
        "https://[fd00::1]/x",
        "https://[::ffff:127.0.0.1]/x",
        "https://[::ffff:192.168.1.1]/x",
        "https://localhost./x",
        "https://db.internal./x",
        "https://2130706433/x",
        "https://0x7f000001/x",
        "not a url",
        "   "
    })
    void rejectsUnsafeTargets(String url) {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(url)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(null)).isInstanceOf(ValidationException.class);
    }
}
