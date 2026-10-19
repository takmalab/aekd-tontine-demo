package cm.aekd.tontine.settings;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogRepository;
import cm.aekd.tontine.document.AekdPdf;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.settings.ThemeService.Theme;
import cm.aekd.tontine.settings.ThemeService.ThemeRequest;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Thème de l'application (docs/decisions.md n° 72). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ThemeServiceTest {

    @Autowired private ThemeService service;
    @Autowired private ThemeController controller;
    @Autowired private SystemSettingsService settingsService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** La transaction est annulée : la copie en mémoire (et les couleurs des PDF) doit l'être aussi. */
    @AfterEach
    void forgetCachedSettings() {
        SecurityContextHolder.clearContext();
        TestTransaction.flagForRollback();
        TestTransaction.end();
        settingsService.reload();
    }

    private static ThemeRequest request(String primary, String headingFont, String pattern) {
        return new ThemeRequest(primary, "#1F6F43", "#202A36", headingFont, "nunito", pattern, "Ensemble, plus forts.");
    }

    private void loginAs(String prefix, RoleName... roles) {
        User user = new User(prefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        for (RoleName role : roles) {
            user.addRole(roleRepository.findByName(role).orElseThrow());
        }
        userRepository.save(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null,
                java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private static MockMultipartFile png(String name, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x1F, 0x6F, 0x43));
        g.fillOval(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", name, "image/png", out.toByteArray());
    }

    @Test
    void withoutSettingsTheAekdThemeApplies() {
        Theme theme = service.current();

        assertThat(theme.primaryColor()).isEqualTo("#e86e09");
        assertThat(theme.secondaryColor()).isEqualTo("#ab583a");
        assertThat(theme.darkColor()).isEqualTo("#36353a");
        assertThat(theme.headingFont()).isEqualTo("Poppins");
        assertThat(theme.bodyFont()).isEqualTo("Inter");
        assertThat(theme.pattern()).isEqualTo("MASQUES");
        assertThat(theme.slogan()).isEqualTo("La solidarité, en toute transparence.");
        assertThat(theme.customLogo()).isFalse();
        assertThat(theme.fonts()).contains("Poppins", "Montserrat");
        assertThat(AekdPdf.ORANGE).isEqualTo(new Color(0xE8, 0x6E, 0x09));
    }

    @Test
    void anUpdatedThemeIsNormalisedAppliedToPdfsAndAudited() {
        Theme theme = service.update(request("#0B5FA5", "montserrat", "symboles"));

        assertThat(theme.primaryColor()).isEqualTo("#0b5fa5");
        assertThat(theme.headingFont()).isEqualTo("Montserrat");
        assertThat(theme.bodyFont()).isEqualTo("Nunito");
        assertThat(theme.pattern()).isEqualTo("SYMBOLES");
        assertThat(theme.slogan()).isEqualTo("Ensemble, plus forts.");
        assertThat(service.current().primaryColor()).isEqualTo("#0b5fa5");
        // PDF : couleurs du thème, teinte claire calculée depuis la principale.
        assertThat(AekdPdf.ORANGE).isEqualTo(new Color(0x0B, 0x5F, 0xA5));
        assertThat(AekdPdf.BROWN).isEqualTo(new Color(0x1F, 0x6F, 0x43));
        assertThat(AekdPdf.GRAY).isEqualTo(new Color(0x20, 0x2A, 0x36));
        assertThat(AekdPdf.SOFT_ORANGE).isEqualTo(new Color(231, 239, 246));
        assertThat(auditLogRepository.findAllByOrderByOccurredAtDesc())
                .anyMatch(a -> a.getAction() == AuditAction.THEME_UPDATED && a.getDescription().contains("#0b5fa5"));
    }

    @Test
    void unknownFontsAndPatternsAreRejected() {
        assertThatThrownBy(() -> service.update(request("#0b5fa5", "Comic Sans MS", "MASQUES")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("Police non proposée");
        assertThatThrownBy(() -> service.update(request("#0b5fa5", "Poppins", "ZEBRES")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("Motif inconnu");
    }

    @Test
    void theLogoGivesTheInstalledAppItsIconsAndManifest() throws IOException {
        assertThat(service.icon(192)).isEmpty();
        assertThat(service.manifest()).contains("\"/icons/icon-192x192.png\"").contains("\"theme_color\":\"#36353a\"");

        settingsService.uploadLogo(png("logo.png", 400, 200));
        service.update(request("#0b5fa5", "Poppins", "AUCUN"));

        byte[] icon = service.icon(192).orElseThrow();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(icon));
        assertThat(image.getWidth()).isEqualTo(192);
        assertThat(image.getHeight()).isEqualTo(192);
        // Fond blanc dans les coins, logo au centre.
        assertThat(new Color(image.getRGB(2, 2))).isEqualTo(Color.WHITE);
        assertThat(new Color(image.getRGB(96, 96))).isEqualTo(new Color(0x1F, 0x6F, 0x43));
        Theme theme = service.current();
        assertThat(theme.customLogo()).isTrue();
        assertThat(service.manifest()).contains("/api/public/icons/192?v=" + theme.version())
                .contains("\"theme_color\":\"#202a36\"");
        assertThatThrownBy(() -> service.icon(100)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void theLoginPhotoAcceptsOnlyImages() throws IOException {
        MockMultipartFile gif = new MockMultipartFile("file", "photo.gif", "image/gif", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.uploadLoginImage(gif))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));

        assertThat(service.uploadLoginImage(png("photo.png", 64, 64)).customLoginImage()).isTrue();
        assertThat(service.loginImage()).isPresent();
        assertThat(service.removeLoginImage().customLoginImage()).isFalse();
        assertThat(service.loginImage()).isEmpty();
    }

    @Test
    void onlyTheSuperAdministratorChangesTheTheme() {
        loginAs("theme-admin", RoleName.ADMIN);
        assertThatThrownBy(() -> controller.update(request("#0b5fa5", "Poppins", "MASQUES")))
                .isInstanceOf(AccessDeniedException.class);

        loginAs("theme-root", RoleName.SUPER_ADMIN, RoleName.ADMIN);
        assertThat(controller.update(request("#0b5fa5", "Poppins", "MASQUES")).primaryColor()).isEqualTo("#0b5fa5");
        // Lecture publique, sans compte.
        SecurityContextHolder.clearContext();
        assertThat(controller.theme().getBody()).isNotNull();
        assertThat(List.of(controller.logo().getStatusCode().value())).containsAnyOf(200, 404);
    }
}
