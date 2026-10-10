package cm.aekd.tontine.notification;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushCryptoTest {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    /** Vecteur de test officiel : RFC 8291, annexe A. */
    @Test
    void matchesRfc8291TestVector() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        KeyPair asKeys = keyPair("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
                "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] uaPublic = WebPushCrypto.decodeBase64Url(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = WebPushCrypto.decodeBase64Url("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = WebPushCrypto.decodeBase64Url("DGv6ra1nlYgDCS1FRnbzlw");

        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, auth, asKeys, salt);

        assertThat(B64URL.encodeToString(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8w"
                        + "EqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void browserCanDecryptWhatTheServerEncrypts() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] auth = new byte[16];
        Arrays.fill(auth, (byte) 7);
        String payload = "{\"title\":\"Paiement validé\",\"body\":\"50 000 FCFA\"}";

        byte[] body = WebPushCrypto.encrypt(payload.getBytes(StandardCharsets.UTF_8),
                B64URL.encodeToString(WebPushCrypto.encodePoint((ECPublicKey) browser.getPublic())),
                B64URL.encodeToString(auth));

        assertThat(new String(decryptAsBrowser(body, browser, auth), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void vapidTokenIsSignedForTheEndpointOrigin() throws Exception {
        WebPushCrypto.VapidKeys keys = WebPushCrypto.generateVapidKeys();
        String header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc", keys,
                "mailto:admin@aekd.cm", Instant.ofEpochSecond(2_000_000_000L));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + keys.publicKey());
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"", "\"exp\":2000000000",
                "\"sub\":\"mailto:admin@aekd.cm\"");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPushCrypto.decodePoint(WebPushCrypto.decodeBase64Url(keys.publicKey())));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    /** Côté navigateur (RFC 8291 §3.4) : sert à vérifier le chiffrement du serveur. */
    private static byte[] decryptAsBrowser(byte[] body, KeyPair browser, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt();
        byte[] asPublic = new byte[buffer.get()];
        buffer.get(asPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(WebPushCrypto.decodePoint(asPublic), true);
        byte[] uaPublic = WebPushCrypto.encodePoint((ECPublicKey) browser.getPublic());
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = WebPushCrypto.hkdf(auth, agreement.generateSecret(), keyInfo, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static KeyPair keyPair(String privateD, String publicPoint) throws Exception {
        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(
                new BigInteger(1, WebPushCrypto.decodeBase64Url(privateD)), WebPushCrypto.P256));
        return new KeyPair(WebPushCrypto.decodePoint(WebPushCrypto.decodeBase64Url(publicPoint)), privateKey);
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(p -> p.length).sum();
        ByteBuffer out = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            out.put(part);
        }
        return out.array();
    }
}
