package cm.aekd.tontine.settings;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.storage.FileStorageService;
import cm.aekd.tontine.storage.StoredFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thème de l'application (decisions.md n° 72) : couleurs, polices, motif de fond,
 * slogan et photo de la page de connexion, réglés par le super administrateur pour
 * qu'une autre association puisse utiliser l'application à ses couleurs.
 * Une valeur absente reprend le thème AEKD. Le thème est public : la page de
 * connexion s'affiche déjà aux couleurs de l'association.
 */
@Service
@Transactional
public class ThemeService {

    public static final String DEFAULT_PRIMARY = "#e86e09";
    public static final String DEFAULT_SECONDARY = "#ab583a";
    public static final String DEFAULT_DARK = "#36353a";
    public static final String DEFAULT_HEADING_FONT = "Poppins";
    public static final String DEFAULT_BODY_FONT = "Inter";
    public static final String DEFAULT_PATTERN = "MASQUES";
    public static final String DEFAULT_SLOGAN = "La solidarité, en toute transparence.";

    /** Polices proposées (Google Fonts), chargées par l'interface à la demande. */
    public static final List<String> FONTS = List.of("Poppins", "Inter", "Montserrat", "Nunito", "Raleway", "Roboto",
            "Open Sans", "Lato", "Ubuntu", "Work Sans", "Source Sans 3", "Playfair Display", "Merriweather");

    /** Motif de fond africain : masques, symboles, ou aucun. */
    public static final List<String> PATTERNS = List.of("MASQUES", "SYMBOLES", "AUCUN");

    /** Tailles des icônes de l'application installée (manifeste). */
    public static final List<Integer> ICON_SIZES = List.of(72, 96, 128, 144, 152, 192, 384, 512);

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long LOGIN_IMAGE_MAX_BYTES = 3L * 1024 * 1024;
    private static final String HEX = "^#[0-9a-fA-F]{6}$";

    private final SystemSettingsRepository repository;
    private final SystemSettingsService settingsService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /** Icônes générées depuis le logo, par version du réglage et taille. */
    private final Map<String, byte[]> iconCache = new ConcurrentHashMap<>();

