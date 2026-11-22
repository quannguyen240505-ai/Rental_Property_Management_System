package com.rental.property.entity;

import jakarta.persistence.*;
import java.util.UUID;

/** Property is the source of truth for staff/property assignment. */
@Entity
@Table(name = "staff_property_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_staff_property", columnNames = {"staff_id", "property_id"})
})
public class StaffPropertyAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    protected StaffPropertyAssignment() { }

    public StaffPropertyAssignment(UUID staffId, Property property) {
        this.staffId = staffId;
        this.property = property;
    }

    public UUID getId() { return id; }
    public UUID getStaffId() { return staffId; }
    public Property getProperty() { return property; }
}
