package com.nexusops.iam.service;

import com.nexusops.iam.domain.MfaSecret;
import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.MfaSetupResponse;
import com.nexusops.iam.infrastructure.repository.MfaSecretRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.shared.crypto.Base32;
import com.nexusops.shared.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regressões do MFA: códigos de recuperação gravados como array JSON válido (coluna jsonb) e ativação por TOTP. */
@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    private static final String EMAIL = "ana@nexusops.com";

    @Mock UserRepository users;
    @Mock MfaSecretRepository secrets;

    private MfaService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new MfaService(users, secrets);
        user = User.builder().email(EMAIL).build();
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    /** TOTP (RFC 6238, SHA-1, 6 dígitos, 30 s) do instante atual, calculado de forma independente do serviço. */
    private static String totp(String base32Secret) throws Exception {
        byte[] key = Base32.decode(base32Secret);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 1000 / 30).array());
        int o = hash[hash.length - 1] & 0xF;
        int bin = ((hash[o] & 0x7F) << 24) | ((hash[o + 1] & 0xFF) << 16) | ((hash[o + 2] & 0xFF) << 8) | (hash[o + 3] & 0xFF);
        return String.format("%06d", bin % 1_000_000);
    }

    @Test
    void setupStoresTheSameRecoveryCodesItShowsAsAValidJsonArrayOfHashes() throws Exception {
        when(secrets.findByUser(user)).thenReturn(Optional.empty());

        MfaSetupResponse response = service.setupMfa(EMAIL);

        ArgumentCaptor<MfaSecret> saved = ArgumentCaptor.forClass(MfaSecret.class);
        verify(secrets).save(saved.capture());
        JsonNode json = new ObjectMapper().readTree(saved.getValue().getBackupCodes());
        assertThat(json.isArray()).isTrue();
        assertThat(json).hasSize(10);
        assertThat(response.getRecoveryCodes()).hasSize(10);
        assertThat(saved.getValue().getEnabled()).isFalse();
        assertThat(saved.getValue().getSecret()).isEqualTo(response.getSecret());
    }

    @Test
    void enableAcceptsACurrentTotpAndTurnsMfaOn() throws Exception {
        MfaSecret secret = MfaSecret.builder().user(user).secret(Base32.encode(new byte[20])).enabled(false).build();
        when(secrets.findByUser(user)).thenReturn(Optional.of(secret));

        service.enableMfa(EMAIL, totp(secret.getSecret()));

        assertThat(secret.getEnabled()).isTrue();
        assertThat(user.getMfaEnabled()).isTrue();
    }

    @Test
    void enableRejectsAWrongCodeAndLeavesMfaOff() {
        MfaSecret secret = MfaSecret.builder().user(user).secret(Base32.encode(new byte[20])).enabled(false).build();
        when(secrets.findByUser(user)).thenReturn(Optional.of(secret));

        assertThatThrownBy(() -> service.enableMfa(EMAIL, "000000")).isInstanceOf(ValidationException.class);

        assertThat(secret.getEnabled()).isFalse();
    }

    @Test
    void setupIsRefusedWhenMfaIsAlreadyOn() {
        MfaSecret on = MfaSecret.builder().user(user).secret("X").enabled(true).build();
        when(secrets.findByUser(user)).thenReturn(Optional.of(on));

        assertThatThrownBy(() -> service.setupMfa(EMAIL)).isInstanceOf(ValidationException.class);
        verify(secrets, org.mockito.Mockito.never()).save(any());
    }
}
