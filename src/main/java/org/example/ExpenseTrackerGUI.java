package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;

public class ExpenseTrackerGUI {
    private JFrame frame;
    private JTextField amountField, timeSearchField;
    private JComboBox<String> categoryBox, filterCategoryBox;
    private JTable table;
    private DefaultTableModel tableModel;

    public ExpenseTrackerGUI() {
        frame = new JFrame("Expense Tracker");
        frame.setSize(750, 550);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // === TOP PANEL (Amount, Category, Filter, Search) ===
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JLabel amountLabel = new JLabel("Amount:");
        amountField = new JTextField(8);

        JLabel categoryLabel = new JLabel("Category:");
        categoryBox = new JComboBox<>(new String[]{"Food", "Transport", "Shopping", "Other"});

        JLabel filterLabel = new JLabel("Filter by Category:");
        filterCategoryBox = new JComboBox<>(new String[]{"All", "Food", "Transport", "Shopping", "Other"});

        JLabel timeSearchLabel = new JLabel("Search Time (HH:mm):");
        timeSearchField = new JTextField(5);
        JButton searchButton = new JButton("Search");

        searchButton.setBackground(new Color(52, 152, 219));
        searchButton.setForeground(Color.WHITE);
        searchButton.setFont(new Font("Arial", Font.BOLD, 14));
        searchButton.addActionListener(e -> loadExpenses(filterCategoryBox.getSelectedItem().toString(), timeSearchField.getText()));

        topPanel.add(amountLabel);
        topPanel.add(amountField);
        topPanel.add(categoryLabel);
        topPanel.add(categoryBox);
        topPanel.add(filterLabel);
        topPanel.add(filterCategoryBox);
        topPanel.add(timeSearchLabel);
        topPanel.add(timeSearchField);
        topPanel.add(searchButton);

        frame.add(topPanel, BorderLayout.NORTH);

        // === TABLE DISPLAY (Expense List) ===
        // Include ID column in the model (for deletion) but hide it from the view.
        tableModel = new DefaultTableModel(new String[]{"ID", "Amount", "Category", "Time"}, 0);
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Arial", Font.BOLD, 14));
        header.setBackground(new Color(44, 62, 80));
        header.setForeground(Color.WHITE);
        table.setFont(new Font("Arial", Font.PLAIN, 14));
        table.setRowHeight(25);
        table.setGridColor(Color.LIGHT_GRAY);

        // Remove the ID column from the view but keep it in the model.
        if(table.getColumnModel().getColumnCount() > 0) {
            table.removeColumn(table.getColumnModel().getColumn(0));
        }

        frame.add(new JScrollPane(table), BorderLayout.CENTER);

        // === BUTTON PANEL (Bottom) ===
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton addButton = new JButton("Add Expense");
        JButton removeButton = new JButton("Remove Expense");
        JButton exportCSVButton = new JButton("Export to CSV");
        JButton exportPDFButton = new JButton("Export to PDF");

        addButton.setBackground(new Color(46, 204, 113));
        addButton.setForeground(Color.WHITE);
        addButton.setFont(new Font("Arial", Font.BOLD, 14));

        removeButton.setBackground(new Color(231, 76, 60));
        removeButton.setForeground(Color.WHITE);
        removeButton.setFont(new Font("Arial", Font.BOLD, 14));

        exportCSVButton.setBackground(new Color(243, 156, 18));
        exportCSVButton.setForeground(Color.WHITE);
        exportCSVButton.setFont(new Font("Arial", Font.BOLD, 14));

        exportPDFButton.setBackground(new Color(192, 57, 43));
        exportPDFButton.setForeground(Color.WHITE);
        exportPDFButton.setFont(new Font("Arial", Font.BOLD, 14));

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(exportCSVButton);
        buttonPanel.add(exportPDFButton);

        frame.add(buttonPanel, BorderLayout.PAGE_END);

        // Button Actions
        addButton.addActionListener(e -> addExpense());
        removeButton.addActionListener(e -> removeExpense());
        exportCSVButton.addActionListener(e -> exportToCSV());
        exportPDFButton.addActionListener(e -> exportToPDF());

        frame.setVisible(true);
        loadExpenses("All", "");
    }

