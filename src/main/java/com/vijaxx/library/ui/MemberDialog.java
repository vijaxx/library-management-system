package com.vijaxx.library.ui;

import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.MembershipType;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Modal add/edit dialog for a {@link Member}. Pure Swing, no DB access. */
public class MemberDialog extends JDialog {

    private final JTextField nameField = new JTextField(18);
    private final JTextField emailField = new JTextField(18);
    private final JTextField phoneField = new JTextField(18);
    private final JComboBox<MembershipType> typeCombo = new JComboBox<>(MembershipType.values());
    private final JCheckBox activeCheck = new JCheckBox("Active", true);

    private Member result;

    public MemberDialog(Frame owner, String title, Member existing) {
        super(owner, title, true);
        buildForm(existing);
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildForm(Member existing) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        addRow(form, gc, 0, "Name:", nameField);
        addRow(form, gc, 1, "Email:", emailField);
        addRow(form, gc, 2, "Phone:", phoneField);
        addRow(form, gc, 3, "Membership:", typeCombo);
        addRow(form, gc, 4, "", activeCheck);

        if (existing != null) {
            nameField.setText(existing.getName());
            emailField.setText(existing.getEmail());
            phoneField.setText(existing.getPhone());
            typeCombo.setSelectedItem(existing.getMembershipType());
            activeCheck.setSelected(existing.isActive());
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

    private void onSave(Member existing) {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        MembershipType type = (MembershipType) typeCombo.getSelectedItem();

        if (name.isEmpty() || email.isEmpty() || !email.contains("@")) {
            JOptionPane.showMessageDialog(this, "A name and a valid email are required.",
                    "Missing data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (existing != null) {
            existing.setName(name);
            existing.setEmail(email);
            existing.setPhone(phone);
            existing.setMembershipType(type);
            existing.setActive(activeCheck.isSelected());
            result = existing;
        } else {
            Member m = Member.of(name, email, phone, type);
            m.setActive(activeCheck.isSelected());
            result = m;
        }
        dispose();
    }

    /** The member to save, or null if the dialog was cancelled. */
    public Member getResult() {
        return result;
    }
}
