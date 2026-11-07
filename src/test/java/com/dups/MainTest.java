package com.dups;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class MainTest {
    @Test
    public void parsesSizeOptionAndDirectoryInEitherOrder() {
        Main.Options options = Main.parseArguments(new String[] { "-s", "5", "/tmp/files" });
        assertEquals("/tmp/files", options.directory());
        assertEquals(5, options.sizeLimit());

        options = Main.parseArguments(new String[] { "/tmp/files", "-s", "3" });
        assertEquals("/tmp/files", options.directory());
        assertEquals(3, options.sizeLimit());
    }

    @Test
    public void defaultsToCurrentDirectoryAndNoSizeLimit() {
        Main.Options options = Main.parseArguments(new String[0]);
        assertEquals(".", options.directory());
        assertEquals(0, options.sizeLimit());
    }

    public static void copyDirectory(Path source, Path target) throws IOException {
        // Use try-with-resources to ensure the directory stream gets closed properly
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    // Map the source path to the target path structure
                    Path targetPath = target.resolve(sourcePath);
                    System.out.println("Copying file: " + sourcePath + " to " + targetPath);

                    // Check if it's a directory to avoid overwriting existing folders as files
                    if (Files.isDirectory(sourcePath)) {
                        if (!Files.exists(targetPath)) {
                            Files.createDirectories(targetPath);
                        }
                    } else {
                        // Use REPLACE_EXISTING to overwrite existing files, but not directories
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    // Copy the file/directory. REPLACE_EXISTING overwrites files if they exist.
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy: " + sourcePath, e);
                }
            });
        } catch (RuntimeException e) {
            // Unpack the IOException thrown inside the lambda block
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    public static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to delete: " + p, e);
                            }
                        });
            } catch (RuntimeException e) {
                // Unpack the IOException thrown inside the lambda block
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw e;
            }
        }
    }

    public int cntBinFiles(List<List<FileDetails>> duplicateGroups) throws IOException {
        int binFiles = 0;
        for (List<FileDetails> group : duplicateGroups) {
            if (group.size() > 1) {
                for (FileDetails file : group) {
                    if (file.getPath().contains("bin")) {
                        binFiles++;
                    }
                }
            }
        }
        return binFiles;
    }

    @Test
    void removedMisingFilesFromDB() throws Exception {
        Path targetDir = Paths.get("bin");
        deleteDirectoryRecursively(targetDir);

        Files.createDirectories(targetDir);

        copyDirectory(Paths.get(".idea"), targetDir);
        copyDirectory(Paths.get("src"), targetDir);

        DatabaseManager dbManager = new DatabaseManager(".");
        FileScanner scanner = new FileScanner(".");

        // Scan and detect duplicates
        System.out.println("Scanning files...");
        scanner.detectAndRecordDuplicates(dbManager);

        int fileCount = dbManager.getFileCount();
        System.out.println("Total files indexed: " + fileCount);

        List<List<FileDetails>> duplicateGroups = dbManager.getDuplicateGroups();
        int binFiles = cntBinFiles(duplicateGroups);
        assertTrue(binFiles > 0, "There should be some files from the bin directory in the duplicates");
        deleteDirectoryRecursively(targetDir);

        // Scan and detect duplicates
        System.out.println("Scanning files...");
        scanner.detectAndRecordDuplicates(dbManager);

        fileCount = dbManager.getFileCount();
        System.out.println("Total files indexed: " + fileCount);

        duplicateGroups = dbManager.getDuplicateGroups();
        binFiles = cntBinFiles(duplicateGroups);
        assertTrue(binFiles == 0, "There should be no files from the bin directory in the duplicates");
    }

    @Test
    public void rejectsMissingOrInvalidSizeLimit() {
        assertThrows(IllegalArgumentException.class, () -> Main.parseArguments(new String[] { "-s" }));
        assertThrows(IllegalArgumentException.class, () -> Main.parseArguments(new String[] { "-s", "0" }));
        assertThrows(IllegalArgumentException.class, () -> Main.parseArguments(new String[] { "-s", "abc" }));
    }

    @Test
    public void parsesHelpFlag() {
        Main.Options options = Main.parseArguments(new String[] { "--help" });
        assertTrue(options.help());
    }
}
