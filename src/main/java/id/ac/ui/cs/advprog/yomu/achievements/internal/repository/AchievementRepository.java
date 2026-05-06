package id.ac.ui.cs.advprog.yomu.achievements.internal.repository;

import id.ac.ui.cs.advprog.yomu.achievements.internal.model.Achievement;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementMetric;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementProgress;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementProgressState;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMission;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMissionProgress;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMissionProgressState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AchievementRepository {

    Achievement saveAchievement(Achievement achievement);

    boolean existsByAchievementCode(String code);

    List<Achievement> findActiveAchievementsByMetric(AchievementMetric metric);

    List<AchievementProgress> findAchievementProgressForUser(UUID userId);

    Optional<AchievementProgressState> findAchievementProgressState(UUID userId, UUID achievementId);

    void saveAchievementProgress(UUID userId, UUID achievementId, int progressCount, Instant unlockedAt);

    DailyMission saveDailyMission(DailyMission mission);

    boolean existsByDailyMissionCode(String code);

    Optional<DailyMission> findDailyMissionById(UUID missionId);

    List<DailyMission> findActiveDailyMissionsByMetric(AchievementMetric metric, LocalDate activeOn);

    List<DailyMissionProgress> findActiveDailyMissionProgressForUser(UUID userId, LocalDate activeOn);

    boolean hasActiveDailyMissionOn(LocalDate activeOn);

    Optional<DailyMissionProgressState> findDailyMissionProgressState(UUID userId, UUID missionId);

    void saveDailyMissionProgress(UUID userId, UUID missionId, int progressCount, Instant claimedAt);

    boolean saveActivityEvent(UUID userId, AchievementMetric metric, String sourceId, Instant occurredAt);
}
