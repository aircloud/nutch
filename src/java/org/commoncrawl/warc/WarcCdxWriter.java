package org.commoncrawl.warc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.io.output.CountingOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.StringUtils;
import org.apache.nutch.metadata.Metadata;
import org.archive.url.WaybackURLKeyMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class WarcCdxWriter extends WarcWriter {

  public static Logger LOG = LoggerFactory.getLogger(WarcCdxWriter.class);

  private static final Charset UTF_8 = StandardCharsets.UTF_8;

  protected CountingOutputStream countingOut;
  protected OutputStream cdxOut;
  protected String warcFilename;

  private SimpleDateFormat timestampFormat;
  private ObjectWriter jsonWriter;
  private WaybackURLKeyMaker surtKeyMaker = new WaybackURLKeyMaker(true);

  /**
   * JSON indentation same as by Python WayBack
   * (https://github.com/ikreymer/pywb)
   */
  @SuppressWarnings("serial")
  public static class JsonIndenter extends MinimalPrettyPrinter {

    // @Override
    public void writeObjectFieldValueSeparator(JsonGenerator jg)
        throws IOException, JsonGenerationException {
      jg.writeRaw(": ");
    }

    // @Override
    public void writeObjectEntrySeparator(JsonGenerator jg)
        throws IOException, JsonGenerationException {
      jg.writeRaw(", ");
    }
  }

  public WarcCdxWriter(OutputStream warcOut, OutputStream cdxOut, Path warcFilePath) {
    super(new CountingOutputStream(warcOut));
    countingOut = (CountingOutputStream) this.out;
    this.cdxOut = cdxOut;
    timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    warcFilename = warcFilePath.toUri().getPath().replaceFirst("^/", "");
    ObjectMapper jsonMapper = new ObjectMapper();
    jsonMapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
    jsonWriter = jsonMapper.writer(new JsonIndenter());
  }

  public URI writeWarcResponseRecord(final URI targetUri, final String ip,
      final Date date, final URI warcinfoId, final URI relatedId,
      final String payloadDigest, final String blockDigest,
      final String truncated, final byte[] content, Metadata meta)
      throws IOException {
    long offset = countingOut.getByteCount();
    URI recordId = super.writeWarcResponseRecord(targetUri, ip, date, warcinfoId,
        relatedId, payloadDigest, blockDigest, truncated, content, meta);
    long length = (countingOut.getByteCount() - offset);
    writeCdxLine(targetUri, date, offset, length, payloadDigest, meta);
    return recordId;
  }

  public void writeCdxLine(final URI targetUri, final Date date, long offset,
      long length, String payloadDigest, Metadata meta) throws IOException {
    String url = targetUri.toString();
    String surt = url;
    try {
      surt = surtKeyMaker.makeKey(url);
    } catch (URISyntaxException e) {
      LOG.error("Failed to make SURT for {}: {}", url,
          StringUtils.stringifyException(e));
      return;
    }
    if (payloadDigest.startsWith("sha1:"))
      payloadDigest = payloadDigest.substring(5);
    cdxOut.write(surt.getBytes(UTF_8));
    cdxOut.write(' ');
    cdxOut.write(timestampFormat.format(date).getBytes(UTF_8));
    cdxOut.write(' ');
    Map<String, String> data = new LinkedHashMap<String, String>();
    data.put("url", url);
    data.put("mime", cleanMimeType(meta.get("Content-Type")));
    data.put("status", meta.get("HTTP-Status-Code"));
    data.put("digest", payloadDigest);
    data.put("length", String.format("%d", length));
    data.put("offset", String.format("%d", offset));
    data.put("filename", warcFilename);
    cdxOut.write(jsonWriter.writeValueAsBytes(data));
    cdxOut.write('\n');
  }

  protected static String cleanMimeType(String mime) {
    if (mime == null)
      return "unk";
    final char[] delimiters = {';', ' '};
    for (char delim : delimiters) {
      int pos = mime.indexOf(delim);
      if (pos > -1)
        mime = mime.substring(0, pos);
    }
    if (mime.isEmpty())
      return "unk";
    return mime;
  }

}
