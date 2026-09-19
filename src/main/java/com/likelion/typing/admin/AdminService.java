package com.likelion.typing.admin;

import com.likelion.typing.common.exception.AppException;
import com.likelion.typing.common.exception.ErrorCode;
import com.likelion.typing.game.*;
import com.likelion.typing.category.CategoryRepository;
import com.likelion.typing.payment.*;
import com.likelion.typing.participant.ParticipantRepository;
import com.likelion.typing.pass.*;
import com.likelion.typing.security.AdminAuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {
    private final AdminAuthService auth;
    private final ParticipantRepository participants;
    private final CategoryRepository categories;
    private final PlayPassRepository passes;
    private final GameSessionRepository games;
    private final PaymentRecordRepository payments;

    public AdminService(AdminAuthService auth, ParticipantRepository participants,
                        CategoryRepository categories, PlayPassRepository passes,
                        GameSessionRepository games, PaymentRecordRepository payments) {
        this.auth = auth;
        this.participants = participants;
        this.categories = categories;
        this.passes = passes;
        this.games = games;
        this.payments = payments;
    }

    public AdminDtos.LoginResponse login(AdminDtos.LoginRequest request) { return auth.login(request.password()); }

    @Transactional(readOnly = true)
    public AdminDtos.DashboardResponse dashboard() {
        var categoryCounts = games.countByCategoryCode().stream()
            .collect(java.util.stream.Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));
        return new AdminDtos.DashboardResponse(
            participants.count(),
            games.count(),
            games.countByPlayPassType(PlayPassType.FREE),
            games.countByPlayPassType(PlayPassType.PAID),
            payments.totalAmountKrw(),
            passes.countByTypeAndStatus(PlayPassType.PAID, PlayPassStatus.AVAILABLE),
            categoryCounts.getOrDefault("CH01", 0L),
            categoryCounts.getOrDefault("CH02", 0L),
            categoryCounts.getOrDefault("CH03", 0L),
            games.countByStatus(GameSessionStatus.COMPLETED),
            games.countByStatus(GameSessionStatus.INVALIDATED));
    }

    @Transactional(readOnly = true)
    public AdminDtos.PaymentHistoryResponse paymentHistory() {
        var history = payments.findAllWithParticipantOrderByCreatedAtDesc();
        return new AdminDtos.PaymentHistoryResponse(
            history.stream().mapToLong(PaymentRecord::getAmountKrw).sum(),
            history.size(),
            history.stream().mapToLong(PaymentRecord::getQuantity).sum(),
            history.stream().map(payment -> new AdminDtos.PaymentHistoryItemResponse(
                payment.getId(),
                payment.getParticipant().getId(),
                payment.getParticipant().getNickname(),
                payment.getParticipant().getPhone(),
                payment.getQuantity(),
                payment.getAmountKrw(),
                payment.getCreatedAt())).toList());
    }

    @Transactional(readOnly = true)
    public AdminDtos.ParticipantResponse findParticipant(String rawPhone) {
        var phone = normalizePhone(rawPhone);
        var participant = participants.findByPhone(phone)
            .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));
        return response(participant);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.ParticipantResponse> searchParticipants(String rawQuery) {
        var query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isBlank()) throw new AppException(ErrorCode.VALIDATION_ERROR);
        var matches = looksLikePhone(query)
            ? participants.findByPhone(normalizePhone(query)).stream().toList()
            : participants.findByNicknameContainingIgnoreCaseOrderByCreatedAtDesc(query);
        return matches.stream().map(this::response).toList();
    }

    private AdminDtos.ParticipantResponse response(com.likelion.typing.participant.Participant participant) {
        var passDtos = passes.findByParticipantIdOrderByCreatedAtAsc(participant.getId()).stream()
            .map(pass -> new AdminDtos.PassResponse(pass.getId(), pass.getType(), pass.getStatus(), pass.getCreatedAt())).toList();
        var gameDtos = games.findByParticipantIdOrderByCreatedAtDesc(participant.getId()).stream()
            .map(game -> new AdminDtos.SessionResponse(game.getId(), game.getCategory().getId(), game.getPlayPass().getId(),
                game.getStatus(), game.getElapsedMs(), game.getStartedAt(), game.getCompletedAt(), game.getInvalidationReason())).toList();
        var paymentDtos = payments.findByParticipantIdOrderByCreatedAtDesc(participant.getId()).stream()
            .map(this::paymentResponse).toList();
        var bests = categories.findAllByOrderByCodeAsc().stream()
            .map(category -> new AdminDtos.CategoryBestResponse(category.getCode(),
                games.findPersonalBest(participant.getId(), category.getId()))).toList();
        var summary = new AdminDtos.ParticipantSummary(
            passDtos.stream().anyMatch(pass -> pass.type() == PlayPassType.FREE && pass.status() == PlayPassStatus.CONSUMED),
            passes.countByParticipantIdAndStatus(participant.getId(), PlayPassStatus.AVAILABLE),
            passes.countByParticipantIdAndTypeAndStatus(participant.getId(), PlayPassType.PAID, PlayPassStatus.AVAILABLE),
            games.countByParticipantId(participant.getId()),
            games.countByParticipantIdAndStatus(participant.getId(), GameSessionStatus.COMPLETED),
            games.countByParticipantIdAndStatus(participant.getId(), GameSessionStatus.INVALIDATED),
            payments.totalAmountKrwByParticipantId(participant.getId()),
            bests);
        return new AdminDtos.ParticipantResponse(participant.getId(), participant.getNickname(), participant.getPhone(),
            passDtos, gameDtos, paymentDtos, summary);
    }

    private String normalizePhone(String rawPhone) { return rawPhone.replaceAll("[-\\s]", ""); }
    private boolean looksLikePhone(String query) { return query.matches("[0-9\\-\\s]+"); }

    @Transactional
    public AdminDtos.IssuePassResponse issuePaidPass(Long participantId, AdminDtos.IssuePassRequest request) {
        int quantity = request == null || request.quantity() == null ? 1 : request.quantity();
        if (quantity <= 0) throw new AppException(ErrorCode.VALIDATION_ERROR);
        var participant = participants.findByIdForUpdate(participantId)
            .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));
        var payment = payments.save(new PaymentRecord(participant, quantity, quantity * 500));
        var issued = new java.util.ArrayList<AdminDtos.PassResponse>(quantity);
        for (int i = 0; i < quantity; i++) {
            var pass = passes.save(PlayPass.paid(participant));
            issued.add(new AdminDtos.PassResponse(pass.getId(), pass.getType(), pass.getStatus(), pass.getCreatedAt()));
        }
        return new AdminDtos.IssuePassResponse(quantity, quantity * 500,
            passes.countByParticipantIdAndTypeAndStatus(participantId, PlayPassType.PAID, PlayPassStatus.AVAILABLE),
            paymentResponse(payment), issued);
    }

    @Transactional
    public AdminDtos.InvalidateResponse invalidate(Long gameSessionId, AdminDtos.InvalidateRequest request) {
        var snapshot = games.findById(gameSessionId)
            .orElseThrow(() -> new AppException(ErrorCode.GAME_SESSION_NOT_FOUND));
        participants.findByIdForUpdate(snapshot.getParticipant().getId())
            .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));
        var game = games.findByIdForUpdate(gameSessionId)
            .orElseThrow(() -> new AppException(ErrorCode.GAME_SESSION_NOT_FOUND));

        if (game.getStatus() != GameSessionStatus.INVALIDATED) game.invalidate(request.reason());
        var pass = game.getPlayPass();
        if (request.restorePass() && pass.getStatus() == PlayPassStatus.CONSUMED) {
            if (games.existsByPlayPassIdAndStatusNot(pass.getId(), GameSessionStatus.INVALIDATED))
                throw new AppException(ErrorCode.INVALID_GAME_STATE);
            pass.restore();
        }
        return new AdminDtos.InvalidateResponse(game.getId(), game.getStatus(), pass.getId(), pass.getStatus());
    }

    private AdminDtos.PaymentResponse paymentResponse(PaymentRecord payment) {
        return new AdminDtos.PaymentResponse(payment.getId(), payment.getQuantity(), payment.getAmountKrw(), payment.getCreatedAt());
    }
}
