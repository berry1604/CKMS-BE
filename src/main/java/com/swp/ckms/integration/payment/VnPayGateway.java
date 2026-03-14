package com.swp.ckms.integration.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class VnPayGateway implements PaymentGateway {

    @Value("${vnpay.tmnCode}")
    private String tmnCode;

    @Value("${vnpay.hashSecret}")
    private String hashSecret;

    @Value("${vnpay.payUrl}")
    private String payUrl;

    @Value("${vnpay.returnUrl}")
    private String returnUrl;

    @Override
    public String createPaymentUrl(Long referenceId, BigDecimal amount, String clientIp) {
        try {
            Map<String, String> params = new HashMap<>();

            String txnRef = referenceId + "_" + System.currentTimeMillis();
            
            // Fix Timezone chuẩn của VNPay GMT+7
            TimeZone tz = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

            Calendar cld = Calendar.getInstance(tz);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            formatter.setTimeZone(tz);



            String createDate = formatter.format(cld.getTime());

            // Add Expire Date (15 minutes limit)
            cld.add(Calendar.MINUTE, 15);
            String expireDate = formatter.format(cld.getTime());

            params.put("vnp_Version", "2.1.0");
            params.put("vnp_Command", "pay");
            params.put("vnp_TmnCode", tmnCode);
            params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
            params.put("vnp_CurrCode", "VND");
            params.put("vnp_TxnRef", txnRef);
            params.put("vnp_OrderInfo", "Payment for statement #" + referenceId);
            params.put("vnp_OrderType", "billpayment");
            params.put("vnp_Locale", "vn");
            params.put("vnp_ReturnUrl", returnUrl);
            params.put("vnp_CreateDate", createDate);
            params.put("vnp_ExpireDate", expireDate);
            params.put("vnp_IpAddr", clientIp != null && !clientIp.isEmpty() ? clientIp : "127.0.0.1");
            //params.put("vnp_IpAddr", "127.0.0.1");
            System.out.println("Using HashSecret = " + hashSecret); //kiểm tra chữ kí
            return buildUrlWithHash(params);

        } catch (Exception e) {
            throw new RuntimeException("Error creating VNPay URL", e);
        }
    }


    @Override
    public boolean verifySignature(Map<String, String> params) {

        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null) {
            return false;
        }

        Map<String, String> filtered = new HashMap<>(params);
        filtered.remove("vnp_SecureHash");
        filtered.remove("vnp_SecureHashType");

        List<String> keys = new ArrayList<>(filtered.keySet());
        Collections.sort(keys);

        StringBuilder hashData = new StringBuilder();

        for (String key : keys) {
            String value = filtered.get(key);
            if (value != null && !value.isEmpty()) {

                String encodedValue = URLEncoder.encode(value, StandardCharsets.US_ASCII);

                hashData.append(key)
                        .append("=")
                        .append(encodedValue)
                        .append("&");
            }
        }

        hashData.deleteCharAt(hashData.length() - 1);

        String calculatedHash = hmacSHA512(hashSecret, hashData.toString());

        return calculatedHash.equalsIgnoreCase(receivedHash);
    }

    @Override
    public String getTransactionReference(Map<String, String> params) {
        return params.get("vnp_TransactionNo");
    }

    @Override
    public boolean isPaymentSuccessful(Map<String, String> params) {
        return "00".equals(params.get("vnp_ResponseCode"));
    }

    // ================= PRIVATE HELPERS =================

    private String buildUrlWithHash(Map<String, String> params) throws Exception {

        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isEmpty()) {

                String encodedKey = URLEncoder.encode(key, StandardCharsets.US_ASCII);
                String encodedValue = URLEncoder.encode(value, StandardCharsets.US_ASCII);

                // Hash trên encoded value
                hashData.append(key).append("=").append(encodedValue).append("&");

                query.append(encodedKey)
                        .append("=")
                        .append(encodedValue)
                        .append("&");
            }
        }

        hashData.deleteCharAt(hashData.length() - 1);
        query.deleteCharAt(query.length() - 1);

        String secureHash = hmacSHA512(hashSecret, hashData.toString());

        return payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }



    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating HMAC", e);
        }
    }


}