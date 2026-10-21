package com.crmops.channel.service;

import com.crmops.channel.config.ChannelProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local-disk file storage for uploaded documents. This is one of the
 * application's canonical precision-baseline locations:
 * every stored-name resolution goes through a canonicalized base directory
 * ({@code toRealPath()}), and the resolved path is verified to stay inside it
 * before any file is opened — path traversal via stored names is impossible.
 */
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(ChannelProperties properties) {
        this.root = Path.of(properties.fileStorageDir()).toAbsolutePath();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create file storage directory " + root, e);
        }
    }

    public Path canonicalRoot() {
        try {
            return root.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("cannot resolve storage root", e);
        }
    }

    /** Resolves a stored name against the canonical base and verifies containment. */
    public Path resolveWithinRoot(String storedName) {
        if (storedName == null || storedName.contains("/") || storedName.contains("\\")
                || storedName.contains("..")) {
            throw new ValidationException("invalid stored name");
        }
        Path base = canonicalRoot();
        Path resolved = base.resolve(storedName).normalize();
        if (!resolved.startsWith(base)) {
            throw new ValidationException("path escapes the storage root");
        }
        return resolved;
    }

    public void store(String storedName, byte[] bytes) throws IOException {
        Path target = resolveWithinRoot(storedName);
        Files.copy(new java.io.ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
    }

    public InputStream open(String storedName) throws IOException {
        Path target = resolveWithinRoot(storedName);
        if (!Files.exists(target)) {
            throw new NotFoundException("file", storedName);
        }
        return Files.newInputStream(target);
    }

    public void writeTo(String storedName, OutputStream out) throws IOException {
        try (InputStream in = open(storedName)) {
            in.transferTo(out);
        }
    }

    public long size(String storedName) throws IOException {
        return Files.size(resolveWithinRoot(storedName));
    }

    public void delete(String storedName) throws IOException {
        Files.deleteIfExists(resolveWithinRoot(storedName));
    }
}
