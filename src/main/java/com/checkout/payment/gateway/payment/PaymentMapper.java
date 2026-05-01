package com.checkout.payment.gateway.payment;

import com.checkout.payment.gateway.generated.api.model.PaymentRequest;
import com.checkout.payment.gateway.generated.api.model.PaymentResponse;
import com.checkout.payment.gateway.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

  @Mapping(ignore = true, target = "id")
  @Mapping(ignore = true, target = "authorizationCode")
  @Mapping(ignore = true, target = "status")
  @Mapping(ignore = true, target = "createdAt")
  Payment toEntity(PaymentRequest request);

  @Mapping(target = "cardNumberLastFour", source = "entity.cardNumberLastFour")
  PaymentResponse toResponse(Payment entity);
}
