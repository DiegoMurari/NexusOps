package com.nexusops.ticketing.service;

import com.nexusops.shared.directory.LocationDirectory;
import com.nexusops.shared.exception.FieldValidationException;
import com.nexusops.ticketing.dto.FormDefinitionDto;
import com.nexusops.ticketing.dto.FormDefinitionDto.Field;
import com.nexusops.ticketing.dto.FormDefinitionDto.FieldType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regras do formulário dinâmico (ADR-013, Fase D). Valida a definição que o administrador publica e as
 * respostas que o solicitante envia. O servidor é a autoridade: o que o Portal valida no navegador é só
 * conveniência. Sem estado, sem banco (a única dependência é a porta de localidades).
 */
@Service
@RequiredArgsConstructor
public class FormValidationService {

    private static final Pattern KEY = Pattern.compile("^[a-z][a-zA-Z0-9_]{0,39}$");
    private static final int MAX_FIELDS = 40;
    private static final int MAX_OPTIONS = 50;
    private static final int MAX_PATTERN_LENGTH = 200;
    private static final int DEFAULT_TEXT_MAX = 500;
    private static final int DEFAULT_AREA_MAX = 5000;

    private final LocationDirectory locationDirectory;

    // ------------------------------------------------------------ definição

    /** Confere a estrutura da definição; lança erro por campo ({@code fields[i].key}, ...). */
    public void validateDefinition(FormDefinitionDto definition) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<Field> fields = definition.fields();
        if (fields.size() > MAX_FIELDS) {
            errors.put("fields", "No máximo " + MAX_FIELDS + " campos por formulário");
        }

        Set<String> earlier = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            String at = "fields[" + i + "]";

            if (f.key() == null || !KEY.matcher(f.key()).matches()) {
                errors.put(at + ".key", "Chave inválida: comece com letra minúscula e use letras, números ou _ (até 40)");
            } else if (!earlier.add(f.key())) {
                errors.put(at + ".key", "Chave repetida: " + f.key());
            }
            if (f.label() == null || f.label().isBlank()) {
                errors.put(at + ".label", "O rótulo é obrigatório");
            }
            if (f.type() == null) {
                errors.put(at + ".type", "O tipo é obrigatório");
                continue;
            }

            validateOptions(f, at, errors);
            validateLimits(f, at, errors);

            if (f.visibleWhen() != null) {
                String ref = f.visibleWhen().field();
                // Só pode depender de campo anterior: assim a visibilidade se resolve em uma passada.
                boolean earlierField = ref != null && fields.subList(0, i).stream().anyMatch(o -> ref.equals(o.key()));
                if (!earlierField) {
                    errors.put(at + ".visibleWhen", "Deve apontar para um campo anterior do formulário");
                } else if (f.visibleWhen().equals() == null) {
                    errors.put(at + ".visibleWhen", "Informe o valor que torna o campo visível");
                }
            }
            if (f.defaultValue() != null) {
                Result r = normalize(f, f.defaultValue(), null, false);
                if (r.error != null) {
                    errors.put(at + ".defaultValue", "Valor padrão inválido: " + r.error);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Formulário inválido", errors);
        }
    }

    private void validateOptions(Field f, String at, Map<String, String> errors) {
        boolean choice = f.type() == FieldType.SELECT || f.type() == FieldType.MULTISELECT;
        if (!choice) {
            return;
        }
        if (f.options().isEmpty()) {
            errors.put(at + ".options", "Informe ao menos uma opção");
            return;
        }
        if (f.options().size() > MAX_OPTIONS) {
            errors.put(at + ".options", "No máximo " + MAX_OPTIONS + " opções");
            return;
        }
        Set<String> values = new HashSet<>();
        for (FormDefinitionDto.Option o : f.options()) {
            if (o.value() == null || o.value().isBlank() || o.label() == null || o.label().isBlank()) {
                errors.put(at + ".options", "Toda opção precisa de valor e rótulo");
                return;
            }
            if (!values.add(o.value())) {
                errors.put(at + ".options", "Valor de opção repetido: " + o.value());
                return;
            }
        }
    }

