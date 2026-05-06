package id.ac.ui.cs.advprog.yomu.achievements.internal.listener;

import id.ac.ui.cs.advprog.yomu.achievements.internal.service.AchievementService;
import id.ac.ui.cs.advprog.yomu.shared.event.LearningCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ReadingCompletedListener {

    private final AchievementService achievementService;

    public ReadingCompletedListener(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @Async
    @EventListener
    public void onLearningCompleted(LearningCompletedEvent event) {
        achievementService.recordReadingCompleted(event);
    }

    @Async
    @EventListener
    public void onQuizCompleted(QuizCompletedEvent event) {
        achievementService.recordQuizCompleted(event);
    }

    @Async
    @EventListener
    public void onLeagueActivity(LeagueActivityEvent event) {
        achievementService.recordLeagueActivity(event);
    }
}
