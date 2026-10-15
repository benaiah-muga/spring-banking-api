package com.banking.api.repository;

import com.banking.api.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByUserIdAndErpSystemAndErpPaymentReference(
            Long userId, String erpSystem, String erpPaymentReference);

    Optional<PaymentOrder> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndErpSystemAndErpPaymentReference(
            Long userId, String erpSystem, String erpPaymentReference);
}
