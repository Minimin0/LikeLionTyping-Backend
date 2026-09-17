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

    @GetMapping(value = "/participants", params = "phone")
    AdminDtos.ParticipantResponse findParticipant(@RequestParam String phone) { return service.findParticipant(phone); }

    @GetMapping(value = "/participants", params = "query")
    List<AdminDtos.ParticipantResponse> searchParticipants(@RequestParam String query) {
        return service.searchParticipants(query);
    }

    @PostMapping("/participants/{id}/passes")
    AdminDtos.PassResponse issuePaidPass(@PathVariable Long id) { return service.issuePaidPass(id); }

    @PostMapping("/game-sessions/{id}/invalidate")
    AdminDtos.InvalidateResponse invalidate(@PathVariable Long id,
                                            @RequestBody AdminDtos.InvalidateRequest request) {
        return service.invalidate(id, request);
    }
}
