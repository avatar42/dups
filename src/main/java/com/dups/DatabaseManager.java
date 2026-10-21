package com.dups;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages SQLite database operations for file tracking and duplicate detection.
 */
public class DatabaseManager {
    private static final String DB_NAME = "file_database.db";
    private static final String TABLE_FILES = "files";
    private static final String TABLE_DUPLICATES = "duplicates";

    private final Connection connection;
    private final String workingDir;

    public DatabaseManager(String workingDir) throws SQLException {
        this.workingDir = workingDir;
        String dbPath = workingDir + File.separator + DB_NAME;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        createTables();
    }

    public String getWorkingDir() {
        return workingDir;
    }

    private void createTables() throws SQLException {
        String createFilesTable = "CREATE TABLE IF NOT EXISTS " + TABLE_FILES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "path TEXT UNIQUE NOT NULL," +
                "size BIGINT NOT NULL," +
                "hash TEXT," +
                "last_modified BIGINT NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        String createDuplicatesTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DUPLICATES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "file_id INTEGER NOT NULL," +
                "duplicate_id INTEGER NOT NULL," +
                "FOREIGN KEY (file_id) REFERENCES files(id)," +
                "FOREIGN KEY (duplicate_id) REFERENCES files(id)," +
                "UNIQUE(file_id, duplicate_id)" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createFilesTable);
            stmt.execute(createDuplicatesTable);
        }
    }

    /**
     * Saves or updates a file record in the database.
     */
    public int saveFile(FileDetails file) throws SQLException {
        String checkExists = "SELECT id FROM " + TABLE_FILES + " WHERE path = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkExists)) {
            checkStmt.setString(1, file.getPath());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String updateSql = "UPDATE " + TABLE_FILES + " SET size = ?, hash = ?, last_modified = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                        updateStmt.setLong(1, file.getSize());
                        updateStmt.setString(2, file.getHash());
                        updateStmt.setLong(3, file.getLastModified());
                        updateStmt.setInt(4, id);
                        updateStmt.executeUpdate();
                        return id;
                    }
                }
            }
        }

        String insertSql = "INSERT INTO " + TABLE_FILES + " (path, size, hash, last_modified) VALUES (?, ?, ?, ?)";
        try (PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insertStmt.setString(1, file.getPath());
            insertStmt.setLong(2, file.getSize());
            insertStmt.setString(3, file.getHash());
            insertStmt.setLong(4, file.getLastModified());
            insertStmt.executeUpdate();

            try (ResultSet rs = insertStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Records a duplicate relationship between two files.
     */
    public void recordDuplicate(int fileId, int duplicateId) throws SQLException {
        if (fileId == duplicateId) {
            return;
        }

        String checkExists = "SELECT id FROM " + TABLE_DUPLICATES + " WHERE (file_id = ? AND duplicate_id = ?) OR (file_id = ? AND duplicate_id = ?)";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkExists)) {
            checkStmt.setInt(1, fileId);
            checkStmt.setInt(2, duplicateId);
            checkStmt.setInt(3, duplicateId);
            checkStmt.setInt(4, fileId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    return; // Already recorded
                }
            }
        }

        String insertSql = "INSERT INTO " + TABLE_DUPLICATES + " (file_id, duplicate_id) VALUES (?, ?)";
        try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
            insertStmt.setInt(1, fileId);
            insertStmt.setInt(2, duplicateId);
            insertStmt.executeUpdate();
        }
    }

    /**
     * Gets all files with their duplicates, sorted by size descending.
     */
    public List<FileDetails> getDuplicatesSortedBySize() throws SQLException {
        String sql = "SELECT f1.id, f1.path, f1.size, f1.hash, f1.last_modified " +
                "FROM " + TABLE_FILES + " f1 " +
                "INNER JOIN " + TABLE_DUPLICATES + " d ON (f1.id = d.file_id OR f1.id = d.duplicate_id) " +
                "GROUP BY f1.id " +
                "ORDER BY f1.size DESC";

        List<FileDetails> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                FileDetails file = new FileDetails(
                        rs.getString("path"),
                        rs.getLong("size"),
                        rs.getString("hash"),
                        rs.getLong("last_modified")
                );
                results.add(file);
            }
        }
        return results;
    }

    /**
     * Gets files sorted by size (largest first), optionally limited.
     */
    public List<FileDetails> getFilesBySize(int limit) throws SQLException {
        String sql = "SELECT id, path, size, hash, last_modified FROM " + TABLE_FILES + " WHERE hash IS NOT NULL ORDER BY size DESC";
        if (limit > 0) {
            sql += " LIMIT " + limit;
        }
        List<FileDetails> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                FileDetails file = new FileDetails(
                        rs.getString("path"),
                        rs.getLong("size"),
                        rs.getString("hash"),
                        rs.getLong("last_modified")
                );
                results.add(file);
            }
        }
        return results;
    }

    /**
     * Gets duplicate groups sorted by size, with optional limit.
     */
    public List<List<FileDetails>> getDuplicateGroupsBySize(int limit) throws SQLException {
        String sql = "SELECT hash, COUNT(*) as count, SUM(size) as total_size " +
                "FROM " + TABLE_FILES + " " +
                "WHERE hash IS NOT NULL " +
                "GROUP BY hash " +
                "HAVING COUNT(*) > 1 " +
                "ORDER BY total_size DESC";
        if (limit > 0) {
            sql += " LIMIT " + limit;
        }

        List<List<FileDetails>> groups = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String hash = rs.getString("hash");
                List<FileDetails> group = getFileGroupByHash(hash);
                if (group.size() > 1) {
                    groups.add(group);
                }
            }
        }
        return groups;
    }

    /**
     * Gets files over a minimum size.
     */
    public List<FileDetails> getFilesOverSize(long minSize) throws SQLException {
        String sql = "SELECT id, path, size, hash, last_modified FROM " + TABLE_FILES + " WHERE size >= ? ORDER BY size DESC";
        List<FileDetails> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, minSize);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FileDetails file = new FileDetails(
                            rs.getString("path"),
                            rs.getLong("size"),
                            rs.getString("hash"),
                            rs.getLong("last_modified")
                    );
                    results.add(file);
                }
            }
        }
        return results;
    }

    /**
     * Deletes a file record by path.
     */
    public boolean deleteFileByPath(String path) throws SQLException {
        String deleteDuplicates = "DELETE FROM " + TABLE_DUPLICATES + " WHERE file_id IN (SELECT id FROM " + TABLE_FILES + " WHERE path = ?) OR duplicate_id IN (SELECT id FROM " + TABLE_FILES + " WHERE path = ?)";
        String deleteFile = "DELETE FROM " + TABLE_FILES + " WHERE path = ?";
        
        try {
            connection.setAutoCommit(false);
            
            try (PreparedStatement deleteDupStmt = connection.prepareStatement(deleteDuplicates)) {
                deleteDupStmt.setString(1, path);
                deleteDupStmt.setString(2, path);
                deleteDupStmt.executeUpdate();
            }
            
            try (PreparedStatement deleteFileStmt = connection.prepareStatement(deleteFile)) {
                deleteFileStmt.setString(1, path);
                int rowsAffected = deleteFileStmt.executeUpdate();
                
                connection.commit();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Deletes a file from both database and filesystem.
     * Returns true if deletion was successful.
     */
    public boolean deleteFile(String path, boolean force) throws SQLException, IOException {
        // Check if file exists in filesystem
        File file = new File(path);
        
        if (!file.exists()) {
            System.out.println("File does not exist: " + path);
            return false;
        }
        
        // Delete from database first
        deleteFileByPath(path);
        
        // Delete from filesystem
        if (force || confirmDelete(path, file.isDirectory())) {
            if (file.isDirectory()) {
                deleteDirectory(file);
            }
            file.delete();
            System.out.println("Deleted: " + path);
            return true;
        } else {
            System.out.println("Deletion cancelled for: " + path);
            // Re-insert into database since we're not deleting the file
            return false;
        }
    }

    /**
     * Deletes a directory recursively.
     */
    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
    }

    /**
     * Prompts user for confirmation before deleting a file/directory.
     */
    private boolean confirmDelete(String path, boolean isDirectory) {
        System.out.println((isDirectory ? "Directory" : "File") + " to delete: " + path);
        System.out.print("Are you sure you want to delete this? (y/N): ");
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("y") || response.equals("yes");
    }

    /**
     * Gets a list of duplicate groups, each group containing files with the same hash.
     */
    public List<List<FileDetails>> getDuplicateGroups() throws SQLException {
        String sql = "SELECT hash, COUNT(*) as count, SUM(size) as total_size " +
                "FROM " + TABLE_FILES + " " +
                "WHERE hash IS NOT NULL " +
                "GROUP BY hash " +
                "HAVING COUNT(*) > 1 " +
                "ORDER BY total_size DESC";

        List<List<FileDetails>> groups = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String hash = rs.getString("hash");
                List<FileDetails> group = getFileGroupByHash(hash);
                if (group.size() > 1) {
                    groups.add(group);
                }
            }
        }
        return groups;
    }

    List<FileDetails> getFileGroupByHash(String hash) throws SQLException {
        String sql = "SELECT id, path, size, hash, last_modified FROM " + TABLE_FILES + " WHERE hash = ? ORDER BY size DESC";
        List<FileDetails> files = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, hash);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FileDetails file = new FileDetails(
                            rs.getString("path"),
                            rs.getLong("size"),
                            rs.getString("hash"),
                            rs.getLong("last_modified")
                    );
                    files.add(file);
                }
            }
        }
        return files;
    }

    /**
     * Deletes all records from the database (useful for re-runs).
     */
    public void clearDatabase() throws SQLException {
        String deleteDuplicates = "DELETE FROM " + TABLE_DUPLICATES;
        String deleteFiles = "DELETE FROM " + TABLE_FILES;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(deleteDuplicates);
            stmt.execute(deleteFiles);
        }
    }

    /**
     * Gets the total number of files in the database.
     */
    public int getFileCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE_FILES;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Closes the database connection.
     */
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}