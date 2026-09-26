package com.practice.payment.service;

import com.practice.payment.entity.AdminAuditEvent;
import com.practice.payment.repository.AdminAuditEventRepository;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AdminAuditService {
    private final AdminAuditEventRepository events;
    private final ObjectMapper mapper;
    public AdminAuditService(AdminAuditEventRepository events, ObjectMapper mapper) { this.events = events; this.mapper = mapper; }
    public void record(String subject, String action, Long paymentId, Object oldValue, Object newValue) {
        events.save(new AdminAuditEvent(subject, action, "PAYMENT", String.valueOf(paymentId), json(oldValue), json(newValue)));
    }
    private String json(Object value) {
        if (value == null) return null;
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { return "{}"; }
    }
}
