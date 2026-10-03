package cm.aekd.tontine.document;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Montant en toutes lettres pour les reçus (« Arrêté le présent reçu à la somme
 * de cinquante mille francs CFA »). Orthographe traditionnelle : traits d'union
 * entre dizaines et unités, « et » pour 21, 31… 71, accords de « vingt » et
 * « cent », « mille » invariable.
 */
public final class AmountInWords {

    private static final String[] UNITS = {
            "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
            "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
            "dix-sept", "dix-huit", "dix-neuf"
    };
    private static final String[] TENS = {
            "", "", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante", "quatre-vingt", "quatre-vingt"
    };

    private AmountInWords() {
    }

    /** « cinquante mille francs CFA » ; arrondi au franc. */
    public static String francsCfa(BigDecimal amount) {
        long value = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        String words = value < 0 ? "moins " + of(-value) : of(value);
        return words + (Math.abs(value) > 1 ? " francs CFA" : " franc CFA");
    }

    static String of(long n) {
        if (n == 0) {
            return UNITS[0];
        }
        StringBuilder out = new StringBuilder();
        long billions = n / 1_000_000_000L;
        long millions = (n / 1_000_000L) % 1000;
        long thousands = (n / 1000) % 1000;
        long rest = n % 1000;

        if (billions > 0) {
            append(out, belowThousand((int) billions, true) + (billions > 1 ? " milliards" : " milliard"));
        }
        if (millions > 0) {
            append(out, belowThousand((int) millions, true) + (millions > 1 ? " millions" : " million"));
        }
        if (thousands > 0) {
            append(out, thousands == 1 ? "mille" : belowThousand((int) thousands, false) + " mille");
        }
        if (rest > 0) {
            append(out, belowThousand((int) rest, true));
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String part) {
        if (!out.isEmpty()) {
            out.append(' ');
        }
        out.append(part);
    }

    /**
     * @param last faux devant « mille » (adjectif numéral invariable) : « quatre-vingts »
     *             et « deux cents » perdent alors leur « s ». Vrai en fin de nombre et
     *             devant « million(s) » / « milliard(s) », qui sont des noms.
     */
    private static String belowThousand(int n, boolean last) {
        int hundreds = n / 100;
        int rest = n % 100;
        StringBuilder out = new StringBuilder();
        if (hundreds > 0) {
            out.append(hundreds == 1 ? "cent" : UNITS[hundreds] + " cent");
            if (hundreds > 1 && rest == 0 && last) {
                out.append('s');
            }
        }
        if (rest > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(belowHundred(rest, last));
        }
        return out.toString();
    }

    private static String belowHundred(int n, boolean last) {
        if (n < 20) {
            return UNITS[n];
        }
        int tens = n / 10;
        int units = n % 10;
        String base = TENS[tens];
        if (tens == 7 || tens == 9) {
            int sub = 10 + units;
            String link = (tens == 7 && units == 1) ? " et " : "-";
            return base + link + UNITS[sub];
        }
        if (units == 0) {
            return tens == 8 && last ? base + "s" : base;
        }
        if (units == 1 && tens != 8) {
            return base + " et un";
        }
        return base + "-" + UNITS[units];
    }
}
