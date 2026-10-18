package io.pyahu.start.generation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipArchiveTest {

    @Test
    void sameFilesGiveTheSameBytesWhateverTheOrder() {
        byte[] a = new ZipArchive()
                .add("demo/b.txt", bytes("b"), false)
                .add("demo/a.txt", bytes("a"), false)
                .toByteArray();
        byte[] b = new ZipArchive()
                .add("demo/a.txt", bytes("a"), false)
                .add("demo/b.txt", bytes("b"), false)
                .toByteArray();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void javaReadsTheEntriesSortedWithTheirContent() throws IOException {
        byte[] zip = new ZipArchive()
                .add("demo/z.txt", bytes("zz"), false)
                .add("demo/a.txt", bytes("a".repeat(10_000)), false)
                .toByteArray();
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName() + "=" + in.readAllBytes().length);
            }
        }
        assertThat(names).containsExactly("demo/a.txt=10000", "demo/z.txt=2");
    }

    /** The JDK's zip file system reads the Unix mode from the same field unzip does. */
    @Test
    void keepsTheExecutableBit(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("demo.zip");
        Files.write(
                zip,
                new ZipArchive()
                        .add("demo/gradlew", bytes("#!/bin/sh\n"), true)
                        .add("demo/README.md", bytes("hi"), false)
                        .toByteArray());
        try (FileSystem fs = FileSystems.newFileSystem(zip, Map.of("enablePosixFileAttributes", true))) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(fs.getPath("demo/gradlew"))))
                    .isEqualTo("rwxr-xr-x");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(fs.getPath("demo/README.md"))))
                    .isEqualTo("rw-r--r--");
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
