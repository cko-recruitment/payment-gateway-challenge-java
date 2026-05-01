package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.client.dto.MounteBankRequest;
import com.checkout.payment.gateway.payment.entity.Payment;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface MounteBankMapper {

  @Mapping(source = ".", target = "expiryDate", qualifiedByName = "formatExpiryDate")
  MounteBankRequest toRequest(Payment entity);

  @Named("formatExpiryDate")
  default String formatExpiryDate(Payment payment) {
    return YearMonth.of(payment.expiryYear(), payment.expiryMonth())
        .format(DateTimeFormatter.ofPattern("MM/yyyy"));
  }
}
