package id.ac.ui.cs.advprog.yomu.achievements.internal.repository;

import id.ac.ui.cs.advprog.yomu.achievements.internal.model.Achievement;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementMetric;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementProgress;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementProgressState;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMission;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMissionProgress;
import id.ac.ui.cs.advprog.yomu.achievements.internal.model.DailyMissionProgressState;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAchievementRepository implements AchievementRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAchievementRepository(@Qualifier("achievementsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createTablesAndSeedDefaults() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS achievements (
                id UUID PRIMARY KEY,
                code VARCHAR(100) UNIQUE NOT NULL,
                name VARCHAR(255) NOT NULL,
                description TEXT NOT NULL,
                metric VARCHAR(50) NOT NULL,
                milestone INTEGER NOT NULL,
                active BOOLEAN NOT NULL,
                created_at TIMESTAMP NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_achievement_progress (
                user_id UUID NOT NULL,
                achievement_id UUID NOT NULL,
                progress_count INTEGER NOT NULL,
                unlocked_at TIMESTAMP,
                updated_at TIMESTAMP NOT NULL,
                PRIMARY KEY (user_id, achievement_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS daily_missions (
                id UUID PRIMARY KEY,
                code VARCHAR(100) UNIQUE NOT NULL,
                name VARCHAR(255) NOT NULL,
                description TEXT NOT NULL,
                metric VARCHAR(50) NOT NULL,
                target_count INTEGER NOT NULL,
                reward_points INTEGER NOT NULL,
                active_from DATE NOT NULL,
                active_until DATE NOT NULL,
                created_at TIMESTAMP NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_daily_mission_progress (
                user_id UUID NOT NULL,
                mission_id UUID NOT NULL,
                progress_count INTEGER NOT NULL,
                claimed_at TIMESTAMP,
                updated_at TIMESTAMP NOT NULL,
                PRIMARY KEY (user_id, mission_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS achievement_activity_events (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                metric VARCHAR(50) NOT NULL,
                source_id VARCHAR(120),
                occurred_at TIMESTAMP NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_achievement_activity_unique_source
            ON achievement_activity_events (user_id, metric, source_id)
            """);

        seedAchievement(
            "FIRST_READ",
            "First Read",
            "Menyelesaikan kuis pertama.",
            AchievementMetric.QUIZ_COMPLETED,
            1
        );
        seedAchievement(
            "TEN_READS",
            "10 Bacaan Selesai",
            "Menyelesaikan 10 bacaan.",
            AchievementMetric.READING_COMPLETED,
            10
        );
    }

    @Override
    public Achievement saveAchievement(Achievement achievement) {
        jdbcTemplate.update("""
            INSERT INTO achievements (id, code, name, description, metric, milestone, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            achievement.id(),
            achievement.code(),
            achievement.name(),
            achievement.description(),
            achievement.metric().name(),
            achievement.milestone(),
            achievement.active(),
            Timestamp.from(achievement.createdAt())
        );
        return achievement;
    }

    @Override
    public boolean existsByAchievementCode(String code) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM achievements WHERE code = ?",
            Integer.class,
            code
        );
        return count != null && count > 0;
    }

    @Override
    public List<Achievement> findActiveAchievementsByMetric(AchievementMetric metric) {
        return jdbcTemplate.query("""
            SELECT id, code, name, description, metric, milestone, active, created_at
            FROM achievements
            WHERE active = TRUE AND metric = ?
            ORDER BY created_at ASC, name ASC
            """,
            achievementRowMapper(),
            metric.name()
        );
    }

    @Override
    public List<AchievementProgress> findAchievementProgressForUser(UUID userId) {
        return jdbcTemplate.query("""
            SELECT a.id, a.code, a.name, a.description, a.metric, a.milestone, a.active, a.created_at,
                   COALESCE(p.progress_count, 0) AS progress_count,
                   p.unlocked_at
            FROM achievements a
            LEFT JOIN user_achievement_progress p
                ON p.achievement_id = a.id AND p.user_id = ?
            WHERE a.active = TRUE
            ORDER BY a.created_at ASC, a.name ASC
            """,
            (rs, rowNum) -> new AchievementProgress(
                achievementRowMapper().mapRow(rs, rowNum),
                rs.getInt("progress_count"),
                timestampToInstant(rs.getTimestamp("unlocked_at"))
            ),
            userId
        );
    }

    @Override
    public Optional<AchievementProgressState> findAchievementProgressState(UUID userId, UUID achievementId) {
        return jdbcTemplate.query("""
            SELECT progress_count, unlocked_at
            FROM user_achievement_progress
            WHERE user_id = ? AND achievement_id = ?
            """,
            (rs, rowNum) -> new AchievementProgressState(
                rs.getInt("progress_count"),
                timestampToInstant(rs.getTimestamp("unlocked_at"))
            ),
            userId,
            achievementId
        ).stream().findFirst();
    }

    @Override
    public void saveAchievementProgress(UUID userId, UUID achievementId, int progressCount, Instant unlockedAt) {
        Instant updatedAt = Instant.now();
        if (findAchievementProgressState(userId, achievementId).isPresent()) {
            jdbcTemplate.update("""
                UPDATE user_achievement_progress
                SET progress_count = ?, unlocked_at = ?, updated_at = ?
                WHERE user_id = ? AND achievement_id = ?
                """,
                progressCount,
                timestampOrNull(unlockedAt),
                Timestamp.from(updatedAt),
                userId,
                achievementId
            );
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO user_achievement_progress
                (user_id, achievement_id, progress_count, unlocked_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            userId,
            achievementId,
            progressCount,
            timestampOrNull(unlockedAt),
            Timestamp.from(updatedAt)
        );
    }

    @Override
    public DailyMission saveDailyMission(DailyMission mission) {
        jdbcTemplate.update("""
            INSERT INTO daily_missions
                (id, code, name, description, metric, target_count, reward_points, active_from, active_until, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            mission.id(),
            mission.code(),
            mission.name(),
            mission.description(),
            mission.metric().name(),
            mission.targetCount(),
            mission.rewardPoints(),
            Date.valueOf(mission.activeFrom()),
            Date.valueOf(mission.activeUntil()),
            Timestamp.from(mission.createdAt())
        );
        return mission;
    }

    @Override
    public boolean existsByDailyMissionCode(String code) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_missions WHERE code = ?",
            Integer.class,
            code
        );
        return count != null && count > 0;
    }

    @Override
    public Optional<DailyMission> findDailyMissionById(UUID missionId) {
        return jdbcTemplate.query("""
            SELECT id, code, name, description, metric, target_count, reward_points, active_from, active_until, created_at
            FROM daily_missions
            WHERE id = ?
            """,
            dailyMissionRowMapper(),
            missionId
        ).stream().findFirst();
    }

    @Override
    public List<DailyMission> findActiveDailyMissionsByMetric(AchievementMetric metric, LocalDate activeOn) {
        return jdbcTemplate.query("""
            SELECT id, code, name, description, metric, target_count, reward_points, active_from, active_until, created_at
            FROM daily_missions
            WHERE metric = ? AND active_from <= ? AND active_until > ?
            ORDER BY created_at ASC, name ASC
            """,
            dailyMissionRowMapper(),
            metric.name(),
            Date.valueOf(activeOn),
            Date.valueOf(activeOn)
        );
    }

    @Override
    public List<DailyMissionProgress> findActiveDailyMissionProgressForUser(UUID userId, LocalDate activeOn) {
        return jdbcTemplate.query("""
            SELECT m.id, m.code, m.name, m.description, m.metric, m.target_count, m.reward_points,
                   m.active_from, m.active_until, m.created_at,
                   COALESCE(p.progress_count, 0) AS progress_count,
                   p.claimed_at
            FROM daily_missions m
            LEFT JOIN user_daily_mission_progress p
                ON p.mission_id = m.id AND p.user_id = ?
            WHERE m.active_from <= ? AND m.active_until > ?
            ORDER BY m.created_at ASC, m.name ASC
            """,
            (rs, rowNum) -> new DailyMissionProgress(
                dailyMissionRowMapper().mapRow(rs, rowNum),
                rs.getInt("progress_count"),
                timestampToInstant(rs.getTimestamp("claimed_at"))
            ),
            userId,
            Date.valueOf(activeOn),
            Date.valueOf(activeOn)
        );
    }

    @Override
    public boolean hasActiveDailyMissionOn(LocalDate activeOn) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM daily_missions
            WHERE active_from <= ? AND active_until > ?
            """,
            Integer.class,
            Date.valueOf(activeOn),
            Date.valueOf(activeOn)
        );
        return count != null && count > 0;
    }

    @Override
    public Optional<DailyMissionProgressState> findDailyMissionProgressState(UUID userId, UUID missionId) {
        return jdbcTemplate.query("""
            SELECT progress_count, claimed_at
            FROM user_daily_mission_progress
            WHERE user_id = ? AND mission_id = ?
            """,
            (rs, rowNum) -> new DailyMissionProgressState(
                rs.getInt("progress_count"),
                timestampToInstant(rs.getTimestamp("claimed_at"))
            ),
            userId,
            missionId
        ).stream().findFirst();
    }

    @Override
    public void saveDailyMissionProgress(UUID userId, UUID missionId, int progressCount, Instant claimedAt) {
        Instant updatedAt = Instant.now();
        if (findDailyMissionProgressState(userId, missionId).isPresent()) {
            jdbcTemplate.update("""
                UPDATE user_daily_mission_progress
                SET progress_count = ?, claimed_at = ?, updated_at = ?
                WHERE user_id = ? AND mission_id = ?
                """,
                progressCount,
                timestampOrNull(claimedAt),
                Timestamp.from(updatedAt),
                userId,
                missionId
            );
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO user_daily_mission_progress
                (user_id, mission_id, progress_count, claimed_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            userId,
            missionId,
            progressCount,
            timestampOrNull(claimedAt),
            Timestamp.from(updatedAt)
        );
    }

    @Override
    public boolean saveActivityEvent(UUID userId, AchievementMetric metric, String sourceId, Instant occurredAt) {
        try {
            jdbcTemplate.update("""
                INSERT INTO achievement_activity_events (id, user_id, metric, source_id, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                metric.name(),
                sourceId == null || sourceId.isBlank() ? null : sourceId,
                Timestamp.from(occurredAt)
            );
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private void seedAchievement(
        String code,
        String name,
        String description,
        AchievementMetric metric,
        int milestone
    ) {
        if (existsByAchievementCode(code)) {
            return;
        }

        saveAchievement(new Achievement(
            UUID.randomUUID(),
            code,
            name,
            description,
            metric,
            milestone,
            true,
            Instant.now()
        ));
    }

    private RowMapper<Achievement> achievementRowMapper() {
        return (rs, rowNum) -> new Achievement(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            AchievementMetric.valueOf(rs.getString("metric")),
            rs.getInt("milestone"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private RowMapper<DailyMission> dailyMissionRowMapper() {
        return (rs, rowNum) -> new DailyMission(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            AchievementMetric.valueOf(rs.getString("metric")),
            rs.getInt("target_count"),
            rs.getInt("reward_points"),
            rs.getDate("active_from").toLocalDate(),
            rs.getDate("active_until").toLocalDate(),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
