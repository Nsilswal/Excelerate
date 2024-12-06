package com.excelerate;

import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVParser;

public class DatabaseManager {
    private Connection connection;
    private static final String DB_URL = "jdbc:sqlite:csvdata.db";
    private ProgressListener progressListener;

    public DatabaseManager() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            // Enable foreign keys and set pragmas for better performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
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

    public void initializeCSVFile(File file) throws SQLException {
        // Drop existing table if it exists
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS current_csv");
        }

        // Read header row to determine columns
        try (CSVReader reader = createCSVReader(file)) {
            String[] headers = reader.readNext();
            if (headers == null) {
                throw new SQLException("CSV file is empty");
            }

            // Create table with dynamic columns
            StringBuilder createTableSQL = new StringBuilder();
            createTableSQL.append("CREATE TABLE current_csv (id INTEGER PRIMARY KEY AUTOINCREMENT");
            for (int i = 0; i < headers.length; i++) {
                String columnName = sanitizeColumnName(headers[i]);
                createTableSQL.append(", ").append('"').append(columnName).append('"').append(" TEXT");
            }
            createTableSQL.append(")");

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL.toString());
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
        try (CSVReader reader = createCSVReader(file)) {
            String[] headers = reader.readNext();
            if (headers == null) return;

            // Count total lines for progress tracking
            long totalLines = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                while (br.readLine() != null) totalLines++;
            }
            totalLines--; // Subtract header row

            // Set column names in the model
            for (String header : headers) {
                model.addColumn(header);
            }

            // Prepare the insert statement
            StringBuilder insertSQL = new StringBuilder();
            insertSQL.append("INSERT INTO current_csv (");
            for (int i = 0; i < headers.length; i++) {
                String columnName = sanitizeColumnName(headers[i]);
                insertSQL.append('"').append(columnName).append('"');
                if (i < headers.length - 1) insertSQL.append(", ");
            }
            insertSQL.append(") VALUES (");
            for (int i = 0; i < headers.length; i++) {
                insertSQL.append("?");
                if (i < headers.length - 1) insertSQL.append(", ");
            }
            insertSQL.append(")");

            connection.setAutoCommit(false);
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL.toString())) {
                String[] nextLine;
                long currentLine = 0;
                int batchSize = 0;
                final int BATCH_COMMIT_SIZE = 1000;

                while ((nextLine = reader.readNext()) != null) {
                    // Handle rows with fewer columns than headers
                    for (int i = 0; i < headers.length; i++) {
                        if (i < nextLine.length) {
                            String value = nextLine[i];
                            value = value.replace("\r", " ").replace("\n", " ").trim();
                            pstmt.setString(i + 1, value);
                        } else {
                            pstmt.setString(i + 1, "");
                        }
                    }
                    pstmt.addBatch();
                    batchSize++;
                    currentLine++;

                    // Execute batch every BATCH_COMMIT_SIZE rows
                    if (batchSize >= BATCH_COMMIT_SIZE) {
                        pstmt.executeBatch();
                        connection.commit();
                        batchSize = 0;
                    }

                    // Update progress
                    if (progressListener != null) {
                        int percentage = (int) ((currentLine * 100) / totalLines);
                        progressListener.onProgressUpdate(percentage, 
                            String.format("Loading row %d of %d...", currentLine, totalLines));
                    }
                }

                // Execute remaining batch
                if (batchSize > 0) {
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