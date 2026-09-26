package com.practice.product.repository;

import com.practice.product.entity.AdminAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> { }
