package id.ac.ui.cs.advprog.yomu.achievements.internal.controller;

import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.AchievementResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.ClaimRewardResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.CreateAchievementRequest;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.CreateDailyMissionRequest;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionProgressResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.dto.DailyMissionResponse;
import id.ac.ui.cs.advprog.yomu.achievements.internal.service.AchievementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @PostMapping("/admin")
    public ResponseEntity<AchievementResponse> createAchievement(
        @Valid @RequestBody CreateAchievementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(achievementService.createAchievement(request));
    }

    @GetMapping
    public List<AchievementProgressResponse> listAchievements(@RequestParam UUID userId) {
        return achievementService.listAchievementProgress(userId);
    }

    @PostMapping("/admin/daily-missions")
    public ResponseEntity<DailyMissionResponse> createDailyMission(
        @Valid @RequestBody CreateDailyMissionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(achievementService.createDailyMission(request));
    }

    @GetMapping("/daily-missions/active")
    public List<DailyMissionProgressResponse> listActiveDailyMissions(@RequestParam UUID userId) {
        return achievementService.listActiveDailyMissions(userId);
    }

    @PostMapping("/daily-missions/{missionId}/claim")
    public ClaimRewardResponse claimDailyMissionReward(
        @PathVariable UUID missionId,
        @RequestParam UUID userId
    ) {
        return achievementService.claimDailyMissionReward(missionId, userId);
    }
}
