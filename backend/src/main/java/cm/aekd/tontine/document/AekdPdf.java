package cm.aekd.tontine.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Gabarit commun des documents PDF de l'AEKD : en-tête (logo, nom de
 * l'association, titre du document), pied de page (date de génération,
 * auteur, pagination « Page X / Y ») et briques de mise en page aux couleurs
 * de la charte (orange / marron / gris).
 *
 * Usage : {@code AekdPdf pdf = AekdPdf.portrait(...); pdf.add(...); byte[] bytes = pdf.finish();}
 */
public final class AekdPdf {

    public static final String DEFAULT_ASSOCIATION_NAME = "Amicale des Enfants Kamkop de Douala";
    public static final String DEFAULT_ASSOCIATION_SHORT = "AEKD";

    /**
     * Identité de l'association sur les PDF, réglée par le super administrateur
     * (decisions.md n° 57). {@code logo} null = logo par défaut ; {@code contactLine}
     * (adresse · téléphone · e-mail) affichée en pied de page si présente.
     */
    public record Branding(String name, String shortName, String contactLine, byte[] logo) {
    }

    private static volatile Branding branding =
            new Branding(DEFAULT_ASSOCIATION_NAME, DEFAULT_ASSOCIATION_SHORT, null, null);

    public static void applyBranding(Branding value) {
        branding = value;
    }

    public static String associationName() {
        return branding.name();
    }

    public static String associationShortName() {
        return branding.shortName();
    }

    public static final Color ORANGE = new Color(0xE8, 0x6E, 0x09);
    public static final Color BROWN = new Color(0xAB, 0x58, 0x3A);
    public static final Color GRAY = new Color(0x36, 0x35, 0x3A);
    public static final Color MUTED = new Color(0x6B, 0x6A, 0x70);
    public static final Color LINE = new Color(0xE3, 0xD9, 0xD1);
    public static final Color ZEBRA = new Color(0xF8, 0xF4, 0xF1);
    public static final Color SOFT_ORANGE = new Color(0xFD, 0xEF, 0xE3);
    public static final Color GREEN = new Color(0x15, 0x80, 0x3D);
    public static final Color RED = new Color(0xB9, 0x1C, 0x1C);
    public static final Color BLUE = new Color(0x1D, 0x4E, 0xD8);
    public static final Color VIOLET = new Color(0x6D, 0x28, 0xD9);

    private static final BaseFont REGULAR = font(BaseFont.HELVETICA);
    private static final BaseFont BOLD = font(BaseFont.HELVETICA_BOLD);
    private static final BaseFont ITALIC = font(BaseFont.HELVETICA_OBLIQUE);
    private static final byte[] LOGO = loadLogo();

    /** Espace minimal (points) sous un titre de section pour ne pas le laisser seul en bas de page. */
    private static final float SECTION_MIN_SPACE = 120;

    private final Document document;
    private final PdfWriter writer;
    private final Frame frame;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private AekdPdf(Rectangle pageSize, String title, String generatedBy) {
        document = new Document(pageSize, 40, 40, 96, 54);
        writer = PdfWriter.getInstance(document, out);
        frame = new Frame(title, generatedBy);
        writer.setPageEvent(frame);
        document.addTitle(title);
        document.addAuthor(associationName());
        document.addCreator("AEKD-Tontine");
        document.open();
    }

    public static AekdPdf portrait(String title, String generatedBy) {
        return new AekdPdf(PageSize.A4, title, generatedBy);
    }

    public static AekdPdf landscape(String title, String generatedBy) {
        return new AekdPdf(PageSize.A4.rotate(), title, generatedBy);
    }

    /**
     * Motif africain en fond de chaque page (identité AEKD), dans la couleur du
     * document. Par défaut : masques, marron. À appeler avant d'ajouter du
     * contenu pour que la première page en profite aussi.
     */
    public AekdPdf background(PdfPattern pattern, Color color) {
        frame.pattern = pattern;
        frame.patternColor = color;
        frame.patternTile = null;
        return this;
    }

    /** Sans motif de fond (ex. document destiné à une impression économe). */
    public AekdPdf noBackground() {
        frame.pattern = null;
        return this;
    }

    // ------------------------------------------------------------------ polices

    public static Font font(float size, Color color) {
        return new Font(REGULAR, size, Font.NORMAL, color);
    }

    public static Font bold(float size, Color color) {
        return new Font(BOLD, size, Font.NORMAL, color);
    }

    public static Font italic(float size, Color color) {
        return new Font(ITALIC, size, Font.NORMAL, color);
    }

    // ------------------------------------------------------------------ contenu

