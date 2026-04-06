package com.example.orderconsumer.repository;

import com.example.orderconsumer.domain.FulfillmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentRecordRepository extends JpaRepository<FulfillmentRecord, Long> {
}
