
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class FileSignatureAnalyzer {

    private static final Path SIGNATURES_FILE =
            Path.of("src", "signatures.txt");

    private static final Path RESULT_FILE =
            Path.of("result.txt");

    private static class Signature {
        private final String type;
        private final byte[] bytes;

        private Signature(String type, byte[] bytes) {
            this.type = type;
            this.bytes = bytes;
        }
    }

    public static void run() throws IOException {

        List<Signature> signatures = loadSignatures();

        int maxLength = 0;

        for (Signature signature : signatures) {
            maxLength = Math.max(
                    maxLength,
                    signature.bytes.length
            );
        }

        Scanner scanner = new Scanner(System.in);

        try (BufferedWriter writer = Files.newBufferedWriter(
                RESULT_FILE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            while (true) {
                System.out.print("-> ");

                if (!scanner.hasNextLine()) {
                    break;
                }

                String input = scanner.nextLine().trim();

                if (input.equals("42")) {
                    break;
                }

                try {
                    Path file = Path.of(input);

                    if (!Files.isRegularFile(file)) {
                        throw new IOException(
                                "File does not exist: " + file
                        );
                    }

                    byte[] header;

                    try (InputStream stream =
                                 Files.newInputStream(file)) {

                        header = stream.readNBytes(maxLength);
                    }

                    String detectedType = null;
                    int longestMatch = -1;

                    for (Signature signature : signatures) {

                        if (header.length >= signature.bytes.length
                                && startsWith(
                                header,
                                signature.bytes
                        )
                                && signature.bytes.length > longestMatch) {

                            detectedType = signature.type;
                            longestMatch = signature.bytes.length;
                        }
                    }

                    if (detectedType != null) {
                        writer.write(detectedType);
                        writer.newLine();
                        writer.flush();
                    }

                    System.out.println("PROCESSED");

                } catch (IOException | InvalidPathException e) {
                    System.out.println(
                            "Error: " + e.getMessage()
                    );
                }
            }
        }
    }

    private static List<Signature> loadSignatures()
            throws IOException {

        List<Signature> signatures = new ArrayList<>();

        try (BufferedReader reader =
                     Files.newBufferedReader(SIGNATURES_FILE)) {

            String line;

            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",", 2);

                if (parts.length != 2) {
                    throw new IOException(
                            "Invalid signature: " + line
                    );
                }

                String type = parts[0].trim();
                String hexText = parts[1].trim();

                if (type.isEmpty() || hexText.isEmpty()) {
                    throw new IOException(
                            "Invalid signature: " + line
                    );
                }

                String[] hex = hexText.split("\\s+");

                byte[] bytes = new byte[hex.length];

                try {
                    for (int i = 0; i < hex.length; i++) {

                        if (!hex[i].matches("[0-9A-Fa-f]{2}")) {
                            throw new NumberFormatException(
                                    "Invalid HEX byte: " + hex[i]
                            );
                        }

                        bytes[i] = (byte) Integer.parseInt(
                                hex[i],
                                16
                        );
                    }

                } catch (NumberFormatException e) {
                    throw new IOException(
                            "Invalid signature: " + line,
                            e
                    );
                }

                signatures.add(new Signature(type, bytes));
            }
        }

        if (signatures.isEmpty()) {
            throw new IOException(
                    "No signatures found in signatures.txt"
            );
        }

        return signatures;
    }

    private static boolean startsWith(
            byte[] content,
            byte[] signature
    ) {
        if (content.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }
}
