package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.AdminAuthService;
import com.ray.service.EventTicketService;
import com.ray.service.VoucherQrTokenService;
import com.ray.realtime.AdminSseSessionRegistry;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.AdminEventTicketVO;
import com.ray.vo.VoucherQrTokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 管理事件票据、SSE 订阅和消费者固定券码。 */
@RestController
@RequestMapping
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "实时事件与固定二维码")
public class RealtimeEventController {
    private final VoucherQrTokenService qr;
    private final EventTicketService tickets;
    private final AdminAuthService admin;
    private final AdminSseSessionRegistry adminSessions;

    public RealtimeEventController(
            VoucherQrTokenService qr,
            EventTicketService tickets,
            AdminAuthService admin,
            AdminSseSessionRegistry adminSessions) {
        this.qr = qr;
        this.tickets = tickets;
        this.admin = admin;
        this.adminSessions = adminSessions;
    }

    @PostMapping("/v1/users/me/vouchers/{voucherId}/qr-tokens")
    @Operation(summary = "获取固定券二维码", operationId = "issueVoucherQrToken")
    public Result<VoucherQrTokenVO> qr(@PathVariable String voucherId) {
        return Result.ok(qr.issue(IdUtils.parse(voucherId, "voucherId")));
    }

    @PostMapping("/v1/admin/event-tickets")
    @Operation(summary = "申请管理事件票据", operationId = "issueAdminEventTicket")
    public Result<AdminEventTicketVO> ticket() {
        return Result.ok(tickets.issue());
    }

    @GetMapping(value = "/v1/admin/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅管理事件", operationId = "subscribeAdminEvents")
    @SecurityRequirements
    public SseEmitter events(@RequestParam String ticket) {
        Long adminId = tickets.consume(ticket);
        if (adminId == null) {
            throw com.ray.exception.BusinessException.forbidden("EVENT_TICKET_INVALID", "事件票据无效或已过期");
        }
        var current = admin.currentAdminById(adminId);
        SseEmitter emitter = adminSessions.register(adminId, current.permissions());
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("type", "CONNECTED")));
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }
}
