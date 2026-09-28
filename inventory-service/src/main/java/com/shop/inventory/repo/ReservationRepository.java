package com.shop.inventory.repo;

import com.shop.inventory.domain.Reservation;
import com.shop.inventory.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    @Query("""
            SELECT DISTINCT r.orderId FROM Reservation r
             WHERE r.status = com.shop.inventory.domain.ReservationStatus.RESERVED
               AND r.expiresAt < :now
            """)
    List<UUID> findOrdersWithExpiredHolds(@Param("now") Instant now);
}
