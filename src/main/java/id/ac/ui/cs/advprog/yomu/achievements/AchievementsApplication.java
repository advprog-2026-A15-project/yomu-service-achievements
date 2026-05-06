package id.ac.ui.cs.advprog.yomu.achievements;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {
    "id.ac.ui.cs.advprog.yomu.achievements",
    "id.ac.ui.cs.advprog.yomu.shared"
})
public class AchievementsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AchievementsApplication.class, args);
    }
}
