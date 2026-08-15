package com.vijaxx.library.ui;

import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs a database call off the Event Dispatch Thread and delivers the result
 * back onto it.
 *
 * <p>This is the single place the application talks to {@link SwingWorker}.
 * {@code doInBackground()} runs on a worker thread — no Swing component may be
 * touched there — while {@code done()} runs on the EDT, which is where the
 * result is handed to the caller and where any error is turned into a dialog.
 * Nothing in this class calls a service method from the EDT, and nothing in the
 * panels calls a service method anywhere else.
 */
public final class BackgroundTask {

    private BackgroundTask() {
    }

    /**
     * @param owner     component used for the wait cursor and error dialogs (may be null)
     * @param work      the off-EDT call, typically a {@code LibraryService} method
     * @param onSuccess consumed on the EDT with the result
     */
    public static <T> void run(Component owner, Callable<T> work, Consumer<T> onSuccess) {
        run(owner, work, onSuccess, null);
    }

    /**
     * @param onFailure consumed on the EDT when {@code work} threw; when null a
     *                  standard error dialog is shown instead
     */
    public static <T> void run(Component owner,
                               Callable<T> work,
                               Consumer<T> onSuccess,
                               Consumer<Throwable> onFailure) {

        if (owner != null) {
            owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                // Worker thread. JDBC only — never touch a Swing component here.
                return work.call();
            }

            @Override
            protected void done() {
                // Event Dispatch Thread.
                if (owner != null) {
                    owner.setCursor(Cursor.getDefaultCursor());
                }
                try {
                    T value = get();
                    if (onSuccess != null) {
                        onSuccess.accept(value);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (onFailure != null) {
                        onFailure.accept(cause);
                    } else {
                        Dialogs.error(owner, cause);
                    }
                }
            }
        }.execute();
    }
}
