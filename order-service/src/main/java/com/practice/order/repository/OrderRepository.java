package com.practice.order.repository;

import com.practice.order.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.practice.order.entity.OrderStatus;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {
    List<PurchaseOrder> findAllByOwnerSubject(String ownerSubject);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);
    long countByStatus(OrderStatus status);
    long countByStatusIsNull();
    long countByCreatedAtGreaterThanEqual(java.time.Instant from);
}
