package com.checkout.payment.gateway.payment;

import static com.checkout.payment.gateway.TestUtils.givenPayment;
import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.payment.entity.Payment;
import com.checkout.payment.gateway.payment.entity.PaymentsRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentsRepositoryTest {

  private PaymentsRepository testObject;

  @BeforeEach
  void setup() {
    testObject = new PaymentsRepository();
  }

  @Test
  void whenDeleteAllShouldRemoveAllPayments() {
    // given
    Payment payment = givenPayment();
    testObject.save(payment);

    // when
    testObject.deleteAll();

    // then
    assertThat(testObject.findAll()).isEmpty();
  }

  @Test
  void whenSaveShouldPersistPayment() {
    // given
    Payment payment = givenPayment();

    // when
    Payment persistedEntity = testObject.save(payment);

    // then
    assertThat(persistedEntity)
        .usingRecursiveComparison()
        .isEqualTo(payment);
  }

  @Test
  void whenFindByIdShouldReturnPayment() {
    // given
    Payment payment = givenPayment();
    testObject.save(payment);

    // when
    Optional<Payment> persistedEntity = testObject.findById(payment.id());

    // then
    assertThat(persistedEntity).isNotEmpty();
    assertThat(persistedEntity.get())
        .usingRecursiveComparison()
        .isEqualTo(payment);

  }

  @Test
  void whenFindAllShouldReturnAllPayments() {
    // given
    Payment payment = givenPayment();
    testObject.save(payment);

    Payment anotherPayment = givenPayment();
    testObject.save(anotherPayment);

    // when
    List<Payment> persistedPayments = testObject.findAll();

    // then
    assertThat(persistedPayments).isNotEmpty();

    assertThat(persistedPayments)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(List.of(payment, anotherPayment));

  }
}