    private void validateLimits(Field f, String at, Map<String, String> errors) {
        boolean text = f.type() == FieldType.TEXT || f.type() == FieldType.TEXTAREA;
        if (text) {
            if ((f.minLength() != null && f.minLength() < 0) || (f.maxLength() != null && f.maxLength() < 1)) {
                errors.put(at + ".maxLength", "Limites de tamanho inválidos");
            } else if (f.minLength() != null && f.maxLength() != null && f.minLength() > f.maxLength()) {
                errors.put(at + ".minLength", "O tamanho mínimo não pode passar do máximo");
            }
            if (f.pattern() != null && !f.pattern().isBlank()) {
                if (f.pattern().length() > MAX_PATTERN_LENGTH) {
                    errors.put(at + ".pattern", "A expressão pode ter no máximo " + MAX_PATTERN_LENGTH + " caracteres");
                } else {
                    try {
                        Pattern.compile(f.pattern());
                    } catch (PatternSyntaxException e) {
                        errors.put(at + ".pattern", "Expressão regular inválida");
                    }
                }
            }
        }
        if (f.type() == FieldType.NUMBER && f.min() != null && f.max() != null && f.min().compareTo(f.max()) > 0) {
            errors.put(at + ".min", "O mínimo não pode passar do máximo");
        }
    }

    // ------------------------------------------------------------- respostas

    /**
     * Valida e normaliza as respostas. Campos invisíveis (por {@code visibleWhen}) são descartados e não
     * contam como obrigatórios; padrões preenchem o que faltou; chaves desconhecidas são recusadas.
     */
    public Map<String, Object> validateAnswers(FormDefinitionDto definition, Map<String, Object> answers, String tenantId) {
        Map<String, Object> raw = answers == null ? Map.of() : answers;
        Map<String, String> errors = new LinkedHashMap<>();

        Set<String> known = new HashSet<>();
        definition.fields().forEach(f -> known.add(f.key()));
        raw.keySet().stream().filter(k -> !known.contains(k)).forEach(k -> errors.put(k, "Campo desconhecido"));

        Map<String, Object> result = new LinkedHashMap<>();
        for (Field f : definition.fields()) {
            if (!isVisible(f, result)) {
                continue;
            }
            Object value = raw.get(f.key());
            if (isBlank(value) && f.defaultValue() != null) {
                value = f.defaultValue();
            }
            if (isBlank(value)) {
                if (f.required()) {
                    errors.put(f.key(), "Campo obrigatório");
                }
                continue;
            }
            Result r = normalize(f, value, tenantId, true);
            if (r.error != null) {
                errors.put(f.key(), r.error);
            } else {
                result.put(f.key(), r.value);
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Respostas do formulário inválidas", errors);
        }
        return result;
    }

    private static boolean isVisible(Field f, Map<String, Object> answeredSoFar) {
        FormDefinitionDto.VisibleWhen vw = f.visibleWhen();
        if (vw == null) {
            return true;
        }
        Object other = answeredSoFar.get(vw.field());
        if (other instanceof Collection<?> list) {
            return list.stream().anyMatch(v -> String.valueOf(v).equals(vw.equals()));
        }
        return other != null && String.valueOf(other).equals(vw.equals());
    }

    private static boolean isBlank(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof CharSequence s) {
            return s.toString().isBlank();
        }
        return v instanceof Collection<?> c && c.isEmpty();
    }

    // ---------------------------------------------------------- normalização

    private record Result(Object value, String error) {
        static Result ok(Object v) {
            return new Result(v, null);
        }

        static Result fail(String message) {
            return new Result(null, message);
        }
    }

