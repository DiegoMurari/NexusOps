package com.nexusops.iam.service;

import com.nexusops.iam.domain.MfaSecret;
import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.MfaSetupResponse;
import com.nexusops.iam.infrastructure.repository.MfaSecretRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.shared.crypto.Base32;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MfaService {

    private final UserRepository userRepository;
    private final MfaSecretRepository mfaSecretRepository;

    public MfaSetupResponse setupMfa(String username) {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username));

        Optional<MfaSecret> existing = mfaSecretRepository.findByUser(user);
        if (existing.isPresent() && existing.get().getEnabled()) {
            throw new ValidationException("MFA is already enabled");
        }

        // Generate TOTP secret (base32, per RFC 6238 / otpauth convention)
        byte[] secretBytes = new byte[20];
        new java.security.SecureRandom().nextBytes(secretBytes);
        String secret = Base32.encode(secretBytes);

        String issuer = "NexusOps";
        String account = user.getEmail();
        String qrCodeUrl = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=6&period=30",
            issuer, account, secret, issuer);

        MfaSecret mfaSecret = existing.orElseGet(() -> MfaSecret.builder()
            .user(user)
            .build());
        
        mfaSecret.setSecret(secret);
        mfaSecret.setEnabled(false);
        mfaSecret.setBackupCodes(null);
        mfaSecretRepository.save(mfaSecret);

        // Generate recovery codes
        List<String> recoveryCodes = generateRecoveryCodes(10);

        return MfaSetupResponse.builder()
            .secret(secret)
            .qrCodeUrl(qrCodeUrl)
            .recoveryCodes(recoveryCodes)
            .build();
    }

    public void enableMfa(String username, String code) {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username));

        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
            .orElseThrow(() -> new ResourceNotFoundException("MFA Secret", username));

        if (mfaSecret.getEnabled()) {
            throw new ValidationException("MFA is already enabled");
        }

        if (!verifyTotp(mfaSecret, code)) {
            throw new ValidationException("Invalid MFA code");
        }

        // Generate and store backup codes
        List<String> recoveryCodes = generateRecoveryCodes(10);
        String hashedCodes = hashRecoveryCodes(recoveryCodes);

        mfaSecret.setEnabled(true);
        mfaSecret.setBackupCodes(hashedCodes);
        mfaSecretRepository.save(mfaSecret);

        user.setMfaEnabled(true);
        userRepository.save(user);
    }

    public void disableMfa(String username, String password) {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username));

        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
            .orElseThrow(() -> new ResourceNotFoundException("MFA Secret", username));

        if (!mfaSecret.getEnabled()) {
            throw new ValidationException("MFA is not enabled");
        }

        mfaSecret.setEnabled(false);
        mfaSecret.setBackupCodes(null);
        mfaSecretRepository.save(mfaSecret);

        user.setMfaEnabled(false);
        userRepository.save(user);
    }

    public List<String> getRecoveryCodes(String username) {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username));

        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
            .orElseThrow(() -> new ResourceNotFoundException("MFA Secret", username));

        if (!mfaSecret.getEnabled()) {
            throw new ValidationException("MFA is not enabled");
        }

        // Return unhashed codes (for display only once)
        return List.of("Backup codes can only be viewed during setup");
    }

    public List<String> regenerateRecoveryCodes(String username) {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username));

        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
            .orElseThrow(() -> new ResourceNotFoundException("MFA Secret", username));

        if (!mfaSecret.getEnabled()) {
            throw new ValidationException("MFA is not enabled");
        }

        List<String> recoveryCodes = generateRecoveryCodes(10);
        String hashedCodes = hashRecoveryCodes(recoveryCodes);

        mfaSecret.setBackupCodes(hashedCodes);
        mfaSecretRepository.save(mfaSecret);

        return recoveryCodes;
    }

    public boolean verifyTotp(User user, String code) {
        MfaSecret mfaSecret = mfaSecretRepository.findByUser(user)
            .orElseThrow(() -> new ResourceNotFoundException("MFA Secret", user.getEmail()));
        return verifyTotp(mfaSecret, code);
    }

    public boolean verifyTotp(MfaSecret mfaSecret, String code) {
        try {
            // Simple TOTP verification using HOTP with time-based counter
            long timeStep = System.currentTimeMillis() / 1000 / 30;
            String expectedCode = generateTotp(mfaSecret.getSecret(), timeStep);
            return code.equals(expectedCode);
        } catch (Exception e) {
            return false;
        }
    }

    private String generateTotp(String secret, long timeStep) {
        try {
            byte[] key = Base32.decode(secret);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(longToBytes(timeStep));
            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24) |
                ((hash[offset + 1] & 0xFF) << 16) |
                ((hash[offset + 2] & 0xFF) << 8) |
                (hash[offset + 3] & 0xFF);
            int otp = binary % 1000000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP", e);
        }
    }

    private byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }

    private List<String> generateRecoveryCodes(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> {
                byte[] bytes = new byte[8];
                new java.security.SecureRandom().nextBytes(bytes);
                return Base64.getEncoder().encodeToString(bytes).substring(0, 10);
            })
            .collect(Collectors.toList());
    }

    private String hashRecoveryCodes(List<String> codes) {
        return codes.stream()
            .map(code -> {
                try {
                    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                    byte[] hash = digest.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return Base64.getEncoder().encodeToString(hash);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to hash recovery code", e);
                }
            })
            .collect(Collectors.joining(","));
    }
}