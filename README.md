# File Duplicate Detector

A Java application that walks a folder tree, stores file details in a SQLite database, and identifies duplicate files based on content hash.

## Features

- Scans directory trees recursively
- Stores file details (path, size, hash, last modified) in SQLite database
- Detects duplicates based on MD5 hash comparison
- Handles re-runs by updating existing file records
- Stores duplicate relationships in a separate table
- Displays duplicates sorted by size (largest first)
- Optionally lists the largest duplicated files with `-s N`
- Human-readable file size formatting

## Building

Java 25 or later is required to build and run the application.

```bash
mvn clean package
```

## Usage

### Command Line

```bash
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar [-s N] [directory]
```

### Arguments

- **directory** (optional) - The directory to scan for duplicate files. If not specified, the current directory is used.
- **-s N** (optional) - Show the N largest files that have duplicates, ordered by size. N must be a positive integer. The option can appear before or after the directory.

### Options

- **-h, --help** - Show help message and usage information
- **-s N** - Show the N largest duplicated files

### Examples

```bash
# Scan current directory
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar

# Show the 10 largest files that have duplicates
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar -s 10

# Scan specific directory
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar /path/to/directory

# Show the 5 largest duplicated files in a specific directory
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar -s 5 /path/to/directory

# Show help
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar --help
```

## Database

The application creates a SQLite database file `file_database.db` in the scanned directory. The database persists between runs: each scan updates records for existing files, adds new files, removes entries for files that are no longer present, and refreshes duplicate relationships. The database file itself is excluded from scanning.

## Project Structure

```
src/
├── main/java/com/dups/
│   ├── Main.java              # Application entry point
│   ├── FileDetails.java       # File metadata model
│   ├── DatabaseManager.java   # SQLite database operations
│   └── FileScanner.java       # Directory scanning logic
└── test/java/com/dups/
    ├── MainTest.java
    ├── DatabaseManagerTest.java
    ├── FileDetailsTest.java
    └── FileScannerTest.java
```

## Dependencies

- SQLite JDBC Driver (org.xerial:sqlite-jdbc)
- JUnit 5 (org.junit.jupiter:junit-jupiter)

## License

MIT