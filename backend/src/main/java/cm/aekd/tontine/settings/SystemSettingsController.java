package cm.aekd.tontine.settings;

import cm.aekd.tontine.settings.SystemSettingsService.SettingsRequest;
import cm.aekd.tontine.settings.SystemSettingsService.SettingsResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** « Paramètres système » : super administrateur (docs/decisions.md n° 57), plus une info publique. */
@RestController
public class SystemSettingsController {

    private final SystemSettingsService service;

    public SystemSettingsController(SystemSettingsService service) {
        this.service = service;
    }

    @GetMapping("/api/settings/system")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SettingsResponse get() {
        return service.get();
    }

    @PutMapping("/api/settings/system")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return service.update(request);
    }

    @PostMapping(value = "/api/settings/system/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SettingsResponse uploadLogo(@RequestParam("file") MultipartFile file) {
        return service.uploadLogo(file);
    }

    @DeleteMapping("/api/settings/system/logo")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SettingsResponse removeLogo() {
        return service.removeLogo();
    }

    /** Information publique : nom de l'association et maintenance, pour la page de connexion. */
    public record PublicInfo(String associationName, String associationShortName, boolean maintenance,
                             String maintenanceMessage) {
    }

    @GetMapping("/api/public/info")
    public PublicInfo publicInfo() {
        SystemSettingsService.Snapshot s = service.current();
        return new PublicInfo(s.associationName(), s.associationShortName(), s.maintenanceEnabled(),
                s.maintenanceEnabled() ? service.maintenanceMessage() : null);
    }
}
