package com.swp.ckms.controller;

import com.swp.ckms.dto.request.AiChatRequest;
import com.swp.ckms.dto.response.AiAnalysisResponse;
import com.swp.ckms.dto.response.AiChatResponse;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.service.AiReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/reports")
@RequiredArgsConstructor
@Slf4j
public class AiReportController {

    private final AiReportService aiReportService;

    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AiAnalysisResponse>> analyzeExecutiveCockpit() {
        log.info("Received request for AI Executive Cockpit analysis");
        AiAnalysisResponse response = aiReportService.analyzeExecutiveCockpit();
        return ResponseEntity.ok(ApiResponse.success("Phân tích báo cáo bằng AI thành công", response));
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AiChatResponse>> chatWithExecutiveData(@Valid @RequestBody AiChatRequest request) {
        log.info("Received AI chat request: {}", request.getQuestion());
        AiChatResponse response = aiReportService.chatWithExecutiveData(request.getQuestion(), request.getChatHistory());
        return ResponseEntity.ok(ApiResponse.success("Trợ lý AI trả lời thành công", response));
    }
}
