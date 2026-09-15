package com.likelion.typing.ranking;

import com.likelion.typing.common.exception.AppException;
import com.likelion.typing.common.exception.ErrorCode;
import com.likelion.typing.category.CategoryRepository;
import com.likelion.typing.game.GameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RankingService {
    private final CategoryRepository categories;
    private final GameSessionRepository games;

    public RankingService(CategoryRepository categories, GameSessionRepository games) {
        this.categories = categories;
        this.games = games;
    }

    @Transactional(readOnly = true)
    public List<RankingDtos.RankingResponse> rankings(Long categoryId) {
        if (!categories.existsById(categoryId)) throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        var rows = games.findBestRecords(categoryId);
        var result = new ArrayList<RankingDtos.RankingResponse>(rows.size());
        long previous = -1;
        int rank = 0;
        for (int index = 0; index < rows.size(); index++) {
            long elapsed = ((Number) rows.get(index)[2]).longValue();
            if (elapsed != previous) rank = index + 1;
            result.add(new RankingDtos.RankingResponse(rank, (String) rows.get(index)[1], elapsed));
            previous = elapsed;
        }
        return result;
    }

    public Integer rankFor(Long participantId, Long categoryId) {
        var rows = games.findBestRecords(categoryId);
        long previous = -1;
        int rank = 0;
        for (int index = 0; index < rows.size(); index++) {
            long elapsed = ((Number) rows.get(index)[2]).longValue();
            if (elapsed != previous) rank = index + 1;
            if (((Number) rows.get(index)[0]).longValue() == participantId) return rank;
            previous = elapsed;
        }
        return null;
    }
}
