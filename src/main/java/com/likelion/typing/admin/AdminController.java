package com.likelion.typing.admin;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }

    @PostMapping("/login")
    AdminDtos.LoginResponse login(@Valid @RequestBody AdminDtos.LoginRequest request) { return service.login(request); }

    @GetMapping("/dashboard")
    AdminDtos.DashboardResponse dashboard() { return service.dashboard(); }

    @GetMapping("/payments")
    AdminDtos.PaymentHistoryResponse payments() { return service.paymentHistory(); }

    @GetMapping("/participants/all")
    AdminDtos.ParticipantHistoryResponse participantHistory() { return service.participantHistory(); }

    @GetMapping(value = "/participants", params = "phone")
    AdminDtos.ParticipantResponse findParticipant(@RequestParam String phone) { return service.findParticipant(phone); }

    @GetMapping(value = "/participants", params = "query")
    List<AdminDtos.ParticipantResponse> searchParticipants(@RequestParam String query) {
        return service.searchParticipants(query);
    }

    @PostMapping("/participants/{id}/passes")
    AdminDtos.IssuePassResponse issuePaidPass(@PathVariable Long id,
                                              @RequestBody(required = false) AdminDtos.IssuePassRequest request) {
        return service.issuePaidPass(id, request);
    }

    @PostMapping("/game-sessions/{id}/invalidate")
    AdminDtos.InvalidateResponse invalidate(@PathVariable Long id,
                                            @RequestBody AdminDtos.InvalidateRequest request) {
        return service.invalidate(id, request);
    }
}
