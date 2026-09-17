package com.likelion.typing.game;

import com.likelion.typing.category.*;
import com.likelion.typing.common.exception.AppException;
import com.likelion.typing.common.exception.ErrorCode;
import com.likelion.typing.participant.ParticipantRepository;
import com.likelion.typing.pass.*;
import com.likelion.typing.ranking.RankingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GameService {
    private final ParticipantRepository participants;
    private final CategoryRepository categories;
    private final SentenceRepository sentences;
    private final PlayPassRepository passes;
    private final GameSessionRepository games;
    private final RankingService rankings;

    public GameService(ParticipantRepository participants, CategoryRepository categories,
                       SentenceRepository sentences, PlayPassRepository passes,
                       GameSessionRepository games, RankingService rankings) {
        this.participants = participants;
        this.categories = categories;
        this.sentences = sentences;
        this.passes = passes;
        this.games = games;
        this.rankings = rankings;
    }

    @Transactional
    public GameDtos.StartResponse start(GameDtos.StartRequest request) {
        var participant = participants.findByIdForUpdate(request.participantId())
            .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));
        var category = categories.findById(request.categoryId())
            .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        var active = games.findFirstByParticipantIdAndStatus(participant.getId(), GameSessionStatus.IN_PROGRESS);
        if (active.isPresent()) {
            if (!active.get().getCategory().getId().equals(category.getId()))
                throw new AppException(ErrorCode.ACTIVE_GAME_EXISTS);
            return startResponse(active.get(), category, true, false,
                passes.countByParticipantIdAndStatus(participant.getId(), PlayPassStatus.AVAILABLE));
        }

        var categorySentences = requireSentences(category.getId());
        var pass = firstAvailable(participant.getId());
        pass.consume();
        passes.flush();
        var game = games.save(new GameSession(participant, category, pass));
        return startResponse(game, category, categorySentences, false, true,
            passes.countByParticipantIdAndStatus(participant.getId(), PlayPassStatus.AVAILABLE));
    }

    @Transactional
    public GameDtos.ResultResponse complete(Long id, GameDtos.CompleteRequest request) {
        if (request.elapsedMs() == null || request.elapsedMs() <= 0)
            throw new AppException(ErrorCode.INVALID_ELAPSED_TIME);
        var game = games.findByIdForUpdate(id)
            .orElseThrow(() -> new AppException(ErrorCode.GAME_SESSION_NOT_FOUND));
        if (game.getStatus() != GameSessionStatus.IN_PROGRESS)
            throw new AppException(ErrorCode.INVALID_GAME_STATE);

        var previousBest = games.findPersonalBest(game.getParticipant().getId(), game.getCategory().getId());
        boolean personalBest = previousBest == null || request.elapsedMs() < previousBest;
        game.complete(request.elapsedMs());
        games.flush();
        long best = games.findPersonalBest(game.getParticipant().getId(), game.getCategory().getId());
        return result(game, best, personalBest);
    }

    @Transactional(readOnly = true)
    public GameDtos.ResultResponse find(Long id) {
        var game = games.findById(id).orElseThrow(() -> new AppException(ErrorCode.GAME_SESSION_NOT_FOUND));
        if (game.getStatus() != GameSessionStatus.COMPLETED)
            return new GameDtos.ResultResponse(game.getId(), game.getStatus(), game.getElapsedMs(), null, false, null);
        long best = games.findPersonalBest(game.getParticipant().getId(), game.getCategory().getId());
        return result(game, best, game.getElapsedMs() == best);
    }

    private PlayPass firstAvailable(Long participantId) {
        for (var type : List.of(PlayPassType.FREE, PlayPassType.PAID)) {
            var matches = passes.findAvailableForUpdate(participantId, type, PlayPassStatus.AVAILABLE);
            if (!matches.isEmpty()) return matches.getFirst();
        }
        throw new AppException(ErrorCode.NO_AVAILABLE_PASS);
    }

    private List<Sentence> requireSentences(Long categoryId) {
        var result = sentences.findByCategoryIdOrderBySequenceAsc(categoryId);
        if (result.size() != 5) throw new AppException(ErrorCode.SENTENCE_CONTENT_INVALID);
        return result;
    }

    private GameDtos.StartResponse startResponse(GameSession game, Category category) {
        return startResponse(game, category, requireSentences(category.getId()), false, false,
            passes.countByParticipantIdAndStatus(game.getParticipant().getId(), PlayPassStatus.AVAILABLE));
    }

    private GameDtos.StartResponse startResponse(GameSession game, Category category, boolean resumedExisting,
                                                boolean passConsumed, long availablePassCount) {
        return startResponse(game, category, requireSentences(category.getId()), resumedExisting, passConsumed, availablePassCount);
    }

    private GameDtos.StartResponse startResponse(GameSession game, Category category, List<Sentence> sentenceList,
                                                boolean resumedExisting, boolean passConsumed, long availablePassCount) {
        var categoryDto = new CategoryDtos.CategoryResponse(category.getId(), category.getCode(), category.getName());
        var sentenceDtos = sentenceList.stream()
            .map(sentence -> new GameDtos.SentenceResponse(sentence.getSequence(), sentence.getContent())).toList();
        return new GameDtos.StartResponse(game.getId(), categoryDto, sentenceDtos, resumedExisting, passConsumed, availablePassCount);
    }

    private GameDtos.ResultResponse result(GameSession game, long best, boolean personalBest) {
        return new GameDtos.ResultResponse(game.getId(), game.getStatus(), game.getElapsedMs(), best, personalBest,
            rankings.rankFor(game.getParticipant().getId(), game.getCategory().getId()));
    }
}
