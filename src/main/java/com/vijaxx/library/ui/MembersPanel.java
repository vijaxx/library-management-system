package com.vijaxx.library.ui;

import com.vijaxx.library.model.Member;
import com.vijaxx.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;

/** Member management tab: search, list, add, edit, delete. Same off-EDT discipline as {@link BooksPanel}. */
public class MembersPanel extends JPanel {

    private final LibraryService service;
    private final ObjectTableModel<Member> tableModel;
    private final JTable table;
    private final JTextField searchField = new JTextField(20);

    public MembersPanel(LibraryService service) {
        super(new BorderLayout(8, 8));
        this.service = service;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tableModel = new ObjectTableModel<>(List.of(
                new ObjectTableModel.Column<>("Name", String.class, Member::getName),
                new ObjectTableModel.Column<>("Email", String.class, Member::getEmail),
                new ObjectTableModel.Column<>("Phone", String.class, Member::getPhone),
                new ObjectTableModel.Column<>("Membership", String.class, m -> m.getMembershipType().label()),
                new ObjectTableModel.Column<>("Limit", Integer.class, Member::borrowLimit),
                new ObjectTableModel.Column<>("Join Date", String.class, m -> String.valueOf(m.getJoinDate())),
                new ObjectTableModel.Column<>("Active", Boolean.class, Member::isActive)
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
        JButton addButton = new JButton("Add Member");
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

    public void reload(String term) {
        BackgroundTask.run(this,
                () -> term == null || term.isBlank() ? service.listMembers() : service.searchMembers(term),
                tableModel::setRows);
    }

    private Member selectedMember() {
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
        MemberDialog dialog = new MemberDialog(ownerFrame(), "Add Member", null);
        dialog.setVisible(true);
        Member toSave = dialog.getResult();
        if (toSave == null) {
            return;
        }
        BackgroundTask.run(this, () -> service.addMember(toSave), saved -> reload(searchField.getText()));
    }

    private void openEditDialog() {
        Member selected = selectedMember();
        if (selected == null) {
            Dialogs.warn(this, "Select a member to edit first.");
            return;
        }
        MemberDialog dialog = new MemberDialog(ownerFrame(), "Edit Member", selected);
        dialog.setVisible(true);
        Member toSave = dialog.getResult();
        if (toSave == null) {
            return;
        }
        BackgroundTask.run(this, () -> service.updateMember(toSave), saved -> reload(searchField.getText()));
    }

    private void deleteSelected() {
        Member selected = selectedMember();
        if (selected == null) {
            Dialogs.warn(this, "Select a member to delete first.");
            return;
        }
        if (!Dialogs.confirm(this, "Delete \"" + selected.getName() + "\"?")) {
            return;
        }
        BackgroundTask.run(this, () -> { service.deleteMember(selected.getId()); return null; },
                v -> reload(searchField.getText()));
    }
}
