package cm.aekd.tontine.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/**
 * Numéros de téléphone acceptés pour la connexion par OTP : Cameroun
 * uniquement (docs/decisions.md n° 47), stockés au format E.164
 * {@code +237XXXXXXXXX}. L'utilisateur peut saisir le numéro local
 * (6XXXXXXXX), avec ou sans indicatif, espaces, points ou tirets.
 */
public final class PhoneNumbers {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.()\\-]");
    private static final Pattern COUNTRY_PREFIX = Pattern.compile("^(\\+237|00237|237)");
    private static final Pattern LOCAL_NUMBER = Pattern.compile("^[26]\\d{8}$");

    private PhoneNumbers() {
    }

    /**
     * Numéro normalisé, ou {@code null} si la saisie est vide.
     *
     * @throws ResponseStatusException 400 si le numéro n'est pas un numéro camerounais valide
     */
    public static String normalizeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = SEPARATORS.matcher(raw.trim()).replaceAll("");
        String local = COUNTRY_PREFIX.matcher(compact).replaceFirst("");
        if (!LOCAL_NUMBER.matcher(local).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Numéro de téléphone invalide : saisissez un numéro camerounais à 9 chiffres (ex. 6XXXXXXXX)");
        }
        return "+237" + local;
    }

    /** Comme {@link #normalizeOrNull}, mais un numéro est obligatoire. */
    public static String normalize(String raw) {
        String phone = normalizeOrNull(raw);
        if (phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le numéro de téléphone est obligatoire");
        }
        return phone;
    }
}
