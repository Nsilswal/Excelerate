package com.excelerate;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Main {
    private JFrame frame;
    private JTable dataTable;
    private NumberedTableModel tableModel;
    private DatabaseManager dbManager;
    private JLabel rowCountLabel;
    private JLabel pageInfoLabel;
    private int currentPage = 1;
    private int rowsPerPage = 1000;
    private int totalRows = 0;
    private JButton prevButton;
    private JButton nextButton;
    private boolean isLoading = false;
    private JScrollPane scrollPane;

    // Custom table model that shows row numbers
    private static class NumberedTableModel extends DefaultTableModel {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false; // Make all cells non-editable
        }

        @Override
        public int getColumnCount() {
            return super.getColumnCount() + 1; // Add one column for row numbers
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0)
                return "#";
            return super.getColumnName(column - 1);
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (column == 0)
                return row + 1; // Row numbers start from 1
            return super.getValueAt(row, column - 1);
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0)
                return; // Prevent modification of row numbers
            super.setValueAt(value, row, column - 1);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column == 0)
                return Integer.class;
            return super.getColumnClass(column - 1);
        }
    }

    public Main() {
        // Initialize database manager
        dbManager = new DatabaseManager();

        // Create and setup the main window
        frame = new JFrame("CSV Viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1024, 768);
        frame.setLocationRelativeTo(null); // Center on screen

        // Create menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open CSV");

        openItem.addActionListener(e -> openCSVFile());
        fileMenu.add(openItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        // Create main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create welcome label
        JLabel welcomeLabel = new JLabel("Welcome to Excelerate! Click File → Open CSV to get started.",
                SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        mainPanel.add(welcomeLabel, BorderLayout.NORTH);

        // Create table with numbered rows
        tableModel = new NumberedTableModel();
        dataTable = new JTable(tableModel);
        dataTable.setFillsViewportHeight(true);
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Style the row number column
        dataTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        dataTable.getColumnModel().getColumn(0).setResizable(false);
        dataTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            {
                setHorizontalAlignment(SwingConstants.CENTER);
                setBackground(new Color(240, 240, 240));
                setFont(getFont().deriveFont(Font.BOLD));
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.GRAY));
                return this;
            }
        });

        // Add table to scroll pane
        scrollPane = new JScrollPane(dataTable);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Add scroll listener for infinite scrolling
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting() && !isLoading) {
                JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
                int extent = scrollBar.getModel().getExtent();
                int maximum = scrollBar.getModel().getMaximum();
                int value = scrollBar.getValue();

                // If we're near the bottom (within 20% of the viewport height)
                if (value + extent >= maximum - (extent * 0.2)) {
                    loadNextPageIfAvailable();
                }
            }
        });

        // Add scroll pane to main panel
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Create status bar with BorderLayout
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());

        // Left status label
        JLabel statusLabel = new JLabel("Ready");
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Right row count label
        rowCountLabel = new JLabel("Rows: 0");
        rowCountLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        statusBar.add(rowCountLabel, BorderLayout.EAST);

        mainPanel.add(statusBar, BorderLayout.SOUTH);

        frame.getContentPane().add(mainPanel);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                dbManager.closeConnection();
            }
        });
    }

    private void loadNextPageIfAvailable() {
        int totalPages = (int) Math.ceil((double) totalRows / rowsPerPage);
        if (currentPage < totalPages && !isLoading) {
            isLoading = true;
            try {
                currentPage++;
                int offset = (currentPage - 1) * rowsPerPage;
                dbManager.appendCSVFilePage(tableModel, offset, rowsPerPage);
            } catch (Exception e) {
                showErrorDialog("Error loading next page", e);
            } finally {
                isLoading = false;
            }
        }
    }

    private void openCSVFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
            }

            public String getDescription() {
                return "CSV Files (*.csv)";
            }
        });

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // Reset the table model before loading new file
                tableModel = new NumberedTableModel();
                dataTable.setModel(tableModel);

                // First load the CSV into database
                dbManager.loadCSVFile(selectedFile, tableModel);

                // Initialize pagination
                totalRows = dbManager.getTotalRowCount(selectedFile);
                currentPage = 1;
                dbManager.initializeCSVFile(selectedFile);
                loadCurrentPage();

                // Update row count label
                rowCountLabel.setText("Total Rows: " + totalRows);

                // Auto-resize columns
                for (int column = 1; column < dataTable.getColumnCount(); column++) {
                    int width = 100;
                    for (int row = 0; row < dataTable.getRowCount(); row++) {
                        TableCellRenderer renderer = dataTable.getCellRenderer(row, column);
                        Component comp = dataTable.prepareRenderer(renderer, row, column);
                        width = Math.max(comp.getPreferredSize().width + 20, width);
                    }
                    dataTable.getColumnModel().getColumn(column).setPreferredWidth(width);
                }
            } catch (Exception e) {
                showErrorDialog("Error loading CSV file", e);
            }
        }
    }

    private void showErrorDialog(String message, Exception e) {
        String detailedMessage = message + "\n\nDetails: " + e.getMessage() +
                "\n\nPossible causes:" +
                "\n- A quoted field is missing a closing quote" +
                "\n- A line break exists within a quoted field" +
                "\n- The CSV file is corrupted or malformed";

        JTextArea textArea = new JTextArea(detailedMessage);
        textArea.setEditable(false);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 200));

        JOptionPane.showMessageDialog(frame,
                scrollPane,
                "Error Loading CSV",
                JOptionPane.ERROR_MESSAGE);
    }

    public void show() {
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        // Set macOS-specific properties
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "CSV Viewer");

        // Set look and feel to system default
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            Main app = new Main();
            app.show();
        });
    }

    private void loadCurrentPage() throws Exception {
        int offset = (currentPage - 1) * rowsPerPage;
        dbManager.appendCSVFilePage(tableModel, offset, rowsPerPage);
    }
}