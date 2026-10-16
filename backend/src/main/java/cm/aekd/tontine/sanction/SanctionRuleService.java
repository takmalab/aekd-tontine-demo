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
 *
 * <p>Décision n° 61 : une règle se modifie, se désactive ou se supprime. Les
 * sanctions déjà appliquées ne changent pas (elles gardent leur montant ou leur
 * description) ; une fois la règle utilisée, son type est figé et elle ne peut
 * plus être supprimée (la suppression effacerait en cascade les sanctions et
 * leurs paiements) : on la désactive à la place.
 */
@Service
@Transactional
public class SanctionRuleService {

    private final SanctionRuleRepository ruleRepository;
    private final ContributionDefinitionRepository definitionRepository;
    private final AppliedSanctionRepository appliedRepository;
    private final AuditLogService auditLogService;

    public SanctionRuleService(SanctionRuleRepository ruleRepository,
                                ContributionDefinitionRepository definitionRepository,
                                AppliedSanctionRepository appliedRepository,
                                AuditLogService auditLogService) {
        this.ruleRepository = ruleRepository;
        this.definitionRepository = definitionRepository;
        this.appliedRepository = appliedRepository;
        this.auditLogService = auditLogService;
    }

    public SanctionRuleResponse create(SanctionRuleRequest request) {
        ContributionDefinition definition = definitionRepository.findById(request.contributionDefinitionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotisation introuvable"));

        if (!definition.isMandatory()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une règle de sanction ne peut être configurée que pour une cotisation obligatoire");
        }

        SanctionRule rule = new SanctionRule(definition, request.type(), request.lateDaysThreshold(),
                monetaryAmountOf(request), descriptionOf(request));
        rule = ruleRepository.save(rule);

        auditLogService.record(AuditAction.SANCTION_RULE_CREATED, "SanctionRule", rule.getId(),
                "Création d'une règle de sanction " + rule.getType() + " pour \"" + definition.getName() + "\"");

        return toResponse(rule);
    }

    public List<SanctionRuleResponse> findByDefinition(UUID contributionDefinitionId) {
        return ruleRepository.findByContributionDefinitionId(contributionDefinitionId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Seuil, montant et description restent modifiables ; le type est figé dès qu'une sanction a été appliquée. */
    public SanctionRuleResponse update(UUID ruleId, SanctionRuleRequest request) {
        SanctionRule rule = getOrThrow(ruleId);
        if (!rule.getContributionDefinition().getId().equals(request.contributionDefinitionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cotisation d'une règle de sanction ne peut pas être changée");
        }
        if (request.type() != rule.getType() && isUsed(ruleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Des sanctions ont déjà été appliquées avec cette règle : son type ne peut plus être changé.");
        }

        rule.update(request.type(), request.lateDaysThreshold(), monetaryAmountOf(request), descriptionOf(request));
        rule = ruleRepository.save(rule);

        auditLogService.record(AuditAction.SANCTION_RULE_UPDATED, "SanctionRule", rule.getId(),
                "Modification d'une règle de sanction pour \"" + rule.getContributionDefinition().getName() + "\"");
        return toResponse(rule);
    }

    /** Une règle inactive ne propose plus de candidats et ne peut plus être appliquée. */
    public SanctionRuleResponse setActive(UUID ruleId, boolean active) {
        SanctionRule rule = getOrThrow(ruleId);
        if (rule.isActive() == active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    active ? "Cette règle est déjà active" : "Cette règle est déjà désactivée");
        }
        rule.setActive(active);
        rule = ruleRepository.save(rule);

        auditLogService.record(active ? AuditAction.SANCTION_RULE_ACTIVATED : AuditAction.SANCTION_RULE_DEACTIVATED,
                "SanctionRule", rule.getId(),
                (active ? "Réactivation" : "Désactivation") + " d'une règle de sanction pour \""
                        + rule.getContributionDefinition().getName() + "\"");
        return toResponse(rule);
    }

    public void delete(UUID ruleId) {
        SanctionRule rule = getOrThrow(ruleId);
        if (isUsed(ruleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Des sanctions ont déjà été appliquées avec cette règle : désactivez-la plutôt que de la supprimer.");
        }
        String definitionName = rule.getContributionDefinition().getName();
        ruleRepository.delete(rule);

        auditLogService.record(AuditAction.SANCTION_RULE_DELETED, "SanctionRule", ruleId,
                "Suppression d'une règle de sanction pour \"" + definitionName + "\"");
    }

    private boolean isUsed(UUID ruleId) {
        return appliedRepository.existsBySanctionRuleId(ruleId);
    }

    private SanctionRuleResponse toResponse(SanctionRule rule) {
        return SanctionRuleResponse.from(rule, isUsed(rule.getId()));
    }

    private SanctionRule getOrThrow(UUID ruleId) {
        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle de sanction introuvable"));
    }

    private static BigDecimal monetaryAmountOf(SanctionRuleRequest request) {
        if (request.type() != SanctionType.MONETARY) {
            return null;
        }
        if (request.monetaryAmount() == null || request.monetaryAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un montant positif est requis pour une sanction monétaire");
        }
        return request.monetaryAmount();
    }

    private static String descriptionOf(SanctionRuleRequest request) {
        if (request.type() == SanctionType.MONETARY) {
            return null;
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une description est requise pour une sanction en nature");
        }
        return request.description();
    }
}
