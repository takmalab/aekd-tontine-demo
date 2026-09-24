package cm.aekd.tontine.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Un seul endpoint (CLAUDE.md §28), dont le contenu diffère selon le rôle
 * de l'appelant (ADMIN/TRESORIER vs MEMBRE, §25) : la restriction
 * d'accès se limite donc à "authentifié", déjà imposée par défaut par
 * SecurityConfig.
 */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    public Object dashboard() {
        return dashboardService.currentDashboard();
    }
}
