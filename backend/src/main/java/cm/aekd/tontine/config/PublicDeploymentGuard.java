package cm.aekd.tontine.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuse de démarrer une version exposée sur le web (le backend sert alors le
 * frontend compilé : APP_FRONTEND_DIR) avec le secret JWT de développement,
 * public dans le dépôt : n'importe qui pourrait fabriquer un jeton ADMIN.
 */
@Component
public class PublicDeploymentGuard {

    static final String DEV_JWT_SECRET = "dev-only-secret-please-change-me-aekd-tontine-mvp";

    private final String frontendDir;
    private final String jwtSecret;

    public PublicDeploymentGuard(@Value("${app.frontend.dir:}") String frontendDir,
                                 @Value("${app.jwt.secret}") String jwtSecret) {
        this.frontendDir = frontendDir;
        this.jwtSecret = jwtSecret;
    }

    @PostConstruct
    void check() {
        if (!frontendDir.isBlank() && DEV_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("Secret JWT de développement interdit sur une version en ligne : "
                    + "définissez APP_JWT_SECRET (au moins 32 caractères aléatoires).");
        }
    }
}