    /** Grand titre du document, avec un sous-titre facultatif. */
    public void heading(String title, String subtitle) {
        Paragraph p = new Paragraph(title.toUpperCase(java.util.Locale.FRANCE), bold(17, GRAY));
        p.setSpacingAfter(subtitle == null ? 10 : 2);
        add(p);
        if (subtitle != null) {
            Paragraph s = new Paragraph(subtitle, font(10, MUTED));
            s.setSpacingAfter(12);
            add(s);
        }
    }

    /** Titre de section avec filet orange ; passe à la page suivante s'il resterait orphelin. */
    public void section(String title) {
        if (writer.getVerticalPosition(true) - document.bottom() < SECTION_MIN_SPACE) {
            document.newPage();
        }
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(12);
        t.setSpacingAfter(6);
        PdfPCell c = new PdfPCell(new Phrase(title, bold(11.5f, BROWN)));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(ORANGE);
        c.setBorderWidth(1.2f);
        c.setPaddingBottom(4);
        c.setPaddingLeft(0);
        t.addCell(c);
        add(t);
    }

    public void paragraph(String text) {
        Paragraph p = new Paragraph(text, font(9.5f, GRAY));
        p.setSpacingAfter(6);
        p.setLeading(13);
        add(p);
    }

    public void note(String text) {
        Paragraph p = new Paragraph(text, italic(8.5f, MUTED));
        p.setSpacingBefore(4);
        p.setSpacingAfter(4);
        add(p);
    }

    /**
     * Grille libellé / valeur sur deux colonnes de paires (libellé, valeur, libellé, valeur…).
     * Une paire dont la valeur est longue peut être passée seule avec {@link #keyValuesFull}.
     */
    public void keyValues(List<String[]> pairs) {
        PdfPTable t = new PdfPTable(new float[]{1.1f, 1.9f, 1.1f, 1.9f});
        t.setWidthPercentage(100);
        t.setSpacingAfter(6);
        for (String[] pair : pairs) {
            t.addCell(labelCell(pair[0]));
            t.addCell(valueCell(pair[1]));
        }
        if (pairs.size() % 2 == 1) {
            PdfPCell empty = new PdfPCell(new Phrase(""));
            empty.setBorder(Rectangle.NO_BORDER);
            empty.setColspan(2);
            t.addCell(empty);
        }
        add(t);
    }

    /** Paires libellé / valeur, une par ligne (valeurs longues). */
    public void keyValuesFull(List<String[]> pairs) {
        PdfPTable t = new PdfPTable(new float[]{1.1f, 4.9f});
        t.setWidthPercentage(100);
        t.setSpacingAfter(6);
        for (String[] pair : pairs) {
            t.addCell(labelCell(pair[0]));
            t.addCell(valueCell(pair[1]));
        }
        add(t);
    }

    /** Rangée de cartes chiffrées (ex. épargne, fonds versés, prêts en cours). */
    public void statCards(List<String[]> cards) {
        PdfPTable t = new PdfPTable(cards.size());
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(8);
        for (String[] card : cards) {
            PdfPCell c = new PdfPCell();
            c.setBackgroundColor(SOFT_ORANGE);
            c.setBorderColor(LINE);
            c.setBorderWidth(0.6f);
            c.setBorderWidthTop(2.2f);
            c.setBorderColorTop(ORANGE);
            c.setPadding(8);
            Paragraph value = new Paragraph(card[1], bold(13, GRAY));
            value.setAlignment(Element.ALIGN_CENTER);
            Paragraph label = new Paragraph(card[0], font(8, MUTED));
            label.setAlignment(Element.ALIGN_CENTER);
            c.addElement(value);
            c.addElement(label);
            t.addCell(c);
        }
        add(t);
    }

