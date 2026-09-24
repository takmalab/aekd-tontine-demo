package cm.aekd.tontine.fund;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.member.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Solde du fonds commun et de l'épargne individuelle (CLAUDE.md §17,
 * §20-22), calculés à la volée à partir des ContributionTransaction
 * VALIDATED. Il n'existe pas d'entité "Fund" séparée dans le modèle de
 * données du §27 : un solde stocké risquerait de dériver des paiements
 * réels, alors qu'une agrégation reste toujours exacte à l'échelle du
 * MVP (moins de 30 membres, regles-metier.md §2). Seuls les paiements
 * VALIDATED sont comptabilisés ; fonds commun et épargne individuelle ne
 * sont jamais mélangés (§17 "IMPORTANT").
 */
@Service
@Transactional(readOnly = true)
public class FundService {

    private final ContributionTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final CurrentUserProvider currentUserProvider;

    public FundService(ContributionTransactionRepository transactionRepository,
                        MemberRepository memberRepository,
                        CurrentUserProvider currentUserProvider) {
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public BigDecimal tontineFundBalance() {
        return transactionRepository.sumAmountByStatusAndFundDestination(
                ContributionTransactionStatus.VALIDATED, FundDestination.TONTINE_FUND);
    }

    public BigDecimal totalIndividualSavings() {
        return transactionRepository.sumAmountByStatusAndFundDestination(
                ContributionTransactionStatus.VALIDATED, FundDestination.INDIVIDUAL_SAVINGS);
    }

    public BigDecimal individualSavingsOf(UUID memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable");
        }
        return transactionRepository.sumAmountByStatusAndFundDestinationAndMember(
                ContributionTransactionStatus.VALIDATED, FundDestination.INDIVIDUAL_SAVINGS, memberId);
    }

    public BigDecimal myIndividualSavings() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        return transactionRepository.sumAmountByStatusAndFundDestinationAndMember(
                ContributionTransactionStatus.VALIDATED, FundDestination.INDIVIDUAL_SAVINGS, memberId);
    }
}
