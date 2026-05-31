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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AchievementResponse> createAchievement(
        @Valid @RequestBody CreateAchievementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(achievementService.createAchievement(request));
    }

    @GetMapping
    public List<AchievementProgressResponse> listAchievements(
        @RequestParam(required = false) UUID userId,
        Authentication authentication
    ) {
        return achievementService.listAchievementProgress(resolveTargetUserId(userId, authentication));
    }

    @GetMapping("/users/{userId}/completed")
    public List<AchievementProgressResponse> listCompletedAchievements(
        @PathVariable UUID userId
    ) {
        return achievementService.listCompletedAchievementProgress(userId);
    }

    @PostMapping("/admin/daily-missions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DailyMissionResponse> createDailyMission(
        @Valid @RequestBody CreateDailyMissionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(achievementService.createDailyMission(request));
    }

    @GetMapping("/admin/daily-missions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DailyMissionResponse> listDailyMissions() {
        return achievementService.listDailyMissions();
    }

    @PutMapping("/admin/daily-missions/{missionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public DailyMissionResponse updateDailyMission(
        @PathVariable UUID missionId,
        @Valid @RequestBody CreateDailyMissionRequest request
    ) {
        return achievementService.updateDailyMission(missionId, request);
    }

    @DeleteMapping("/admin/daily-missions/{missionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDailyMission(
        @PathVariable UUID missionId
    ) {
        achievementService.deleteDailyMission(missionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/daily-missions/active")
    public List<DailyMissionProgressResponse> listActiveDailyMissions(
        @RequestParam(required = false) UUID userId,
        Authentication authentication
    ) {
        return achievementService.listActiveDailyMissions(resolveTargetUserId(userId, authentication));
    }

    @PostMapping("/daily-missions/{missionId}/claim")
    public ClaimRewardResponse claimDailyMissionReward(
        @PathVariable UUID missionId,
        @RequestParam(required = false) UUID userId,
        Authentication authentication
    ) {
        return achievementService.claimDailyMissionReward(missionId, resolveTargetUserId(userId, authentication));
    }

    @GetMapping("/total-points")
    public int getTotalClaimedPoints(
        @RequestParam(required = false) UUID userId,
        Authentication authentication
    ) {
        return achievementService.getTotalClaimedPoints(resolveTargetUserId(userId, authentication));
    }

    @PutMapping("/{achievementId}/pin")
    public ResponseEntity<Void> pinAchievement(
        @PathVariable UUID achievementId,
        @RequestParam(required = false) UUID userId,
        @RequestParam boolean pin,
        Authentication authentication
    ) {
        achievementService.pinAchievement(resolveTargetUserId(userId, authentication), achievementId, pin);
        return ResponseEntity.ok().build();
    }

    private UUID resolveTargetUserId(UUID requestedUserId, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            if (requestedUserId != null) {
                return requestedUserId;
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User tidak terautentikasi");
        }

        UUID authenticatedUserId = UUID.fromString(authentication.getPrincipal().toString());
        if (requestedUserId == null || requestedUserId.equals(authenticatedUserId)) {
            return authenticatedUserId;
        }
        if (isAdmin(authentication)) {
            return requestedUserId;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User tidak dapat mengakses data akun lain");
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
