package org.commoncrawl.warc;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.apache.nutch.metadata.Metadata;

public class WarcWriter {
  protected OutputStream out = null;
  protected OutputStream origOut = null;

  private final String WARC_VERSION = "WARC/1.0";

  // Record types
  private final String WARC_INFO = "warcinfo";
  private final String WARC_RESPONSE = "response";
  private final String WARC_REQUEST = "request";
  private final String WARC_REVISIT = "revisit";
  private final String WARC_CONVERSION = "conversion";
  private final String WARC_METADATA = "metadata";

  // Defined fields
  private final String WARC_TYPE = "WARC-Type";
  private final String WARC_DATE = "WARC-Date";
  private final String WARC_RECORD_ID = "WARC-Record-ID";
  private final String CONTENT_LENGTH = "Content-Length";
  private final String CONTENT_TYPE = "Content-Type";
  private final String WARC_IP_ADDRESS = "WARC-IP-Address";
  private final String WARC_WARCINFO_ID = "WARC-Warcinfo-ID";
  private final String WARC_TARGET_URI = "WARC-Target-URI";
  private final String WARC_CONCURRENT_TO = "WARC-Concurrent-To";
  private final String WARC_REFERS_TO = "WARC-Refers-To";
  private final String WARC_BLOCK_DIGEST = "WARC-Block-Digest";
  private final String WARC_PAYLOAD_DIGEST = "WARC-Payload-Digest";
  private final String WARC_TRUNCATED = "WARC-Truncated";
  private final String WARC_IDENTIFIED_PAYLOAD_TYPE = "WARC-Identified-Payload-Type";
  private final String WARC_PROFILE = "WARC-Profile";
  private final String WARC_FILENAME = "WARC-Filename";

  public static final String PROFILE_REVISIT_IDENTICAL_DIGEST =
      "http://netpreserve.org/warc/1.0/revisit/identical-payload-digest";
  public static final String PROFILE_REVISIT_NOT_MODIFIED =
      "http://netpreserve.org/warc/1.0/revisit/server-not-modified";


  private final String CRLF = "\r\n";
  private final String COLONSP = ": ";

  private SimpleDateFormat isoDate;


  public static class CompressedOutputStream extends GZIPOutputStream {
    public CompressedOutputStream(OutputStream out) throws IOException {
      super(out);
    }

    public void end() {
      def.end();
    }
  }

