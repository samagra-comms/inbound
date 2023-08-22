package com.uci.inbound.repository;

import com.uci.inbound.entity.DeliveryReport;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeliveryReportRepository extends R2dbcRepository<DeliveryReport, UUID> {

}
