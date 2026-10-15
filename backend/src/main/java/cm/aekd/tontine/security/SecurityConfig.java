package cm.aekd.tontine.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration de sécurité de base (CLAUDE.md §4 : Spring Security + JWT + BCrypt).
 *
 * - Session stateless (authentification 100% par JWT, pas de session serveur).
 * - "/api/auth/**" reste public : c'est le futur point d'entrée de login
 *   (CLAUDE.md §28), dont le contrôleur sera implémenté à l'étape 13.
 * - Toute autre requête nécessite une authentification.
 *
 * Les règles d'autorisation fines par rôle (ADMIN / TRESORIER / MEMBRE,
 * détaillées en CLAUDE.md §6) sont ajoutées au niveau des contrôleurs via
 * {@code @PreAuthorize} (voir par ex. SessionController), au fur et à
 * mesure de leur création, pour rester alignées avec les permissions
 * réelles de chaque endpoint plutôt que d'anticiper des règles.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Décision du 2026-10-03 (docs/decisions.md n° 40) : l'ADMIN a tous les droits,
     * y compris ceux du TRESORIER. Toute règle {@code hasRole('TRESORIER')} accepte
     * donc aussi l'ADMIN. L'ADMIN ne devient pas MEMBRE pour autant : l'espace
     * personnel dépend du profil membre, pas du rôle. Bean statique, comme le
     * recommande Spring Security pour qu'il soit pris en compte par
     * {@code @EnableMethodSecurity}.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        // decisions.md n° 40 et 56 : SUPER_ADMIN > ADMIN > TRESORIER.
        return RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN\nROLE_ADMIN > ROLE_TRESORIER");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Le frontend Angular (dev: http://localhost:4200) est servi sur une
     * origine différente du backend : sans ceci, le navigateur bloque
     * toute requête (échec du preflight OPTIONS), même si l'endpoint est
     * permitAll côté Spring Security.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Nom des fichiers téléchargés (PDF générés), lu par le frontend.
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Hors API : uniquement les fichiers du frontend compilé
                        // (FrontendConfig), publics par nature (page de connexion).
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
