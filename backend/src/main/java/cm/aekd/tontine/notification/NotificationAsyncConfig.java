package cm.aekd.tontine.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Envoi des Web Push en tâche de fond (PushDispatcher). */
@Configuration
@EnableAsync
public class NotificationAsyncConfig {
}
