package com.excelerate;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.sql.*;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:csvdata.db";
    private Connection connection;
    private File currentFile;

    public DatabaseManager() {
        try {
            connection = DriverManager.getConnection(DB_URL);
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

    public void loadCSVFile(File csvFile, DefaultTableModel tableModel) throws IOException, CsvException, SQLException {
        // Read CSV file
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) {
                throw new IOException("CSV file is empty");
            }

            // Get headers
            String[] headers = allRows.get(0);
            
            // Create table name from file name (sanitized)
            String tableName = csvFile.getName().replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
            
            // Drop existing table if exists
            dropTableIfExists(tableName);
            
            // Create new table
            createTable(tableName, headers);
            
            // Insert data
            insertData(tableName, headers, allRows.subList(1, allRows.size()));
            
            // Update table model
            updateTableModel(tableModel, headers, allRows.subList(1, allRows.size()));
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
} 