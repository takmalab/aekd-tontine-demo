package cm.aekd.tontine.document;

/** Un PDF généré, prêt à être téléchargé. {@code filename} se termine par « .pdf ». */
public record GeneratedDocument(String filename, byte[] content) {
}
