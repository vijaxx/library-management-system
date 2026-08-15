package com.vijaxx.library.model;

/**
 * Membership tiers. Each tier fixes how many books a member may hold at once
 * and how long a loan runs before it is considered overdue.
 */
public enum MembershipType {

    STUDENT("Student", 3, 14),
    STANDARD("Standard", 5, 21),
    PREMIUM("Premium", 10, 30);

    private final String label;
    private final int borrowLimit;
    private final int loanPeriodDays;

    MembershipType(String label, int borrowLimit, int loanPeriodDays) {
        this.label = label;
        this.borrowLimit = borrowLimit;
        this.loanPeriodDays = loanPeriodDays;
    }

    public String label() {
        return label;
    }

    /** Maximum number of simultaneously outstanding loans for this tier. */
    public int borrowLimit() {
        return borrowLimit;
    }

    /** Days between issue date and due date for this tier. */
    public int loanPeriodDays() {
        return loanPeriodDays;
    }

    @Override
    public String toString() {
        return label;
    }
}
