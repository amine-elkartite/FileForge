import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class FileManager {
    private Path cwd;
    private final Scanner scanner = new Scanner(System.in);
    private static final int PAGE_LINES = 20;

    public FileManager(String initialFolder) throws IOException {
        Path p = Paths.get(initialFolder).toAbsolutePath().normalize();
        if (!Files.isDirectory(p)) throw new IOException("Not a directory: " + p);
        cwd = p;
    }

    public static void run(String folder) throws IOException {
        new FileManager(folder).start();
    }

    private void start() {
        System.out.println(cwd);
        while (true) {
            System.out.print("-> ");
            if (!scanner.hasNextLine()) return;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equals("exit")) return;
            try { execute(line); }
            catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }
    }

    private Path path(String text) {
        if (text.equals("~")) return Paths.get(System.getProperty("user.home"));
        if (text.startsWith("~/")) return Paths.get(System.getProperty("user.home")).resolve(text.substring(2)).normalize();
        return cwd.resolve(text).toAbsolutePath().normalize();
    }

    private List<String> tokens(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (char c : line.toCharArray()) {
            if (escaped) { current.append(c); escaped = false; continue; }
            if (c == '\\' && quote != '\'') { escaped = true; continue; }
            if (quote != 0) {
                if (c == quote) quote = 0;
                else current.append(c);
            } else if (c == '\'' || c == '"') quote = c;
            else if (Character.isWhitespace(c)) {
                if (current.length() > 0) { result.add(current.toString()); current.setLength(0); }
            } else current.append(c);
        }
        if (escaped) current.append('\\');
        if (quote != 0) throw new IllegalArgumentException("Unclosed quote");
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private void execute(String line) throws IOException {
        List<String> a = tokens(line);
        if (a.isEmpty()) return;
        String cmd = a.get(0);
        switch (cmd) {
            case "help": help(); break;
            case "pwd": System.out.println(cwd); break;
            case "cd": cd(a); break;
            case "ls": ls(a); break;
            case "tree": tree(a); break;
            case "touch": touch(a); break;
            case "mkdir": mkdir(a); break;
            case "cat": cat(a); break;
            case "cp": cp(a); break;
            case "mv": mv(a); break;
            case "ln": ln(a); break;
            case "rm": rm(a); break;
            case "rmdir": rmdir(a); break;
            case "less": case "more": page(a); break;
            case "head": case "tail": headTail(a); break;
            case "find": find(a); break;
            case "grep": grep(a); break;
            case "wc": wc(a); break;
            case "chmod": chmod(a); break;
            case "chown": chown(a); break;
            default: System.out.println("Unknown command: " + cmd + " (type help)");
        }
    }

    private void help() {
        System.out.println("ls [-la] [path] | cd [path] | pwd | tree [path]");
        System.out.println("touch FILE... | mkdir [-p] DIR... | cat FILE... | cat > FILE");
        System.out.println("cp [-r] SOURCE DEST | mv SOURCE DEST | ln [-s] TARGET LINK");
        System.out.println("rm [-r] [-f] PATH... | rmdir DIR...");
        System.out.println("less FILE | more FILE | head [-n N] FILE | tail [-n N] FILE");
        System.out.println("find [DIR] [-name GLOB] [-type f|d] | grep [-i] PATTERN FILE...");
        System.out.println("wc FILE... | chmod MODE FILE | chown OWNER[:GROUP] FILE | exit");
        System.out.println("For cat > FILE, enter lines, then a single dot (.) to save.");
        System.out.println("Paths containing spaces can be quoted with double quotes.");
    }

    private void require(List<String> a, int count, String usage) {
        if (a.size() < count) throw new IllegalArgumentException("Usage: " + usage);
    }

    private void cd(List<String> a) throws IOException {
        Path p = path(a.size() < 2 ? "~" : a.get(1));
        if (!Files.isDirectory(p)) throw new IOException("Directory does not exist: " + p);
        cwd = p.toRealPath();
        System.out.println(cwd);
    }

    private void ls(List<String> a) throws IOException {
        boolean all = false, longFormat = false;
        Path directory = cwd;
        for (int i = 1; i < a.size(); i++) {
            String arg = a.get(i);
            if (arg.startsWith("-") && arg.length() > 1) {
                for (char c : arg.substring(1).toCharArray()) {
                    if (c == 'a') all = true;
                    else if (c == 'l') longFormat = true;
                    else throw new IllegalArgumentException("Unknown ls flag: -" + c);
                }
            } else directory = path(arg);
        }
        if (!Files.isDirectory(directory)) {
            showEntry(directory, longFormat);
            return;
        }
        List<Path> entries;
        try (Stream<Path> stream = Files.list(directory)) {
            entries = stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        if (all) {
            showEntry(directory.resolve("."), longFormat);
            showEntry(directory.resolve(".."), longFormat);
        }
        for (Path entry : entries) {
            if (!all && entry.getFileName().toString().startsWith(".")) continue;
            showEntry(entry, longFormat);
        }
    }

    private void showEntry(Path p, boolean details) throws IOException {
        String name = p.getFileName() == null ? p.toString() : p.getFileName().toString();
        long bytes = size(p);
        long kb = (bytes + 1023) / 1024;
        if (details) {
            String perms;
            try { perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(p)); }
            catch (UnsupportedOperationException e) { perms = "---------"; }
            String type = Files.isDirectory(p) ? "d" : Files.isSymbolicLink(p) ? "l" : "-";
            System.out.printf("%s%s %9d KB  %s%n", type, perms, kb, name);
        } else System.out.println(name + " " + kb + " KB");
    }

    private long size(Path p) throws IOException {
        if (!Files.isDirectory(p)) return Files.size(p);
        final long[] total = {0};
        try (Stream<Path> paths = Files.walk(p)) {
            for (Path f : paths.toList()) if (Files.isRegularFile(f)) total[0] += Files.size(f);
        }
        return total[0];
    }

    private void tree(List<String> a) throws IOException {
        Path root = path(a.size() > 1 ? a.get(1) : ".");
        if (!Files.isDirectory(root)) throw new IOException("Not a directory: " + root);
        System.out.println(root);
        printTree(root, "", 0);
    }

    private void printTree(Path dir, String prefix, int depth) throws IOException {
        if (depth >= 15) { System.out.println(prefix + "... (depth limit)"); return; }
        List<Path> paths;
        try (Stream<Path> s = Files.list(dir)) {
            paths = s.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        for (int i = 0; i < paths.size(); i++) {
            Path p = paths.get(i);
            boolean last = i == paths.size() - 1;
            System.out.println(prefix + (last ? "└── " : "├── ") + p.getFileName());
            if (Files.isDirectory(p) && !Files.isSymbolicLink(p))
                printTree(p, prefix + (last ? "    " : "│   "), depth + 1);
        }
    }

    private void touch(List<String> a) throws IOException {
        require(a, 2, "touch FILE...");
        for (int i = 1; i < a.size(); i++) {
            Path p = path(a.get(i));
            if (Files.exists(p)) Files.setLastModifiedTime(p, FileTime.from(Instant.now()));
            else Files.createFile(p);
        }
    }

    private void mkdir(List<String> a) throws IOException {
        require(a, 2, "mkdir [-p] DIR...");
        boolean parents = a.contains("-p");
        for (int i = 1; i < a.size(); i++) {
            if (a.get(i).equals("-p")) continue;
            if (parents) Files.createDirectories(path(a.get(i)));
            else Files.createDirectory(path(a.get(i)));
        }
    }

    private void cat(List<String> a) throws IOException {
        require(a, 2, "cat FILE... | cat > FILE");
        if (a.size() == 3 && a.get(1).equals(">")) {
            Path p = path(a.get(2));
            if (Files.exists(p) && !confirm("Overwrite " + p + "?")) return;
            System.out.println("Enter text; type a single . on its own line to save:");
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.equals(".")) break;
                    writer.write(line); writer.newLine();
                }
            }
            return;
        }
        for (int i = 1; i < a.size(); i++) {
            System.out.print(Files.readString(path(a.get(i)), StandardCharsets.UTF_8));
        }
        System.out.println();
    }

    private void cp(List<String> a) throws IOException {
        boolean recursive = a.contains("-r") || a.contains("-R");
        List<String> positional = new ArrayList<>();
        for (int i = 1; i < a.size(); i++) if (!a.get(i).equals("-r") && !a.get(i).equals("-R")) positional.add(a.get(i));
        if (positional.size() != 2) throw new IllegalArgumentException("Usage: cp [-r] SOURCE DEST");
        Path source = path(positional.get(0)), destination = path(positional.get(1));
        if (Files.isDirectory(destination)) destination = destination.resolve(source.getFileName());
        if (!Files.exists(source)) throw new IOException("Source missing: " + source);
        if (source.equals(destination)) throw new IOException("Source and destination are identical");
        if (Files.isDirectory(source) && !recursive) throw new IOException("Use cp -r for directories");
        if (Files.isDirectory(source) && destination.startsWith(source)) throw new IOException("Cannot copy directory into itself");
        if (Files.exists(destination) && !confirm("Overwrite existing destination " + destination + "?")) return;
        final Path dest = destination;
        if (Files.isDirectory(source)) {
            try (Stream<Path> paths = Files.walk(source)) {
                for (Path item : paths.toList()) {
                    Path target = dest.resolve(source.relativize(item));
                    if (Files.isDirectory(item)) Files.createDirectories(target);
                    else Files.copy(item, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } else Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private void mv(List<String> a) throws IOException {
        if (a.size() != 3) throw new IllegalArgumentException("Usage: mv SOURCE DEST");
        Path source = path(a.get(1)), destination = path(a.get(2));
        if (Files.isDirectory(destination)) destination = destination.resolve(source.getFileName());
        if (Files.exists(destination) && !source.equals(destination) && !confirm("Overwrite " + destination + "?")) return;
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("PROCESSED");
    }

    private void ln(List<String> a) throws IOException {
        boolean symbolic = a.contains("-s");
        List<String> positional = new ArrayList<>();
        for (int i = 1; i < a.size(); i++) if (!a.get(i).equals("-s")) positional.add(a.get(i));
        if (positional.size() != 2) throw new IllegalArgumentException("Usage: ln [-s] TARGET LINK");
        Path target = path(positional.get(0)), link = path(positional.get(1));
        if (symbolic) Files.createSymbolicLink(link, target);
        else Files.createLink(link, target);
    }

    private boolean confirm(String question) {
        System.out.print(question + " [y/N] ");
        return scanner.hasNextLine() && scanner.nextLine().trim().equalsIgnoreCase("y");
    }

    private void rm(List<String> a) throws IOException {
        require(a, 2, "rm [-r] [-f] PATH...");
        boolean recursive = false, force = false;
        List<String> targets = new ArrayList<>();
        for (int i = 1; i < a.size(); i++) {
            String v = a.get(i);
            if (v.startsWith("-") && v.length() > 1) {
                for (char c : v.substring(1).toCharArray()) {
                    if (c == 'r' || c == 'R') recursive = true;
                    else if (c == 'f') force = true;
                    else throw new IllegalArgumentException("Unknown rm flag: " + c);
                }
            } else targets.add(v);
        }
        if (targets.isEmpty()) throw new IllegalArgumentException("Usage: rm [-r] [-f] PATH...");
        for (String t : targets) {
            Path p = path(t);
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                if (!force) System.out.println("No such file: " + p);
                continue;
            }
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) && !recursive) {
                System.out.println("Is a directory (use -r): " + p); continue;
            }
            if (p.equals(p.getRoot()) || p.equals(cwd) || cwd.startsWith(p)) {
                System.out.println("Refusing to delete current directory or its ancestors: " + p); continue;
            }
            // Require confirmation even with -f: this is an educational file manager.
            if (!confirm("Permanently delete " + p + "?")) continue;
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> stream = Files.walk(p)) {
                    for (Path f : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(f);
                }
            } else Files.delete(p);
        }
    }

    private void rmdir(List<String> a) throws IOException {
        require(a, 2, "rmdir DIR...");
        for (int i = 1; i < a.size(); i++) {
            Path p = path(a.get(i));
            if (p.equals(cwd) || cwd.startsWith(p)) throw new IOException("Cannot remove current directory/ancestor");
            Files.delete(p); // fails when not empty
        }
    }

    private void page(List<String> a) throws IOException {
        require(a, 2, "less FILE | more FILE");
        try (java.io.BufferedReader reader = Files.newBufferedReader(path(a.get(1)), StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (++count % PAGE_LINES == 0) {
                    System.out.print("--More-- (Enter to continue, q to quit) ");
                    if (!scanner.hasNextLine() || scanner.nextLine().trim().equalsIgnoreCase("q")) break;
                }
            }
        }
    }

    private void headTail(List<String> a) throws IOException {
        int count = 10, i = 1;
        if (a.size() > 2 && a.get(1).equals("-n")) {
            count = Integer.parseInt(a.get(2)); i = 3;
        }
        if (count < 0 || i >= a.size()) throw new IllegalArgumentException("Usage: head|tail [-n N] FILE");
        List<String> lines = Files.readAllLines(path(a.get(i)), StandardCharsets.UTF_8);
        int start = a.get(0).equals("tail") ? Math.max(0, lines.size() - count) : 0;
        int end = a.get(0).equals("head") ? Math.min(lines.size(), count) : lines.size();
        for (int n = start; n < end; n++) System.out.println(lines.get(n));
    }

    private void find(List<String> a) throws IOException {
        int i = 1;
        Path root = cwd;
        if (i < a.size() && !a.get(i).startsWith("-")) root = path(a.get(i++));
        String glob = null, type = null;
        while (i < a.size()) {
            if (a.get(i).equals("-name") && i + 1 < a.size()) glob = a.get(++i);
            else if (a.get(i).equals("-type") && i + 1 < a.size()) type = a.get(++i);
            else throw new IllegalArgumentException("Usage: find [DIR] [-name GLOB] [-type f|d]");
            i++;
        }
        if (type != null && !type.equals("f") && !type.equals("d")) throw new IllegalArgumentException("Type must be f or d");
        final PathMatcher matcher = glob == null ? null : FileSystems.getDefault().getPathMatcher("glob:" + glob);
        final String selectedType = type;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> matcher == null || matcher.matches(p.getFileName()))
                    .filter(p -> selectedType == null || (selectedType.equals("d") ? Files.isDirectory(p) : Files.isRegularFile(p)))
                    .forEach(System.out::println);
        }
    }

    private void grep(List<String> a) throws IOException {
        boolean ignoreCase = false;
        List<String> pos = new ArrayList<>();
        for (int i = 1; i < a.size(); i++) {
            if (a.get(i).equals("-i")) ignoreCase = true;
            else pos.add(a.get(i));
        }
        if (pos.size() < 2) throw new IllegalArgumentException("Usage: grep [-i] REGEX FILE...");
        Pattern pattern;
        try { pattern = Pattern.compile(pos.get(0), ignoreCase ? Pattern.CASE_INSENSITIVE : 0); }
        catch (PatternSyntaxException e) { throw new IllegalArgumentException("Invalid regex: " + e.getMessage()); }
        for (int i = 1; i < pos.size(); i++) {
            String filename = pos.get(i);
            try (java.io.BufferedReader reader = Files.newBufferedReader(path(filename), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (pattern.matcher(line).find()) System.out.println((pos.size() > 2 ? filename + ":" : "") + line);
                }
            }
        }
    }

    private void wc(List<String> a) throws IOException {
        require(a, 2, "wc FILE...");
        for (int i = 1; i < a.size(); i++) {
            Path file = path(a.get(i));
            byte[] bytes = Files.readAllBytes(file);
            String data = new String(bytes, StandardCharsets.UTF_8);
            long lines = data.chars().filter(c -> c == '\n').count();
            long words = data.isBlank() ? 0 : data.trim().split("\\s+").length;
            System.out.printf("%d %d %d %s%n", lines, words, bytes.length, a.get(i));
        }
    }

    private void chmod(List<String> a) throws IOException {
        if (a.size() != 3) throw new IllegalArgumentException("Usage: chmod OCTAL_MODE FILE");
        String mode = a.get(1);
        if (!mode.matches("[0-7]{3}")) throw new IllegalArgumentException("Use three octal digits, e.g. 644 or 755");
        int value = Integer.parseInt(mode, 8);
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        PosixFilePermission[][] flags = {
                {PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE},
                {PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE},
                {PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE}
        };
        for (int group = 0; group < 3; group++) {
            int bits = (value >> (6 - 3 * group)) & 7;
            if ((bits & 4) != 0) perms.add(flags[group][0]);
            if ((bits & 2) != 0) perms.add(flags[group][1]);
            if ((bits & 1) != 0) perms.add(flags[group][2]);
        }
        Files.setPosixFilePermissions(path(a.get(2)), perms);
    }

    private void chown(List<String> a) throws IOException {
        if (a.size() != 3) throw new IllegalArgumentException("Usage: chown USER[:GROUP] FILE");
        String[] parts = a.get(1).split(":", -1);
        Path file = path(a.get(2));
        UserPrincipalLookupService lookup = file.getFileSystem().getUserPrincipalLookupService();
        if (!parts[0].isEmpty()) Files.setOwner(file, lookup.lookupPrincipalByName(parts[0]));
        if (parts.length > 1 && !parts[1].isEmpty()) {
            PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (view == null) throw new IOException("Group ownership not supported on this filesystem");
            view.setGroup(lookup.lookupPrincipalByGroupName(parts[1]));
        }
    }
}
