package id.ac.ui.cs.advprog.yomu.achievements.internal.monitoring;

import id.ac.ui.cs.advprog.yomu.achievements.internal.model.AchievementMetric;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class AchievementMetrics {
    public static final String ACTION_COUNTER_METRIC = "yomu_achievements_actions_total";
    public static final String ACTION_TIMER_METRIC = "yomu_achievements_action_duration";
    public static final String ACTIVITY_EVENT_COUNTER_METRIC = "yomu_achievements_activity_events_total";
    public static final String UNLOCK_COUNTER_METRIC = "yomu_achievements_unlocks_total";
    public static final String DAILY_MISSION_COMPLETION_COUNTER_METRIC =
        "yomu_achievements_daily_mission_completions_total";

    private final MeterRegistry meterRegistry;

    public AchievementMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T recordAction(String action, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            outcome = "failure";
            throw exception;
        } finally {
            recordActionMetric(action, outcome, sample);
        }
    }

    public void recordAction(String action, Runnable operation) {
        recordAction(action, () -> {
            operation.run();
            return null;
        });
    }

    public void recordActivityEvent(AchievementMetric metric, String outcome) {
        Counter.builder(ACTIVITY_EVENT_COUNTER_METRIC)
            .tag("metric", metric.name())
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }

    public void recordAchievementUnlocked(AchievementMetric metric) {
        Counter.builder(UNLOCK_COUNTER_METRIC)
            .tag("metric", metric.name())
            .register(meterRegistry)
            .increment();
    }

    public void recordDailyMissionCompleted(AchievementMetric metric) {
        Counter.builder(DAILY_MISSION_COMPLETION_COUNTER_METRIC)
            .tag("metric", metric.name())
            .register(meterRegistry)
            .increment();
    }

    private void recordActionMetric(String action, String outcome, Timer.Sample sample) {
        Counter.builder(ACTION_COUNTER_METRIC)
            .tag("action", action)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
        sample.stop(Timer.builder(ACTION_TIMER_METRIC)
            .tag("action", action)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(meterRegistry));
    }
}
