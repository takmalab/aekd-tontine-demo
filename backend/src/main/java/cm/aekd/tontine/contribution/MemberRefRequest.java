package cm.aekd.tontine.contribution;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MemberRefRequest(@NotNull UUID memberId) {
}
