package com.likelion.typing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.likelion.typing.admin.*;
import com.likelion.typing.category.*;
import com.likelion.typing.common.exception.AppException;
import com.likelion.typing.common.exception.ErrorCode;
import com.likelion.typing.game.*;
import com.likelion.typing.participant.*;
import com.likelion.typing.pass.*;
import com.likelion.typing.ranking.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.*;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendIntegrationTests {
    private static final String ADMIN_PASSWORD = "test-admin-password";

    @DynamicPropertySource
    static void adminProperties(DynamicPropertyRegistry registry) {
        registry.add("app.admin.password-hash", () -> new BCryptPasswordEncoder().encode(ADMIN_PASSWORD));
    }

    @Autowired ParticipantService participantService;
    @Autowired ParticipantRepository participants;
    @Autowired PlayPassRepository passes;
    @Autowired CategoryRepository categories;
    @Autowired SentenceRepository sentences;
    @Autowired GameSessionRepository games;
    @Autowired GameService gameService;
    @Autowired RankingService rankingService;
    @Autowired AdminService adminService;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void cleanDatabase() {
        games.deleteAll();
        passes.deleteAll();
        sentences.deleteAll();
        categories.deleteAll();
        participants.deleteAll();
    }

    @Test
    void identifyNormalizesPhoneAndCreatesOnlyOneFreePass() {
        var first = identify("likelion", "010-1234 5678");
        var second = identify("likelion", "01012345678");

        assertThat(first.isNewParticipant()).isTrue();
        assertThat(second.isNewParticipant()).isFalse();
        assertThat(second.participantId()).isEqualTo(first.participantId());
        assertThat(participants.count()).isOne();
        assertThat(passes.count()).isOne();
        assertThatThrownBy(() -> identify("other", "010-1234-5678"))
            .isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.code()).isEqualTo(ErrorCode.NICKNAME_MISMATCH));
    }

    @Test
    void concurrentIdentifyCreatesOneParticipantAndOneFreePass() throws Exception {
        var responses = concurrently(
            () -> identify("lion", "010-9999-0000"),
            () -> identify("lion", "010 9999 0000"));

        assertThat(responses.stream().map(ParticipantDtos.IdentifyResponse::participantId).distinct()).hasSize(1);
        assertThat(participants.count()).isOne();
        assertThat(passes.count()).isOne();
    }

    @Test
    void gameFlowKeepsBestRanksTiesAndRestoresInvalidatedPass() {
        var category = category("CH01");
        var lion = identify("lion", "01011112222");
        var tiger = identify("tiger", "01033334444");

        var first = gameService.start(new GameDtos.StartRequest(lion.participantId(), category.getId()));
        assertThat(gameService.start(new GameDtos.StartRequest(lion.participantId(), category.getId())).gameSessionId())
            .isEqualTo(first.gameSessionId());
        var firstResult = gameService.complete(first.gameSessionId(), new GameDtos.CompleteRequest(50_000L));
        assertThat(firstResult.personalBest()).isTrue();
        assertThatThrownBy(() -> gameService.complete(first.gameSessionId(), new GameDtos.CompleteRequest(50_000L)))
            .isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_GAME_STATE));

        adminService.issuePaidPass(lion.participantId());
        assertThat(adminService.issuePaidPass(lion.participantId()).id())
            .isEqualTo(adminService.issuePaidPass(lion.participantId()).id());
        var slower = gameService.start(new GameDtos.StartRequest(lion.participantId(), category.getId()));
        assertThat(gameService.complete(slower.gameSessionId(), new GameDtos.CompleteRequest(55_000L)))
            .extracting(GameDtos.ResultResponse::personalBestMs, GameDtos.ResultResponse::personalBest)
            .containsExactly(50_000L, false);

        adminService.issuePaidPass(lion.participantId());
        var fastest = gameService.start(new GameDtos.StartRequest(lion.participantId(), category.getId()));
        gameService.complete(fastest.gameSessionId(), new GameDtos.CompleteRequest(45_000L));
        var tigerGame = gameService.start(new GameDtos.StartRequest(tiger.participantId(), category.getId()));
        gameService.complete(tigerGame.gameSessionId(), new GameDtos.CompleteRequest(45_000L));

        assertThat(rankingService.rankings(category.getId())).extracting("rank", "elapsedMs")
            .containsExactly(tuple(1, 45_000L), tuple(1, 45_000L));

        var invalidated = adminService.invalidate(fastest.gameSessionId(), new AdminDtos.InvalidateRequest(true));
        assertThat(invalidated.gameSessionStatus()).isEqualTo(GameSessionStatus.INVALIDATED);
        assertThat(invalidated.playPassStatus()).isEqualTo(PlayPassStatus.AVAILABLE);
        assertThat(gameService.find(fastest.gameSessionId()).status()).isEqualTo(GameSessionStatus.INVALIDATED);
        assertThat(rankingService.rankings(category.getId())).extracting("rank", "elapsedMs")
            .containsExactly(tuple(1, 45_000L), tuple(2, 50_000L));
    }

    @Test
    void startRequiresFiveSentencesAndAnAvailablePass() {
        var emptyCategory = categories.save(new Category("CH02", "test category"));
        var participant = identify("lion", "01055556666");

        assertCode(() -> gameService.start(new GameDtos.StartRequest(participant.participantId(), emptyCategory.getId())),
            ErrorCode.SENTENCE_CONTENT_INVALID);

        for (int i = 1; i <= 5; i++) sentences.save(new Sentence(emptyCategory, i, "sentence " + i));
        var game = gameService.start(new GameDtos.StartRequest(participant.participantId(), emptyCategory.getId()));
        assertThat(game.sentences()).extracting(GameDtos.SentenceResponse::sequence).containsExactly(1, 2, 3, 4, 5);
        gameService.complete(game.gameSessionId(), new GameDtos.CompleteRequest(1_000L));
        assertCode(() -> gameService.start(new GameDtos.StartRequest(participant.participantId(), emptyCategory.getId())),
            ErrorCode.NO_AVAILABLE_PASS);
    }

    @Test
    void concurrentStartConsumesOnePassAndCreatesOneSession() throws Exception {
        var category = category("CH03");
        var participant = identify("lion", "01077778888");
        var responses = concurrently(
            () -> gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId())),
            () -> gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId())));

        assertThat(responses.stream().map(GameDtos.StartResponse::gameSessionId).distinct()).hasSize(1);
        assertThat(games.count()).isOne();
        assertThat(passes.findAll()).allMatch(pass -> pass.getStatus() == PlayPassStatus.CONSUMED);
    }

    @Test
    void concurrentCompleteAcceptsOnlyOneRequest() throws Exception {
        var category = category("CH01");
        var participant = identify("lion", "01088889999");
        var game = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));

        var outcomes = concurrently(
            () -> completeOutcome(game.gameSessionId()),
            () -> completeOutcome(game.gameSessionId()));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(games.findById(game.gameSessionId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.COMPLETED);
    }

    @Test
    void concurrentPaidIssueReturnsOneAvailablePass() throws Exception {
        var participant = identify("lion", "01022223333");

        var issued = concurrently(
            () -> adminService.issuePaidPass(participant.participantId()),
            () -> adminService.issuePaidPass(participant.participantId()));

        assertThat(issued.stream().map(AdminDtos.PassResponse::id).distinct()).hasSize(1);
        assertThat(passes.findByParticipantIdOrderByCreatedAtAsc(participant.participantId()))
            .filteredOn(pass -> pass.getType() == PlayPassType.PAID).hasSize(1);
    }

    @Test
    void adminEndpointsRequireAuthAndNeverExposePhonePublicly() throws Exception {
        var category = category("CH01");
        var participant = identify("lion", "01012340000");

        mvc.perform(get("/api/admin/participants").param("phone", "01012340000"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/login").contentType(APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));

        var login = mvc.perform(post("/api/admin/login").contentType(APPLICATION_JSON)
                .content("{\"password\":\"" + ADMIN_PASSWORD + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode token = json.readTree(login).get("token");
        mvc.perform(get("/api/admin/participants").param("phone", "010-1234-0000")
                .header("Authorization", "Bearer " + token.asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("01012340000"));

        var game = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));
        gameService.complete(game.gameSessionId(), new GameDtos.CompleteRequest(2_000L));
        mvc.perform(get("/api/rankings").param("categoryId", category.getId().toString()))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("phone"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("01012340000"))));
    }

    private ParticipantDtos.IdentifyResponse identify(String nickname, String phone) {
        return participantService.identify(new ParticipantDtos.IdentifyRequest(nickname, phone));
    }

    private Category category(String code) {
        var category = categories.save(new Category(code, "test " + code));
        for (int i = 1; i <= 5; i++) sentences.save(new Sentence(category, i, "sentence " + i));
        return category;
    }

    private boolean completeOutcome(Long gameId) {
        try {
            gameService.complete(gameId, new GameDtos.CompleteRequest(3_000L));
            return true;
        } catch (AppException exception) {
            assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_GAME_STATE);
            return false;
        }
    }

    private void assertCode(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AppException.class,
            exception -> assertThat(exception.code()).isEqualTo(code));
    }

    @SafeVarargs
    private <T> List<T> concurrently(Callable<T>... actions) throws Exception {
        try (var executor = Executors.newFixedThreadPool(actions.length)) {
            var start = new CountDownLatch(1);
            var futures = java.util.Arrays.stream(actions)
                .map(action -> executor.submit(() -> { start.await(); return action.call(); })).toList();
            start.countDown();
            var result = new java.util.ArrayList<T>();
            for (var future : futures) result.add(future.get(10, TimeUnit.SECONDS));
            return result;
        }
    }
}
