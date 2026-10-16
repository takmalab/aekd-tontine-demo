package cm.aekd.tontine.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

/**
 * Mode maintenance (docs/decisions.md n° 57) : toute requête de l'API d'un
 * utilisateur autre que le super administrateur reçoit 503 avec
 * {@code maintenance: true}, sauf l'authentification et l'information publique.
 */
@Configuration
public class MaintenanceInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final SystemSettingsService settings;
    private final ObjectMapper objectMapper;

    public MaintenanceInterceptor(SystemSettingsService settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**", "/api/public/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!settings.maintenanceEnabled()) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean superAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (superAdmin) {
            return true;
        }
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", 503, "maintenance", true, "message", settings.maintenanceMessage()));
        return false;
    }
}
