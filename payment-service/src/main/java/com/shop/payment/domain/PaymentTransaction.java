package com.shop.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Append-only audit trail of every attempt made against the PSP. */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID intentId;
    private String type;
    private String status;
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String pspResponse;

    private Instant createdAt = Instant.now();

    protected PaymentTransaction() {}

    public PaymentTransaction(UUID intentId, String type, String status, BigDecimal amount, String pspResponse) {
        this.intentId = intentId;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.pspResponse = pspResponse;
    }
}
