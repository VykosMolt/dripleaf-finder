package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.List;

final class Data {
    static void write(Path target, List<String> lines) throws IOException {
        Files.createDirectories(target.getParent());
        Path part = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            Files.write(part, lines);
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(part);
        }
    }

    static int[] ints(String line, int count) {
        String[] f = line.split("\t");
        if (f.length < count) return null;
        int[] values = new int[count];
        try {
            for (int i = 0; i < count; i++) values[i] = Integer.parseInt(f[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return values;
    }

    static List<String> lines(Path file) throws IOException {
        return Files.exists(file) ? Files.readAllLines(file) : List.of();
    }

    static String read(Path file) {
        try {
            if (file == null || !Files.exists(file)) return null;
            String text = Files.readString(file).trim();
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            return null;
        }
    }

    private Data() {
    }
}
