package com.likelion.typing.admin;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }

    @PostMapping("/login")
    AdminDtos.LoginResponse login(@Valid @RequestBody AdminDtos.LoginRequest request) { return service.login(request); }

    @GetMapping("/participants")
    AdminDtos.ParticipantResponse findParticipant(@RequestParam String phone) { return service.findParticipant(phone); }

    @PostMapping("/participants/{id}/passes")
    AdminDtos.PassResponse issuePaidPass(@PathVariable Long id) { return service.issuePaidPass(id); }

    @PostMapping("/game-sessions/{id}/invalidate")
    AdminDtos.InvalidateResponse invalidate(@PathVariable Long id,
                                            @RequestBody AdminDtos.InvalidateRequest request) {
        return service.invalidate(id, request);
    }
}