    /** Montant mis en valeur (reçus), avec son libellé et sa mention en toutes lettres. */
    public void amountBox(String label, String amount, String words) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        t.setSpacingAfter(10);
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(SOFT_ORANGE);
        c.setBorderColor(ORANGE);
        c.setBorderWidth(1.2f);
        c.setPadding(12);
        Paragraph l = new Paragraph(label, font(9, MUTED));
        l.setAlignment(Element.ALIGN_CENTER);
        Paragraph a = new Paragraph(amount, bold(22, ORANGE));
        a.setAlignment(Element.ALIGN_CENTER);
        a.setSpacingBefore(2);
        c.addElement(l);
        c.addElement(a);
        if (words != null) {
            Paragraph w = new Paragraph(words, italic(9, GRAY));
            w.setAlignment(Element.ALIGN_CENTER);
            w.setSpacingBefore(4);
            c.addElement(w);
        }
        t.addCell(c);
        add(t);
    }

    /** Zones de signature côte à côte (ex. « Le trésorier », « Le membre »). */
    public void signatures(String... roles) {
        PdfPTable t = new PdfPTable(roles.length);
        t.setWidthPercentage(100);
        t.setSpacingBefore(26);
        for (String role : roles) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingRight(18);
            c.addElement(new Paragraph(role, bold(9.5f, GRAY)));
            Paragraph space = new Paragraph(" ", font(30, GRAY));
            c.addElement(space);
            Paragraph rule = new Paragraph("Signature", font(8, MUTED));
            c.addElement(rule);
            t.addCell(c);
        }
        add(t);
    }

    /**
     * Tableau de données avec en-tête marron et lignes alternées.
     *
     * @param widths     largeurs relatives des colonnes
     * @param rightAlign colonnes alignées à droite (montants), par index
     */
    public void table(String[] headers, float[] widths, List<Cell[]> rows, int... rightAlign) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setSpacingAfter(6);
        for (int i = 0; i < headers.length; i++) {
            PdfPCell h = new PdfPCell(new Phrase(headers[i], bold(8.5f, Color.WHITE)));
            h.setBackgroundColor(BROWN);
            h.setBorderColor(BROWN);
            h.setPadding(5);
            h.setHorizontalAlignment(isRight(i, rightAlign) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            t.addCell(h);
        }
        if (rows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Aucun élément.", italic(9, MUTED)));
            empty.setColspan(headers.length);
            empty.setPadding(8);
            empty.setBorderColor(LINE);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(empty);
        }
        for (int r = 0; r < rows.size(); r++) {
            Cell[] row = rows.get(r);
            for (int i = 0; i < row.length; i++) {
                Cell cell = row[i];
                PdfPCell c = new PdfPCell(new Phrase(cell.text(),
                        cell.bold() ? bold(8.5f, cell.color()) : font(8.5f, cell.color())));
                c.setPadding(4.5f);
                c.setBorderColor(LINE);
                c.setBorderWidth(0.5f);
                if (cell.background() != null) {
                    c.setBackgroundColor(cell.background());
                } else if (r % 2 == 1) {
                    c.setBackgroundColor(ZEBRA);
                }
                c.setHorizontalAlignment(isRight(i, rightAlign) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                t.addCell(c);
            }
        }
        add(t);
    }

    public void spacer(float height) {
        Paragraph p = new Paragraph(" ", font(1, GRAY));
        p.setSpacingAfter(height);
        add(p);
    }

    public void newPage() {
        document.newPage();
    }

    public byte[] finish() {
        document.close();
        return out.toByteArray();
    }

    private void add(Element element) {
        try {
            document.add(element);
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du PDF impossible", e);
        }
    }

    private static boolean isRight(int column, int[] rightAlign) {
        for (int c : rightAlign) {
            if (c == column) {
                return true;
            }
        }
        return false;
    }

    private static PdfPCell labelCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(8.5f, MUTED)));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setPadding(5);
        return c;
    }

    private static PdfPCell valueCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(DocumentFormat.orDash(text), bold(9, GRAY)));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setPadding(5);
        return c;
    }

    private static BaseFont font(String name) {
        try {
            return BaseFont.createFont(name, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Police PDF indisponible : " + name, e);
        }
    }

    private static byte[] loadLogo() {
        try (InputStream in = AekdPdf.class.getResourceAsStream("/branding/logo-aekd.png")) {
            if (in == null) {
                throw new IllegalStateException("Logo introuvable : /branding/logo-aekd.png");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Une cellule de tableau : texte, couleur, gras, fond facultatif. */
    public record Cell(String text, Color color, boolean bold, Color background) {

        public static Cell of(String text) {
            return new Cell(DocumentFormat.orDash(text), GRAY, false, null);
        }

        public static Cell strong(String text) {
            return new Cell(DocumentFormat.orDash(text), GRAY, true, null);
        }

        public static Cell colored(String text, Color color) {
            return new Cell(DocumentFormat.orDash(text), color, true, null);
        }

        /** Ligne de total : texte gras sur fond orangé. */
        public static Cell total(String text) {
            return new Cell(text == null ? "" : text, GRAY, true, SOFT_ORANGE);
        }
    }

    /** En-tête et pied de page dessinés sur chaque page ; total des pages écrit à la fermeture. */
    private static final class Frame extends PdfPageEventHelper {

        /** Opacité du motif : visible à l'écran, discret à l'impression. */
        private static final float PATTERN_OPACITY = 0.05f;
        /** Largeur d'une tuile du motif, en points (≈ 12,7 cm). */
        private static final float PATTERN_TILE_WIDTH = 360;

        private final String title;
        private final String generatedBy;
        private PdfTemplate totalPages;
        private PdfPattern pattern = PdfPattern.MASQUES;
        private Color patternColor = BROWN;
        /** Tuile créée une fois par document puis réutilisée sur chaque page. */
        private PdfTemplate patternTile;

        Frame(String title, String generatedBy) {
            this.title = title;
            this.generatedBy = generatedBy;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            drawBackground(writer, document.getPageSize());
            PdfContentByte cb = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            float left = document.leftMargin();
            float right = page.getWidth() - document.rightMargin();
            float top = page.getHeight() - 22;

            // Logo
            try {
                Image logo = Image.getInstance(branding.logo() != null ? branding.logo() : LOGO);
                logo.scaleToFit(62, 54);
                logo.setAbsolutePosition(left, top - 54);
                cb.addImage(logo);
            } catch (IOException | DocumentException e) {
                throw new IllegalStateException("Logo PDF illisible", e);
            }

            // Nom de l'association
            float textX = left + 72;
            cb.beginText();
            cb.setFontAndSize(BOLD, 12.5f);
            cb.setColorFill(BROWN);
            cb.setTextMatrix(textX, top - 20);
            cb.showText(associationName().toUpperCase(java.util.Locale.FRANCE));
            cb.setFontAndSize(REGULAR, 9);
            cb.setColorFill(MUTED);
            cb.setTextMatrix(textX, top - 34);
            cb.showText(associationShortName() + " – Gestion de la tontine");
            cb.endText();

            // Titre du document, à droite
            cb.beginText();
            cb.setFontAndSize(BOLD, 9);
            cb.setColorFill(GRAY);
            cb.showTextAligned(Element.ALIGN_RIGHT, title, right, top - 48, 0);
            cb.endText();

            // Bandeau orange / marron
            cb.setColorFill(ORANGE);
            cb.rectangle(left, top - 62, (right - left) * 0.72f, 2.4f);
            cb.fill();
            cb.setColorFill(BROWN);
            cb.rectangle(left + (right - left) * 0.72f, top - 62, (right - left) * 0.28f, 2.4f);
            cb.fill();

            // Pied de page
            float bottom = 30;
            cb.setColorStroke(LINE);
            cb.setLineWidth(0.6f);
            cb.moveTo(left, bottom + 12);
            cb.lineTo(right, bottom + 12);
            cb.stroke();

            String generated = "Généré le " + DocumentFormat.dateTime(java.time.Instant.now())
                    + (generatedBy != null ? " par " + generatedBy : "") + " – AEKD-Tontine"
                    + (branding.contactLine() != null ? "   |   " + branding.contactLine() : "");
            cb.beginText();
            cb.setFontAndSize(REGULAR, 7.5f);
            cb.setColorFill(MUTED);
            cb.setTextMatrix(left, bottom);
            cb.showText(generated);
            String pageText = "Page " + writer.getPageNumber() + " / ";
            float pageWidth = REGULAR.getWidthPoint(pageText, 7.5f);
            cb.setTextMatrix(right - pageWidth - 14, bottom);
            cb.showText(pageText);
            cb.endText();
            cb.addTemplate(totalPages, right - 14, bottom);
        }

        /** Motif sous le contenu, entre le bandeau d'en-tête et le filet de pied de page. */
        private void drawBackground(PdfWriter writer, Rectangle page) {
            if (pattern == null) {
                return;
            }
            PdfContentByte under = writer.getDirectContentUnder();
            if (patternTile == null) {
                patternTile = pattern.tile(under, PATTERN_TILE_WIDTH, patternColor, PATTERN_OPACITY);
            }
            float tileHeight = pattern.tileHeight(PATTERN_TILE_WIDTH);
            float top = page.getHeight() - 86;
            float bottom = 44;

            under.saveState();
            under.rectangle(0, bottom, page.getWidth(), top - bottom);
            under.clip();
            under.newPath();
            for (float y = top; y > bottom - tileHeight; y -= tileHeight) {
                for (float x = 0; x < page.getWidth(); x += PATTERN_TILE_WIDTH) {
                    under.addTemplate(patternTile, x, y - tileHeight);
                }
            }
            under.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPages.beginText();
            totalPages.setFontAndSize(REGULAR, 7.5f);
            totalPages.setColorFill(MUTED);
            totalPages.setTextMatrix(0, 0);
            // À la fermeture, le compteur a déjà avancé d'une page.
            totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPages.endText();
        }
    }
}
