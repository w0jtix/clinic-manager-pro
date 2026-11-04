package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.service.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

@Service
public class LocalFileStorage implements FileStorage {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9/.-]*");
    private static final int MAX_KEY_LENGTH = 200;
    private static final String TEMP_PREFIX = ".tmp-";

    private final Path root;

    public LocalFileStorage(@Value("${card.storage.path}") String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalStateException("card.storage.path (CARD_STORAGE_PATH) is not set.");
        }
        this.root = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create card storage directory " + root
                    + " (card.storage.path / CARD_STORAGE_PATH): " + e, e);
        }
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw new IllegalStateException("Card storage directory " + root
                    + " is not a writable directory (card.storage.path / CARD_STORAGE_PATH).");
        }
    }

    @Override
    public void put(String key, byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("File content must not be null.");
        }
        Path target = resolve(key);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), TEMP_PREFIX, ".part");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw new UncheckedIOException("Failed to store file " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || key.length() > MAX_KEY_LENGTH || !KEY_PATTERN.matcher(key).matches()
                || key.contains("..") || key.contains("//") || key.endsWith("/")) {
            throw new IllegalArgumentException("Invalid file key.");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Invalid file key.");
        }
        return resolved;
    }
}
