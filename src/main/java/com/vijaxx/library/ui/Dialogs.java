package com.vijaxx.library.ui;

import com.vijaxx.library.service.LibraryException;

import javax.swing.JOptionPane;
import java.awt.Component;

/** Small helpers for the message boxes the panels raise. All called on the EDT. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void info(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Library", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void warn(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Library", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component owner, String message) {
        return JOptionPane.showConfirmDialog(owner, message, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Business-rule failures get their own message; anything else is reported as
     * an unexpected error and printed for diagnosis.
     */
    public static void error(Component owner, Throwable t) {
        String message;
        if (t instanceof LibraryException) {
            message = t.getMessage();
        } else {
            message = "Unexpected error: " + t;
            t.printStackTrace();
        }
        JOptionPane.showMessageDialog(owner, message, "Operation failed", JOptionPane.ERROR_MESSAGE);
    }
}