    private Result normalize(Field f, Object value, String tenantId, boolean checkLocation) {
        return switch (f.type()) {
            case TEXT -> text(f, value, DEFAULT_TEXT_MAX);
            case TEXTAREA -> text(f, value, DEFAULT_AREA_MAX);
            case NUMBER -> number(f, value);
            case DATE -> date(value);
            case BOOLEAN -> bool(f, value);
            case SELECT -> select(f, value);
            case MULTISELECT -> multiselect(f, value);
            case LOCATION -> location(value, tenantId, checkLocation);
        };
    }

    private Result text(Field f, Object value, int defaultMax) {
        if (!(value instanceof CharSequence)) {
            return Result.fail("Informe um texto");
        }
        String s = value.toString().trim();
        int max = f.maxLength() != null ? f.maxLength() : defaultMax;
        if (s.length() > max) {
            return Result.fail("No máximo " + max + " caracteres");
        }
        if (f.minLength() != null && s.length() < f.minLength()) {
            return Result.fail("No mínimo " + f.minLength() + " caracteres");
        }
        if (f.pattern() != null && !f.pattern().isBlank() && !Pattern.compile(f.pattern()).matcher(s).matches()) {
            return Result.fail("Formato inválido");
        }
        return Result.ok(s);
    }

    private Result number(Field f, Object value) {
        BigDecimal n;
        try {
            if (value instanceof Number num) {
                n = new BigDecimal(num.toString());
            } else if (value instanceof CharSequence s) {
                n = new BigDecimal(s.toString().trim().replace(',', '.'));
            } else {
                return Result.fail("Informe um número");
            }
        } catch (NumberFormatException e) {
            return Result.fail("Informe um número válido");
        }
        if (f.min() != null && n.compareTo(f.min()) < 0) {
            return Result.fail("O mínimo é " + f.min().toPlainString());
        }
        if (f.max() != null && n.compareTo(f.max()) > 0) {
            return Result.fail("O máximo é " + f.max().toPlainString());
        }
        return Result.ok(n);
    }

    private Result date(Object value) {
        if (!(value instanceof CharSequence)) {
            return Result.fail("Informe uma data");
        }
        try {
            return Result.ok(LocalDate.parse(value.toString().trim()).toString());
        } catch (DateTimeParseException e) {
            return Result.fail("Data inválida (use AAAA-MM-DD)");
        }
    }

    private Result bool(Field f, Object value) {
        Boolean b;
        if (value instanceof Boolean x) {
            b = x;
        } else if (value instanceof CharSequence s && ("true".equalsIgnoreCase(s.toString()) || "false".equalsIgnoreCase(s.toString()))) {
            b = Boolean.parseBoolean(s.toString());
        } else {
            return Result.fail("Informe sim ou não");
        }
        // Obrigatório em um campo sim/não significa "precisa confirmar".
        if (f.required() && !b) {
            return Result.fail("É preciso confirmar");
        }
        return Result.ok(b);
    }

    private Result select(Field f, Object value) {
        if (!(value instanceof CharSequence)) {
            return Result.fail("Escolha uma opção");
        }
        String v = value.toString();
        return f.options().stream().anyMatch(o -> o.value().equals(v)) ? Result.ok(v) : Result.fail("Opção inválida");
    }

    private Result multiselect(Field f, Object value) {
        if (!(value instanceof Collection<?> items)) {
            return Result.fail("Escolha uma ou mais opções");
        }
        Set<String> allowed = new HashSet<>();
        f.options().forEach(o -> allowed.add(o.value()));
        Set<String> chosen = new LinkedHashSet<>();
        for (Object item : items) {
            if (!(item instanceof CharSequence) || !allowed.contains(item.toString())) {
                return Result.fail("Opção inválida");
            }
            chosen.add(item.toString());
        }
        return Result.ok(new ArrayList<>(chosen));
    }

    private Result location(Object value, String tenantId, boolean check) {
        if (!(value instanceof CharSequence)) {
            return Result.fail("Escolha uma localidade");
        }
        String id = value.toString().trim();
        if (check && !locationDirectory.isActiveInTenant(id, tenantId)) {
            return Result.fail("Localidade inexistente ou inativa");
        }
        return Result.ok(id);
    }
}
