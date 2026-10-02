package com.nexusops.ticketing.service;

import com.nexusops.shared.directory.LocationDirectory;
import com.nexusops.shared.exception.FieldValidationException;
import com.nexusops.ticketing.dto.FormDefinitionDto;
import com.nexusops.ticketing.dto.FormDefinitionDto.Field;
import com.nexusops.ticketing.dto.FormDefinitionDto.FieldType;
import com.nexusops.ticketing.dto.FormDefinitionDto.Option;
import com.nexusops.ticketing.dto.FormDefinitionDto.VisibleWhen;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormValidationServiceTest {

    private static final String TENANT = "tenant-1";

    private final LocationDirectory locations = (id, tenant) -> "loc-ok".equals(id) && TENANT.equals(tenant);
    private final FormValidationService service = new FormValidationService(locations);

    private static Field field(String key, FieldType type, boolean required) {
        return new Field(key, key, type, required, null, null, List.of(), null, null, null, null, null, null, null, null);
    }

    private static Field withOptions(String key, FieldType type, boolean required, String... values) {
        List<Option> options = java.util.Arrays.stream(values).map(v -> new Option(v, v.toUpperCase())).toList();
        return new Field(key, key, type, required, null, null, options, null, null, null, null, null, null, null, null);
    }

    private static FormDefinitionDto form(Field... fields) {
        return new FormDefinitionDto(List.of(fields), null);
    }

    private Map<String, String> errorsOf(Runnable action) {
        try {
            action.run();
        } catch (FieldValidationException e) {
            return e.getFieldErrors();
        }
        throw new AssertionError("esperava FieldValidationException");
    }

    // ------------------------------------------------------------- definição

    @Test
    void definition_acceptsAWellFormedForm() {
        service.validateDefinition(form(
            field("assunto", FieldType.TEXT, true),
            withOptions("urgencia", FieldType.SELECT, true, "baixa", "alta")));
    }

    @Test
    void definition_refusesBadKeysDuplicatesAndMissingLabels() {
        Field noLabel = new Field("sem_rotulo", " ", FieldType.TEXT, false, null, null, List.of(), null, null, null, null, null, null, null, null);
        Map<String, String> errors = errorsOf(() -> service.validateDefinition(form(
            field("Chave Ruim", FieldType.TEXT, false),
            field("ok", FieldType.TEXT, false),
            field("ok", FieldType.TEXT, false),
            noLabel)));

        assertThat(errors).containsKeys("fields[0].key", "fields[2].key", "fields[3].label");
        assertThat(errors).doesNotContainKey("fields[1].key");
    }

    @Test
    void definition_choiceFieldsNeedUniqueOptions() {
        Map<String, String> none = errorsOf(() -> service.validateDefinition(form(withOptions("a", FieldType.SELECT, false))));
        Map<String, String> dup = errorsOf(() -> service.validateDefinition(form(withOptions("a", FieldType.MULTISELECT, false, "x", "x"))));

        assertThat(none).containsKey("fields[0].options");
        assertThat(dup).containsKey("fields[0].options");
    }

    @Test
    void definition_visibleWhenMustPointToAnEarlierField() {
        Field later = new Field("detalhe", "Detalhe", FieldType.TEXT, false, null, null, List.of(), null, null, null, null, null, null,
            new VisibleWhen("tipo", "outro"), null);
        Map<String, String> errors = errorsOf(() -> service.validateDefinition(form(
            later, withOptions("tipo", FieldType.SELECT, false, "outro"))));

        assertThat(errors).containsKey("fields[0].visibleWhen");
    }

    @Test
    void definition_refusesInvalidRegexAndInvertedLimits() {
        Field badRegex = new Field("a", "A", FieldType.TEXT, false, null, null, List.of(), null, null, null, null, "([", null, null, null);
        Field badLen = new Field("b", "B", FieldType.TEXT, false, null, null, List.of(), 10, 5, null, null, null, null, null, null);
        Field badRange = new Field("c", "C", FieldType.NUMBER, false, null, null, List.of(), null, null, BigDecimal.TEN, BigDecimal.ONE, null, null, null, null);

        Map<String, String> errors = errorsOf(() -> service.validateDefinition(form(badRegex, badLen, badRange)));

        assertThat(errors).containsKeys("fields[0].pattern", "fields[1].minLength", "fields[2].min");
    }

    @Test
    void definition_refusesADefaultThatDoesNotFitTheField() {
        Field badDefault = new Field("n", "N", FieldType.NUMBER, false, null, null, List.of(), null, null, null, null, null, "abc", null, null);

        assertThat(errorsOf(() -> service.validateDefinition(form(badDefault)))).containsKey("fields[0].defaultValue");
    }

    // -------------------------------------------------------------- respostas

    @Test
    void answers_requiredMissingAndUnknownKeysAreRefused() {
        FormDefinitionDto def = form(field("assunto", FieldType.TEXT, true));

        Map<String, String> errors = errorsOf(() -> service.validateAnswers(def, Map.of("intruso", "x"), TENANT));

        assertThat(errors).containsEntry("assunto", "Campo obrigatório").containsEntry("intruso", "Campo desconhecido");
    }

    @Test
    void answers_areNormalizedPerType() {
        FormDefinitionDto def = form(
            field("texto", FieldType.TEXT, true),
            field("qtd", FieldType.NUMBER, true),
            field("quando", FieldType.DATE, true),
            field("urgente", FieldType.BOOLEAN, false),
            withOptions("area", FieldType.SELECT, true, "rede", "voz"),
            withOptions("itens", FieldType.MULTISELECT, true, "a", "b", "c"),
            field("local", FieldType.LOCATION, true));

        Map<String, Object> out = service.validateAnswers(def, Map.of(
            "texto", "  VPN caiu  ", "qtd", "3,5", "quando", "2026-10-01", "urgente", "true",
            "area", "rede", "itens", List.of("a", "b", "a"), "local", "loc-ok"), TENANT);

        assertThat(out.get("texto")).isEqualTo("VPN caiu");
        assertThat(out.get("qtd")).isEqualTo(new BigDecimal("3.5"));
        assertThat(out.get("quando")).isEqualTo("2026-10-01");
        assertThat(out.get("urgente")).isEqualTo(true);
        assertThat(out.get("itens")).isEqualTo(List.of("a", "b"));
    }

    @Test
    void answers_rejectWrongTypesOptionsAndLocations() {
        FormDefinitionDto def = form(
            field("qtd", FieldType.NUMBER, true),
            field("quando", FieldType.DATE, true),
            withOptions("area", FieldType.SELECT, true, "rede"),
            field("local", FieldType.LOCATION, true));

        Map<String, String> errors = errorsOf(() -> service.validateAnswers(def, Map.of(
            "qtd", "muitos", "quando", "01/10/2026", "area", "outra", "local", "loc-fechada"), TENANT));

        assertThat(errors).containsOnlyKeys("qtd", "quando", "area", "local");
        assertThat(errors.get("local")).contains("inativa");
    }

    @Test
    void answers_textLimitsAndPatternAreEnforced() {
        Field cod = new Field("cod", "Código", FieldType.TEXT, true, null, null, List.of(), 3, 5, null, null, "[A-Z]+", null, null, null);
        FormDefinitionDto def = form(cod);

        assertThat(errorsOf(() -> service.validateAnswers(def, Map.of("cod", "AB"), TENANT)).get("cod")).contains("mínimo");
        assertThat(errorsOf(() -> service.validateAnswers(def, Map.of("cod", "ABCDEF"), TENANT)).get("cod")).contains("máximo");
        assertThat(errorsOf(() -> service.validateAnswers(def, Map.of("cod", "ab1"), TENANT)).get("cod")).contains("Formato");
        assertThat(service.validateAnswers(def, Map.of("cod", "ABC"), TENANT)).containsEntry("cod", "ABC");
    }

    @Test
    void answers_numberRangeIsEnforced() {
        Field n = new Field("n", "N", FieldType.NUMBER, true, null, null, List.of(), null, null, BigDecimal.ONE, BigDecimal.TEN, null, null, null, null);

        assertThat(errorsOf(() -> service.validateAnswers(form(n), Map.of("n", 0), TENANT))).containsKey("n");
        assertThat(errorsOf(() -> service.validateAnswers(form(n), Map.of("n", 11), TENANT))).containsKey("n");
        assertThat(service.validateAnswers(form(n), Map.of("n", 10), TENANT)).containsKey("n");
    }

    @Test
    void answers_hiddenFieldsAreDroppedAndNotRequired() {
        Field detail = new Field("detalhe", "Detalhe", FieldType.TEXT, true, null, null, List.of(), null, null, null, null, null, null,
            new VisibleWhen("tipo", "outro"), null);
        FormDefinitionDto def = form(withOptions("tipo", FieldType.SELECT, true, "rede", "outro"), detail);

        // Tipo "rede": o detalhe não aparece, então não é exigido e uma resposta enviada é descartada.
        Map<String, Object> hidden = service.validateAnswers(def, Map.of("tipo", "rede", "detalhe", "lixo"), TENANT);
        assertThat(hidden).containsOnlyKeys("tipo");

        // Tipo "outro": o detalhe aparece e passa a ser obrigatório.
        assertThat(errorsOf(() -> service.validateAnswers(def, Map.of("tipo", "outro"), TENANT))).containsKey("detalhe");
        assertThat(service.validateAnswers(def, Map.of("tipo", "outro", "detalhe", "explicação"), TENANT)).containsKey("detalhe");
    }

    @Test
    void answers_defaultsFillWhatIsMissing_andRequiredBooleanMeansConfirm() {
        Field withDefault = new Field("canal", "Canal", FieldType.SELECT, true, null, null, List.of(new Option("email", "E-mail")), null, null, null, null, null, "email", null, null);
        Field consent = field("ciente", FieldType.BOOLEAN, true);
        FormDefinitionDto def = form(withDefault, consent);

        assertThat(errorsOf(() -> service.validateAnswers(def, Map.of("ciente", false), TENANT)).get("ciente")).contains("confirmar");
        assertThat(service.validateAnswers(def, Map.of("ciente", true), TENANT)).containsEntry("canal", "email");
    }

    @Test
    void answers_emptyFormAcceptsNothingButEmptyAnswers() {
        assertThat(service.validateAnswers(FormDefinitionDto.empty(), Map.of(), TENANT)).isEmpty();
        assertThat(service.validateAnswers(FormDefinitionDto.empty(), null, TENANT)).isEmpty();
        assertThatThrownBy(() -> service.validateAnswers(FormDefinitionDto.empty(), Map.of("x", 1), TENANT))
            .isInstanceOf(FieldValidationException.class);
    }
}
