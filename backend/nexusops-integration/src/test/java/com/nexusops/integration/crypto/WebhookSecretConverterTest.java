package com.nexusops.integration.crypto;

import com.nexusops.shared.crypto.EncryptedStringConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSecretConverterTest {

    private WebhookSecretConverter converter;

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        converter = new WebhookSecretConverter(new EncryptedStringConverter("", env));
    }

    @Test
    void storesTheSecretEncryptedWithAVersionPrefix_andReadsItBack() {
        String stored = converter.convertToDatabaseColumn("whsec_super-secret-value");

        assertThat(stored).startsWith("enc:v1:").doesNotContain("whsec_super-secret-value");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("whsec_super-secret-value");
    }

    @Test
    void sameSecretEncryptsDifferentlyEachTime() {
        assertThat(converter.convertToDatabaseColumn("abc")).isNotEqualTo(converter.convertToDatabaseColumn("abc"));
    }

    @Test
    void aLegacyPlaintextSecretIsStillReadable() {
        assertThat(converter.convertToEntityAttribute("old-plain-secret")).isEqualTo("old-plain-secret");
    }

    @Test
    void nullsPassThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void theLongestAllowedSecretFitsTheColumn() {
        assertThat(converter.convertToDatabaseColumn("x".repeat(255)).length()).isLessThanOrEqualTo(512);
    }
}
