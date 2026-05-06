package id.ac.ui.cs.advprog.yomu.achievements.internal.configuration;

import id.ac.ui.cs.advprog.yomu.achievements.internal.service.AchievementService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyMissionRotationScheduler {

    private final AchievementService achievementService;

    public DailyMissionRotationScheduler(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDailyMissionOnStartup() {
        achievementService.rotateDailyMissions();
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void rotateDailyMissionAtMidnight() {
        achievementService.rotateDailyMissions();
    }
}
