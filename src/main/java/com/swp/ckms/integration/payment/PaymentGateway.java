package com.swp.ckms.integration.payment;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {

    String createPaymentUrl(Long referenceId, BigDecimal amount, String clientIp);

    boolean verifySignature(Map<String, String> params);

    String getTransactionReference(Map<String, String> params);

    boolean isPaymentSuccessful(Map<String, String> params);
}