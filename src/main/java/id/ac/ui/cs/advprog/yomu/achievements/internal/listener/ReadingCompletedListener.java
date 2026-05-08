package id.ac.ui.cs.advprog.yomu.achievements.internal.listener;

import id.ac.ui.cs.advprog.yomu.achievements.internal.service.AchievementService;
import id.ac.ui.cs.advprog.yomu.shared.event.LearningCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.QuizCompletedEvent;
import id.ac.ui.cs.advprog.yomu.shared.event.LeagueActivityEvent;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReadingCompletedListener {

    private final AchievementService achievementService;

    public ReadingCompletedListener(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "achievements.learning.completed.queue", durable = "true"),
        exchange = @Exchange(value = "yomu.events", type = "topic"),
        key = "yomu.learning.completed"
    ))
    public void onLearningCompleted(LearningCompletedEvent event) {
        achievementService.recordReadingCompleted(event);
    }

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "achievements.quiz.completed.queue", durable = "true"),
        exchange = @Exchange(value = "yomu.events", type = "topic"),
        key = "yomu.quiz.completed"
    ))
    public void onQuizCompleted(QuizCompletedEvent event) {
        achievementService.recordQuizCompleted(event);
    }

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "achievements.league.activity.queue", durable = "true"),
        exchange = @Exchange(value = "yomu.events", type = "topic"),
        key = "yomu.league.activity"
    ))
    public void onLeagueActivity(LeagueActivityEvent event) {
        achievementService.recordLeagueActivity(event);
    }

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "achievements.comment.created.queue", durable = "true"),
        exchange = @Exchange(value = "yomu.events", type = "topic"),
        key = "yomu.comment.created"
    ))
    public void onCommentCreated(CommentCreatedEvent event) {
        achievementService.recordCommentCreated(event);
    }

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "achievements.clan.promoted.queue", durable = "true"),
        exchange = @Exchange(value = "yomu.events", type = "topic"),
        key = "yomu.clan.promoted"
    ))
    public void onClanPromoted(ClanPromotedEvent event) {
        achievementService.recordClanPromoted(event);
    }
}