    private void addExpense() {
        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO expenses (amount, category, date, time) VALUES (?, ?, ?, ?)")) {

            double amount = Double.parseDouble(amountField.getText());
            String category = categoryBox.getSelectedItem().toString();
            String currentDate = java.time.LocalDate.now().toString();
            String currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

            stmt.setDouble(1, amount);
            stmt.setString(2, category);
            stmt.setString(3, currentDate);
            stmt.setString(4, currentTime);
            stmt.executeUpdate();

            amountField.setText("");
            loadExpenses("All", "");
        } catch (SQLException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private void removeExpense() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Select a row to delete!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Even though the ID column is hidden, it is still at index 0 in the model.
        int expenseId = (int) tableModel.getValueAt(selectedRow, 0);
        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM expenses WHERE id = ?")) {
            stmt.setInt(1, expenseId);
            stmt.executeUpdate();
            loadExpenses("All", "");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadExpenses(String categoryFilter, String timeFilter) {
        tableModel.setRowCount(0); // Clear table before loading new data
        String query = "SELECT id, amount, category, time FROM expenses WHERE 1=1";
        if (!categoryFilter.equals("All")) {
            query += " AND category = ?";
        }
        if (!timeFilter.isEmpty()) {
            query += " AND time LIKE ?";
        }
        try (Connection conn = DBManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            int paramIndex = 1;
            if (!categoryFilter.equals("All")) {
                stmt.setString(paramIndex++, categoryFilter);
            }
            if (!timeFilter.isEmpty()) {
                stmt.setString(paramIndex++, timeFilter + "%");
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                // Add row to model; ID is kept in the model but not displayed.
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getDouble("amount"),
                        rs.getString("category"),
                        rs.getString("time")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void exportToCSV() {
        try (FileWriter writer = new FileWriter("expenses.csv")) {
            // Export only Amount, Category, and Time (skip ID)
            writer.append("Amount,Category,Time\n");
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                writer.append(tableModel.getValueAt(i, 1).toString()).append(",");
                writer.append(tableModel.getValueAt(i, 2).toString()).append(",");
                writer.append(tableModel.getValueAt(i, 3).toString()).append("\n");
            }
            JOptionPane.showMessageDialog(frame, "Expenses exported to CSV successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void exportToPDF() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save as PDF");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF File", "pdf"));
        int userSelection = fileChooser.showSaveDialog(frame);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            try {
                String filePath = fileChooser.getSelectedFile() + ".pdf";
                PdfWriter writer = new PdfWriter(filePath);
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document document = new Document(pdfDoc);
                document.add(new Paragraph("Expense Tracker Report\n\n"));

                // Create a PDF table with 3 columns (skipping ID)
                Table pdfTable = new Table(3);
                pdfTable.addCell(new Cell().add(new Paragraph("Amount")));
                pdfTable.addCell(new Cell().add(new Paragraph("Category")));
                pdfTable.addCell(new Cell().add(new Paragraph("Time")));

                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    pdfTable.addCell(new Cell().add(new Paragraph(tableModel.getValueAt(i, 1).toString())));
                    pdfTable.addCell(new Cell().add(new Paragraph(tableModel.getValueAt(i, 2).toString())));
                    pdfTable.addCell(new Cell().add(new Paragraph(tableModel.getValueAt(i, 3).toString())));
                }
                document.add(pdfTable);
                document.close();
                JOptionPane.showMessageDialog(frame, "Expenses exported to PDF successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Error exporting PDF!", "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        DBManager.initializeDB();
        SwingUtilities.invokeLater(ExpenseTrackerGUI::new);
    }
}
