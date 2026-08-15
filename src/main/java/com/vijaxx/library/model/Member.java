package com.vijaxx.library.model;

import java.time.LocalDate;
import java.util.Objects;

/** A library member. */
public class Member {

    private int id;
    private String name;
    private String email;
    private String phone;
    private MembershipType membershipType;
    private LocalDate joinDate;
    private boolean active = true;

    public Member() {
    }

    public Member(int id, String name, String email, String phone,
                  MembershipType membershipType, LocalDate joinDate, boolean active) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.membershipType = membershipType;
        this.joinDate = joinDate;
        this.active = active;
    }

    public static Member of(String name, String email, String phone, MembershipType type) {
        return new Member(0, name, email, phone, type, LocalDate.now(), true);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public MembershipType getMembershipType() { return membershipType; }
    public void setMembershipType(MembershipType membershipType) { this.membershipType = membershipType; }

    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** Convenience: borrow limit implied by the membership tier. */
    public int borrowLimit() {
        return membershipType == null ? 0 : membershipType.borrowLimit();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member other)) return false;
        return id == other.id && Objects.equals(email, other.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    @Override
    public String toString() {
        return name + " <" + email + ">";
    }
}
