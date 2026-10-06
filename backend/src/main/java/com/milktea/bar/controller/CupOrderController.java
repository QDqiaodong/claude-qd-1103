package com.milktea.bar.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.milktea.bar.common.ApiResponse;
import com.milktea.bar.dto.CupOrderCreateRequest;
import com.milktea.bar.dto.CupOrderPreviewResponse;
import com.milktea.bar.entity.CupOrder;
import com.milktea.bar.service.CupOrderService;

@RestController
@RequestMapping("/api/cup-orders")
public class CupOrderController {

    private final CupOrderService cupOrderService;

    public CupOrderController(CupOrderService cupOrderService) {
        this.cupOrderService = cupOrderService;
    }

    @GetMapping
    public ApiResponse<List<CupOrder>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(cupOrderService.list(status));
    }

    /** 出杯预览：预计扣减 / 不足与停用原料 / 预计金额与积分（不落库） */
    @PostMapping("/preview")
    public ApiResponse<CupOrderPreviewResponse> preview(@RequestBody CupOrderCreateRequest req) {
        return ApiResponse.success(cupOrderService.preview(req));
    }

    @PostMapping
    public ApiResponse<CupOrder> create(@RequestBody CupOrderCreateRequest req) {
        return ApiResponse.success(cupOrderService.create(req));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<CupOrder> refund(@PathVariable Long id) {
        return ApiResponse.success(cupOrderService.refund(id));
    }
}
