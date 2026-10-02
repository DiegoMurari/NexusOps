package com.nexusops.ticketing.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceServiceTest {

    @Test
    void sanitizeNameKeepsOnlyTheFileNameAndSafeCharacters() {
        assertThat(EvidenceService.sanitizeName("../../etc/passwd<>.png")).isEqualTo("passwd__.png");
        assertThat(EvidenceService.sanitizeName("C:\\Users\\x\\tela de erro (1).png")).isEqualTo("tela de erro (1).png");
        assertThat(EvidenceService.sanitizeName("relatório-março.pdf")).isEqualTo("relatório-março.pdf");
    }

    @Test
    void sanitizeNameNeverReturnsEmptyOrHiddenNames() {
        assertThat(EvidenceService.sanitizeName(null)).isEqualTo("arquivo");
        assertThat(EvidenceService.sanitizeName("   ")).isEqualTo("arquivo");
        assertThat(EvidenceService.sanitizeName(".env")).isEqualTo("arquivo.env");
    }

    @Test
    void sanitizeNameLimitsLengthKeepingTheExtension() {
        String name = EvidenceService.sanitizeName("a".repeat(500) + ".png");
        assertThat(name).hasSize(200).endsWith(".png");
    }

    @Test
    void extensionIsLowercasedAndEmptyWithoutDot() {
        assertThat(EvidenceService.extension("FOTO.JPG")).isEqualTo("jpg");
        assertThat(EvidenceService.extension("semextensao")).isEmpty();
    }

    @Test
    void contentMustMatchTheExtension() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2};
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        byte[] pdf = "%PDF-1.7".getBytes();
        byte[] webp = "RIFFxxxxWEBPyy".getBytes();
        assertThat(EvidenceService.contentMatches("png", png)).isTrue();
        assertThat(EvidenceService.contentMatches("jpg", jpg)).isTrue();
        assertThat(EvidenceService.contentMatches("jpeg", jpg)).isTrue();
        assertThat(EvidenceService.contentMatches("pdf", pdf)).isTrue();
        assertThat(EvidenceService.contentMatches("webp", webp)).isTrue();
        assertThat(EvidenceService.contentMatches("png", jpg)).isFalse();
        assertThat(EvidenceService.contentMatches("pdf", png)).isFalse();
        assertThat(EvidenceService.contentMatches("gif", "nope".getBytes())).isFalse();
        assertThat(EvidenceService.contentMatches("png", new byte[]{1})).isFalse();
    }

    @Test
    void textFilesMustNotContainNulBytes() {
        assertThat(EvidenceService.contentMatches("txt", "linha 1\nlinha 2".getBytes())).isTrue();
        assertThat(EvidenceService.contentMatches("csv", "a,b\n1,2".getBytes())).isTrue();
        assertThat(EvidenceService.contentMatches("log", new byte[]{65, 0, 66})).isFalse();
        assertThat(EvidenceService.contentMatches("exe", "MZ".getBytes())).isFalse();
    }
}
