package cm.aekd.tontine.loan;

public record LoanRuleEvaluationResponse(String ruleName, boolean respected, String message) {
    public static LoanRuleEvaluationResponse from(LoanRuleEvaluation evaluation) {
        return new LoanRuleEvaluationResponse(evaluation.getRuleName(), evaluation.isRespected(), evaluation.getMessage());
    }
}
