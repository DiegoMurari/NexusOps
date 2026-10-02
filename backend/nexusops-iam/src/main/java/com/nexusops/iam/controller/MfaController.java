package com.nexusops.iam.controller;

import com.nexusops.iam.dto.MfaSetupResponse;
import com.nexusops.iam.dto.MfaEnableRequest;
import com.nexusops.iam.dto.MfaDisableRequest;
import com.nexusops.iam.service.MfaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.nexusops.shared.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    @PostMapping("/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa() {
        return ResponseEntity.ok(mfaService.setupMfa(currentUser()));
    }

    @PostMapping("/enable")
    public ResponseEntity<Void> enableMfa(@Valid @RequestBody MfaEnableRequest request) {
        mfaService.enableMfa(currentUser(), request.getCode());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disableMfa(@Valid @RequestBody MfaDisableRequest request) {
        mfaService.disableMfa(currentUser(), request.getPassword());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recovery-codes")
    public ResponseEntity<java.util.List<String>> getRecoveryCodes() {
        return ResponseEntity.ok(mfaService.getRecoveryCodes(currentUser()));
    }

    @PostMapping("/recovery-codes/regenerate")
    public ResponseEntity<java.util.List<String>> regenerateRecoveryCodes() {
        return ResponseEntity.ok(mfaService.regenerateRecoveryCodes(currentUser()));
    }

    /** O principal é o e-mail do usuário autenticado; sem ele a rota nem chega aqui (exige login). */
    private static String currentUser() {
        return SecurityUtils.getCurrentUserId().orElseThrow();
    }
}
