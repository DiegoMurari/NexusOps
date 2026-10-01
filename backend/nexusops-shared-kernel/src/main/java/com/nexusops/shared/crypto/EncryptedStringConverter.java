package com.nexusops.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA converter that encrypts a String column at rest with AES-256-GCM.
 * Applied explicitly per field via {@code @Convert(converter = EncryptedStringConverter.class)}
 * rather than autoApply, since most String columns must NOT be encrypted.
 *
 * Resolved as a Spring bean (Spring Boot wires Hibernate's bean container to
 * the application context), so the key comes from configuration instead of
 * a static/global lookup.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;

    public EncryptedStringConverter(
            @Value("${nexusops.crypto.secret-key:}") String secretKeyBase64,
            Environment environment) {
        byte[] keyBytes;
        if (secretKeyBase64 != null && !secretKeyBase64.isBlank()) {
            keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        } else if (environment.matchesProfiles("dev", "test")) {
            // Fixed, well-known dev/test key - never used outside those profiles.
            keyBytes = "nexusops-dev-only-encryption-key".getBytes(StandardCharsets.UTF_8);
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        } else {
            throw new IllegalStateException(
                "nexusops.crypto.secret-key (CRYPTO_SECRET_KEY) must be set outside the dev/test profiles; "
                    + "refusing to boot without an encryption key for data encrypted at rest.");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv).put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(dbData);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }
}
