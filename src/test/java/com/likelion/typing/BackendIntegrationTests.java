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
import com.likelion.typing.payment.PaymentRecordRepository;
import com.likelion.typing.ranking.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired PaymentRecordRepository payments;
    @Autowired CategoryRepository categories;
    @Autowired SentenceRepository sentences;
    @Autowired GameSessionRepository games;
    @Autowired GameService gameService;
    @Autowired RankingService rankingService;
    @Autowired AdminService adminService;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        games.deleteAll();
        passes.deleteAll();
        payments.deleteAll();
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
    void invalidParticipantPhoneIsRejectedByServiceAndHttp() throws Exception {
        for (var phone : List.of("0101234567", "010-123-4567", "01112345678", "010123456789")) {
            assertCode(() -> identify("bad", phone), ErrorCode.VALIDATION_ERROR);

            mvc.perform(post("/api/participants/identify")
                    .contentType(APPLICATION_JSON)
                    .content("{\"nickname\":\"bad\",\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isBadRequest());
        }

        assertThat(participants.count()).isZero();
        assertThat(passes.count()).isZero();
    }

    @Test
    void databaseRejectsFreePassWithoutItsParticipantOwner() {
        var participant = participants.save(new Participant("constraint", "01056565656"));

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO play_passes
                (participant_id, free_participant_id, type, status, created_at, updated_at)
            VALUES (?, NULL, 'FREE', 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, participant.getId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
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

        var issuedOne = adminService.issuePaidPass(lion.participantId(), new AdminDtos.IssuePassRequest(1));
        assertThat(issuedOne.amountKrw()).isEqualTo(500);
        assertThat(issuedOne.passes()).hasSize(1);
        var slower = gameService.start(new GameDtos.StartRequest(lion.participantId(), category.getId()));
        assertThat(gameService.complete(slower.gameSessionId(), new GameDtos.CompleteRequest(55_000L)))
            .extracting(GameDtos.ResultResponse::personalBestMs, GameDtos.ResultResponse::personalBest)
            .containsExactly(50_000L, false);

        adminService.issuePaidPass(lion.participantId(), new AdminDtos.IssuePassRequest(1));
        var fastest = gameService.start(new GameDtos.StartRequest(lion.participantId(), category.getId()));
        gameService.complete(fastest.gameSessionId(), new GameDtos.CompleteRequest(45_000L));
        var tigerGame = gameService.start(new GameDtos.StartRequest(tiger.participantId(), category.getId()));
        gameService.complete(tigerGame.gameSessionId(), new GameDtos.CompleteRequest(45_000L));

        assertThat(rankingService.rankings(category.getId())).extracting("rank", "elapsedMs")
            .containsExactly(tuple(1, 45_000L), tuple(1, 45_000L));

        var invalidated = adminService.invalidate(fastest.gameSessionId(), new AdminDtos.InvalidateRequest("operator error", true));
        assertThat(invalidated.gameSessionStatus()).isEqualTo(GameSessionStatus.INVALIDATED);
        assertThat(invalidated.playPassStatus()).isEqualTo(PlayPassStatus.AVAILABLE);
        assertThat(gameService.find(fastest.gameSessionId()).status()).isEqualTo(GameSessionStatus.INVALIDATED);
        assertThat(rankingService.rankings(category.getId())).extracting("rank", "elapsedMs")
            .containsExactly(tuple(1, 45_000L), tuple(2, 50_000L));
    }

    @Test
    void rankingShowsOneBestCompletedRecordPerParticipantAndCategory() {
        var ch01 = category("CH01");
        var ch02 = category("CH02");
        var lion = identify("lion", "01010101010");

        var first = completeStarted(lion.participantId(), ch01.getId(), 35_000L);
        adminService.issuePaidPass(lion.participantId(), new AdminDtos.IssuePassRequest(1));
        completeStarted(lion.participantId(), ch01.getId(), 38_000L);
        adminService.issuePaidPass(lion.participantId(), new AdminDtos.IssuePassRequest(1));
        completeStarted(lion.participantId(), ch01.getId(), 35_000L);
        adminService.issuePaidPass(lion.participantId(), new AdminDtos.IssuePassRequest(1));
        var inProgress = gameService.start(new GameDtos.StartRequest(lion.participantId(), ch01.getId()));
        adminService.invalidate(inProgress.gameSessionId(), new AdminDtos.InvalidateRequest("restore test", true));
        adminService.issuePaidPass(lion.participantId(), new AdminDtos.IssuePassRequest(1));
        completeStarted(lion.participantId(), ch02.getId(), 30_000L);

        var retry = games.findAll().stream()
            .filter(game -> game.getCategory().getId().equals(ch01.getId()) && Long.valueOf(38_000L).equals(game.getElapsedMs()))
            .findFirst().orElseThrow();

        assertThat(games.findByParticipantIdOrderByCreatedAtDesc(lion.participantId())).hasSize(5);
        assertThat(rankingService.rankings(ch01.getId())).extracting("nickname", "elapsedMs")
            .containsExactly(tuple("lion", 35_000L));
        assertThat(rankingService.rankings(ch02.getId())).extracting("nickname", "elapsedMs")
            .containsExactly(tuple("lion", 30_000L));
        assertThat(gameService.find(retry.getId()))
            .extracting(GameDtos.ResultResponse::personalBestMs, GameDtos.ResultResponse::personalBest, GameDtos.ResultResponse::rank)
            .containsExactly(35_000L, false, 1);
        assertThat(gameService.find(first.gameSessionId()).rank()).isEqualTo(1);
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
    void startResponseAndPlayStateUseAuthoritativePassState() {
        var category = category("CH01");
        var participant = identify("lion", "01051515151");
        assertThat(participant.availablePassCount()).isOne();

        var first = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));
        assertThat(first.resumedExisting()).isFalse();
        assertThat(first.passConsumed()).isTrue();
        assertThat(first.availablePassCount()).isZero();
        assertThat(passes.findByParticipantIdOrderByCreatedAtAsc(participant.participantId()))
            .filteredOn(pass -> pass.getStatus() == PlayPassStatus.CONSUMED).hasSize(1);

        var resumed = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));
        assertThat(resumed.gameSessionId()).isEqualTo(first.gameSessionId());
        assertThat(resumed.resumedExisting()).isTrue();
        assertThat(resumed.passConsumed()).isFalse();
        assertThat(resumed.availablePassCount()).isZero();
        assertThat(games.count()).isOne();
        assertThat(passes.findByParticipantIdOrderByCreatedAtAsc(participant.participantId()))
            .filteredOn(pass -> pass.getStatus() == PlayPassStatus.CONSUMED).hasSize(1);

        var active = participantService.playState(participant.participantId());
        assertThat(active.availablePassCount()).isZero();
        assertThat(active.activeGame().gameSessionId()).isEqualTo(first.gameSessionId());
        assertThat(active.activeGame().categoryId()).isEqualTo(category.getId());

        gameService.complete(first.gameSessionId(), new GameDtos.CompleteRequest(3_000L));
        var noActive = participantService.playState(participant.participantId());
        assertThat(noActive.availablePassCount()).isZero();
        assertThat(noActive.activeGame()).isNull();
        assertCode(() -> gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId())),
            ErrorCode.NO_AVAILABLE_PASS);
        assertThat(games.count()).isOne();

        adminService.issuePaidPass(participant.participantId(), new AdminDtos.IssuePassRequest(2));
        assertThat(participantService.playState(participant.participantId()).availablePassCount()).isEqualTo(2);
        var paid = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));
        assertThat(paid.passConsumed()).isTrue();
        assertThat(paid.resumedExisting()).isFalse();
        assertThat(paid.availablePassCount()).isEqualTo(1);
    }

    @Test
    void freePassIsStillConsumedBeforePaidPass() {
        var category = category("CH02");
        var participant = identify("lion", "01061616161");
        adminService.issuePaidPass(participant.participantId(), new AdminDtos.IssuePassRequest(1));

        var game = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));

        assertThat(game.availablePassCount()).isOne();
        assertThat(passes.findByParticipantIdOrderByCreatedAtAsc(participant.participantId()))
            .filteredOn(pass -> pass.getType() == PlayPassType.FREE)
            .allMatch(pass -> pass.getStatus() == PlayPassStatus.CONSUMED);
        assertThat(passes.findByParticipantIdOrderByCreatedAtAsc(participant.participantId()))
            .filteredOn(pass -> pass.getType() == PlayPassType.PAID)
            .allMatch(pass -> pass.getStatus() == PlayPassStatus.AVAILABLE);
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
    void elapsedMillisecondsUsesBigint() {
        var category = category("CH01");
        var participant = identify("lion", "01012121212");
        var game = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));

        var result = gameService.complete(game.gameSessionId(), new GameDtos.CompleteRequest(5_000_000_000L));

        assertThat(result.elapsedMs()).isEqualTo(5_000_000_000L);
        assertThat(games.findById(game.gameSessionId()).orElseThrow().getElapsedMs()).isEqualTo(5_000_000_000L);
    }

    @Test
    void invalidateAndRestoreRollBackTogetherOnConflict() {
        var category = category("CH01");
        var participant = identify("lion", "01034343434");
        var started = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));
        gameService.complete(started.gameSessionId(), new GameDtos.CompleteRequest(4_000L));
        var completed = games.findById(started.gameSessionId()).orElseThrow();
        games.save(new GameSession(completed.getParticipant(), completed.getCategory(), completed.getPlayPass()));

        assertCode(() -> adminService.invalidate(started.gameSessionId(), new AdminDtos.InvalidateRequest("conflict", true)),
            ErrorCode.INVALID_GAME_STATE);

        assertThat(games.findById(started.gameSessionId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.COMPLETED);
        assertThat(passes.findById(completed.getPlayPass().getId()).orElseThrow().getStatus())
            .isEqualTo(PlayPassStatus.CONSUMED);
    }

    @Test
    void paidIssueIsCumulativeAndRecordsPayment() throws Exception {
        var participant = identify("lion", "01022223333");
        var category = category("CH02");
        completeStarted(participant.participantId(), category.getId(), 4_000L);

        var first = adminService.issuePaidPass(participant.participantId(), new AdminDtos.IssuePassRequest(1));
        var second = adminService.issuePaidPass(participant.participantId(), new AdminDtos.IssuePassRequest(2));

        assertThat(first.availablePaidPassCount()).isEqualTo(1);
        assertThat(second.availablePaidPassCount()).isEqualTo(3);
        assertThat(second.amountKrw()).isEqualTo(1_000);
        assertThat(payments.totalAmountKrwByParticipantId(participant.participantId())).isEqualTo(1_500);
        assertThat(passes.findByParticipantIdOrderByCreatedAtAsc(participant.participantId()))
            .filteredOn(pass -> pass.getType() == PlayPassType.PAID && pass.getStatus() == PlayPassStatus.AVAILABLE).hasSize(3);

        var game = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));
        assertThat(passes.countByParticipantIdAndTypeAndStatus(participant.participantId(), PlayPassType.PAID, PlayPassStatus.AVAILABLE))
            .isEqualTo(2);
        gameService.complete(game.gameSessionId(), new GameDtos.CompleteRequest(3_000L));
    }

    @Test
    void invalidPaidQuantityIsRejected() {
        var participant = identify("lion", "01022224444");

        assertCode(() -> adminService.issuePaidPass(participant.participantId(), new AdminDtos.IssuePassRequest(0)),
            ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void dashboardAndParticipantSummaryUseServerAggregates() {
        var ch01 = category("CH01");
        var ch02 = category("CH02");
        var lion = identify("lion", "01020202020");
        var tiger = identify("tiger", "01020202021");
        completeStarted(lion.participantId(), ch01.getId(), 5_000L);
        adminService.issuePaidPass(lion.participantId(), new AdminDtos.IssuePassRequest(2));
        var paid = completeStarted(lion.participantId(), ch02.getId(), 4_000L);
        adminService.issuePaidPass(tiger.participantId(), new AdminDtos.IssuePassRequest(1));
        var invalid = gameService.start(new GameDtos.StartRequest(tiger.participantId(), ch01.getId()));
        adminService.invalidate(invalid.gameSessionId(), new AdminDtos.InvalidateRequest("bad keyboard", false));

        var dashboard = adminService.dashboard();
        assertThat(dashboard.totalParticipants()).isEqualTo(2);
        assertThat(dashboard.totalPlayCount()).isEqualTo(3);
        assertThat(dashboard.freePlayCount()).isEqualTo(2);
        assertThat(dashboard.paidPlayCount()).isEqualTo(1);
        assertThat(dashboard.totalPaymentAmountKrw()).isEqualTo(1_500);
        assertThat(dashboard.availablePaidPassCount()).isEqualTo(2);
        assertThat(dashboard.ch01PlayCount()).isEqualTo(2);
        assertThat(dashboard.ch02PlayCount()).isEqualTo(1);
        assertThat(dashboard.completedGameCount()).isEqualTo(2);
        assertThat(dashboard.invalidatedGameCount()).isEqualTo(1);

        var detail = adminService.findParticipant("01020202020");
        assertThat(detail.summary().totalPaymentAmountKrw()).isEqualTo(1_000);
        assertThat(detail.summary().completedGameCount()).isEqualTo(2);
        assertThat(detail.summary().invalidatedGameCount()).isZero();
        assertThat(detail.summary().bestRecords()).extracting("categoryCode", "elapsedMs")
            .contains(tuple("CH01", 5_000L), tuple("CH02", 4_000L));
        assertThat(gameService.find(paid.gameSessionId()).rank()).isEqualTo(1);
    }

    @Test
    void adminPaymentHistorySummarizesPaymentsAndSortsNewestFirst() throws Exception {
        assertThat(adminService.paymentHistory())
            .extracting(AdminDtos.PaymentHistoryResponse::totalPaymentAmountKrw,
                AdminDtos.PaymentHistoryResponse::totalPaymentCount,
                AdminDtos.PaymentHistoryResponse::totalPaidPassQuantity,
                response -> response.payments().size())
            .containsExactly(0L, 0L, 0L, 0);

        var lion = identify("lion", "01030303030");
        var tiger = identify("tiger", "01030303031");
        adminService.issuePaidPass(lion.participantId(), new AdminDtos.IssuePassRequest(1));
        Thread.sleep(2);
        adminService.issuePaidPass(tiger.participantId(), new AdminDtos.IssuePassRequest(3));

        var history = adminService.paymentHistory();
        assertThat(history.totalPaymentAmountKrw()).isEqualTo(2_000);
        assertThat(history.totalPaymentCount()).isEqualTo(2);
        assertThat(history.totalPaidPassQuantity()).isEqualTo(4);
        assertThat(history.totalPaymentAmountKrw()).isEqualTo(adminService.dashboard().totalPaymentAmountKrw());
        assertThat(history.payments()).extracting("nickname", "phone", "quantity", "amountKrw")
            .containsExactly(
                tuple("tiger", "01030303031", 3, 1_500),
                tuple("lion", "01030303030", 1, 500));

        mvc.perform(get("/api/admin/payments"))
            .andExpect(status().isForbidden());

        var login = mvc.perform(post("/api/admin/login").contentType(APPLICATION_JSON)
                .content("{\"password\":\"" + ADMIN_PASSWORD + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var token = json.readTree(login).get("token").asText();
        mvc.perform(get("/api/admin/payments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPaymentAmountKrw").value(2_000))
            .andExpect(jsonPath("$.totalPaymentCount").value(2))
            .andExpect(jsonPath("$.totalPaidPassQuantity").value(4))
            .andExpect(jsonPath("$.payments[0].nickname").value("tiger"));
    }

    @Test
    void adminEndpointsRequireAuthAndNeverExposePhonePublicly() throws Exception {
        var category = category("CH01");
        var participant = identify("lion", "01012340000");

        mvc.perform(get("/api/admin/participants").param("phone", "01012340000"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payments"))
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
        mvc.perform(get("/api/admin/participants").param("query", "010-1234-0000")
                .header("Authorization", "Bearer " + token.asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].phone").value("01012340000"));
        mvc.perform(get("/api/admin/participants").param("query", "li")
                .header("Authorization", "Bearer " + token.asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].nickname").value("lion"));

        identify("lionel", "01012340001");
        mvc.perform(get("/api/admin/participants").param("query", "lion")
                .header("Authorization", "Bearer " + token.asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        var game = gameService.start(new GameDtos.StartRequest(participant.participantId(), category.getId()));
        mvc.perform(get("/api/participants/{id}/play-state", participant.participantId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availablePassCount").value(0))
            .andExpect(jsonPath("$.activeGame.gameSessionId").value(game.gameSessionId()))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("phone"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("01012340000"))));
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

    private GameDtos.ResultResponse completeStarted(Long participantId, Long categoryId, long elapsedMs) {
        var game = gameService.start(new GameDtos.StartRequest(participantId, categoryId));
        return gameService.complete(game.gameSessionId(), new GameDtos.CompleteRequest(elapsedMs));
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
