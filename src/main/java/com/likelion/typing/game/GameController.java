package com.likelion.typing.game;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game-sessions")
public class GameController {
    private final GameService service;
    public GameController(GameService service) { this.service = service; }

    @PostMapping
    GameDtos.StartResponse start(@Valid @RequestBody GameDtos.StartRequest request) { return service.start(request); }

    @PostMapping("/{id}/complete")
    GameDtos.ResultResponse complete(@PathVariable Long id, @Valid @RequestBody GameDtos.CompleteRequest request) {
        return service.complete(id, request);
    }

    @GetMapping("/{id}")
    GameDtos.ResultResponse find(@PathVariable Long id) { return service.find(id); }
}
