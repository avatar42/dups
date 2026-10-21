package com.dups;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileDetails class.
 */
public class FileDetailsTest {
    
    @Test
    void testFileDetailsConstructor() {
        String path = "/test/path/file.txt";
        long size = 1024L;
        String hash = "abc123";
        long lastModified = System.currentTimeMillis();
        
        FileDetails file = new FileDetails(path, size, hash, lastModified);
        
        assertEquals(path, file.getPath());
        assertEquals(size, file.getSize());
        assertEquals(hash, file.getHash());
        assertEquals(lastModified, file.getLastModified());
    }

    @Test
    void testGenerateHash() throws IOException {
        // Create a temporary file
        File tempFile = File.createTempFile("test", ".txt");
        tempFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("Hello, World!");
        }
        
        String hash = FileDetails.generateHash(tempFile);
        assertNotNull(hash, "Hash should not be null");
        assertEquals(32, hash.length(), "MD5 hash should be 32 characters");
    }

    @Test
    void testGenerateHashEmptyFile() throws IOException {
        File tempFile = File.createTempFile("test", ".txt");
        tempFile.deleteOnExit();
        
        String hash = FileDetails.generateHash(tempFile);
        assertNotNull(hash, "Hash should not be null for empty file");
    }

    @Test
    void testEqualsAndHashCode() {
        String path = "/test/path/file.txt";
        long size = 1024L;
        String hash = "abc123";
        long lastModified = System.currentTimeMillis();
        
        FileDetails file1 = new FileDetails(path, size, hash, lastModified);
        FileDetails file2 = new FileDetails(path, size, hash, lastModified);
        FileDetails file3 = new FileDetails("/different/path.txt", size, hash, lastModified);
        
        assertEquals(file1, file2, "Files with same details should be equal");
        assertNotEquals(file1, file3, "Files with different paths should not be equal");
        assertNotEquals(file1, null, "File should not equal null");
        
        // Test hash code consistency
        assertEquals(file1.hashCode(), file2.hashCode(), "Equal objects should have equal hash codes");
    }

    @Test
    void testToString() {
        FileDetails file = new FileDetails("/test/path/file.txt", 1024L, "abc123", System.currentTimeMillis());
        String toString = file.toString();
        
        assertTrue(toString.contains("FileDetails"));
        assertTrue(toString.contains("/test/path/file.txt"));
        assertTrue(toString.contains("1024"));
    }
}