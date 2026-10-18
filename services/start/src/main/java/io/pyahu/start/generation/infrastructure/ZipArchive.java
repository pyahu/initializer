package io.pyahu.start.generation.infrastructure;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A zip writer that gives the same bytes for the same files: entries sorted by path, one fixed timestamp, Unix file
 * modes (so {@code gradlew} stays executable after {@code unzip}) and nothing else in the headers. Written by hand
 * because {@code java.util.zip} cannot set file modes.
 */
final class ZipArchive {

    /** 1980-01-01 00:00, the first instant a zip timestamp can hold, in MS-DOS format. */
    private static final int DOS_TIME = 0;

    private static final int DOS_DATE = (1 << 5) | 1;
    private static final int UTF8_NAMES = 1 << 11;
    private static final int DEFLATED = 8;
    private static final int REGULAR_FILE = 0100000;

    /** One file of the archive. */
    record Entry(byte[] content, boolean executable) {}

    private final SortedMap<String, Entry> entries = new TreeMap<>();

    ZipArchive add(String path, byte[] content, boolean executable) {
        if (entries.putIfAbsent(path, new Entry(content.clone(), executable)) != null) {
            throw new IllegalStateException("duplicate entry " + path);
        }
        return this;
    }

    byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream central = new ByteArrayOutputStream();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            byte[] name = e.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] data = e.getValue().content();
            byte[] compressed = deflate(data);
            CRC32 crc = new CRC32();
            crc.update(data);
            int offset = out.size();

            int32(out, 0x04034b50);
            int16(out, 20);
            int16(out, UTF8_NAMES);
            int16(out, DEFLATED);
            int16(out, DOS_TIME);
            int16(out, DOS_DATE);
            int32(out, (int) crc.getValue());
            int32(out, compressed.length);
            int32(out, data.length);
            int16(out, name.length);
            int16(out, 0);
            out.writeBytes(name);
            out.writeBytes(compressed);

            int mode = REGULAR_FILE | (e.getValue().executable() ? 0755 : 0644);
            int32(central, 0x02014b50);
            int16(central, (3 << 8) | 20); // made by Unix, so tools read the mode below
            int16(central, 20);
            int16(central, UTF8_NAMES);
            int16(central, DEFLATED);
            int16(central, DOS_TIME);
            int16(central, DOS_DATE);
            int32(central, (int) crc.getValue());
            int32(central, compressed.length);
            int32(central, data.length);
            int16(central, name.length);
            int16(central, 0);
            int16(central, 0);
            int16(central, 0);
            int16(central, 0);
            int32(central, mode << 16);
            int32(central, offset);
            central.writeBytes(name);
        }
        int centralOffset = out.size();
        out.writeBytes(central.toByteArray());
        int32(out, 0x06054b50);
        int16(out, 0);
        int16(out, 0);
        int16(out, entries.size());
        int16(out, entries.size());
        int32(out, central.size());
        int32(out, centralOffset);
        int16(out, 0);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void int16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
    }

    private static void int32(ByteArrayOutputStream out, int v) {
        int16(out, v & 0xffff);
        int16(out, (v >>> 16) & 0xffff);
    }
}