  public WarcWriter(final OutputStream out) {
    this.origOut = this.out = out;
    isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    isoDate.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  /**
   *
   * @return record id for the warcinfo record
   * @throws IOException
   */
  public URI writeWarcinfoRecord(String filename, String hostname, String publisher, String operator, String software,
                                 String isPartOf, String description) throws IOException  {
    Map<String, String> extra = new LinkedHashMap<String, String>();
    extra.put(WARC_FILENAME, filename);

    StringBuilder sb = new StringBuilder(1024);
    Map<String, String> settings = new LinkedHashMap<String, String>();

    settings.put("robots", "classic");
    if (hostname != null) {
      settings.put("hostname", hostname);
    }

    if (software != null) {
      settings.put("software", software);
    }

    if (isPartOf != null) {
      settings.put("isPartOf", isPartOf);
    }

    if (operator != null) {
      settings.put("operator", operator);
    }

    if (description != null) {
      settings.put("description", description);
    }

    if (publisher != null) {
      settings.put("publisher", publisher);
    }

    settings.put("format", "WARC File Format 1.0");
    settings.put("conformsTo", "http://bibnum.bnf.fr/WARC/WARC_ISO_28500_version1_latestdraft.pdf");

    writeWarcKeyValue(sb, settings);

    byte[] ba = sb.toString().getBytes("utf-8");
    URI recordId = getRecordId();

    writeRecord(WARC_INFO, new Date(), "application/warc-fields", recordId, extra,
        new ByteArrayInputStream(ba), ba.length);
    return recordId;
  }

  public URI writeWarcRequestRecord(final URI targetUri, final String ip, final Date date,
                                    final URI warcinfoId, final byte[] content) throws IOException {
    Map<String, String> extra = new LinkedHashMap<String, String>();
    extra.put(WARC_WARCINFO_ID, "<" + warcinfoId.toString() + ">");
    extra.put(WARC_IP_ADDRESS, ip);
    extra.put(WARC_TARGET_URI, targetUri.toString());

    URI recordId = getRecordId();
    writeRecord(WARC_REQUEST, date, "application/http; msgtype=request", recordId, extra, content);
    return recordId;
  }

  public URI writeWarcResponseRecord(final URI targetUri, final String ip, final Date date,
                                     final URI warcinfoId, final URI relatedId, final String payloadDigest,
                                     final String blockDigest, final String truncated, final byte[] content,
                                     Metadata meta)
      throws IOException {
    Map<String, String> extra = new LinkedHashMap<String, String>();
    extra.put(WARC_WARCINFO_ID, "<" + warcinfoId.toString() + ">");
    extra.put(WARC_CONCURRENT_TO, "<" + relatedId.toString() + ">");
    extra.put(WARC_IP_ADDRESS, ip);
    extra.put(WARC_TARGET_URI, targetUri.toString());

    if (payloadDigest != null) {
      extra.put(WARC_PAYLOAD_DIGEST, payloadDigest);
    }

    if (blockDigest != null) {
      extra.put(WARC_BLOCK_DIGEST, blockDigest);
    }

    if (truncated != null) {
      extra.put(WARC_TRUNCATED, truncated);
    }

    URI recordId = getRecordId();
    writeRecord(WARC_RESPONSE, date, "application/http; msgtype=response", recordId, extra, content);
    return recordId;
  }

  public URI writeWarcRevisitRecord(final URI targetUri, final String ip, final Date date,
                                     final URI warcinfoId, final URI relatedId, final String warcProfile,
                                     final String payloadDigest,
                                     final InputStream content, final long contentLength) throws IOException {
    Map<String, String> extra = new LinkedHashMap<String, String>();
    extra.put(WARC_WARCINFO_ID, "<" + warcinfoId.toString() + ">");
    extra.put(WARC_REFERS_TO, "<" + relatedId.toString() + ">");
    extra.put(WARC_IP_ADDRESS, ip);
    extra.put(WARC_TARGET_URI, targetUri.toString());
    extra.put(WARC_PROFILE, warcProfile);

    if (payloadDigest != null) {
      extra.put(WARC_PAYLOAD_DIGEST, payloadDigest);
    }

    URI recordId = getRecordId();
    writeRecord(WARC_RESPONSE, date, "application/http; msgtype=response", recordId, extra, content, contentLength);
    return recordId;
  }

  public URI writeWarcMetadataRecord(final URI targetUri, final Date date,
                                     final URI warcinfoId, final URI relatedId,
                                     final String blockDigest,
                                     final byte[] content) throws IOException {
    Map<String, String> extra = new LinkedHashMap<String, String>();
    extra.put(WARC_WARCINFO_ID, "<" + warcinfoId.toString() + ">");
    extra.put(WARC_CONCURRENT_TO, "<" + relatedId.toString() + ">");
    extra.put(WARC_TARGET_URI, targetUri.toString());

    if (blockDigest != null) {
      extra.put(WARC_BLOCK_DIGEST, blockDigest);
    }

    URI recordId = getRecordId();
    writeRecord(WARC_METADATA, date, "application/warc-fields", recordId, extra, content);
    return recordId;
  }

  public URI writeWarcConversionRecord(final URI targetUri, final Date date,
                                       final URI warcinfoId, final URI relatedId,
                                       final String blockDigest, final String contentType,
                                       final byte[] content) throws IOException {
    Map<String, String> extra = new LinkedHashMap<String, String>();
    extra.put(WARC_WARCINFO_ID, "<" + warcinfoId.toString() + ">");
    extra.put(WARC_REFERS_TO, "<" + relatedId.toString() + ">");
    extra.put(WARC_TARGET_URI, targetUri.toString());

    if (blockDigest != null) {
      extra.put(WARC_BLOCK_DIGEST, blockDigest);
    }

    URI recordId = getRecordId();
    writeRecord(WARC_CONVERSION, date, contentType, recordId, extra, content);
    return recordId;
  }


  protected void writeRecord(final String type, final Date date, final String contentType,
                             final URI recordId, Map<String, String> extra,
                             final InputStream content, final long contentLength) throws IOException {
    StringBuilder sb = new StringBuilder(4096);

    sb.append(WARC_VERSION).append(CRLF);

    Map<String, String> header = new LinkedHashMap<String, String>();
    header.put(WARC_TYPE, type);
    header.put(WARC_DATE, isoDate.format(date));
    header.put(WARC_RECORD_ID, "<" + recordId.toString() + ">");
    header.put(CONTENT_LENGTH, Long.toString(contentLength));
    header.put(CONTENT_TYPE, contentType);

    writeWarcKeyValue(sb, header);
    writeWarcKeyValue(sb, extra);

    sb.append(CRLF);

    startRecord();
    out.write(sb.toString().getBytes("UTF-8"));
    if (contentLength != 0 && content != null) {
      copyStream(content, out, contentLength);
    }

    out.write(CRLF.getBytes());
    out.write(CRLF.getBytes());
    endRecord();
  }


  protected void writeRecord(final String type, final Date date, final String contentType,
                             final URI recordId, Map<String, String> extra,
                             final byte[] content) throws IOException {
    StringBuilder sb = new StringBuilder(4096);

    sb.append(WARC_VERSION).append(CRLF);

    Map<String, String> header = new LinkedHashMap<String, String>();
    header.put(WARC_TYPE, type);
    header.put(WARC_DATE, isoDate.format(date));
    header.put(WARC_RECORD_ID, "<" + recordId.toString() + ">");
    header.put(CONTENT_LENGTH, Long.toString(content.length));
    header.put(CONTENT_TYPE, contentType);

    writeWarcKeyValue(sb, header);
    writeWarcKeyValue(sb, extra);

    sb.append(CRLF);

    startRecord();
    out.write(sb.toString().getBytes("UTF-8"));
    if (content != null && content.length != 0) {
      out.write(content);
    }

    out.write(CRLF.getBytes());
    out.write(CRLF.getBytes());
    endRecord();
  }

  protected void startRecord() throws IOException {
    this.out = new CompressedOutputStream(this.origOut);
  }

  protected void endRecord() throws IOException {
    CompressedOutputStream compressedOut = (CompressedOutputStream)this.out;
    compressedOut.finish();
    compressedOut.flush();
    compressedOut.end();

    this.out = this.origOut;
  }

  protected long copyStream(InputStream input, OutputStream output, long maxBytes) throws IOException {
    byte[] buffer = new byte[4096];
    long count = 0L;
    int n = 0;
    while (-1 != (n = input.read(buffer))) {
      if (maxBytes > 0 && maxBytes < n) {
        n = (int)maxBytes;
      }

      output.write(buffer, 0, n);
      count += n;
      maxBytes -= n;

      if (maxBytes == 0) {
        return count;
      }
    }
    return count;
  }

  protected void writeWarcKeyValue(StringBuilder sb, Map<String, String> headers) {
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        sb.append(entry.getKey()).append(COLONSP).append(entry.getValue()).append(CRLF);
      }
    }
  }

  private String getUUID() {
    return UUID.randomUUID().toString();
  }

  public URI getRecordId() {
    try {
      return new URI("urn:uuid:" + getUUID());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
