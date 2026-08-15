package com.vijaxx.library.ui;

import com.vijaxx.library.model.Book;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Modal add/edit dialog for a {@link Book}. Pure Swing, no DB access. */
public class BookDialog extends JDialog {

    private final JTextField isbnField = new JTextField(18);
    private final JTextField titleField = new JTextField(18);
    private final JTextField authorField = new JTextField(18);
    private final JTextField categoryField = new JTextField(18);
    private final JSpinner totalCopiesSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 999, 1));

    private Book result;

    public BookDialog(Frame owner, String title, Book existing) {
        super(owner, title, true);
        buildForm(existing);
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildForm(Book existing) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        addRow(form, gc, 0, "ISBN:", isbnField);
        addRow(form, gc, 1, "Title:", titleField);
        addRow(form, gc, 2, "Author:", authorField);
        addRow(form, gc, 3, "Category:", categoryField);
        addRow(form, gc, 4, "Total copies:", totalCopiesSpinner);

        if (existing != null) {
            isbnField.setText(existing.getIsbn());
            titleField.setText(existing.getTitle());
            authorField.setText(existing.getAuthor());
            categoryField.setText(existing.getCategory());
            totalCopiesSpinner.setValue(existing.getTotalCopies());
        }

        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(e -> onSave(existing));
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(save);

        JPanel buttons = new JPanel();
        buttons.add(save);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void addRow(JPanel form, GridBagConstraints gc, int row, String label, java.awt.Component field) {
        gc.gridx = 0;
        gc.gridy = row;
        form.add(new JLabel(label), gc);
        gc.gridx = 1;
        form.add(field, gc);
    }

    private void onSave(Book existing) {
        String isbn = isbnField.getText().trim();
        String title = titleField.getText().trim();
        String author = authorField.getText().trim();
        String category = categoryField.getText().trim();
        int totalCopies = (Integer) totalCopiesSpinner.getValue();

        if (isbn.isEmpty() || title.isEmpty() || author.isEmpty() || category.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All fields are required.", "Missing data",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (existing != null) {
            existing.setIsbn(isbn);
            existing.setTitle(title);
            existing.setAuthor(author);
            existing.setCategory(category);
            existing.setTotalCopies(totalCopies);
            result = existing;
        } else {
            result = Book.of(isbn, title, author, category, totalCopies);
        }
        dispose();
    }

    /** The book to save, or null if the dialog was cancelled. */
    public Book getResult() {
        return result;
    }
}
