package cm.aekd.tontine.sanction;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Configuration des règles de sanction (CLAUDE.md §18). Une règle ne
 * peut être créée que pour une cotisation obligatoire : une cotisation
 * facultative non payée ne doit jamais générer de sanction (§18
 * "IMPORTANT"), donc aucune règle ne doit même pouvoir exister sur une
 * cotisation facultative.
 */
@Service
@Transactional
public class SanctionRuleService {

    private final SanctionRuleRepository ruleRepository;
    private final ContributionDefinitionRepository definitionRepository;
    private final AuditLogService auditLogService;

    public SanctionRuleService(SanctionRuleRepository ruleRepository,
                                ContributionDefinitionRepository definitionRepository,
                                AuditLogService auditLogService) {
        this.ruleRepository = ruleRepository;
        this.definitionRepository = definitionRepository;
        this.auditLogService = auditLogService;
    }

    public SanctionRuleResponse create(SanctionRuleRequest request) {
        ContributionDefinition definition = definitionRepository.findById(request.contributionDefinitionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotisation introuvable"));

        if (!definition.isMandatory()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une règle de sanction ne peut être configurée que pour une cotisation obligatoire");
        }

        BigDecimal monetaryAmount = null;
        String description = null;
        if (request.type() == SanctionType.MONETARY) {
            if (request.monetaryAmount() == null || request.monetaryAmount().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un montant positif est requis pour une sanction monétaire");
            }
            monetaryAmount = request.monetaryAmount();
        } else {
            if (request.description() == null || request.description().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Une description est requise pour une sanction en nature");
            }
            description = request.description();
        }

        SanctionRule rule = new SanctionRule(definition, request.type(), request.lateDaysThreshold(),
                monetaryAmount, description);
        rule = ruleRepository.save(rule);

        auditLogService.record(AuditAction.SANCTION_RULE_CREATED, "SanctionRule", rule.getId(),
                "Création d'une règle de sanction " + rule.getType() + " pour \"" + definition.getName() + "\"");

        return SanctionRuleResponse.from(rule);
    }

    public List<SanctionRuleResponse> findByDefinition(UUID contributionDefinitionId) {
        return ruleRepository.findByContributionDefinitionId(contributionDefinitionId).stream()
                .map(SanctionRuleResponse::from)
                .toList();
    }
}
