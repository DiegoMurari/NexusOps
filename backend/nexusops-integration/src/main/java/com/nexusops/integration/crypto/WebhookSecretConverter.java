package com.nexusops.integration.crypto;

import com.nexusops.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Criptografa o segredo do webhook em repouso (AES-256-GCM, o mesmo conversor do MFA). Todo valor gravado
 * recebe o prefixo {@code enc:v1:}; valor sem o prefixo é um segredo antigo, ainda em texto puro, e é lido
 * como está (passa a ser criptografado na próxima vez que o webhook for salvo).
 */
@Component
@Converter
public class WebhookSecretConverter implements AttributeConverter<String, String> {

    static final String PREFIX = "enc:v1:";

    private final EncryptedStringConverter delegate;

    public WebhookSecretConverter(EncryptedStringConverter delegate) {
        this.delegate = delegate;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return PREFIX + delegate.convertToDatabaseColumn(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!dbData.startsWith(PREFIX)) {
            return dbData;
        }
        return delegate.convertToEntityAttribute(dbData.substring(PREFIX.length()));
    }
}
