package com.nexusops.ticketing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Guarda os bytes das evidências no disco, fora do banco. O nome em disco é sempre gerado aqui (nunca vem do
 * usuário), então nome de arquivo malicioso não consegue sair do diretório raiz.
 */
@Service
public class EvidenceStorageService {

    private final Path root;

    public EvidenceStorageService(@Value("${nexusops.evidence.dir:./data/evidence}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public record Stored(String key, long size, String sha256) {
    }

    public Stored store(String tenantId, byte[] content) {
        String key = safeSegment(tenantId) + "/" + LocalDate.now().getYear() + "/" + UUID.randomUUID();
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar a evidência", e);
        }
        return new Stored(key, content.length, sha256(content));
    }

    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Arquivo da evidência não encontrado no armazenamento");
        }
        return new FileSystemResource(path);
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível remover a evidência", e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        return path;
    }

    private static String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9-]", "_");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
