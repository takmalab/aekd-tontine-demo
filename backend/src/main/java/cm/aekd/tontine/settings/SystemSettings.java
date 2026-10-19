package cm.aekd.tontine.settings;

import cm.aekd.tontine.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Paramètres système (decisions.md n° 57), ligne unique créée par la migration
 * V32. Une règle nulle reprend la valeur de la configuration du serveur.
 */
@Entity
@Table(name = "system_settings")
public class SystemSettings {

    public static final short SINGLETON_ID = 1;
    public static final UUID AUDIT_ENTITY_ID = new UUID(0, 3);

    @Id
    private Short id;

    @Column(name = "association_name", nullable = false, length = 150)
    private String associationName;

    @Column(name = "association_short_name", nullable = false, length = 30)
    private String associationShortName;

    @Column(name = "association_address")
    private String associationAddress;

    @Column(name = "association_phone", length = 30)
    private String associationPhone;

    @Column(name = "association_email")
    private String associationEmail;

    @Column(name = "logo_storage_key", length = 500)
    private String logoStorageKey;

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(name = "session_days")
    private Integer sessionDays;

    @Column(name = "otp_ttl_minutes")
    private Integer otpTtlMinutes;

    @Column(name = "otp_max_attempts")
    private Integer otpMaxAttempts;

    @Column(name = "otp_resend_seconds")
    private Integer otpResendSeconds;

    @Column(name = "otp_max_per_hour")
    private Integer otpMaxPerHour;

    @Column(name = "maintenance_enabled", nullable = false)
    private boolean maintenanceEnabled;

    @Column(name = "maintenance_message", length = 500)
    private String maintenanceMessage;

    // Thème (decisions.md n° 72) : null = valeur par défaut (thème AEKD).
    @Column(name = "theme_primary_color", length = 7)
    private String themePrimaryColor;

    @Column(name = "theme_secondary_color", length = 7)
    private String themeSecondaryColor;

    @Column(name = "theme_dark_color", length = 7)
    private String themeDarkColor;

    @Column(name = "theme_heading_font", length = 40)
    private String themeHeadingFont;

    @Column(name = "theme_body_font", length = 40)
    private String themeBodyFont;

    @Column(name = "theme_pattern", length = 20)
    private String themePattern;

    @Column(name = "login_slogan", length = 150)
    private String loginSlogan;

    @Column(name = "login_image_storage_key", length = 500)
    private String loginImageStorageKey;

    @Column(name = "login_image_content_type", length = 100)
    private String loginImageContentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SystemSettings() {
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getAssociationName() {
        return associationName;
    }

    public void setAssociationName(String associationName) {
        this.associationName = associationName;
    }

    public String getAssociationShortName() {
        return associationShortName;
    }

    public void setAssociationShortName(String associationShortName) {
        this.associationShortName = associationShortName;
    }

    public String getAssociationAddress() {
        return associationAddress;
    }

    public void setAssociationAddress(String associationAddress) {
        this.associationAddress = associationAddress;
    }

    public String getAssociationPhone() {
        return associationPhone;
    }

    public void setAssociationPhone(String associationPhone) {
        this.associationPhone = associationPhone;
    }

    public String getAssociationEmail() {
        return associationEmail;
    }

    public void setAssociationEmail(String associationEmail) {
        this.associationEmail = associationEmail;
    }

    public String getLogoStorageKey() {
        return logoStorageKey;
    }

    public String getLogoContentType() {
        return logoContentType;
    }

    public void setLogo(String storageKey, String contentType) {
        this.logoStorageKey = storageKey;
        this.logoContentType = storageKey == null ? null : contentType;
    }

    public String getThemePrimaryColor() {
        return themePrimaryColor;
    }

    public String getThemeSecondaryColor() {
        return themeSecondaryColor;
    }

    public String getThemeDarkColor() {
        return themeDarkColor;
    }

    public void setThemeColors(String primary, String secondary, String dark) {
        this.themePrimaryColor = primary;
        this.themeSecondaryColor = secondary;
        this.themeDarkColor = dark;
    }

    public String getThemeHeadingFont() {
        return themeHeadingFont;
    }

    public String getThemeBodyFont() {
        return themeBodyFont;
    }

    public void setThemeFonts(String heading, String body) {
        this.themeHeadingFont = heading;
        this.themeBodyFont = body;
    }

    public String getThemePattern() {
        return themePattern;
    }

    public void setThemePattern(String themePattern) {
        this.themePattern = themePattern;
    }

    public String getLoginSlogan() {
        return loginSlogan;
    }

    public void setLoginSlogan(String loginSlogan) {
        this.loginSlogan = loginSlogan;
    }

    public String getLoginImageStorageKey() {
        return loginImageStorageKey;
    }

    public String getLoginImageContentType() {
        return loginImageContentType;
    }

    public void setLoginImage(String storageKey, String contentType) {
        this.loginImageStorageKey = storageKey;
        this.loginImageContentType = storageKey == null ? null : contentType;
    }

    public Integer getSessionDays() {
        return sessionDays;
    }

    public void setSessionDays(Integer sessionDays) {
        this.sessionDays = sessionDays;
    }

    public Integer getOtpTtlMinutes() {
        return otpTtlMinutes;
    }

    public void setOtpTtlMinutes(Integer otpTtlMinutes) {
        this.otpTtlMinutes = otpTtlMinutes;
    }

    public Integer getOtpMaxAttempts() {
        return otpMaxAttempts;
    }

    public void setOtpMaxAttempts(Integer otpMaxAttempts) {
        this.otpMaxAttempts = otpMaxAttempts;
    }

    public Integer getOtpResendSeconds() {
        return otpResendSeconds;
    }

    public void setOtpResendSeconds(Integer otpResendSeconds) {
        this.otpResendSeconds = otpResendSeconds;
    }

    public Integer getOtpMaxPerHour() {
        return otpMaxPerHour;
    }

    public void setOtpMaxPerHour(Integer otpMaxPerHour) {
        this.otpMaxPerHour = otpMaxPerHour;
    }

    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    public void setMaintenanceEnabled(boolean maintenanceEnabled) {
        this.maintenanceEnabled = maintenanceEnabled;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    public void setMaintenanceMessage(String maintenanceMessage) {
        this.maintenanceMessage = maintenanceMessage;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
