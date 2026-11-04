package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.service.impl.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage storage() {
        return new LocalFileStorage(tempDir.resolve("card-files").toString());
    }

    @Test
    void constructor_createsMissingDirectory() {
        storage();

        assertThat(tempDir.resolve("card-files")).isDirectory();
    }

    @Test
    void constructor_failsWithReadableMessage_whenPathIsAFile() throws IOException {
        Path file = Files.writeString(tempDir.resolve("not-a-dir"), "x");

        assertThatThrownBy(() -> new LocalFileStorage(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CARD_STORAGE_PATH");
    }

    @Test
    void constructor_rejectsBlankPath() {
        assertThatThrownBy(() -> new LocalFileStorage(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CARD_STORAGE_PATH");
    }

    @Test
    void putGetExistsDelete_roundtrip() {
        LocalFileStorage storage = storage();
        byte[] bytes = {1, 2, 3, 4, 5};

        storage.put("drawings/abc-123.bin", bytes);

        assertThat(storage.exists("drawings/abc-123.bin")).isTrue();
        assertThat(storage.get("drawings/abc-123.bin")).isEqualTo(bytes);
        assertThat(tempDir.resolve("card-files/drawings/abc-123.bin")).hasBinaryContent(bytes);

        storage.delete("drawings/abc-123.bin");

        assertThat(storage.exists("drawings/abc-123.bin")).isFalse();
    }

    @Test
    void put_overwritesAtomically_andLeavesNoTempFiles() throws IOException {
        LocalFileStorage storage = storage();

        storage.put("drawings/a.bin", new byte[]{1});
        storage.put("drawings/a.bin", new byte[]{2, 2});

        assertThat(storage.get("drawings/a.bin")).containsExactly(2, 2);
        try (Stream<Path> files = Files.list(tempDir.resolve("card-files/drawings"))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("a.bin");
        }
    }

    @Test
    void put_emptyContent_isStored() {
        LocalFileStorage storage = storage();

        storage.put("drawings/empty.bin", new byte[0]);

        assertThat(storage.get("drawings/empty.bin")).isEmpty();
    }

    @Test
    void delete_missingFile_isNoOp() {
        LocalFileStorage storage = storage();

        storage.delete("drawings/missing.bin");

        assertThat(storage.exists("drawings/missing.bin")).isFalse();
    }

    @Test
    void get_missingFile_throws() {
        LocalFileStorage storage = storage();

        assertThatThrownBy(() -> storage.get("drawings/missing.bin"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void invalidKeys_areRejected() {
        LocalFileStorage storage = storage();

        Stream.of("../escape.bin", "drawings/../../escape.bin", "drawings/..", "/etc/passwd",
                        "C:/Windows/x.bin", "drawings\\x.bin", "Drawings/X.bin", "drawings//x.bin",
                        "drawings/", ".hidden", "", "drawings/x y.bin", "a".repeat(201))
                .forEach(key -> {
                    assertThatThrownBy(() -> storage.put(key, new byte[]{1}))
                            .as(key)
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> storage.get(key)).isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> storage.exists(key)).isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IllegalArgumentException.class);
                });
        assertThatThrownBy(() -> storage.put(null, new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThat(tempDir.resolve("escape.bin")).doesNotExist();
    }

    @Test
    void put_rejectsNullContent() {
        LocalFileStorage storage = storage();

        assertThatThrownBy(() -> storage.put("drawings/a.bin", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
