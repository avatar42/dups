# File Duplicate Detector

A Java application that walks a folder tree, stores file details in a SQLite database, and identifies duplicate files based on content hash.

## Features

- Scans directory trees recursively
- Stores file details (path, size, hash, last modified) in SQLite database
- Detects duplicates based on MD5 hash comparison
- Handles re-runs by updating existing file records
- Stores duplicate relationships in a separate table
- Displays duplicates sorted by size (largest first)
- Human-readable file size formatting

## Building

```bash
mvn clean package
```

## Usage

### Command Line

```bash
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar [directory]
```

### Arguments

- **directory** (optional) - The directory to scan for duplicate files. If not specified, the current directory is used.

### Options

- **-h, --help** - Show help message and usage information

### Examples

```bash
# Scan current directory
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar

# Scan specific directory
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar /path/to/directory

# Show help
java -jar target/file-dup-detector-1.0-SNAPSHOT.jar --help
```

## Database

The application creates a SQLite database file `file_database.db` in the scanned directory. This database persists between runs, allowing for incremental updates when re-running the scanner.

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