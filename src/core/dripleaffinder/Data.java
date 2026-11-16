package dripleaffinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Data {
    public static List<String[]> rows(List<String> lines, int fields, int ints) {
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            String[] f = line.split("\t");
            if (f.length < fields) continue;
            for (int i = 0; i < f.length; i++) f[i] = f[i].trim();
            try {
                for (int i = 0; i < ints; i++) Integer.parseInt(f[i]);
                rows.add(f);
            } catch (NumberFormatException e) {
            }
        }
        return rows;
    }

    public static List<String> lines(Path file, String resource) throws IOException {
        if (file != null && Files.exists(file)) return Files.readAllLines(file);
        if (resource == null) return List.of();
        try (InputStream in = Data.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return List.of();
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList();
        }
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
