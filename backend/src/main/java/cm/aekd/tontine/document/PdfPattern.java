package cm.aekd.tontine.document;

import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfTemplate;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motifs africains de l'identité AEKD en fond des PDF, dessinés en vectoriel :
 * nets à toute taille, de la couleur voulue, et stockés une seule fois par
 * document (une tuile répétée sur chaque page).
 *
 * Les tracés (branding/patterns/*.path) viennent des mêmes SVG que l'interface,
 * simplifiés par scripts/patterns-to-pdf.mjs : il ne reste que les commandes
 * absolues M, L, H, V, C, Q et Z, remplies en règle evenodd.
 */
public enum PdfPattern {

    MASQUES("aekd-masques"),
    SYMBOLES("aekd-symboles");

    private static final Pattern TOKEN = Pattern.compile("[MLHVCQZ]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    private final String file;
    private volatile Shape shape;

    PdfPattern(String file) {
        this.file = file;
    }

    /**
     * Tuile du motif, à poser avec {@code addTemplate} autant de fois que
     * nécessaire.
     *
     * @param width   largeur de la tuile, en points
     * @param color   couleur du motif
     * @param opacity opacité (ex. 0,05 pour un fond discret)
     */
    public PdfTemplate tile(PdfContentByte cb, float width, Color color, float opacity) {
        Shape s = shape();
        float scale = width / s.width;
        float height = s.height * scale;
        PdfTemplate tile = cb.createTemplate(width, height);

        PdfGState transparency = new PdfGState();
        transparency.setFillOpacity(opacity);
        tile.setGState(transparency);
        tile.setColorFill(color);
        // Repère SVG (y vers le bas) vers repère PDF (y vers le haut).
        tile.concatCTM(scale, 0, 0, -scale, 0, height);
        s.draw(tile);
        tile.eoFill();
        return tile;
    }

    /** Hauteur d'une tuile de la largeur donnée. */
    public float tileHeight(float width) {
        Shape s = shape();
        return s.height * width / s.width;
    }

    private Shape shape() {
        Shape s = shape;
        if (s == null) {
            synchronized (this) {
                if (shape == null) {
                    shape = load();
                }
                s = shape;
            }
        }
        return s;
    }

    private Shape load() {
        String resource = "/branding/patterns/" + file + ".path";
        try (InputStream in = PdfPattern.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Motif introuvable : " + resource);
            }
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R", 2);
            String[] viewBox = lines[0].trim().split("[\\s,]+");
            return new Shape(Float.parseFloat(viewBox[2]), Float.parseFloat(viewBox[3]), parse(lines[1]));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Commandes à plat : code de commande puis ses coordonnées. */
    static float[] parse(String d) {
        List<Float> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(d);
        char command = 0;
        List<Float> args = new ArrayList<>();
        while (m.find()) {
            String token = m.group();
            if (Character.isLetter(token.charAt(0))) {
                flush(command, args, out);
                command = token.charAt(0);
                if (command == 'Z') {
                    out.add((float) 'Z');
                }
            } else {
                args.add(Float.parseFloat(token));
            }
        }
        flush(command, args, out);
        float[] ops = new float[out.size()];
        for (int i = 0; i < ops.length; i++) {
            ops[i] = out.get(i);
        }
        return ops;
    }

    /** Répète la commande pour chaque groupe d'arguments (M suivi de paires = L). */
    private static void flush(char command, List<Float> args, List<Float> out) {
        if (command == 0 || command == 'Z' || args.isEmpty()) {
            args.clear();
            return;
        }
        int arity = switch (command) {
            case 'M', 'L' -> 2;
            case 'H', 'V' -> 1;
            case 'C' -> 6;
            case 'Q' -> 4;
            default -> throw new IllegalArgumentException("Commande de tracé non prise en charge : " + command);
        };
        if (args.size() % arity != 0) {
            throw new IllegalArgumentException("Nombre d'arguments invalide pour " + command);
        }
        for (int i = 0; i < args.size(); i += arity) {
            char effective = command == 'M' && i > 0 ? 'L' : command;
            out.add((float) effective);
            out.addAll(args.subList(i, i + arity));
        }
        args.clear();
    }

    private record Shape(float width, float height, float[] ops) {

        void draw(PdfContentByte cb) {
            float x = 0;
            float y = 0;
            float startX = 0;
            float startY = 0;
            int i = 0;
            while (i < ops.length) {
                char c = (char) ops[i++];
                switch (c) {
                    case 'M' -> {
                        cb.moveTo(startX = x = ops[i++], startY = y = ops[i++]);
                    }
                    case 'L' -> cb.lineTo(x = ops[i++], y = ops[i++]);
                    case 'H' -> cb.lineTo(x = ops[i++], y);
                    case 'V' -> cb.lineTo(x, y = ops[i++]);
                    case 'C' -> {
                        cb.curveTo(ops[i], ops[i + 1], ops[i + 2], ops[i + 3], ops[i + 4], ops[i + 5]);
                        x = ops[i + 4];
                        y = ops[i + 5];
                        i += 6;
                    }
                    case 'Q' -> {
                        // Courbe quadratique convertie en cubique (le PDF n'a que des cubiques).
                        float qx = ops[i];
                        float qy = ops[i + 1];
                        float ex = ops[i + 2];
                        float ey = ops[i + 3];
                        cb.curveTo(x + 2f / 3 * (qx - x), y + 2f / 3 * (qy - y),
                                ex + 2f / 3 * (qx - ex), ey + 2f / 3 * (qy - ey), ex, ey);
                        x = ex;
                        y = ey;
                        i += 4;
                    }
                    case 'Z' -> {
                        cb.closePath();
                        x = startX;
                        y = startY;
                    }
                    default -> throw new IllegalStateException("Commande inattendue : " + c);
                }
            }
        }
    }
}
