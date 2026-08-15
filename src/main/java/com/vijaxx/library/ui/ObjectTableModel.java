package com.vijaxx.library.ui;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A read-only {@link AbstractTableModel} over a list of domain objects.
 *
 * <p>Concrete models declare their columns as {@link Column} value extractors,
 * which keeps the JTable free of any knowledge of the domain and keeps the
 * models free of {@code switch (columnIndex)} ladders.
 *
 * <p>All mutation happens on the Event Dispatch Thread: background work
 * (querying the database) produces a {@code List<T>}, and only the final
 * {@link #setRows(List)} call touches the model.
 *
 * @param <T> row type
 */
public class ObjectTableModel<T> extends AbstractTableModel {

    /** One column: its header, its value type (for renderers/sorting) and how to read it. */
    public record Column<T>(String header, Class<?> type, Function<T, Object> value) {
    }

    private final List<Column<T>> columns;
    private List<T> rows = new ArrayList<>();

    public ObjectTableModel(List<Column<T>> columns) {
        this.columns = List.copyOf(columns);
    }

    public void setRows(List<T> newRows) {
        this.rows = newRows == null ? new ArrayList<>() : new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    public List<T> getRows() {
        return List.copyOf(rows);
    }

    /** The domain object behind a view row index, or null if out of range. */
    public T getRowAt(int rowIndex) {
        return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex) : null;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int columnIndex) {
        return columns.get(columnIndex).header();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columns.get(columnIndex).type();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        T row = rows.get(rowIndex);
        return columns.get(columnIndex).value().apply(row);
    }
}
