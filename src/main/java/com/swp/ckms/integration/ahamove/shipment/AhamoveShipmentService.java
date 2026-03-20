package com.swp.ckms.integration.ahamove.shipment;

import com.swp.ckms.entity.Shipment;
import com.swp.ckms.enums.ShipmentStatus;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveWebhookRequest;

public interface AhamoveShipmentService {

    /**
     * Gửi shipment lên Ahamove để tạo đơn giao hàng.
     * Được gọi khi shipment chuyển sang trạng thái IN_TRANSIT.
     * Lưu ahamoveOrderId + trackingLink vào shipment sau khi tạo thành công.
     */
    void dispatchToAhamove(Shipment shipment);

    /**
     * Hủy đơn hàng trên Ahamove khi coordinator cancel shipment.
     * Không throw exception nếu Ahamove fail để không ảnh hưởng cancel nội bộ.
     */
    void cancelAhamoveOrder(Shipment shipment, String reason);

    /**
     * Map trạng thái từ Ahamove sang ShipmentStatus nội bộ.
     * Dùng trong webhook handler khi Ahamove gửi status update về.
     * Trả về null nếu không map được (status không xác định).
     */
    ShipmentStatus mapAhamoveStatus(String ahamoveStatus);
    //Xử lý webhook update
    boolean handleWebhookUpdate(AhamoveWebhookRequest request);
}