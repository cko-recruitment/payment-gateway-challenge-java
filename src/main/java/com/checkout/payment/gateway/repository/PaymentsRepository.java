
// ------------------------------------------------------------------------------------------------
// ================================================================================================
// PACKAGE DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------

package com.checkout.payment.gateway.repository;

// ------------------------------------------------------------------------------------------------
// ================================================================================================
// IMPORT DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
// ------------------------------------------------------------------------------------------------
// ================================================================================================
// CLASS DEFINITION
// ================================================================================================
// ------------------------------------------------------------------------------------------------

@Repository
public class PaymentsRepository {

  private final ConcurrentHashMap<UUID, PostPaymentResponse> payments = new ConcurrentHashMap<>();

  /**
   * Stores a payment response, keyed by its ID.
   *
   * @param payment the payment response to store
   */
  public void add(PostPaymentResponse payment) {
    payments.put(payment.getIdempotencyKey(), payment);
  }

  /**
   * Retrieves a payment response by its ID.
   *
   * @param id the UUID of the payment to look up
   * @return an Optional containing the payment if found, or empty if not
   */
  public Optional<PostPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
