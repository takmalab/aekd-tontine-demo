package cm.aekd.tontine.document;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Export Excel (.xlsx) d'une liste telle qu'affichée à l'écran (docs/decisions.md n° 62),
 * pendant du PDF de {@link ListExportDocumentService} : mêmes lignes, déjà filtrées par le
 * frontend. Les montants (« 2 000 FCFA ») deviennent de vrais nombres, utilisables dans
 * des calculs ; le reste est du texte.
 */
@Service
public class ListExcelExportService {

    /** Entier éventuellement signé, séparateurs d'espaces, suffixe « FCFA » facultatif. */
    private static final Pattern AMOUNT = Pattern.compile("^(-?\\d[\\d\\s\\u00A0\\u202F]*?)\\s*(FCFA)?$");
    private static final byte[] AEKD_ORANGE = {(byte) 0xE0, (byte) 0x7A, (byte) 0x1F};
    private static final int MAX_COLUMN_CHARS = 60;

    private final DocumentAccess access;

    public ListExcelExportService(DocumentAccess access) {
        this.access = access;
    }

    public GeneratedDocument export(ListExportRequest request) {
        List<String> columns = request.columns();
        List<List<String>> rows = request.rows() == null ? List.of() : request.rows();
        String title = request.title().trim();

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName(title));
            Styles styles = new Styles(workbook);
            int[] widths = new int[columns.size()];

            Row titleRow = sheet.createRow(0);
            text(titleRow, 0, "AEKD – " + title, styles.title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(0, columns.size() - 1)));

            String filters = request.filters() == null || request.filters().isBlank() ? null : request.filters().trim();
            String author = access.currentAuthorName();
            String subtitle = rows.size() + " élément" + (rows.size() > 1 ? "s" : "")
                    + (filters != null ? " – " + filters : "")
                    + (author != null ? " – exporté par " + author : "");
            text(sheet.createRow(1), 0, subtitle, styles.subtitle);

            int headerIndex = 3;
            Row header = sheet.createRow(headerIndex);
            for (int c = 0; c < columns.size(); c++) {
                text(header, c, columns.get(c), styles.header);
                widths[c] = columns.get(c).length();
            }

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(headerIndex + 1 + r);
                List<String> values = rows.get(r);
                for (int c = 0; c < columns.size(); c++) {
                    String value = c < values.size() ? values.get(c) : null;
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    Matcher amount = AMOUNT.matcher(value.trim());
                    if (amount.matches()) {
                        Cell cell = row.createCell(c);
                        cell.setCellValue(Double.parseDouble(amount.group(1).replaceAll("[\\s\\u00A0\\u202F]", "")));
                        cell.setCellStyle(amount.group(2) != null ? styles.fcfa : styles.number);
                    } else {
                        text(row, c, value, styles.body);
                    }
                    widths[c] = Math.max(widths[c], value.length());
                }
            }

            for (int c = 0; c < columns.size(); c++) {
                sheet.setColumnWidth(c, (Math.min(widths[c], MAX_COLUMN_CHARS) + 3) * 256);
            }
            sheet.createFreezePane(0, headerIndex + 1);
            if (!rows.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(headerIndex, headerIndex + rows.size(), 0, columns.size() - 1));
            }

            workbook.write(out);
            return new GeneratedDocument(DocumentFormat.slug(title) + ".xlsx", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** Excel limite le nom d'un onglet à 31 caractères, sans : \ / ? * [ ]. */
    private static String sheetName(String title) {
        String name = title.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        name = name.length() > 31 ? name.substring(0, 31).trim() : name;
        return name.isEmpty() ? "Export" : name;
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle header;
        final CellStyle body;
        final CellStyle number;
        final CellStyle fcfa;

        Styles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title = workbook.createCellStyle();
            title.setFont(titleFont);

            Font mutedFont = workbook.createFont();
            mutedFont.setItalic(true);
            mutedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subtitle = workbook.createCellStyle();
            subtitle.setFont(mutedFont);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(AEKD_ORANGE, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bordered(headerStyle);
            header = headerStyle;

            body = workbook.createCellStyle();
            bordered(body);
            body.setWrapText(false);

            short integerFormat = workbook.createDataFormat().getFormat("#,##0");
            number = workbook.createCellStyle();
            number.setDataFormat(integerFormat);
            number.setAlignment(HorizontalAlignment.RIGHT);
            bordered(number);

            fcfa = workbook.createCellStyle();
            fcfa.setDataFormat(workbook.createDataFormat().getFormat("#,##0 \"FCFA\""));
            fcfa.setAlignment(HorizontalAlignment.RIGHT);
            bordered(fcfa);
        }

        private static void bordered(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}
