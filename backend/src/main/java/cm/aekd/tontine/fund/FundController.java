package cm.aekd.tontine.fund;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * CLAUDE.md §6 : ADMIN et TRESORIER peuvent tous deux consulter les
 * fonds. Un membre ne peut consulter que sa propre épargne (§6 MEMBRE).
 */
@RestController
public class FundController {

    private final FundService fundService;
    private final FundOverviewService overviewService;

    public FundController(FundService fundService, FundOverviewService overviewService) {
        this.fundService = fundService;
        this.overviewService = overviewService;
    }

    /** Écran « Fonds » : soldes et journal des mouvements validés (CLAUDE.md §29). */
    @GetMapping("/api/funds/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public FundOverviewResponse overview() {
        return overviewService.overview();
    }

    @GetMapping("/api/funds/tontine")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public FundBalanceResponse tontineFund() {
        return new FundBalanceResponse(fundService.tontineFundBalance());
    }

    /** Argent d'épargne disponible pour prêter (docs/decisions.md n° 46). */
    @GetMapping("/api/funds/available-for-lending")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public FundBalanceResponse availableForLending() {
        return new FundBalanceResponse(fundService.availableForLending());
    }

    @GetMapping("/api/savings")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public FundBalanceResponse totalIndividualSavings() {
        return new FundBalanceResponse(fundService.totalIndividualSavings());
    }

    @GetMapping("/api/savings/mine")
    public FundBalanceResponse mySavings() {
        return new FundBalanceResponse(fundService.myIndividualSavings());
    }

    @GetMapping("/api/savings/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public FundBalanceResponse savingsOf(@PathVariable UUID memberId) {
        return new FundBalanceResponse(fundService.individualSavingsOf(memberId));
    }
}
