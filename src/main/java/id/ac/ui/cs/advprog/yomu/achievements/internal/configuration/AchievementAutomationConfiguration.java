package id.ac.ui.cs.advprog.yomu.achievements.internal.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AchievementAutomationConfiguration {

    @Bean("achievementsJdbcTemplate")
    public JdbcTemplate achievementsJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
