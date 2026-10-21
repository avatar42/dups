package com.dups;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Main entry point for the file duplicate detector application.
 * 
 * Usage: java -jar file-dup-detector.jar [directory]
 *        If no directory is specified, the current directory is used.
 */
public class Main {
    public static void main(String[] args) {
        String directory = ".";
        
        if (args.length > 0) {
            directory = args[0];
        }

        System.out.println("=== File Duplicate Detector ===");
        System.out.println("Scanning directory: " + directory);
        System.out.println();

        try {
            DatabaseManager dbManager = new DatabaseManager(directory);
            FileScanner scanner = new FileScanner(directory);

            // Scan and detect duplicates
            System.out.println("Scanning files...");
            scanner.detectAndRecordDuplicates(dbManager);

            int fileCount = dbManager.getFileCount();
            System.out.println("Total files indexed: " + fileCount);

            // Get and display duplicates
            List<List<FileDetails>> duplicateGroups = dbManager.getDuplicateGroups();
            
            if (duplicateGroups.isEmpty()) {
                System.out.println();
                System.out.println("No duplicate files found.");
            } else {
                System.out.println();
                System.out.println("=== Duplicate Files (sorted by size, largest first) ===");
                System.out.println();

                int totalDuplicateGroups = 0;
                for (List<FileDetails> group : duplicateGroups) {
                    if (group.size() > 1) {
                        totalDuplicateGroups++;
                        FileDetails firstFile = group.get(0);
                        System.out.println("Size: " + formatSize(firstFile.getSize()) + " (" + group.size() + " files)");
                        for (FileDetails file : group) {
                            System.out.println("  - " + file.getPath());
                        }
                        System.out.println();
                    }
                }

                System.out.println("Total duplicate groups: " + totalDuplicateGroups);
            }

            dbManager.close();
            System.out.println("Done! Database saved to file_database.db");

        } catch (IOException e) {
            System.err.println("Error scanning directory: " + e.getMessage());
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Formats file size in human-readable format.
     */
    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " bytes";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}