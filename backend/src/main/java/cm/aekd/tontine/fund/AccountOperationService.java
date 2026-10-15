package cm.aekd.tontine.fund;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.fund.AccountOperationDtos.OperationRequest;
import cm.aekd.tontine.fund.AccountOperationDtos.OperationResponse;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Dépenses et virements (decisions.md n° 50) : uniquement sur les comptes
 * Sanctions, Fond de caisse et Ration ; aucun compte ne peut devenir négatif ;
 * chaque opération est tracée dans l'audit.
 */
@Service
@Transactional
public class AccountOperationService {

    private static final Map<FundAccount, String> NAMES = Map.of(
            FundAccount.TONTINE_FUND, "Fonds commun",
            FundAccount.SAVINGS, "Épargne et prêts",
            FundAccount.SANCTIONS, "Sanctions",
            FundAccount.CASH_FUND, "Fond de caisse",
            FundAccount.RATION, "Ration");

    private final AccountOperationRepository repository;
    private final FundService fundService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;

    public AccountOperationService(AccountOperationRepository repository, FundService fundService,
                                   UserRepository userRepository, CurrentUserProvider currentUserProvider,
                                   AuditLogService auditLogService) {
        this.repository = repository;
        this.fundService = fundService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<OperationResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(OperationResponse::from).toList();
    }

    public OperationResponse record(OperationRequest request) {
        FundAccount source = request.sourceAccount();
        FundAccount target = request.type() == AccountOperationType.TRANSFER ? request.targetAccount() : null;
        if (!source.isOperable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le compte « " + NAMES.get(source)
                    + " » est protégé : aucune dépense ni aucun virement ne peut en sortir");
        }
        if (request.type() == AccountOperationType.TRANSFER) {
            if (target == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez le compte qui reçoit le virement");
            }
            if (target == source) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le virement doit aller vers un autre compte");
            }
            if (!target.isOperable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le compte « " + NAMES.get(target)
                        + " » est protégé : il ne reçoit pas de virement");
            }
        }

        repository.lockAccounts();
        BigDecimal balance = fundService.balanceOf(source);
        if (request.amount().compareTo(balance) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solde insuffisant sur le compte « "
                    + NAMES.get(source) + " » (" + DocumentFormat.fcfa(balance) + " disponibles)");
        }

        String observation = request.observation() == null || request.observation().isBlank()
                ? null : request.observation().trim();
        AccountOperation operation = repository.save(new AccountOperation(request.type(), source, target,
                request.amount(), request.operationDate(), request.label().trim(), observation, currentUserOrThrow()));

        if (target == null) {
            auditLogService.record(AuditAction.ACCOUNT_EXPENSE_RECORDED, "AccountOperation", operation.getId(),
                    "Dépense de " + DocumentFormat.fcfa(request.amount()) + " sur le compte " + NAMES.get(source)
                            + " : " + operation.getLabel());
        } else {
            auditLogService.record(AuditAction.ACCOUNT_TRANSFER_RECORDED, "AccountOperation", operation.getId(),
                    "Virement de " + DocumentFormat.fcfa(request.amount()) + " du compte " + NAMES.get(source)
                            + " vers le compte " + NAMES.get(target) + " : " + operation.getLabel());
        }
        return OperationResponse.from(operation);
    }

    private User currentUserOrThrow() {
        return currentUserProvider.currentUserId().flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié"));
    }
}
