package cm.aekd.tontine.contribution;

import java.io.InputStream;

public record StoredResource(InputStream content, String contentType, String filename) {
}
