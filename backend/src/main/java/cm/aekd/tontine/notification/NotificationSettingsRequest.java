package cm.aekd.tontine.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** {@code reminderDaysBefore} null = aucun rappel avant la date limite. */
public record NotificationSettingsRequest(@Min(0) @Max(60) Integer reminderDaysBefore) {
}
