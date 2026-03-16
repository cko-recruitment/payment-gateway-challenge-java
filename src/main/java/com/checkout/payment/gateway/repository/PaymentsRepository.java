package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentsRepository {
 Payment add(Payment payment);
 Optional<Payment> get(UUID id);
}
