package com.excelerate;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.sql.SQLException;
import javax.swing.SwingWorker;
import java.util.List;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

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
    private JDialog progressDialog;
    private JProgressBar progressBar;
    private static final String LOGO_PATH = "/images/logo.png";
    private static final Color BACKGROUND_COLOR = new Color(250, 250, 250);
    private static final Color ACCENT_COLOR = new Color(66, 133, 244); // Google Blue
    private static final Color HEADER_COLOR = new Color(245, 245, 245);
    private static final int BORDER_RADIUS = 8;

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

        // Set modern look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create and setup the main window
        frame = new JFrame("Excelerate");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1024, 768);
        frame.setLocationRelativeTo(null);
        frame.setBackground(BACKGROUND_COLOR);

        // Set application icon
        setApplicationIcon();

        // Create modern menu bar
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(BACKGROUND_COLOR);
        menuBar.setBorder(new EmptyBorder(5, 5, 5, 5));

        JMenu fileMenu = new JMenu("File");
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JMenuItem openItem = new JMenuItem("Open CSV");
        openItem.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        openItem.addActionListener(e -> openCSVFile());
        fileMenu.add(openItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        // Create main panel with modern styling
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Create welcome panel with modern styling
        JPanel welcomePanel = new JPanel(new BorderLayout());
        welcomePanel.setBackground(BACKGROUND_COLOR);
        welcomePanel.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel welcomeLabel = new JLabel("Welcome to Excelerate", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        welcomeLabel.setForeground(ACCENT_COLOR);

        JLabel subtitleLabel = new JLabel("Click File → Open CSV to get started", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitleLabel.setForeground(Color.GRAY);

        welcomePanel.add(welcomeLabel, BorderLayout.CENTER);
        welcomePanel.add(subtitleLabel, BorderLayout.SOUTH);
        mainPanel.add(welcomePanel, BorderLayout.NORTH);

        // Style the table
        tableModel = new NumberedTableModel();
        dataTable = new JTable(tableModel);
        dataTable.setShowGrid(false);
        dataTable.setIntercellSpacing(new Dimension(0, 0));
        dataTable.setRowHeight(28);
        dataTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dataTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        dataTable.getTableHeader().setBackground(HEADER_COLOR);
        dataTable.getTableHeader().setForeground(Color.DARK_GRAY);
        dataTable.setSelectionBackground(new Color(232, 240, 254)); // Light blue selection
        dataTable.setSelectionForeground(Color.BLACK);
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Style the row number column
        dataTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        dataTable.getColumnModel().getColumn(0).setResizable(false);
        dataTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            {
                setHorizontalAlignment(SwingConstants.CENTER);
                setBackground(HEADER_COLOR);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(new MatteBorder(0, 0, 0, 1, new Color(218, 220, 224)));
                return this;
            }
        });

        // Create modern scroll pane
        scrollPane = new JScrollPane(dataTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(218, 220, 224)));
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Add this line
        setupInfiniteScroll();

        // Create modern status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(BACKGROUND_COLOR);
        statusBar.setBorder(new EmptyBorder(10, 0, 0, 0));

        JLabel statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusBar.add(statusLabel, BorderLayout.WEST);

        rowCountLabel = new JLabel("Rows: 0");
        rowCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
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

    private void setupInfiniteScroll() {
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            JScrollBar scrollBar = (JScrollBar) e.getAdjustable();
            int extent = scrollBar.getModel().getExtent();
            int maximum = scrollBar.getModel().getMaximum();
            int value = scrollBar.getValue();
            
            // If we're within 20% of the bottom, load more data
            if (value + extent >= maximum - (maximum * 0.2)) {
                loadNextPageIfAvailable();
            }
        });
    }

    private void loadNextPageIfAvailable() {
        int totalPages = (int) Math.ceil((double) totalRows / rowsPerPage);
        if (currentPage < totalPages && !isLoading) {
            isLoading = true;
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    try {
                        currentPage++;
                        int offset = (currentPage - 1) * rowsPerPage;
                        dbManager.appendCSVFilePage(tableModel, offset, rowsPerPage);
                    } catch (SQLException e) {
                        SwingUtilities.invokeLater(() -> {
                            showErrorDialog("Error loading next page", e);
                            currentPage--; // Revert page increment on error
                        });
                    }
                    return null;
                }

                @Override
                protected void done() {
                    isLoading = false;
                }
            };
            worker.execute();
        }
    }

    private void createProgressDialog() {
        progressDialog = new JDialog(frame, "Loading CSV File", true);
        progressDialog.setLayout(new BorderLayout());
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel statusLabel = new JLabel("Preparing to load file...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(statusLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(300, 5)); // Make it thinner
        progressBar.setForeground(ACCENT_COLOR);
        progressBar.setBackground(new Color(232, 240, 254));
        progressBar.setBorderPainted(false);
        progressBar.setStringPainted(false);

        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel);

        progressDialog.pack();
        progressDialog.setLocationRelativeTo(frame);

        dbManager.setProgressListener((percentage, message) -> {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(message);
                progressBar.setValue(percentage);
            });
        });
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

            // Create and show progress dialog
            createProgressDialog();

            // Use SwingWorker to load CSV in background
            SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
                @Override
                protected Void doInBackground() throws Exception {
                    try {
                        // Reset the table model before loading new file
                        tableModel = new NumberedTableModel();
                        dataTable.setModel(tableModel);

                        // Initialize database and create table first
                        dbManager.initializeCSVFile(selectedFile);

                        // Load the CSV data into the database
                        dbManager.loadCSVFile(selectedFile, tableModel);

                        // Get total rows after data is loaded
                        totalRows = dbManager.getTotalRowCount(selectedFile);
                        currentPage = 1;

                        // Clear existing data from the table model
                        tableModel.setRowCount(0);

                        // Load the first page
                        loadCurrentPage();
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> showErrorDialog("Error loading CSV file", e));
                    }
                    return null;
                }

                @Override
                protected void done() {
                    progressDialog.dispose();

                    // Update row count label
                    rowCountLabel.setText("Total Rows: " + String.format("%,d", totalRows));

                    // Auto-resize columns with minimum and maximum widths
                    for (int column = 1; column < dataTable.getColumnCount(); column++) {
                        int width = 100; // minimum width
                        for (int row = 0; row < Math.min(dataTable.getRowCount(), 100); row++) {
                            TableCellRenderer renderer = dataTable.getCellRenderer(row, column);
                            Component comp = dataTable.prepareRenderer(renderer, row, column);
                            width = Math.max(comp.getPreferredSize().width + 20, width);
                        }
                        // Set a maximum width to prevent extremely wide columns
                        width = Math.min(width, 300); // maximum width of 300 pixels
                        dataTable.getColumnModel().getColumn(column).setPreferredWidth(width);
                    }
                }
            };

            worker.execute();
            progressDialog.setVisible(true);
        }
    }

    private void setCursor(Cursor cursor) {
        frame.setCursor(cursor);
        if (dataTable != null) {
            dataTable.setCursor(cursor);
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

    private void loadCurrentPage() {
        try {
            int offset = (currentPage - 1) * rowsPerPage;
            dbManager.appendCSVFilePage(tableModel, offset, rowsPerPage);
        } catch (SQLException e) {
            showErrorDialog("Error loading page", e);
        }
    }

    private void setApplicationIcon() {
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource(LOGO_PATH));
            frame.setIconImage(icon.getImage());

            // For macOS dock icon - only attempt on macOS and newer Java versions
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("mac")) {
                try {
                    // Use reflection to check if Taskbar is available (Java 9+)
                    Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
                    if (taskbarClass != null) {
                        Object taskbar = taskbarClass.getMethod("getTaskbar").invoke(null);
                        taskbarClass.getMethod("setIconImage", Image.class)
                                .invoke(taskbar, icon.getImage());
                    }
                } catch (Exception e) {
                    // Silently fail for older Java versions or if Taskbar is not supported
                    System.err.println("Taskbar icon not supported on this platform");
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load application icon: " + e.getMessage());
        }
    }
}