package com.likelion.typing.participant;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/participants")
public class ParticipantController {
    private final ParticipantService service;

    public ParticipantController(ParticipantService service) { this.service = service; }

    @PostMapping("/identify")
    ParticipantDtos.IdentifyResponse identify(@Valid @RequestBody ParticipantDtos.IdentifyRequest request) {
        return service.identify(request);
    }
}
