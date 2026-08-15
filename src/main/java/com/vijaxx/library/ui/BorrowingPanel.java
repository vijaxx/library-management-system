package com.vijaxx.library.ui;

import com.vijaxx.library.model.Book;
import com.vijaxx.library.model.Loan;
import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.Reports;
import com.vijaxx.library.service.LibraryService;
import com.vijaxx.library.service.ReturnReceipt;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Borrowing / returning tab. Wraps the transactional {@code issueBook}/
 * {@code returnBook} calls in {@link BackgroundTask}, so a click cannot block
 * the UI thread while the database round-trips, and the table refresh only
 * happens after the transaction has actually committed.
 */
public class BorrowingPanel extends JPanel {

    private final LibraryService service;

    private final JComboBox<Book> bookCombo = new JComboBox<>();
    private final JComboBox<Member> memberCombo = new JComboBox<>();

    private final ObjectTableModel<Reports.LoanRow> loanTableModel;
    private final JTable loanTable;

    public BorrowingPanel(LibraryService service) {
        super(new BorderLayout(8, 8));
        this.service = service;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel issuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        issuePanel.setBorder(BorderFactory.createTitledBorder("Issue a book"));
        issuePanel.add(new JLabel("Book:"));
        issuePanel.add(bookCombo);
        issuePanel.add(new JLabel("Member:"));
        issuePanel.add(memberCombo);
        JButton issueButton = new JButton("Issue");
        issuePanel.add(issueButton);
        bookCombo.setPrototypeDisplayValue(new Book(0, "", "A reasonably long title to size the box", "", "", 0, 0));
        memberCombo.setPrototypeDisplayValue(new Member(0, "A reasonably long member name", "", "", null, null, true));

        loanTableModel = new ObjectTableModel<>(List.of(
                new ObjectTableModel.Column<>("Loan #", Integer.class, Reports.LoanRow::loanId),
                new ObjectTableModel.Column<>("Book", String.class, Reports.LoanRow::title),
                new ObjectTableModel.Column<>("Member", String.class, Reports.LoanRow::memberName),
                new ObjectTableModel.Column<>("Issued", String.class, r -> String.valueOf(r.issueDate())),
                new ObjectTableModel.Column<>("Due", String.class, r -> String.valueOf(r.dueDate())),
                new ObjectTableModel.Column<>("Days Overdue", Long.class, Reports.LoanRow::daysOverdue),
                new ObjectTableModel.Column<>("Accrued Fine", String.class, r -> r.fine().toPlainString())
        ));
        loanTable = new JTable(loanTableModel);
        loanTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        loanTable.setAutoCreateRowSorter(true);

        JPanel returnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        returnPanel.setBorder(BorderFactory.createTitledBorder("Currently borrowed"));
        JButton returnButton = new JButton("Return Selected");
        JButton refreshButton = new JButton("Refresh");
        returnPanel.add(returnButton);
        returnPanel.add(refreshButton);

        JPanel top = new JPanel(new BorderLayout());
        top.add(issuePanel, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(new JScrollPane(loanTable), BorderLayout.CENTER);
        center.add(returnPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, center);
        split.setDividerLocation(70);
        split.setResizeWeight(0.0);
        add(split, BorderLayout.CENTER);

        issueButton.addActionListener(e -> issueSelected());
        returnButton.addActionListener(e -> returnSelected());
        refreshButton.addActionListener(e -> reloadAll());

        reloadAll();
    }

    /** Reloads the book/member pickers and the open-loans table, each off the EDT. */
    public void reloadAll() {
        BackgroundTask.run(this, service::listBooks, books -> {
            Book selected = (Book) bookCombo.getSelectedItem();
            bookCombo.removeAllItems();
            for (Book b : books) {
                bookCombo.addItem(b);
            }
            if (selected != null) {
                bookCombo.setSelectedItem(selected);
            }
        });
        BackgroundTask.run(this, service::listMembers, members -> {
            Member selected = (Member) memberCombo.getSelectedItem();
            memberCombo.removeAllItems();
            for (Member m : members) {
                memberCombo.addItem(m);
            }
            if (selected != null) {
                memberCombo.setSelectedItem(selected);
            }
        });
        BackgroundTask.run(this, service::currentlyBorrowed, loanTableModel::setRows);
    }

    private void issueSelected() {
        Book book = (Book) bookCombo.getSelectedItem();
        Member member = (Member) memberCombo.getSelectedItem();
        if (book == null || member == null) {
            Dialogs.warn(this, "Add at least one book and one member first.");
            return;
        }
        BackgroundTask.run(this,
                () -> service.issueBook(book.getId(), member.getId()),
                (Loan loan) -> {
                    Dialogs.info(this, "Issued \"" + book.getTitle() + "\" to " + member.getName()
                            + ". Due back " + loan.getDueDate() + ".");
                    reloadAll();
                });
    }

    private void returnSelected() {
        int row = loanTable.getSelectedRow();
        if (row < 0) {
            Dialogs.warn(this, "Select a borrowed book to return first.");
            return;
        }
        int modelRow = loanTable.convertRowIndexToModel(row);
        Reports.LoanRow selected = loanTableModel.getRowAt(modelRow);
        if (selected == null) {
            return;
        }
        BackgroundTask.run(this,
                () -> service.returnBook(selected.loanId()),
                (ReturnReceipt receipt) -> {
                    String message = receipt.wasLate()
                            ? "Returned \"" + receipt.bookTitle() + "\". " + receipt.daysLate()
                                    + " day(s) late — fine: " + receipt.fine()
                            : "Returned \"" + receipt.bookTitle() + "\" on time. No fine.";
                    Dialogs.info(this, message);
                    reloadAll();
                });
    }

    // Referenced by MainWindow to keep the picker Frame lookup consistent with other panels.
    java.awt.Frame ownerFrame() {
        return (java.awt.Frame) SwingUtilities.getWindowAncestor(this);
    }
}
