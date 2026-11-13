
public class Program {

    public static void main(String[] args) {

        try {
            if (args.length == 0) {
                FileSignatureAnalyzer.run();

            } else if (args.length == 2) {
                TextSimilarityAnalyzer.run(args[0], args[1]);

            } else if (args.length == 1
                    && args[0].startsWith("--current-folder=")) {

                String folder = args[0].substring(
                        "--current-folder=".length()
                );

                FileManager.run(folder);

            } else {
                System.out.println("Usage:");
                System.out.println(
                        "File signatures: java Program"
                );
                System.out.println(
                        "Text similarity: java Program fileA.txt fileB.txt"
                );
                System.out.println(
                        "File manager: java Program --current-folder=/path"
                );
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
