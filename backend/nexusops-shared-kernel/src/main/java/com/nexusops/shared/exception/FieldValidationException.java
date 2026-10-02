package com.nexusops.shared.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erro de validação com mensagem por campo (chave do campo -> mensagem). Vira 422 com os campos em
 * {@code extensions}, no mesmo formato da validação de corpo, para o formulário mostrar o erro no campo certo.
 */
public class FieldValidationException extends BusinessException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(String message, Map<String, String> fieldErrors) {
        super("VALIDATION_ERROR", message);
        this.fieldErrors = new LinkedHashMap<>(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