    public ThemeService(SystemSettingsRepository repository, SystemSettingsService settingsService,
                        FileStorageService fileStorageService, AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * Thème en vigueur, valeurs par défaut comprises. {@code version} change à chaque
     * modification des paramètres : l'interface l'ajoute aux adresses des images.
     */
    public record Theme(String associationName, String associationShortName, String primaryColor,
                        String secondaryColor, String darkColor, String headingFont, String bodyFont,
                        String pattern, String slogan, boolean customLogo, boolean customLoginImage,
                        String version, List<String> fonts) {
    }

    public record ThemeRequest(
            @NotBlank @Pattern(regexp = HEX, message = "Couleur attendue au format #RRGGBB") String primaryColor,
            @NotBlank @Pattern(regexp = HEX, message = "Couleur attendue au format #RRGGBB") String secondaryColor,
            @NotBlank @Pattern(regexp = HEX, message = "Couleur attendue au format #RRGGBB") String darkColor,
            @NotBlank String headingFont,
            @NotBlank String bodyFont,
            @NotBlank String pattern,
            @Size(max = 150) String slogan
    ) {
    }

    @Transactional(readOnly = true)
    public Theme current() {
        SystemSettingsService.Snapshot s = settingsService.current();
        return new Theme(s.associationName(), s.associationShortName(),
                or(s.themePrimaryColor(), DEFAULT_PRIMARY), or(s.themeSecondaryColor(), DEFAULT_SECONDARY),
                or(s.themeDarkColor(), DEFAULT_DARK), or(s.themeHeadingFont(), DEFAULT_HEADING_FONT),
                or(s.themeBodyFont(), DEFAULT_BODY_FONT), or(s.themePattern(), DEFAULT_PATTERN),
                or(s.loginSlogan(), DEFAULT_SLOGAN), s.customLogo(), s.customLoginImage(),
                Long.toString(s.version()), FONTS);
    }

    public Theme update(ThemeRequest request) {
        String heading = font(request.headingFont());
        String body = font(request.bodyFont());
        String pattern = request.pattern().trim().toUpperCase(Locale.ROOT);
        if (!PATTERNS.contains(pattern)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motif inconnu : " + request.pattern());
        }
        SystemSettings settings = entity();
        settings.setThemeColors(color(request.primaryColor()), color(request.secondaryColor()), color(request.darkColor()));
        settings.setThemeFonts(heading, body);
        settings.setThemePattern(pattern);
        settings.setLoginSlogan(request.slogan() == null || request.slogan().isBlank() ? null : request.slogan().trim());
        repository.flush();
        auditLogService.record(AuditAction.THEME_UPDATED, "SystemSettings", SystemSettings.AUDIT_ENTITY_ID,
                "Thème modifié : couleurs " + color(request.primaryColor()) + ", " + color(request.secondaryColor()) + ", "
                        + color(request.darkColor()) + " ; polices " + heading + " / " + body + " ; motif " + pattern.toLowerCase(Locale.ROOT));
        settingsService.reload();
        return current();
    }

    // ---------------------------------------------------------------- photo de la page de connexion

    /** Photo de la page de connexion : PNG, JPEG ou WebP, 3 Mo au plus. */
    public Theme uploadLoginImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune image fournie");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format accepté : PNG, JPEG ou WebP");
        }
        if (file.getSize() > LOGIN_IMAGE_MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La photo dépasse 3 Mo");
        }
        SystemSettings settings = entity();
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorageService.store("branding", file.getOriginalFilename(), type, in);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Échec de l'enregistrement de la photo");
        }
        String previous = settings.getLoginImageStorageKey();
        settings.setLoginImage(stored.storageKey(), type);
        repository.flush();
        deleteQuietly(previous);
        auditLogService.record(AuditAction.THEME_UPDATED, "SystemSettings", SystemSettings.AUDIT_ENTITY_ID,
                "Nouvelle photo pour la page de connexion");
        settingsService.reload();
        return current();
    }

    public Theme removeLoginImage() {
        SystemSettings settings = entity();
        String previous = settings.getLoginImageStorageKey();
        if (previous != null) {
            settings.setLoginImage(null, null);
            repository.flush();
            deleteQuietly(previous);
            auditLogService.record(AuditAction.THEME_UPDATED, "SystemSettings", SystemSettings.AUDIT_ENTITY_ID,
                    "Photo de la page de connexion remise par défaut");
        }
        settingsService.reload();
        return current();
    }

    // ---------------------------------------------------------------- fichiers publics

    public record Image(byte[] content, String contentType) {
    }

    /** Logo téléversé, ou vide (l'interface garde alors le logo AEKD). */
    @Transactional(readOnly = true)
    public Optional<Image> logo() {
        SystemSettings s = entity();
        return read(s.getLogoStorageKey(), s.getLogoContentType());
    }

    @Transactional(readOnly = true)
    public Optional<Image> loginImage() {
        SystemSettings s = entity();
        return read(s.getLoginImageStorageKey(), s.getLoginImageContentType());
    }

    /**
     * Icône carrée de l'application installée, dessinée depuis le logo sur fond blanc,
     * avec une marge qui le garde visible dans les icônes rondes (« maskable »).
     * Vide sans logo téléversé : le manifeste garde alors les icônes AEKD.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> icon(int size) {
        if (!ICON_SIZES.contains(size)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Taille d'icône inconnue");
        }
        Optional<Image> logo = logo();
        if (logo.isEmpty()) {
            return Optional.empty();
        }
        String key = current().version() + ":" + size;
        return Optional.of(iconCache.computeIfAbsent(key, k -> drawIcon(logo.get().content(), size)));
    }

    /** Manifeste de l'application installée : nom, couleur et icônes de l'association. */
    @Transactional(readOnly = true)
    public String manifest() {
        Theme theme = current();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", theme.associationShortName() + " – Gestion de Tontine");
        m.put("short_name", theme.associationShortName() + " Tontine");
        m.put("description", "Gestion de la tontine : " + theme.associationName() + ".");
        m.put("lang", "fr");
        m.put("display", "standalone");
        m.put("scope", "/");
        m.put("start_url", "/");
        m.put("theme_color", theme.darkColor());
        m.put("background_color", "#ffffff");
        List<Map<String, String>> icons = new ArrayList<>();
        for (int size : ICON_SIZES) {
            String src = theme.customLogo()
                    ? "/api/public/icons/" + size + "?v=" + theme.version()
                    : "/icons/icon-" + size + "x" + size + ".png";
            icons.add(Map.of("src", src, "sizes", size + "x" + size, "type", "image/png", "purpose", "maskable any"));
        }
        m.put("icons", icons);
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- interne

    private static byte[] drawIcon(byte[] logoBytes, int size) {
        try {
            BufferedImage logo = ImageIO.read(new ByteArrayInputStream(logoBytes));
            if (logo == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Logo illisible");
            }
            BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = icon.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            // Zone sûre des icônes « maskable » : un cercle de 80 % ; le logo tient dans 72 %.
            double box = size * 0.72;
            double scale = Math.min(box / logo.getWidth(), box / logo.getHeight());
            int w = (int) Math.round(logo.getWidth() * scale);
            int h = (int) Math.round(logo.getHeight() * scale);
            g.drawImage(logo, (size - w) / 2, (size - h) / 2, w, h, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(icon, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Logo illisible");
        }
    }

    private Optional<Image> read(String key, String type) {
        if (key == null) {
            return Optional.empty();
        }
        try (InputStream in = fileStorageService.retrieve(key)) {
            return Optional.of(new Image(in.readAllBytes(), type));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String font(String value) {
        return FONTS.stream().filter(f -> f.equalsIgnoreCase(value.trim())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Police non proposée : " + value));
    }

    private static String color(String hex) {
        return hex.trim().toLowerCase(Locale.ROOT);
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private SystemSettings entity() {
        return repository.findById(SystemSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Ligne system_settings absente (migration V32)"));
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            fileStorageService.delete(key);
        } catch (IOException ignored) {
            // Best-effort : un fichier orphelin n'est pas bloquant.
        }
    }
}
