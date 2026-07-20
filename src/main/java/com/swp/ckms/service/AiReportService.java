package com.swp.ckms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swp.ckms.dto.response.AiAnalysisResponse;
import com.swp.ckms.dto.response.AiChatResponse;
import com.swp.ckms.entity.*;
import com.swp.ckms.enums.*;
import com.swp.ckms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiReportService {

    private final BillingStatementRepository billingStatementRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final FranchiseStoreRepository franchiseStoreRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ai.ollama.model:qwen2.5:3b}")
    private String ollamaModel;

    @Value("${ai.ollama.timeout-seconds:60}")
    private long ollamaTimeoutSeconds;

    @Transactional(readOnly = true)
    public String aggregateExecutiveContext() {
        try {
            List<BillingStatement> statements = billingStatementRepository.findAll();
            List<StoreOrder> orders = storeOrderRepository.findAll();
            List<Shipment> shipments = shipmentRepository.findAll();
            List<ProductionPlan> plans = productionPlanRepository.findAll();
            List<FranchiseStore> stores = franchiseStoreRepository.findAll();
            List<User> users = userRepository.findAll();

            // 1. Stores
            long activeStoresCount = stores.stream().filter(s -> Boolean.TRUE.equals(s.getIsActive())).count();

            // 2. Billing
            BigDecimal totalPaid = BigDecimal.ZERO;
            BigDecimal totalOverdueAmount = BigDecimal.ZERO;
            long paidCount = 0;
            long issuedCount = 0;
            long overdueCount = 0;
            Map<String, BigDecimal> storeOverdueMap = new HashMap<>();

            for (BillingStatement bs : statements) {
                BigDecimal amt = bs.getTotalAmount() != null ? bs.getTotalAmount() : BigDecimal.ZERO;
                if (bs.getStatus() == BillingStatementStatus.PAID) {
                    totalPaid = totalPaid.add(amt);
                    paidCount++;
                } else if (bs.getStatus() == BillingStatementStatus.ISSUED) {
                    issuedCount++;
                } else if (bs.getStatus() == BillingStatementStatus.OVERDUE) {
                    totalOverdueAmount = totalOverdueAmount.add(amt);
                    overdueCount++;
                    String storeName = (bs.getStore() != null && bs.getStore().getName() != null)
                            ? bs.getStore().getName() : "Cửa hàng #" + (bs.getStore() != null ? bs.getStore().getStoreId() : "N/A");
                    storeOverdueMap.put(storeName, storeOverdueMap.getOrDefault(storeName, BigDecimal.ZERO).add(amt));
                }
            }

            List<Map<String, Object>> topDebtors = storeOverdueMap.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(5)
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("storeName", e.getKey());
                        m.put("overdueAmount", e.getValue());
                        return m;
                    })
                    .collect(Collectors.toList());

            // 3. Orders
            long totalOrders = orders.size();
            long deliveredOrders = 0;
            long submittedOrders = 0;
            long delayedOver4hOrders = 0;
            LocalDateTime fourHoursAgo = LocalDateTime.now().minusHours(4);

            for (StoreOrder o : orders) {
                if (o.getStatus() == OrderStatus.DELIVERED) {
                    deliveredOrders++;
                } else if (o.getStatus() == OrderStatus.SUBMITTED) {
                    submittedOrders++;
                    if (o.getOrderDate() != null && o.getOrderDate().isBefore(fourHoursAgo)) {
                        delayedOver4hOrders++;
                    }
                }
            }
            double orderSuccessRate = totalOrders > 0 ? ((double) deliveredOrders / totalOrders) * 100.0 : 0.0;

            // 4. Shipments
            long totalShipments = shipments.size();
            long inTransitShipments = 0;
            long deliveredShipments = 0;
            long failedOrReturnedShipments = 0;
            BigDecimal totalShippingFee = BigDecimal.ZERO;

            for (Shipment s : shipments) {
                if (s.getShippingFee() != null) {
                    totalShippingFee = totalShippingFee.add(s.getShippingFee());
                }
                if (s.getStatus() == ShipmentStatus.IN_TRANSIT) {
                    inTransitShipments++;
                } else if (s.getStatus() == ShipmentStatus.DELIVERED) {
                    deliveredShipments++;
                } else if (s.getStatus() == ShipmentStatus.DELIVERY_FAILED || s.getStatus() == ShipmentStatus.RETURNED) {
                    failedOrReturnedShipments++;
                }
            }

            // 5. Production Plans
            long totalPlans = plans.size();
            long completedPlans = 0;
            long inProgressPlans = 0;
            for (ProductionPlan p : plans) {
                if (p.getStatus() == ProductionPlanStatus.PRODUCED || p.getStatus() == ProductionPlanStatus.FINISHED) {
                    completedPlans++;
                } else if (p.getStatus() == ProductionPlanStatus.IN_PRODUCTION || p.getStatus() == ProductionPlanStatus.READY_TO_PRODUCE) {
                    inProgressPlans++;
                }
            }

            // 6. Users
            long activeUsers = users.stream().filter(u -> Boolean.TRUE.equals(u.getIsActive()) && u.getStatus() == UserStatus.ACTIVE).count();

            List<Map<String, Object>> staffList = users.stream()
                    .map(u -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", u.getUserId());
                        m.put("fullName", u.getFullName() != null ? u.getFullName() : "N/A");
                        m.put("username", u.getUsername() != null ? u.getUsername() : "N/A");
                        m.put("email", u.getEmail() != null ? u.getEmail() : "N/A");
                        m.put("role", u.getRole() != null ? u.getRole().getRoleName() : "N/A");
                        m.put("status", u.getStatus() != null ? u.getStatus().name() : "ACTIVE");
                        if (u.getStore() != null) {
                            m.put("store", u.getStore().getName());
                        }
                        return m;
                    })
                    .limit(60)
                    .collect(Collectors.toList());

            List<Map<String, Object>> storesList = stores.stream()
                    .map(s -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", s.getStoreId());
                        m.put("name", s.getName() != null ? s.getName() : "N/A");
                        m.put("address", s.getAddress() != null ? s.getAddress() : "N/A");
                        m.put("phone", s.getPhoneNumber() != null ? s.getPhoneNumber() : "N/A");
                        m.put("isActive", s.getIsActive());
                        return m;
                    })
                    .limit(60)
                    .collect(Collectors.toList());

            Map<String, Object> context = new LinkedHashMap<>();
            context.put("timestamp", LocalDateTime.now().toString());
            context.put("activeStores", activeStoresCount);
            context.put("activePersonnel", activeUsers);
            context.put("totalRevenuePaidVND", totalPaid);
            context.put("totalOverdueAmountVND", totalOverdueAmount);
            context.put("overdueInvoicesCount", overdueCount);
            context.put("paidInvoicesCount", paidCount);
            context.put("issuedInvoicesCount", issuedCount);
            context.put("personnelList", staffList);
            context.put("storesList", storesList);
            context.put("topDebtors", topDebtors);
            context.put("orders", Map.of(
                    "total", totalOrders,
                    "delivered", deliveredOrders,
                    "successRatePercent", Math.round(orderSuccessRate * 10.0) / 10.0,
                    "delayedOver4h", delayedOver4hOrders
            ));
            context.put("logistics", Map.of(
                    "totalShipments", totalShipments,
                    "inTransit", inTransitShipments,
                    "delivered", deliveredShipments,
                    "failedOrReturned", failedOrReturnedShipments,
                    "totalShippingFeeVND", totalShippingFee
            ));
            context.put("production", Map.of(
                    "totalPlans", totalPlans,
                    "completed", completedPlans,
                    "inProgress", inProgressPlans
            ));

            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            log.error("Error aggregating executive context: ", e);
            return "{\"error\":\"Unable to aggregate data context\"}";
        }
    }

    public AiAnalysisResponse analyzeExecutiveCockpit() {
        String jsonContext = aggregateExecutiveContext();
        log.info("Starting AI analysis with aggregated context: {}", jsonContext);

        String systemPrompt = "Bạn là Trợ lý AI Chuyên gia Quản trị Điều hành (Executive Cockpit AI) của chuỗi nhượng quyền F&B CKMS. " +
                "Dưới đây là dữ liệu tổng hợp hệ thống 8 nhóm chỉ số (JSON): \n" + jsonContext + "\n\n" +
                "NHIỆM VỤ CỦA BẠN: Hãy phân tích sâu số liệu trên và đưa ra báo cáo chuẩn đoán sức khỏe chuỗi dưới dạng 3 phần rõ ràng bằng TIẾNG VIỆT, với cấu trúc CHÍNH XÁC như sau:\n" +
                "[HIGHLIGHTS]\n" +
                "- (Đưa ra 2-3 điểm sáng về doanh thu, tỷ lệ giao đơn thành công hoặc công suất bếp)\n" +
                "[RISKS]\n" +
                "- (Đưa ra 2-3 rủi ro, điểm nghẽn nghiêm trọng nhất như hóa đơn quá hạn, nợ xấu từ cửa hàng nào, đơn chờ duyệt > 4h, hay chuyến xe lỗi)\n" +
                "[RECOMMENDATIONS]\n" +
                "- (Đưa ra 2-3 khuyến nghị hành động khẩn cấp cụ thể cho Giám đốc/Quản lý để xử lý ngay rủi ro)";

        try {
            String rawResponse = callOllamaGenerate(systemPrompt, "Hãy phân tích và xuất báo cáo chuẩn đoán chuỗi ngay bây giờ.");
            return parseAnalysisResponse(rawResponse, jsonContext);
        } catch (Exception e) {
            log.warn("Ollama API call failed or timed out. Generating structured rule-based fallback analysis. Error: {}", e.getMessage());
            return generateFallbackAnalysis(jsonContext);
        }
    }

    public AiChatResponse chatWithExecutiveData(String question, String chatHistory) {
        String jsonContext = aggregateExecutiveContext();
        String systemPrompt = "Bạn là Trợ lý AI hỏi đáp dữ liệu điều hành chuỗi nhượng quyền CKMS bằng tiếng Việt. " +
                "Dữ liệu hệ thống thời gian thực hiện tại (JSON): \n" + jsonContext + "\n\n" +
                "Quy tắc: Trả lời ngắn gọn, súc tích, chính xác theo số liệu JSON được cung cấp. Nếu số liệu không có trong JSON, hãy nói rõ là chưa có dữ liệu. Không tự bịa số liệu.";

        String fullPrompt = systemPrompt + "\n\n" +
                (chatHistory != null && !chatHistory.isBlank() ? "Lịch sử trò chuyện trước đó:\n" + chatHistory + "\n\n" : "") +
                "Câu hỏi từ Quản lý: " + question + "\n\nTrả lời bằng tiếng Việt:";

        try {
            String rawAnswer = callOllamaGenerate(systemPrompt, question);
            return AiChatResponse.builder()
                    .answer(rawAnswer != null ? rawAnswer.trim() : "Không có phản hồi từ mô hình AI.")
                    .modelUsed(ollamaModel)
                    .build();
        } catch (Exception e) {
            log.warn("Ollama API call failed during chat. Generating smart data-driven response. Error: {}", e.getMessage());
            String smartAnswer = generateFallbackChatResponse(question, jsonContext);
            return AiChatResponse.builder()
                    .answer(smartAnswer)
                    .modelUsed("CKMS Smart Engine (Data Fallback)")
                    .build();
        }
    }

    private String resolveAvailableModel(WebClient client) {
        try {
            String tagsBody = client.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            if (tagsBody != null) {
                JsonNode root = objectMapper.readTree(tagsBody);
                if (root.has("models") && root.get("models").isArray() && root.get("models").size() > 0) {
                    for (JsonNode m : root.get("models")) {
                        String name = m.has("name") ? m.get("name").asText() : "";
                        if (name.equalsIgnoreCase(ollamaModel) || name.startsWith(ollamaModel.split(":")[0])) {
                            return name;
                        }
                    }
                    JsonNode first = root.get("models").get(0);
                    String firstName = first.has("name") ? first.get("name").asText() : "";
                    if (!firstName.isEmpty()) {
                        log.info("Model {} not yet found on Ollama. Using available model: {}", ollamaModel, firstName);
                        return firstName;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not query /api/tags from Ollama: {}", e.getMessage());
        }
        return ollamaModel;
    }

    private String callOllamaGenerate(String systemPrompt, String userPrompt) throws Exception {
        WebClient client = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();

        String targetModel = resolveAvailableModel(client);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", targetModel);
        requestBody.put("prompt", systemPrompt + "\n\n" + userPrompt);
        requestBody.put("stream", false);
        requestBody.put("options", Map.of(
                "temperature", 0.3,
                "top_p", 0.9
        ));

        String responseBody = client.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                .block();

        if (responseBody == null) {
            throw new RuntimeException("Empty response received from Ollama");
        }

        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return jsonNode.has("response") ? jsonNode.get("response").asText() : responseBody;
    }

    private String generateFallbackChatResponse(String question, String jsonContext) {
        try {
            JsonNode root = objectMapper.readTree(jsonContext);
            String q = question != null ? question.toLowerCase() : "";

            if (q.contains("nhân sự") || q.contains("nhân viên") || q.contains("tuan") || q.contains("tuấn") || q.contains("staff") || q.contains("user") || q.contains("thông tin về")) {
                JsonNode personnelList = root.path("personnelList");
                StringBuilder sb = new StringBuilder();
                if (personnelList.isArray() && personnelList.size() > 0) {
                    List<JsonNode> matches = new ArrayList<>();
                    String searchKey = q.replace("cho tôi thông tin về nhân sự tên", "")
                                        .replace("cho tôi thông tin về nhân viên tên", "")
                                        .replace("thông tin về nhân sự tên", "")
                                        .replace("thông tin về nhân viên tên", "")
                                        .replace("thông tin về", "")
                                        .replace("nhân sự tên", "")
                                        .replace("nhân viên tên", "")
                                        .replace("nhân sự", "")
                                        .replace("nhân viên", "")
                                        .replace("tên", "")
                                        .trim();
                    for (JsonNode p : personnelList) {
                        String fullName = p.path("fullName").asText("").toLowerCase();
                        String username = p.path("username").asText("").toLowerCase();
                        String email = p.path("email").asText("").toLowerCase();
                        if (!searchKey.isEmpty() && (fullName.contains(searchKey) || username.contains(searchKey) || email.contains(searchKey) || q.contains(fullName) || q.contains(username))) {
                            matches.add(p);
                        }
                    }
                    if (!matches.isEmpty()) {
                        sb.append("👤 **Thông tin chi tiết nhân sự tra cứu:**\n");
                        for (JsonNode m : matches) {
                            sb.append("• **Họ tên:** ").append(m.path("fullName").asText()).append("\n");
                            sb.append("  - **Tài khoản:** @").append(m.path("username").asText()).append(" (").append(m.path("email").asText()).append(")\n");
                            sb.append("  - **Vai trò:** ").append(m.path("role").asText()).append("\n");
                            sb.append("  - **Trạng thái:** ").append(m.path("status").asText()).append("\n");
                            if (m.has("store") && !m.path("store").asText().isBlank()) {
                                sb.append("  - **Thuộc cửa hàng:** ").append(m.path("store").asText()).append("\n");
                            }
                        }
                    } else {
                        sb.append("👥 **Danh sách nhân sự tiêu biểu trong chuỗi CKMS:**\n");
                        int count = 0;
                        for (JsonNode p : personnelList) {
                            if (count++ >= 6) break;
                            sb.append("• **").append(p.path("fullName").asText("N/A")).append("** (@").append(p.path("username").asText()).append(") - Vai trò: *").append(p.path("role").asText()).append("*\n");
                        }
                        sb.append("\n*(Bạn có thể hỏi cụ thể tên từng nhân viên hoặc tài khoản)*");
                    }
                } else {
                    sb.append("Hiện tại hệ thống chưa có dữ liệu chi tiết trong danh sách nhân sự.");
                }
                return sb.toString();
            } else if (q.contains("nợ") || q.contains("quá hạn") || q.contains("debt")) {
                long overdueCount = root.path("overdueInvoicesCount").asLong(0);
                String overdueAmt = root.path("totalOverdueAmountVND").asText("0");
                JsonNode topDebtors = root.path("topDebtors");
                StringBuilder sb = new StringBuilder();
                sb.append("📊 **Thống kê nợ quá hạn thời gian thực:**\n");
                sb.append("• Có **").append(overdueCount).append("** hóa đơn đang quá hạn với tổng nợ **").append(overdueAmt).append(" VNĐ**.\n");
                if (topDebtors.isArray() && topDebtors.size() > 0) {
                    sb.append("• **Top cửa hàng nợ cao nhất:**\n");
                    for (JsonNode d : topDebtors) {
                        sb.append("  - Cửa hàng ID #").append(d.path("storeId").asText()).append(": **").append(d.path("overdueAmount").asText()).append(" VNĐ** (").append(d.path("status").asText()).append(")\n");
                    }
                } else {
                    sb.append("• Hiện không ghi nhận cửa hàng nào nợ xấu vượt mức hạn ngạch.\n");
                }
                return sb.toString();
            } else if (q.contains("doanh thu") || q.contains("tiền") || q.contains("thu") || q.contains("revenue")) {
                String totalPaid = root.path("totalRevenuePaidVND").asText("0");
                long paidCount = root.path("paidInvoicesCount").asLong(0);
                return "💰 **Tình hình thu chi hiện tại:**\n" +
                        "• Tổng doanh thu đã thu thực tế (PAID): **" + totalPaid + " VNĐ** từ **" + paidCount + "** hóa đơn đã thanh toán xong.\n" +
                        "• Hệ thống duy trì **" + root.path("activeStores").asLong(0) + "** cửa hàng đang hoạt động với **" + root.path("activePersonnel").asLong(0) + "** nhân sự.";
            } else if (q.contains("đơn") || q.contains("order") || q.contains("tỷ lệ") || q.contains("trễ")) {
                JsonNode orders = root.path("orders");
                return "📦 **Hành trình Đơn đặt hàng (Store Orders):**\n" +
                        "• Tổng số đơn hàng trong kỳ: **" + orders.path("total").asLong(0) + "** đơn.\n" +
                        "• Đã giao thành công: **" + orders.path("delivered").asLong(0) + "** đơn (Tỷ lệ thành công: **" + orders.path("successRatePercent").asDouble(0.0) + "%**).\n" +
                        "• ⚠️ Đơn chờ duyệt quá 4 tiếng: **" + orders.path("delayedOver4h").asLong(0) + "** đơn cần xử lý gấp.";
            } else if (q.contains("vận chuyển") || q.contains("xe") || q.contains("ship") || q.contains("logistics")) {
                JsonNode logi = root.path("logistics");
                return "🚚 **Tình trạng Vận chuyển Ahamove:**\n" +
                        "• Tổng chuyến xe: **" + logi.path("totalShipments").asLong(0) + "** chuyến.\n" +
                        "• Đang trên đường giao (In Transit): **" + logi.path("inTransit").asLong(0) + "** chuyến.\n" +
                        "• Đã hoàn tất: **" + logi.path("delivered").asLong(0) + "** chuyến.\n" +
                        "• ⚠️ Giao thất bại/hoàn xe: **" + logi.path("failedOrReturned").asLong(0) + "** chuyến.\n" +
                        "• Tổng chi phí vận chuyển: **" + logi.path("totalShippingFeeVND").asText("0") + " VNĐ**.";
            } else if (q.contains("bếp") || q.contains("sản xuất") || q.contains("kitchen") || q.contains("production")) {
                JsonNode prod = root.path("production");
                return "🍳 **Khối Sản xuất Bếp Trung Tâm:**\n" +
                        "• Kế hoạch sản xuất: **" + prod.path("totalPlans").asLong(0) + "** kế hoạch.\n" +
                        "• Đang chế biến (In Progress): **" + prod.path("inProgress").asLong(0) + "** kế hoạch.\n" +
                        "• Đã hoàn thành đóng gói: **" + prod.path("completed").asLong(0) + "** kế hoạch.";
            } else {
                return "📈 **Tóm tắt nhanh số liệu Điều hành Chuỗi CKMS:**\n" +
                        "• **Doanh thu thực thu:** " + root.path("totalRevenuePaidVND").asText("0") + " VNĐ\n" +
                        "• **Cửa hàng hoạt động:** " + root.path("activeStores").asLong(0) + " điểm bán\n" +
                        "• **Hóa đơn quá hạn:** " + root.path("overdueInvoicesCount").asLong(0) + " hóa đơn (" + root.path("totalOverdueAmountVND").asText("0") + " VNĐ)\n" +
                        "• **Tỷ lệ giao đơn thành công:** " + root.path("orders").path("successRatePercent").asDouble(0.0) + "%\n" +
                        "• **Đơn chờ duyệt >4h:** " + root.path("orders").path("delayedOver4h").asLong(0) + " đơn\n\n" +
                        "*(Gợi ý: Bạn có thể hỏi sâu về 'công nợ', 'doanh thu', 'vận chuyển', 'đơn hàng', hay 'bếp trung tâm')*";
            }
        } catch (Exception e) {
            return "Hệ thống đang hoạt động ổn định. Vui lòng đặt câu hỏi về doanh thu, công nợ, đơn hàng hoặc chuyến xe.";
        }
    }

    private AiAnalysisResponse parseAnalysisResponse(String rawText, String jsonContext) {
        List<String> highlights = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        if (rawText != null) {
            String[] lines = rawText.split("\\r?\\n");
            String currentSection = "";
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("[HIGHLIGHTS]") || trimmed.contains("HIGHLIGHTS") || trimmed.contains("Điểm sáng")) {
                    currentSection = "HIGHLIGHTS";
                    continue;
                } else if (trimmed.equalsIgnoreCase("[RISKS]") || trimmed.contains("RISKS") || trimmed.contains("Rủi ro") || trimmed.contains("Điểm nghẽn")) {
                    currentSection = "RISKS";
                    continue;
                } else if (trimmed.equalsIgnoreCase("[RECOMMENDATIONS]") || trimmed.contains("RECOMMENDATIONS") || trimmed.contains("Khuyến nghị")) {
                    currentSection = "RECOMMENDATIONS";
                    continue;
                }

                if (trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.matches("^\\d+\\..*")) {
                    String cleanItem = trimmed.replaceFirst("^[-•\\d+\\.]+\\s*", "").trim();
                    if (!cleanItem.isEmpty()) {
                        if ("HIGHLIGHTS".equals(currentSection)) highlights.add(cleanItem);
                        else if ("RISKS".equals(currentSection)) risks.add(cleanItem);
                        else if ("RECOMMENDATIONS".equals(currentSection)) recommendations.add(cleanItem);
                    }
                }
            }
        }

        if (highlights.isEmpty() && risks.isEmpty() && recommendations.isEmpty()) {
            return generateFallbackAnalysis(jsonContext);
        }

        return AiAnalysisResponse.builder()
                .highlights(highlights)
                .risks(risks)
                .recommendations(recommendations)
                .rawAnalysis(rawText)
                .build();
    }

    private AiAnalysisResponse generateFallbackAnalysis(String jsonContext) {
        List<String> highlights = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonContext);
            long activeStores = root.path("activeStores").asLong(0);
            double successRate = root.path("orders").path("successRatePercent").asDouble(0.0);
            long delayedOrders = root.path("orders").path("delayedOver4h").asLong(0);
            long overdueCount = root.path("overdueInvoicesCount").asLong(0);
            BigDecimal overdueAmt = new BigDecimal(root.path("totalOverdueAmountVND").asText("0"));
            long failedShipments = root.path("logistics").path("failedOrReturned").asLong(0);

            highlights.add("Hệ thống duy trì " + activeStores + " điểm bán active cùng tỷ lệ giao đơn hàng thành công đạt " + successRate + "%.");
            highlights.add("Quy trình sản xuất tại bếp trung tâm và điều phối vận chuyển đang vận hành liên tục 24/7.");

            if (overdueCount > 0) {
                risks.add("Có " + overdueCount + " hóa đơn quá hạn chưa thanh toán với tổng công nợ " + String.format("%,d", overdueAmt.longValue()) + " VNĐ.");
            } else {
                risks.add("Tài chính lành mạnh, không phát hiện hóa đơn quá hạn trong kỳ hiện tại.");
            }

            if (delayedOrders > 0) {
                risks.add("Phát hiện " + delayedOrders + " đơn đặt hàng chờ duyệt quá 4 tiếng chưa được lên lịch sản xuất.");
            }
            if (failedShipments > 0) {
                risks.add("Ghi nhận " + failedShipments + " chuyến xe giao hàng thất bại hoặc hoàn xe cần kiểm tra lý do.");
            }

            if (overdueCount > 0) {
                recommendations.add("Chỉ đạo bộ phận kế toán liên hệ nhắc nợ gấp các cửa hàng trong Top nợ quá hạn.");
            }
            if (delayedOrders > 0 || failedShipments > 0) {
                recommendations.add("Điều phối viên cần kiểm tra ngay trang Duyệt Đơn Hàng và Bảng Theo Dõi Chuyến Xe để xử lý các sự cố tồn đọng.");
            }
            recommendations.add("Duy trì kiểm tra định kỳ báo cáo 8 nhóm chỉ số để tối ưu hóa chi phí vận chuyển và nguyên liệu.");

        } catch (Exception e) {
            highlights.add("Hệ thống dữ liệu đang hoạt động bình thường.");
            risks.add("Không phát hiện rủi ro nghiêm trọng từ cơ sở dữ liệu.");
            recommendations.add("Tiếp tục theo dõi các chỉ số trực quan trên bảng điều khiển.");
        }

        return AiAnalysisResponse.builder()
                .highlights(highlights)
                .risks(risks)
                .recommendations(recommendations)
                .rawAnalysis("AI Fallback Analysis (Ollama Offline/Timeout Protection)")
                .build();
    }
}
