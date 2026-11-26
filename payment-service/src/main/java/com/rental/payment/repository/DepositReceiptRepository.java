package com.rental.payment.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.rental.payment.entity.DepositPaymentMethod;
import com.rental.payment.entity.DepositReceipt;
import com.rental.payment.entity.DepositReceiptStatus;

@Repository
public class DepositReceiptRepository {

    private final JdbcTemplate jdbc;

    public DepositReceiptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockHold(UUID holdId) {
        jdbc.update("""
                INSERT INTO deposit_receipt_hold_locks (hold_id) VALUES (?)
                ON DUPLICATE KEY UPDATE hold_id = hold_id
                """, holdId.toString());
        jdbc.queryForObject("""
                SELECT hold_id FROM deposit_receipt_hold_locks
                WHERE hold_id = ? FOR UPDATE
                """, String.class, holdId.toString());
    }

    public Optional<DepositReceipt> findByHoldIdForUpdate(UUID holdId) {
        return jdbc.query("""
                SELECT id, receipt_code, property_id, room_id,
                       hold_id, customer_id, collected_by, amount,
                       payment_method, status, evidence_url, created_at, updated_at
                FROM deposit_receipts WHERE hold_id = ? FOR UPDATE
                """, (rs, rowNum) -> mapRow(rs), holdId.toString()).stream().findFirst();
    }

    public void insert(DepositReceipt receipt) {
        jdbc.update("""
                INSERT INTO deposit_receipts (
                    id, receipt_code, property_id, room_id,
                    hold_id, customer_id, collected_by, amount,
                    payment_method, status, evidence_url,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                receipt.id().toString(),
                receipt.receiptCode(),
                receipt.propertyId().toString(),
                receipt.roomId().toString(),
                receipt.holdId().toString(),
                receipt.customerId().toString(),
                receipt.collectedBy().toString(),
                receipt.amount(),
                receipt.paymentMethod().name(),
                receipt.status().name(),
                receipt.evidenceUrl(),
                receipt.createdAt(),
                receipt.updatedAt());
    }

    public Optional<DepositReceipt> findById(UUID id) {
        return jdbc.query("""
                SELECT id, receipt_code, property_id, room_id,
                       hold_id, customer_id, collected_by, amount,
                       payment_method, status, evidence_url,
                       created_at, updated_at
                FROM deposit_receipts
                WHERE id = ?
                """,
                (rs, rowNum) -> mapRow(rs),
                id.toString())
                .stream()
                .findFirst();
    }

    public int verifyOwner(UUID id, UUID owner) {
        return jdbc.update("""
                UPDATE deposit_receipts SET status = 'OWNER_VERIFIED',
                    owner_verified_by = ?, owner_verified_at = UTC_TIMESTAMP(),
                    updated_at = UTC_TIMESTAMP()
                WHERE id = ? AND status IN ('REPORTED', 'CUSTOMER_CONFIRMED')
                """, owner.toString(), id.toString());
    }

    private DepositReceipt mapRow(ResultSet rs) throws SQLException {
        return new DepositReceipt(
                UUID.fromString(rs.getString("id")),
                rs.getString("receipt_code"),
                UUID.fromString(rs.getString("property_id")),
                UUID.fromString(rs.getString("room_id")),
                UUID.fromString(rs.getString("hold_id")),
                UUID.fromString(rs.getString("customer_id")),
                UUID.fromString(rs.getString("collected_by")),
                rs.getLong("amount"),
                DepositPaymentMethod.valueOf(
                        rs.getString("payment_method")),
                DepositReceiptStatus.valueOf(rs.getString("status")),
                rs.getString("evidence_url"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
