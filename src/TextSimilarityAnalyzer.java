
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class TextSimilarityAnalyzer {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private static final Pattern WORD_PATTERN =
            Pattern.compile("\\p{L}+");

    public static void run(String firstFile, String secondFile)
            throws IOException {

        Map<String, Integer> first =
                countWords(Path.of(firstFile));

        Map<String, Integer> second =
                countWords(Path.of(secondFile));

        Set<String> dictionary = new TreeSet<>();

        dictionary.addAll(first.keySet());
        dictionary.addAll(second.keySet());

        Files.write(
                Path.of("dictionary.txt"),
                dictionary,
                StandardCharsets.UTF_8
        );

        double dotProduct = 0;
        double magnitudeA = 0;
        double magnitudeB = 0;

        for (String word : dictionary) {

            int a = first.getOrDefault(word, 0);
            int b = second.getOrDefault(word, 0);

            dotProduct += (double) a * b;
            magnitudeA += (double) a * a;
            magnitudeB += (double) b * b;
        }

        double similarity;

        if (magnitudeA == 0 || magnitudeB == 0) {
            similarity = 0;
        } else {
            similarity = dotProduct /
                    (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
        }

        System.out.printf(
                Locale.US,
                "Similarity = %.2f%n",
                similarity
        );
    }

    private static Map<String, Integer> countWords(Path file)
            throws IOException {

        if (!Files.isRegularFile(file)) {
            throw new IOException("File not found: " + file);
        }

        if (Files.size(file) > MAX_FILE_SIZE) {
            throw new IOException("File exceeds 10 MB: " + file);
        }

        Map<String, Integer> frequency = new HashMap<>();

        try (BufferedReader reader =
                     Files.newBufferedReader(file)) {

            String line;

            while ((line = reader.readLine()) != null) {

                Matcher matcher = WORD_PATTERN.matcher(
                        line.toLowerCase(Locale.ROOT)
                );

                while (matcher.find()) {

                    String word = matcher.group();

                    frequency.merge(word, 1, Integer::sum);
                }
            }
        }

        return frequency;
    }
}
