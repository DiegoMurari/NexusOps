package com.nexusops.ticketing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Definição de um formulário de tópico. É o mesmo contrato que o administrador edita, que o Portal desenha e
 * que o servidor usa para validar as respostas: o servidor é quem decide o que vale.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormDefinitionDto(List<Field> fields, Evidence evidence) {

    public enum FieldType { TEXT, TEXTAREA, NUMBER, DATE, BOOLEAN, SELECT, MULTISELECT, LOCATION }

    /** Dado do perfil do solicitante que o Portal usa para pré-preencher o campo. */
    public enum Prefill { USER_LOCATION, USER_PHONE, USER_DEPARTMENT, USER_JOB_TITLE }

    public enum EvidenceMode { NONE, OPTIONAL, REQUIRED }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String value, String label) {
    }

    /** O campo só aparece (e só vale) quando a resposta de {@code field} for igual a {@code equals}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VisibleWhen(String field, String equals) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Field(
        String key,
        String label,
        FieldType type,
        boolean required,
        String helpText,
        String placeholder,
        List<Option> options,
        Integer minLength,
        Integer maxLength,
        BigDecimal min,
        BigDecimal max,
        String pattern,
        Object defaultValue,
        VisibleWhen visibleWhen,
        Prefill prefill
    ) {
        public Field {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** Política de evidência do formulário (aplicada na Fase F). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evidence(EvidenceMode mode, String hint) {
    }

    public FormDefinitionDto {
        fields = fields == null ? List.of() : List.copyOf(fields);
        evidence = evidence == null ? new Evidence(EvidenceMode.NONE, null) : evidence;
    }

    public static FormDefinitionDto empty() {
        return new FormDefinitionDto(List.of(), null);
    }
}
