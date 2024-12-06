package com.excelerate;

import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVParser;

public class DatabaseManager {
    private Connection connection;
    private static final String DB_URL = "jdbc:sqlite:csvdata.db";
    private ProgressListener progressListener;
    private static final int DEFAULT_BATCH_SIZE = 5000;
    private static final int LARGE_FILE_THRESHOLD = 1000000; // 1 million lines

    public DatabaseManager() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            // Enhanced performance settings
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
                stmt.execute("PRAGMA cache_size = -2000"); // Use 2MB of cache
                stmt.execute("PRAGMA temp_store = MEMORY");
                stmt.execute("PRAGMA mmap_size = 30000000000"); // 30GB memory map
                stmt.execute("PRAGMA page_size = 4096");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    private CSVReader createCSVReader(File file) throws IOException {
        ICSVParser parser = new CSVParserBuilder()
            .withSeparator(',')
            .withQuoteChar('"')
            .withEscapeChar('\\')
            .withStrictQuotes(false)        // Don't require strict quotes
            .withIgnoreQuotations(true)     // Ignore problematic quotes
            .build();

        return new CSVReaderBuilder(new FileReader(file))
            .withCSVParser(parser)
            .withKeepCarriageReturn(true)   // Keep line breaks in fields
            .withVerifyReader(false)        // Don't verify reader
            .build();
    }

    private long countLines(File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long fileSize = channel.size();
            
            // For small files, do a quick count
            if (fileSize < 10_000_000) { // 10MB
                try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    return reader.lines().count();
                }
            }
            
            // For large files, estimate based on sampling
            long sampleSize = Math.min(fileSize, 1_000_000); // 1MB sample
            byte[] sample = new byte[(int) sampleSize];
            channel.read(ByteBuffer.wrap(sample));
            
            int lines = 0;
            for (byte b : sample) {
                if (b == '\n') lines++;
            }
            
            // Extrapolate to full file size
            return (lines * fileSize / sampleSize);
        }
    }

    public void initializeCSVFile(File file) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS current_csv");
            
            // Read header row to determine columns
            try (CSVReader reader = createCSVReader(file)) {
                String[] headers = reader.readNext();
                if (headers == null) {
                    throw new SQLException("CSV file is empty");
                }

                // Optimized table creation
                StringBuilder createTableSQL = new StringBuilder(1024);
                createTableSQL.append("CREATE TABLE current_csv (id INTEGER PRIMARY KEY AUTOINCREMENT");
                for (String header : headers) {
                    createTableSQL.append(",\"").append(sanitizeColumnName(header)).append("\" TEXT");
                }
                createTableSQL.append(")");

                stmt.execute(createTableSQL.toString());
                
                // Create indexes after data load for better performance
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_csv_id ON current_csv(id)");
            }
        } catch (IOException | CsvValidationException e) {
            throw new SQLException("Error reading CSV file: " + e.getMessage());
        }
    }

    private String sanitizeColumnName(String columnName) {
        // Replace any non-alphanumeric characters with underscore
        String sanitized = columnName.replaceAll("[^a-zA-Z0-9]", "_");
        
        // If the column name starts with a number, prepend with 'col_'
        if (sanitized.matches("^[0-9].*")) {
            sanitized = "col_" + sanitized;
        }
        
        // Ensure the column name is unique by appending a number if necessary
        return sanitized;
    }

    public void loadCSVFile(File file, DefaultTableModel model) throws SQLException {
        try {
            String[] headers = null;
            try (CSVReader reader = createCSVReader(file)) {
                headers = reader.readNext();
                if (headers == null) return;
            }

            // Count lines efficiently
            long totalLines = countLines(file) - 1; // Subtract header
            
            // Determine optimal batch size based on file size
            int batchSize = totalLines > LARGE_FILE_THRESHOLD ? DEFAULT_BATCH_SIZE * 2 : DEFAULT_BATCH_SIZE;

            // Set column names in the model
            for (String header : headers) {
                model.addColumn(header);
            }

            // Prepare optimized insert statement
            StringBuilder insertSQL = new StringBuilder(1024);
            insertSQL.append("INSERT INTO current_csv (");
            for (int i = 0; i < headers.length; i++) {
                if (i > 0) insertSQL.append(',');
                insertSQL.append('"').append(sanitizeColumnName(headers[i])).append('"');
            }
            insertSQL.append(") VALUES (").append("?,".repeat(headers.length - 1)).append("?)");

            connection.setAutoCommit(false);
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL.toString());
                 CSVReader reader = createCSVReader(file)) {
                
                reader.readNext(); // Skip header
                String[] nextLine;
                long currentLine = 0;
                int batchCount = 0;

                while ((nextLine = reader.readNext()) != null) {
                    for (int i = 0; i < headers.length; i++) {
                        pstmt.setString(i + 1, i < nextLine.length ? 
                            nextLine[i].replace("\r", " ").replace("\n", " ").trim() : "");
                    }
                    pstmt.addBatch();
                    batchCount++;
                    currentLine++;

                    if (batchCount >= batchSize) {
                        pstmt.executeBatch();
                        connection.commit();
                        batchCount = 0;
                        
                        if (progressListener != null) {
                            int percentage = (int) ((currentLine * 100) / totalLines);
                            progressListener.onProgressUpdate(percentage,
                                String.format("Loading row %d of %d...", currentLine, totalLines));
                        }
                    }
                }

                if (batchCount > 0) {
                    pstmt.executeBatch();
                    connection.commit();
                }

                if (progressListener != null) {
                    progressListener.onProgressUpdate(100, "Loading complete!");
                }

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (IOException | CsvValidationException e) {
            throw new SQLException("Error reading CSV file: " + e.getMessage());
        }
    }

    public void appendCSVFilePage(DefaultTableModel model, int offset, int limit) throws SQLException {
        String query = "SELECT * FROM current_csv LIMIT ? OFFSET ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);
            
            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            int modelColumnCount = model.getColumnCount() - 1; // Subtract 1 for the row number column

            while (rs.next()) {
                Object[] row = new Object[modelColumnCount];
                // Start from 2 to skip the id column, ensure we don't exceed array bounds
                for (int i = 2; i <= columnCount && (i-2) < modelColumnCount; i++) {
                    row[i - 2] = rs.getString(i);
                }
                model.addRow(row);
            }
        }
    }

    public int getTotalRowCount(File file) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM current_csv")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
} 