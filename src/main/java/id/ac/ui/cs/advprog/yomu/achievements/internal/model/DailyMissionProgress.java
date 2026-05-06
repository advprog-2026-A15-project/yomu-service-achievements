package id.ac.ui.cs.advprog.yomu.achievements.internal.model;

import java.time.Instant;

public record DailyMissionProgress(
    DailyMission mission,
    int progressCount,
    Instant claimedAt
) {
    public boolean completed() {
        return progressCount >= mission.targetCount();
    }

    public boolean claimed() {
        return claimedAt != null;
    }
}
