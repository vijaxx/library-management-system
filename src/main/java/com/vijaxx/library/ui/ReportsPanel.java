package com.vijaxx.library.ui;

import com.vijaxx.library.model.Reports;
import com.vijaxx.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Reports tab: four sub-tabs, each backed by a real multi-table JOIN with
 * aggregation in {@code ReportDao}. All four loads run off the EDT.
 */
public class ReportsPanel extends JPanel {

    private final LibraryService service;

    private final ObjectTableModel<Reports.LoanRow> borrowedModel;
    private final ObjectTableModel<Reports.LoanRow> overdueModel;
    private final ObjectTableModel<Reports.TitlePopularity> popularModel;
    private final ObjectTableModel<Reports.MemberActivity> activityModel;
    private final JLabel totalFinesLabel = new JLabel("Total fines collected: -");

    public ReportsPanel(LibraryService service) {
        super(new BorderLayout(8, 8));
        this.service = service;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        borrowedModel = new ObjectTableModel<>(List.of(
                new ObjectTableModel.Column<>("Book", String.class, Reports.LoanRow::title),
                new ObjectTableModel.Column<>("Member", String.class, Reports.LoanRow::memberName),
                new ObjectTableModel.Column<>("Issued", String.class, r -> String.valueOf(r.issueDate())),
                new ObjectTableModel.Column<>("Due", String.class, r -> String.valueOf(r.dueDate()))
        ));

        overdueModel = new ObjectTableModel<>(List.of(
                new ObjectTableModel.Column<>("Book", String.class, Reports.LoanRow::title),
                new ObjectTableModel.Column<>("Member", String.class, Reports.LoanRow::memberName),
                new ObjectTableModel.Column<>("Due", String.class, r -> String.valueOf(r.dueDate())),
                new ObjectTableModel.Column<>("Days Overdue", Long.class, Reports.LoanRow::daysOverdue),
                new ObjectTableModel.Column<>("Accrued Fine", String.class, r -> r.fine().toPlainString())
        ));

        popularModel = new ObjectTableModel<>(List.of(
                new ObjectTableModel.Column<>("Title", String.class, Reports.TitlePopularity::title),
                new ObjectTableModel.Column<>("Author", String.class, Reports.TitlePopularity::author),
                new ObjectTableModel.Column<>("Category", String.class, Reports.TitlePopularity::category),
                new ObjectTableModel.Column<>("Times Borrowed", Long.class, Reports.TitlePopularity::timesBorrowed),
                new ObjectTableModel.Column<>("Currently Out", Long.class, Reports.TitlePopularity::currentlyOut)
        ));

        activityModel = new ObjectTableModel<>(List.of(
                new ObjectTableModel.Column<>("Member", String.class, Reports.MemberActivity::memberName),
                new ObjectTableModel.Column<>("Membership", String.class, a -> a.membershipType().label()),
                new ObjectTableModel.Column<>("Total Loans", Long.class, Reports.MemberActivity::totalLoans),
                new ObjectTableModel.Column<>("Open Loans", Long.class, Reports.MemberActivity::openLoans),
                new ObjectTableModel.Column<>("Overdue", Long.class, Reports.MemberActivity::overdueLoans),
                new ObjectTableModel.Column<>("Fines Paid", String.class, a -> a.finesPaid().toPlainString())
        ));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Currently Borrowed", new JScrollPane(new JTable(borrowedModel)));
        tabs.addTab("Overdue", new JScrollPane(new JTable(overdueModel)));
        tabs.addTab("Most Borrowed", new JScrollPane(new JTable(popularModel)));
        tabs.addTab("Member Activity", new JScrollPane(new JTable(activityModel)));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Refresh Reports");
        top.add(refreshButton);
        top.add(totalFinesLabel);

        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        refreshButton.addActionListener(e -> reload());
        reload();
    }

    /** Runs all five report queries off the EDT and applies each result independently. */
    public void reload() {
        BackgroundTask.run(this, service::currentlyBorrowed, borrowedModel::setRows);
        BackgroundTask.run(this, service::overdueLoans, overdueModel::setRows);
        BackgroundTask.run(this, () -> service.mostBorrowed(20), popularModel::setRows);
        BackgroundTask.run(this, service::memberActivity, activityModel::setRows);
        BackgroundTask.run(this, service::totalFinesCollected,
                total -> totalFinesLabel.setText("Total fines collected: " + total.toPlainString()));
    }
}
