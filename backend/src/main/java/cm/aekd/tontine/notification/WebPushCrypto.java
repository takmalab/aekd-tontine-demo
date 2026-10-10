package cm.aekd.tontine.notification;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Web Push sans bibliothèque externe, avec le JDK seul :
 * <ul>
 *   <li>chiffrement du message « aes128gcm » (RFC 8291 / RFC 8188) ;</li>
 *   <li>authentification VAPID (RFC 8292) : jeton JWT signé ES256.</li>
 * </ul>
 */
public final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();
    private static final int RECORD_SIZE = 4096;
    static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {
    }

    /** Clés VAPID : publique (point non compressé, base64url) et privée (PKCS#8, base64). */
    public record VapidKeys(String publicKey, String privateKey) {
    }

    public static VapidKeys generateVapidKeys() {
        KeyPair pair = generateKeyPair();
        return new VapidKeys(B64URL.encodeToString(encodePoint((ECPublicKey) pair.getPublic())),
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
    }

    /** Chiffre {@code payload} pour l'abonnement (clé p256dh et secret auth du navigateur). */
    public static byte[] encrypt(byte[] payload, String p256dh, String auth) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(payload, decodeBase64Url(p256dh), decodeBase64Url(auth), generateKeyPair(), salt);
    }

    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt) {
        try {
            byte[] asPublic = encodePoint((ECPublicKey) ephemeral.getPublic());
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(ephemeral.getPrivate());
            agreement.doPhase(decodePoint(uaPublic), true);
            byte[] ecdhSecret = agreement.generateSecret();

            byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
            byte[] cek = hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII),
                    new byte[]{0}), 16);
            byte[] nonce = hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII),
                    new byte[]{0}), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // Délimiteur 0x02 : dernier (et unique) enregistrement, sans remplissage.
            byte[] ciphertext = cipher.doFinal(concat(payload, new byte[]{2}));

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement Web Push impossible", e);
        }
    }

    /** En-tête Authorization VAPID pour l'URL du service push du navigateur. */
    public static String vapidAuthorization(String endpoint, VapidKeys keys, String subject, Instant expiresAt) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        String header = B64URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64URL.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAt.getEpochSecond()
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        try {
            PrivateKey privateKey = KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keys.privateKey())));
            // Format P1363 (r || s sur 64 octets), exigé par JWS ES256.
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String jwt = signingInput + "." + B64URL.encodeToString(signature.sign());
            return "vapid t=" + jwt + ", k=" + keys.publicKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signature VAPID impossible", e);
        }
    }

    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Génération de clé EC impossible", e);
        }
    }

    static byte[] encodePoint(ECPublicKey key) {
        byte[] x = toFixedLength(key.getW().getAffineX());
        byte[] y = toFixedLength(key.getW().getAffineY());
        return concat(new byte[]{4}, x, y);
    }

    static ECPublicKey decodePoint(byte[] point) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 4) {
            throw new GeneralSecurityException("Clé publique P-256 non compressée attendue");
        }
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, P256));
    }

    /** HKDF-SHA256 (RFC 5869) limité à une sortie de 32 octets au plus. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{1})), length);
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] decodeBase64Url(String value) {
        // Les navigateurs envoient du base64url, parfois avec remplissage « = ».
        return B64URL_DECODER.decode(value.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    private static byte[] toFixedLength(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        }
        byte[] fixed = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, fixed, 32 - copy, copy);
        return fixed;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Courbe P-256 indisponible", e);
        }
    }
}
