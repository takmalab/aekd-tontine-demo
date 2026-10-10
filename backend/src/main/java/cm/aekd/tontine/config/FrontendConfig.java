package cm.aekd.tontine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Sert le frontend Angular compilé (ng build) depuis le backend, sur la même
 * origine que l'API : une seule adresse à exposer pour les tests à distance
 * (tunnel HTTPS, voir partager.bat), sans configuration CORS supplémentaire.
 *
 * Inactif tant que {@code app.frontend.dir} (APP_FRONTEND_DIR) est vide : en
 * développement, le frontend reste servi par {@code ng serve}.
 *
 * Toute route inconnue hors "/api" renvoie index.html, pour que le routeur
 * Angular gère les liens directs et le rafraîchissement (ex. /loans/12).
 */
@Configuration
@ConditionalOnExpression("!'${app.frontend.dir:}'.isBlank()")
public class FrontendConfig implements WebMvcConfigurer {

    private final String frontendLocation;

    public FrontendConfig(@Value("${app.frontend.dir}") String frontendDir) {
        String uri = Path.of(frontendDir).toAbsolutePath().normalize().toUri().toString();
        this.frontendLocation = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(frontendLocation)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Une API inexistante doit rester un 404, pas la page Angular.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return location.createRelative("index.html");
                    }
                });
    }
}
