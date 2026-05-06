package id.ac.ui.cs.advprog.yomu.achievements.internal.service;

import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.ClaimRewardResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.CreateAchievementRequest;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.CreateDailyMissionRequest;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionResponse;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LearningCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;

import java.util.List;
import java.util.UUID;

public interface AchievementService {

    AchievementResponse createAchievement(CreateAchievementRequest request);

    List<AchievementProgressResponse> listAchievementProgress(UUID userId);

    DailyMissionResponse createDailyMission(CreateDailyMissionRequest request);

    List<DailyMissionProgressResponse> listActiveDailyMissions(UUID userId);

    ClaimRewardResponse claimDailyMissionReward(UUID missionId, UUID userId);

    void recordReadingCompleted(LearningCompletedEvent event);

    void recordQuizCompleted(QuizCompletedEvent event);

    void recordLeagueActivity(LeagueActivityEvent event);

    void rotateDailyMissions();
}
