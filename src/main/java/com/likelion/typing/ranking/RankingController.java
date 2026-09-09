package com.likelion.typing.ranking;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {
    private final RankingService service;
    public RankingController(RankingService service) { this.service = service; }

    @GetMapping
    List<RankingDtos.RankingResponse> rankings(@RequestParam Long categoryId) {
        return service.rankings(categoryId);
    }
}
