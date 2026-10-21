package com.dups;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans a directory tree and collects file details.
 */
public class FileScanner {
    private final File rootDirectory;
    private final List<FileDetails> files;
    private int fileCount;
    private int directoryCount;
    private final String workingDir;

    public FileScanner(String rootPath) {
        this.rootDirectory = new File(rootPath);
        this.files = new ArrayList<>();
        this.fileCount = 0;
        this.directoryCount = 0;
        this.workingDir = rootPath;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    /**
     * Scans the directory tree and collects file details.
     */
    public List<FileDetails> scan() throws IOException {
        files.clear();
        fileCount = 0;
        directoryCount = 0;

        if (!rootDirectory.exists()) {
            throw new IOException("Directory does not exist: " + rootDirectory.getAbsolutePath());
        }

        if (!rootDirectory.isDirectory()) {
            throw new IOException("Path is not a directory: " + rootDirectory.getAbsolutePath());
        }

        Files.walkFileTree(rootDirectory.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                directoryCount++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    File file = path.toFile();
                    String hash = FileDetails.generateHash(file);
                    FileDetails fileDetails = new FileDetails(
                            file.getAbsolutePath(),
                            file.length(),
                            hash,
                            file.lastModified()
                    );
                    files.add(fileDetails);
                    fileCount++;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Failed to visit file: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Scans and detects duplicates based on file hash.
     */
    public void detectAndRecordDuplicates(DatabaseManager dbManager) throws IOException, SQLException {
        scan();

        // Group files by hash to find duplicates
        java.util.Map<String, List<FileDetails>> hashMap = new java.util.HashMap<>();
        for (FileDetails file : files) {
            if (file.getHash() != null) {
                hashMap.computeIfAbsent(file.getHash(), k -> new java.util.ArrayList<>()).add(file);
            }
        }

        // Record duplicates
        for (java.util.Map.Entry<String, List<FileDetails>> entry : hashMap.entrySet()) {
            List<FileDetails> duplicates = entry.getValue();
            if (duplicates.size() > 1) {
                // Record all pairs as duplicates
                for (int i = 0; i < duplicates.size(); i++) {
                    for (int j = i + 1; j < duplicates.size(); j++) {
                        int fileId = dbManager.saveFile(duplicates.get(i));
                        int duplicateId = dbManager.saveFile(duplicates.get(j));
                        dbManager.recordDuplicate(fileId, duplicateId);
                    }
                }
            } else {
                // Single file with this hash
                dbManager.saveFile(duplicates.get(0));
            }
        }
    }

    public int getFileCount() {
        return fileCount;
    }

    public int getDirectoryCount() {
        return directoryCount;
    }

    public List<FileDetails> getFiles() {
        return files;
    }
}