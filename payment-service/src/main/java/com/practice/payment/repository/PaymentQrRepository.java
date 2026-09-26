package com.practice.payment.repository;

import com.practice.payment.entity.PaymentQr;
import com.practice.payment.entity.PaymentStatus;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.math.BigDecimal;
import java.time.Instant;

public interface PaymentQrRepository extends JpaRepository<PaymentQr, Long>, JpaSpecificationExecutor<PaymentQr> {
    Optional<PaymentQr> findByMd5(String md5);
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
    Optional<PaymentQr> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
            Long orderId, Collection<PaymentStatus> statuses);

    long countByStatus(PaymentStatus status);

    @Query("select coalesce(sum(p.amount), 0) from PaymentQr p where p.status = :status and p.paidAt >= :from")
    BigDecimal sumAmountByStatusAndPaidAtGreaterThanEqual(@Param("status") PaymentStatus status,
                                                           @Param("from") Instant from);

    @Modifying
    @Transactional
    @Query("update PaymentQr p set p.status = :expired where p.orderId = :orderId "
            + "and p.id <> :paidId and p.status in :activeStatuses")
    int expireOtherActivePayments(@Param("orderId") Long orderId, @Param("paidId") Long paidId,
                                  @Param("activeStatuses") Collection<PaymentStatus> activeStatuses,
                                  @Param("expired") PaymentStatus expired);
}
