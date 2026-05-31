package id.ac.ui.cs.advprog.yomu.achievements.internal.controller;

import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.ClaimRewardResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.CreateAchievementRequest;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.CreateDailyMissionRequest;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementMetric;
import id.ac.ui.cs.advprog.yomu.achievements.internal.service.AchievementService;
import id.ac.ui.cs.advprog.yomu.shared.event.ClanPromotedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.CommentCreatedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LearningCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AchievementControllerTest {

    @Test
    void listCompletedAchievements_allowsViewingOtherStudentAchievements() {
        FakeAchievementService service = new FakeAchievementService();
        UUID otherStudentId = UUID.randomUUID();
        AchievementController controller = new AchievementController(service);

        List<AchievementProgressResponse> response = controller.listCompletedAchievements(otherStudentId);

        assertThat(service.completedProgressUserId).isEqualTo(otherStudentId);
        assertThat(response).singleElement()
            .extracting(AchievementProgressResponse::code)
            .isEqualTo("FIRST_READ");
    }

    @Test
    void listAchievements_stillRejectsOtherStudentForFullPrivateProgress() {
        FakeAchievementService service = new FakeAchievementService();
        AchievementController controller = new AchievementController(service);
        UUID authenticatedUserId = UUID.randomUUID();
        UUID otherStudentId = UUID.randomUUID();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            authenticatedUserId.toString(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        assertThatThrownBy(() -> controller.listAchievements(otherStudentId, authentication))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminDailyMissionEndpoints_delegateToService() {
        FakeAchievementService service = new FakeAchievementService();
        AchievementController controller = new AchievementController(service);
        UUID missionId = UUID.randomUUID();
        CreateDailyMissionRequest request = new CreateDailyMissionRequest(
            "DAILY_READ",
            "Daily Read",
            "Baca satu materi.",
            AchievementMetric.READING_COMPLETED,
            1,
            10,
            LocalDate.now(),
            LocalDate.now().plusDays(1)
        );

        DailyMissionResponse updated = controller.updateDailyMission(missionId, request);
        List<DailyMissionResponse> missions = controller.listDailyMissions();
        var deleteResponse = controller.deleteDailyMission(missionId);

        assertThat(service.updatedMissionId).isEqualTo(missionId);
        assertThat(service.updatedRequest).isEqualTo(request);
        assertThat(updated.name()).isEqualTo("Daily Read");
        assertThat(missions).singleElement()
            .extracting(DailyMissionResponse::code)
            .isEqualTo("DAILY_READ");
        assertThat(service.deletedMissionId).isEqualTo(missionId);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private static class FakeAchievementService implements AchievementService {
        private UUID completedProgressUserId;
        private UUID updatedMissionId;
        private CreateDailyMissionRequest updatedRequest;
        private UUID deletedMissionId;

        @Override
        public AchievementResponse createAchievement(CreateAchievementRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AchievementProgressResponse> listAchievementProgress(UUID userId) {
            return List.of();
        }

        @Override
        public List<AchievementProgressResponse> listCompletedAchievementProgress(UUID userId) {
            completedProgressUserId = userId;
            return List.of(new AchievementProgressResponse(
                UUID.randomUUID(),
                "FIRST_READ",
                "First Read",
                "Selesai kuis pertama.",
                AchievementMetric.QUIZ_COMPLETED.name(),
                1,
                1,
                Instant.now(),
                true,
                true
            ));
        }

        @Override
        public DailyMissionResponse createDailyMission(CreateDailyMissionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DailyMissionResponse> listDailyMissions() {
            return List.of(dailyMissionResponse(UUID.randomUUID(), "DAILY_READ", "Daily Read"));
        }

        @Override
        public DailyMissionResponse updateDailyMission(UUID missionId, CreateDailyMissionRequest request) {
            updatedMissionId = missionId;
            updatedRequest = request;
            return dailyMissionResponse(missionId, request.code(), request.name());
        }

        @Override
        public void deleteDailyMission(UUID missionId) {
            deletedMissionId = missionId;
        }

        @Override
        public List<DailyMissionProgressResponse> listActiveDailyMissions(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClaimRewardResponse claimDailyMissionReward(UUID missionId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void pinAchievement(UUID userId, UUID achievementId, boolean pin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordReadingCompleted(LearningCompletedEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordQuizCompleted(QuizCompletedEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordLeagueActivity(LeagueActivityEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordCommentCreated(CommentCreatedEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordClanPromoted(ClanPromotedEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rotateDailyMissions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTotalClaimedPoints(UUID userId) {
            throw new UnsupportedOperationException();
        }

        private DailyMissionResponse dailyMissionResponse(UUID missionId, String code, String name) {
            return new DailyMissionResponse(
                missionId,
                code,
                name,
                "Baca satu materi.",
                AchievementMetric.READING_COMPLETED.name(),
                1,
                10,
                LocalDate.now(),
                LocalDate.now().plusDays(1)
            );
        }
    }
}
