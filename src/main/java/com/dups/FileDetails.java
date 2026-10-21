package com.dups;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents file details including path, size, and content hash.
 */
public class FileDetails {
    private final String path;
    private final long size;
    private final String hash;
    private final long lastModified;

    public FileDetails(String path, long size, String hash, long lastModified) {
        this.path = path;
        this.size = size;
        this.hash = hash;
        this.lastModified = lastModified;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getHash() {
        return hash;
    }

    public long getLastModified() {
        return lastModified;
    }

    /**
     * Generates MD5 hash for a file.
     */
    public static String generateHash(File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileDetails that = (FileDetails) o;
        return size == that.size && lastModified == that.lastModified && 
               Objects.equals(path, that.path) && Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, size, hash, lastModified);
    }

    @Override
    public String toString() {
        return "FileDetails{" +
                "path='" + path + '\'' +
                ", size=" + size +
                ", hash='" + hash + '\'' +
                ", lastModified=" + lastModified +
                '}';
    }
}