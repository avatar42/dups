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
    private final Path databasePath;

    public FileScanner(String rootPath) {
        this.rootDirectory = new File(rootPath);
        this.files = new ArrayList<>();
        this.fileCount = 0;
        this.directoryCount = 0;
        this.workingDir = rootPath;
        this.databasePath = rootDirectory.toPath().toAbsolutePath().normalize()
                .resolve(DatabaseManager.DB_NAME);
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
                    Path normalizedPath = path.toAbsolutePath().normalize();
                    if (normalizedPath.equals(databasePath)) {
                        return FileVisitResult.CONTINUE;
                    }
                    File file = path.toFile();
                    String hash = FileDetails.generateHash(file);
                    FileDetails fileDetails = new FileDetails(
                            normalizedPath.toString(),
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
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }
        });

        return files;
    }

    /**
     * Scans and detects duplicates based on file hash.
     */
    public void detectAndRecordDuplicates(DatabaseManager dbManager) throws IOException, SQLException {
        scan();
        dbManager.synchronizeFiles(files, rootDirectory.toPath());
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