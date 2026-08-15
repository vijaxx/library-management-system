package com.vijaxx.library.ui;

import com.vijaxx.library.model.Book;
import com.vijaxx.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;

/**
 * Book inventory tab: search, list, add, edit, delete. Every call into
 * {@link LibraryService} goes through {@link BackgroundTask#run}, so the
 * table listing, adding, editing and deleting all happen off the EDT while
 * only the resulting Swing updates run on it.
 */
public class BooksPanel extends JPanel {

    private final LibraryService service;
    private final ObjectTableModel<Book> tableModel;
    private final JTable table;
    private final JTextField searchField = new JTextField(20);

    public BooksPanel(LibraryService service) {
        super(new BorderLayout(8, 8));
        this.service = service;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tableModel = new ObjectTableModel<>(List.of(
                new ObjectTableModel.Column<>("ISBN", String.class, Book::getIsbn),
                new ObjectTableModel.Column<>("Title", String.class, Book::getTitle),
                new ObjectTableModel.Column<>("Author", String.class, Book::getAuthor),
                new ObjectTableModel.Column<>("Category", String.class, Book::getCategory),
                new ObjectTableModel.Column<>("Total", Integer.class, Book::getTotalCopies),
                new ObjectTableModel.Column<>("Available", Integer.class, Book::getAvailableCopies)
        ));
        table = new JTable(tableModel);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        top.add(new javax.swing.JLabel("Search:"));
        top.add(searchField);
        top.add(searchButton);
        top.add(clearButton);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add Book");
        JButton editButton = new JButton("Edit Selected");
        JButton deleteButton = new JButton("Delete Selected");
        JButton refreshButton = new JButton("Refresh");
        bottom.add(addButton);
        bottom.add(editButton);
        bottom.add(deleteButton);
        bottom.add(refreshButton);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> reload(searchField.getText()));
        clearButton.addActionListener(e -> { searchField.setText(""); reload(null); });
        refreshButton.addActionListener(e -> reload(searchField.getText()));
        addButton.addActionListener(e -> openAddDialog());
        editButton.addActionListener(e -> openEditDialog());
        deleteButton.addActionListener(e -> deleteSelected());

        reload(null);
    }

    /** Reloads the table off the EDT and applies the result on it. */
    public void reload(String term) {
        BackgroundTask.run(this,
                () -> term == null || term.isBlank() ? service.listBooks() : service.searchBooks(term),
                tableModel::setRows);
    }

    private Book selectedBook() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(row);
        return tableModel.getRowAt(modelRow);
    }

    private Frame ownerFrame() {
        return (Frame) SwingUtilities.getWindowAncestor(this);
    }

    private void openAddDialog() {
        BookDialog dialog = new BookDialog(ownerFrame(), "Add Book", null);
        dialog.setVisible(true);
        Book toSave = dialog.getResult();
        if (toSave == null) {
            return;
        }
        BackgroundTask.run(this, () -> service.addBook(toSave), saved -> reload(searchField.getText()));
    }

    private void openEditDialog() {
        Book selected = selectedBook();
        if (selected == null) {
            Dialogs.warn(this, "Select a book to edit first.");
            return;
        }
        BookDialog dialog = new BookDialog(ownerFrame(), "Edit Book", selected);
        dialog.setVisible(true);
        Book toSave = dialog.getResult();
        if (toSave == null) {
            return;
        }
        BackgroundTask.run(this, () -> service.updateBook(toSave), saved -> reload(searchField.getText()));
    }

    private void deleteSelected() {
        Book selected = selectedBook();
        if (selected == null) {
            Dialogs.warn(this, "Select a book to delete first.");
            return;
        }
        if (!Dialogs.confirm(this, "Delete \"" + selected.getTitle() + "\"?")) {
            return;
        }
        BackgroundTask.run(this, () -> { service.deleteBook(selected.getId()); return null; },
                v -> reload(searchField.getText()));
    }
}
