package cm.aekd.tontine.settings;

import cm.aekd.tontine.settings.ThemeService.Theme;
import cm.aekd.tontine.settings.ThemeService.ThemeRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * Thème de l'application (decisions.md n° 72). Réglage par le super administrateur ;
 * lecture publique (page de connexion, icônes et manifeste de l'application installée).
 * Les images sont mises en cache : l'interface ajoute la version du thème à leur adresse.
 */
@RestController
public class ThemeController {

    private static final CacheControl IMAGE_CACHE = CacheControl.maxAge(Duration.ofDays(7)).cachePublic();

    private final ThemeService service;

    public ThemeController(ThemeService service) {
        this.service = service;
    }

    @PutMapping("/api/settings/theme")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Theme update(@Valid @RequestBody ThemeRequest request) {
        return service.update(request);
    }

    @PostMapping(value = "/api/settings/theme/login-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Theme uploadLoginImage(@RequestParam("file") MultipartFile file) {
        return service.uploadLoginImage(file);
    }

    @DeleteMapping("/api/settings/theme/login-image")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Theme removeLoginImage() {
        return service.removeLoginImage();
    }

    @GetMapping("/api/public/theme")
    public ResponseEntity<Theme> theme() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.current());
    }

    @GetMapping("/api/public/logo")
    public ResponseEntity<byte[]> logo() {
        return service.logo()
                .map(i -> ResponseEntity.ok().cacheControl(IMAGE_CACHE).contentType(MediaType.parseMediaType(i.contentType())).body(i.content()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/public/login-image")
    public ResponseEntity<byte[]> loginImage() {
        return service.loginImage()
                .map(i -> ResponseEntity.ok().cacheControl(IMAGE_CACHE).contentType(MediaType.parseMediaType(i.contentType())).body(i.content()))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Pas d'extension « .png » : le service worker de l'interface ne doit pas les figer en cache. */
    @GetMapping("/api/public/icons/{size}")
    public ResponseEntity<byte[]> icon(@PathVariable int size) {
        return service.icon(size)
                .map(bytes -> ResponseEntity.ok().cacheControl(IMAGE_CACHE).contentType(MediaType.IMAGE_PNG).body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/api/public/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<String> manifest() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.manifest());
    }
}
