package cm.aekd.tontine.auth.otp;

/**
 * Réponse identique que le numéro soit inscrit ou non, pour ne pas révéler
 * quels numéros ont un compte.
 */
public record OtpRequestResponse(
        String phone,
        String message,
        long expiresInSeconds,
        long resendAfterSeconds
) {
}
