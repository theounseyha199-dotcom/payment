package com.practice.payment.repository;

import com.practice.payment.entity.AdminAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> { }
