package com.vijaxx.library;

import com.vijaxx.library.db.Database;
import com.vijaxx.library.service.LibraryService;
import com.vijaxx.library.ui.MainWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Application entry point.
 *
 * <p>Startup order: open/initialise the embedded H2 database on the main
 * thread (fast local file I/O, done once, before any UI exists), build the
 * service layer around it, then hand off to the Event Dispatch Thread via
 * {@link SwingUtilities#invokeLater} to build and show the window. No Swing
 * component is created or touched before that hand-off.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Database database = Database.defaultFile().initialize();
        LibraryService service = new LibraryService(database);

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to the default cross-platform look and feel.
            }
            new MainWindow(service).setVisible(true);
        });
    }
}
