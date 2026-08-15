package com.vijaxx.library.ui;

import com.vijaxx.library.service.LibraryService;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;
import java.awt.Dimension;

/**
 * Application window: one tab each for Books, Members, Borrowing and Reports.
 *
 * <p>This class, and every panel it owns, is built entirely on the Event
 * Dispatch Thread — {@code Main} only ever constructs it inside
 * {@code SwingUtilities.invokeLater}. None of these constructors talk to the
 * database directly; each panel kicks off its own {@link BackgroundTask} once
 * built, so the window appears immediately and fills in as data arrives.
 */
public class MainWindow extends JFrame {

    private final BorrowingPanel borrowingPanel;
    private final ReportsPanel reportsPanel;

    public MainWindow(LibraryService service) {
        super("Library Management System");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1000, 650));

        BooksPanel booksPanel = new BooksPanel(service);
        MembersPanel membersPanel = new MembersPanel(service);
        borrowingPanel = new BorrowingPanel(service);
        reportsPanel = new ReportsPanel(service);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Books", booksPanel);
        tabs.addTab("Members", membersPanel);
        tabs.addTab("Borrowing", borrowingPanel);
        tabs.addTab("Reports", reportsPanel);

        // Refresh the tab a user is switching into, so issuing a book on the
        // Borrowing tab is reflected in Reports without a manual click.
        tabs.addChangeListener(e -> {
            int index = tabs.getSelectedIndex();
            String title = index >= 0 ? tabs.getTitleAt(index) : "";
            if ("Borrowing".equals(title)) {
                borrowingPanel.reloadAll();
            } else if ("Reports".equals(title)) {
                reportsPanel.reload();
            }
        });

        setContentPane(tabs);
        pack();
        setLocationRelativeTo(null);
    }
}
