package com.dups;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileScanner class.
 */
public class FileScannerTest {
    private FileScanner scanner;
    private Path tempDir;

    @TempDir
    static Path tempDirStatic;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = tempDirStatic.resolve("test_files");
        Files.createDirectories(tempDir);
        
        // Create some test files
        File file1 = new File(tempDir.toFile(), "file1.txt");
        try (FileWriter writer = new FileWriter(file1)) {
            writer.write("Hello World!");
        }
        
        File file2 = new File(tempDir.toFile(), "file2.txt");
        try (FileWriter writer = new FileWriter(file2)) {
            writer.write("Hello World!");  // Same content as file1
        }
        
        File file3 = new File(tempDir.toFile(), "unique.txt");
        try (FileWriter writer = new FileWriter(file3)) {
            writer.write("Unique content!");
        }
        
        // Create a subdirectory with a file
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        File file4 = new File(subDir.toFile(), "nested.txt");
        try (FileWriter writer = new FileWriter(file4)) {
            writer.write("Nested file content");
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        }
    }

    @Test
    void testScan() throws IOException {
        scanner = new FileScanner(tempDir.toString());
        List<FileDetails> files = scanner.scan();
        
        assertEquals(4, files.size(), "Should find 4 files");
        assertEquals(4, scanner.getFileCount(), "File count should be 4");
        assertTrue(scanner.getDirectoryCount() >= 1, "Should find at least 1 directory");
    }

    @Test
    void testScanNonExistentDirectory() {
        scanner = new FileScanner("/nonexistent/path");
        
        IOException exception = assertThrows(IOException.class, () -> scanner.scan());
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testScanFileInsteadOfDirectory() throws IOException {
        File singleFile = new File(tempDir.toFile(), "file1.txt");
        scanner = new FileScanner(singleFile.getAbsolutePath());
        
        IOException exception = assertThrows(IOException.class, () -> scanner.scan());
        assertTrue(exception.getMessage().contains("not a directory"));
    }

    @Test
    void testDetectAndRecordDuplicates() throws IOException, SQLException {
        scanner = new FileScanner(tempDir.toString());
        
        DatabaseManager dbManager = new DatabaseManager(tempDir.toString());
        scanner.detectAndRecordDuplicates(dbManager);
        
        // file1.txt and file2.txt have the same content, so they should be duplicates
        List<List<FileDetails>> duplicateGroups = dbManager.getDuplicateGroups();
        
        dbManager.close();
        
        // At least one group with duplicates should be found
        assertFalse(duplicateGroups.isEmpty(), "Should find duplicate groups");
    }

    @Test
    void testGetFiles() throws IOException {
        scanner = new FileScanner(tempDir.toString());
        scanner.scan();
        
        List<FileDetails> files = scanner.getFiles();
        assertNotNull(files);
        assertFalse(files.isEmpty());
        
        // Verify file details
        FileDetails file1 = files.stream()
                .filter(f -> f.getPath().endsWith("file1.txt"))
                .findFirst()
                .orElse(null);
        
        assertNotNull(file1);
        assertEquals(file1.getPath(), tempDir.resolve("file1.txt").toString());
        assertTrue(file1.getSize() > 0);
    }
}