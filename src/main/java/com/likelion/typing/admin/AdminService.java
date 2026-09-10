package com.likelion.typing.admin;

import com.likelion.typing.common.exception.AppException;
import com.likelion.typing.common.exception.ErrorCode;
import com.likelion.typing.game.*;
import com.likelion.typing.participant.ParticipantRepository;
import com.likelion.typing.pass.*;
import com.likelion.typing.security.AdminAuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final AdminAuthService auth;
    private final ParticipantRepository participants;
    private final PlayPassRepository passes;
    private final GameSessionRepository games;

    public AdminService(AdminAuthService auth, ParticipantRepository participants,
                        PlayPassRepository passes, GameSessionRepository games) {
        this.auth = auth;
        this.participants = participants;
        this.passes = passes;
        this.games = games;
    }

    public AdminDtos.LoginResponse login(AdminDtos.LoginRequest request) { return auth.login(request.password()); }

    @Transactional(readOnly = true)
    public AdminDtos.ParticipantResponse findParticipant(String rawPhone) {
        var phone = rawPhone.replaceAll("[-\\s]", "");
        var participant = participants.findByPhone(phone)
            .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));
        var passDtos = passes.findByParticipantIdOrderByCreatedAtAsc(participant.getId()).stream()
            .map(pass -> new AdminDtos.PassResponse(pass.getId(), pass.getType(), pass.getStatus(), pass.getCreatedAt())).toList();
        var gameDtos = games.findByParticipantIdOrderByCreatedAtDesc(participant.getId()).stream()
            .map(game -> new AdminDtos.SessionResponse(game.getId(), game.getCategory().getId(), game.getPlayPass().getId(),
                game.getStatus(), game.getElapsedMs(), game.getStartedAt(), game.getCompletedAt())).toList();
        return new AdminDtos.ParticipantResponse(participant.getId(), participant.getNickname(), participant.getPhone(), passDtos, gameDtos);
    }

    @Transactional
    public AdminDtos.PassResponse issuePaidPass(Long participantId) {
        var participant = participants.findByIdForUpdate(participantId)
            .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));
        var existing = passes.findFirstByParticipantIdAndTypeAndStatusOrderByCreatedAtAsc(
            participantId, PlayPassType.PAID, PlayPassStatus.AVAILABLE);
        var pass = existing.orElseGet(() -> passes.save(PlayPass.paid(participant)));
        return new AdminDtos.PassResponse(pass.getId(), pass.getType(), pass.getStatus(), pass.getCreatedAt());
    }

    @Transactional
    public AdminDtos.InvalidateResponse invalidate(Long gameSessionId, AdminDtos.InvalidateRequest request) {
        var snapshot = games.findById(gameSessionId)
            .orElseThrow(() -> new AppException(ErrorCode.GAME_SESSION_NOT_FOUND));
        participants.findByIdForUpdate(snapshot.getParticipant().getId())
            .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));
        var game = games.findByIdForUpdate(gameSessionId)
            .orElseThrow(() -> new AppException(ErrorCode.GAME_SESSION_NOT_FOUND));

        if (game.getStatus() != GameSessionStatus.INVALIDATED) game.invalidate();
        var pass = game.getPlayPass();
        if (request.restorePass() && pass.getStatus() == PlayPassStatus.CONSUMED) {
            if (games.existsByPlayPassIdAndStatusNot(pass.getId(), GameSessionStatus.INVALIDATED))
                throw new AppException(ErrorCode.INVALID_GAME_STATE);
            pass.restore();
        }
        return new AdminDtos.InvalidateResponse(game.getId(), game.getStatus(), pass.getId(), pass.getStatus());
    }
}
