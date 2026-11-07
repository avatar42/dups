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
        Options options;
        try {
            options = parseArguments(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(2);
            return;
        }

        if (options.help()) {
            printUsage();
            return;
        }

        String directory = options.directory();
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

            if (options.sizeLimit() > 0) {
                List<FileDetails> duplicatedFiles = dbManager.getDuplicatesSortedBySize();
                int count = Math.min(options.sizeLimit(), duplicatedFiles.size());
                System.out.println();
                System.out.println("=== Top " + options.sizeLimit() + " Duplicated Files by Size ===");
                if (count == 0) {
                    System.out.println("No duplicate files found.");
                } else {
                    for (FileDetails file : duplicatedFiles.subList(0, count)) {
                        System.out.println(formatSize(file.getSize()) + "  " + file.getPath());
                    }
                }
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

    static Options parseArguments(String[] args) {
        String directory = ".";
        int sizeLimit = 0;
        boolean directorySpecified = false;
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-h".equals(arg) || "--help".equals(arg)) {
                help = true;
            } else if ("-s".equals(arg)) {
                if (++i >= args.length) {
                    throw new IllegalArgumentException("Option -s requires a positive number of files");
                }
                try {
                    sizeLimit = Integer.parseInt(args[i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Option -s requires a positive number of files");
                }
                if (sizeLimit <= 0) {
                    throw new IllegalArgumentException("Option -s requires a positive number of files");
                }
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else if (directorySpecified) {
                throw new IllegalArgumentException("Only one directory may be specified");
            } else {
                directory = arg;
                directorySpecified = true;
            }
        }

        return new Options(directory, sizeLimit, help);
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar file-dup-detector.jar [-s N] [directory]");
        System.out.println("  directory  Directory to scan (default: current directory)");
        System.out.println("  -s N       Show the N largest files that have duplicates");
        System.out.println("  -h, --help Show this help message");
    }

    record Options(String directory, int sizeLimit, boolean help) {
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