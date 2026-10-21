package com.dups;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseManager class.
 */
public class DatabaseManagerTest {
    private DatabaseManager dbManager;
    private Path tempDir;

    @TempDir
    static Path tempDirStatic;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        tempDir = tempDirStatic.resolve("test_db");
        Files.createDirectories(tempDir);
        dbManager = new DatabaseManager(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (dbManager != null) {
            dbManager.close();
        }
        // Clean up temp files
        if (Files.exists(tempDir)) {
            try {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                // ignore
                            }
                        });
            } catch (IOException e) {
                // ignore
            }
        }
    }

    @Test
    void testSaveFileNew() throws SQLException {
        FileDetails file = new FileDetails("/test/path/file1.txt", 1024L, "abc123", System.currentTimeMillis());
        
        int id = dbManager.saveFile(file);
        
        Assertions.assertTrue(id > 0, "Should return a positive ID for new file");
    }

    @Test
    void testSaveFileUpdateExisting() throws SQLException {
        FileDetails file1 = new FileDetails("/test/path/file1.txt", 1024L, "abc123", 1000L);
        int id1 = dbManager.saveFile(file1);
        
        // Update with same path but different size
        FileDetails file2 = new FileDetails("/test/path/file1.txt", 2048L, "xyz789", 2000L);
        int id2 = dbManager.saveFile(file2);
        
        Assertions.assertEquals(id1, id2, "Should return the same ID for updated file");
        
        // Verify the update
        List<FileDetails> files = dbManager.getFileGroupByHash("xyz789");
        Assertions.assertEquals(1, files.size());
        Assertions.assertEquals(2048L, files.get(0).getSize());
    }

    @Test
    void testRecordDuplicate() throws SQLException {
        FileDetails file1 = new FileDetails("/test/path/file1.txt", 1024L, "abc123", System.currentTimeMillis());
        FileDetails file2 = new FileDetails("/test/path/file2.txt", 1024L, "abc123", System.currentTimeMillis());
        
        int id1 = dbManager.saveFile(file1);
        int id2 = dbManager.saveFile(file2);
        
        dbManager.recordDuplicate(id1, id2);
        
        // Verify duplicate was recorded
        List<List<FileDetails>> duplicates = dbManager.getDuplicateGroups();
        Assertions.assertFalse(duplicates.isEmpty(), "Should find duplicate groups");
    }

    @Test
    void testRecordDuplicateSameFile() throws SQLException {
        FileDetails file1 = new FileDetails("/test/path/file1.txt", 1024L, "abc123", System.currentTimeMillis());
        
        int id1 = dbManager.saveFile(file1);
        
        // Should not throw exception or create duplicate
        Assertions.assertDoesNotThrow(() -> dbManager.recordDuplicate(id1, id1));
    }

    @Test
    void testGetDuplicateGroups() throws SQLException {
        // Create multiple files with same hash
        for (int i = 0; i < 3; i++) {
            FileDetails file = new FileDetails("/test/path/file" + i + ".txt", 1024L * (i + 1), "samehash", System.currentTimeMillis());
            dbManager.saveFile(file);
        }
        
        // Create a unique file
        FileDetails uniqueFile = new FileDetails("/test/path/unique.txt", 512L, "uniquehash", System.currentTimeMillis());
        dbManager.saveFile(uniqueFile);
        
        // Get duplicate groups
        List<List<FileDetails>> groups = dbManager.getDuplicateGroups();
        
        Assertions.assertFalse(groups.isEmpty(), "Should find at least one duplicate group");
    }

    @Test
    void testGetDuplicateGroupsOrderBySize() throws SQLException {
        FileDetails smallFile = new FileDetails("/test/path/small.txt", 100L, "hash1", System.currentTimeMillis());
        FileDetails largeFile = new FileDetails("/test/path/large.txt", 1000L, "hash1", System.currentTimeMillis());
        
        dbManager.saveFile(smallFile);
        dbManager.saveFile(largeFile);
        
        List<List<FileDetails>> groups = dbManager.getDuplicateGroups();
        
        Assertions.assertFalse(groups.isEmpty());
    }

    @Test
    void testClearDatabase() throws SQLException {
        FileDetails file1 = new FileDetails("/test/path/file1.txt", 1024L, "abc123", System.currentTimeMillis());
        dbManager.saveFile(file1);
        
        int countBefore = dbManager.getFileCount();
        Assertions.assertEquals(1, countBefore, "Should have 1 file before clear");
        
        dbManager.clearDatabase();
        
        int countAfter = dbManager.getFileCount();
        Assertions.assertEquals(0, countAfter, "Should have 0 files after clear");
    }

    @Test
    void testGetFileCount() throws SQLException {
        for (int i = 0; i < 5; i++) {
            FileDetails file = new FileDetails("/test/path/file" + i + ".txt", 1024L, "abc123", System.currentTimeMillis());
            dbManager.saveFile(file);
        }
        
        int count = dbManager.getFileCount();
        Assertions.assertEquals(5, count, "Should have 5 files");
    }

    @Test
    void testDatabasePersistence() throws SQLException {
        // Test that data persists after connection is closed and reopened
        
        // Save file to first connection
        FileDetails file = new FileDetails("/test/path/persistent.txt", 1024L, "persistenthash", System.currentTimeMillis());
        int id = dbManager.saveFile(file);
        Assertions.assertTrue(id > 0);
        
        dbManager.close();
        
        // Reopen database
        DatabaseManager newDbManager = new DatabaseManager(tempDir.toString());
        
        // Verify file still exists
        int count = newDbManager.getFileCount();
        Assertions.assertEquals(1, count, "File should persist after restart");
        
        newDbManager.close();
    }
}