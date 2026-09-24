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

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    @GetMapping("/api/funds/tontine")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public FundBalanceResponse tontineFund() {
        return new FundBalanceResponse(fundService.tontineFundBalance());
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
