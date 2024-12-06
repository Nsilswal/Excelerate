package com.excelerate;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvValidationException;

import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.sql.*;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:csvdata.db";
    private Connection connection;
    private File currentFile;

    public DatabaseManager() {
        initializeConnection();
    }

    private void initializeConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
                // Enable foreign keys and set other SQLite pragmas
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                    stmt.execute("PRAGMA journal_mode = WAL");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Connection getConnection() throws SQLException {
        initializeConnection();
        return connection;
    }

    // Add this method to properly close the connection when the application exits
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void initializeCSVFile(File file) {
        this.currentFile = file;
    }

    public int getTotalRowCount(File file) throws Exception {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.readLine(); // Skip header
            while (reader.readLine() != null) count++;
        }
        return count;
    }

    public void loadCSVFilePage(DefaultTableModel model, int offset, int limit) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
            String[] headers = reader.readLine().split(",");
            model.setColumnIdentifiers(headers);
            
            // Skip to offset
            for (int i = 0; i < offset; i++) reader.readLine();
            
            // Read requested page
            model.setRowCount(0);
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < limit) {
                model.addRow(line.split(","));
                count++;
            }
        }
    }

    public void loadCSVFile(File file, DefaultTableModel model) throws Exception {
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            // Read header
            String[] header = reader.readNext();
            if (header == null) {
                throw new Exception("CSV file is empty");
            }
            
            // Create the current_csv table
            dropTableIfExists("current_csv");
            createTable("current_csv", header);
            
            // Prepare the insert statement
            StringBuilder insertSQL = new StringBuilder();
            insertSQL.append("INSERT INTO current_csv (");
            for (int i = 0; i < header.length; i++) {
                insertSQL.append(header[i].replaceAll("[^a-zA-Z0-9]", "_"));
                if (i < header.length - 1) insertSQL.append(", ");
            }
            insertSQL.append(") VALUES (");
            for (int i = 0; i < header.length; i++) {
                insertSQL.append("?");
                if (i < header.length - 1) insertSQL.append(", ");
            }
            insertSQL.append(")");

            // Insert data in batches
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL.toString())) {
                connection.setAutoCommit(false);
                String[] nextLine;
                int batchSize = 1000;
                int count = 0;
                
                while ((nextLine = reader.readNext()) != null) {
                    for (int i = 0; i < nextLine.length; i++) {
                        pstmt.setString(i + 1, nextLine[i]);
                    }
                    pstmt.addBatch();
                    
                    if (++count % batchSize == 0) {
                        pstmt.executeBatch();
                        connection.commit();
                    }
                }
                
                // Execute any remaining records
                pstmt.executeBatch();
                connection.commit();
                connection.setAutoCommit(true);
            }
            
            // Update the table model with first page
            model.setColumnCount(0);
            model.setRowCount(0);
            for (String columnName : header) {
                model.addColumn(columnName);
            }
            
        } catch (IOException e) {
            throw new Exception("Error reading CSV file: " + e.getMessage());
        } catch (CsvValidationException e) {
            throw new Exception("Invalid CSV format: " + e.getMessage());
        } catch (SQLException e) {
            connection.rollback();
            connection.setAutoCommit(true);
            throw new Exception("Database error: " + e.getMessage());
        }
    }

    private void dropTableIfExists(String tableName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    private void createTable(String tableName, String[] headers) throws SQLException {
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append("CREATE TABLE ").append(tableName).append(" (");
        
        for (int i = 0; i < headers.length; i++) {
            createTableSQL.append(headers[i].replaceAll("[^a-zA-Z0-9]", "_"))
                         .append(" TEXT");
            if (i < headers.length - 1) {
                createTableSQL.append(", ");
            }
        }
        createTableSQL.append(")");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL.toString());
        }
    }

    private void insertData(String tableName, String[] headers, List<String[]> data) throws SQLException {
        StringBuilder insertSQL = new StringBuilder();
        insertSQL.append("INSERT INTO ").append(tableName).append(" (");
        
        // Add column names
        for (int i = 0; i < headers.length; i++) {
            insertSQL.append(headers[i].replaceAll("[^a-zA-Z0-9]", "_"));
            if (i < headers.length - 1) {
                insertSQL.append(", ");
            }
        }
        
        insertSQL.append(") VALUES (");
        for (int i = 0; i < headers.length; i++) {
            insertSQL.append("?");
            if (i < headers.length - 1) {
                insertSQL.append(", ");
            }
        }
        insertSQL.append(")");

        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL.toString())) {
            for (String[] row : data) {
                for (int i = 0; i < row.length; i++) {
                    pstmt.setString(i + 1, row[i]);
                }
                pstmt.executeUpdate();
            }
        }
    }

    private void updateTableModel(DefaultTableModel tableModel, String[] headers, List<String[]> data) {
        // Clear existing data
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);

        // Set columns
        for (String header : headers) {
            tableModel.addColumn(header);
        }

        // Add rows
        for (String[] row : data) {
            tableModel.addRow(row);
        }
    }

    public void appendCSVFilePage(DefaultTableModel model, int offset, int limit) throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM current_csv LIMIT ? OFFSET ?")) {
            
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            
            ResultSet rs = stmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            
            // If this is the first page, set up the columns
            if (model.getColumnCount() <= 1) { // 1 because of the row number column
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    model.addColumn(metaData.getColumnName(i));
                }
            }
            
            // Add new rows to the existing model
            while (rs.next()) {
                Object[] row = new Object[metaData.getColumnCount()];
                for (int i = 0; i < row.length; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                model.addRow(row);
            }
        }
    }
} 