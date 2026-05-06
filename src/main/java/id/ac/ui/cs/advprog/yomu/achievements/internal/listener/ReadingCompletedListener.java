package id.ac.ui.cs.advprog.yomu.achievements.internal.listener;

import id.ac.ui.cs.advprog.yomu.achievements.internal.service.AchievementService;
import id.ac.ui.cs.advprog.yomu.shared.event.LearningCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReadingCompletedListener {

    private final AchievementService achievementService;

    public ReadingCompletedListener(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @RabbitListener(queuesToDeclare = @Queue("yomu.learning.completed"))
    public void onLearningCompleted(LearningCompletedEvent event) {
        achievementService.recordReadingCompleted(event);
    }

    @RabbitListener(queuesToDeclare = @Queue("yomu.quiz.completed"))
    public void onQuizCompleted(QuizCompletedEvent event) {
        achievementService.recordQuizCompleted(event);
    }

    @RabbitListener(queuesToDeclare = @Queue("yomu.league.activity"))
    public void onLeagueActivity(LeagueActivityEvent event) {
        achievementService.recordLeagueActivity(event);
    }
}
