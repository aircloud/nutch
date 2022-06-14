/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.protocol.okhttp;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.net.protocols.Response;
import org.apache.nutch.protocol.ProtocolException;
import org.apache.nutch.protocol.http.api.HttpBase;
import org.apache.nutch.util.NutchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Authenticator;
import okhttp3.Connection;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.brotli.BrotliInterceptor;

public class OkHttp extends HttpBase {

  protected static final Logger LOG = LoggerFactory
      .getLogger(MethodHandles.lookup().lookupClass());

  private final List<String[]> customRequestHeaders = new LinkedList<>();

  private OkHttpClient client;

  private static final TrustManager[] trustAllCerts = new TrustManager[] {
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(
            java.security.cert.X509Certificate[] chain, String authType)
            throws CertificateException {
        }

        @Override
        public void checkServerTrusted(
            java.security.cert.X509Certificate[] chain, String authType)
            throws CertificateException {
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
          return new java.security.cert.X509Certificate[] {};
        }
      } };

  public OkHttp() {
    super(LOG);
  }

  @Override
  public void setConf(Configuration conf) {
    super.setConf(conf);

    // protocols in order of preference
    List<okhttp3.Protocol> protocols = new ArrayList<>();
    if (this.useHttp2) {
      protocols.add(okhttp3.Protocol.HTTP_2);
    }
    protocols.add(okhttp3.Protocol.HTTP_1_1);

    okhttp3.OkHttpClient.Builder builder = new OkHttpClient.Builder()
        .protocols(protocols) //
        .retryOnConnectionFailure(true) //
        .followRedirects(false) //
        .connectTimeout(this.timeout, TimeUnit.MILLISECONDS)
        .writeTimeout(this.timeout, TimeUnit.MILLISECONDS)
        .readTimeout(this.timeout, TimeUnit.MILLISECONDS);

    if (!this.tlsCheckCertificate) {
      try {
        SSLContext trustAllSslContext = SSLContext.getInstance("TLS");
        trustAllSslContext.init(null, trustAllCerts, null);
        SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext
            .getSocketFactory();
        builder.sslSocketFactory(trustAllSslSocketFactory,
            (X509TrustManager) trustAllCerts[0]);
      } catch (Exception e) {
        LOG.error(
            "Failed to disable TLS certificate verification (property http.tls.certificates.check)",
            e);
      }
      builder.hostnameVerifier(new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      });
    }

    if (!this.accept.isEmpty()) {
      getCustomRequestHeaders().add(new String[] { "Accept", this.accept });
    }

    if (!this.acceptLanguage.isEmpty()) {
      getCustomRequestHeaders()
          .add(new String[] { "Accept-Language", this.acceptLanguage });
    }

    if (!this.acceptCharset.isEmpty()) {
      getCustomRequestHeaders()
          .add(new String[] { "Accept-Charset", this.acceptCharset });
    }

    if (this.useProxy) {
      Proxy proxy = new Proxy(this.proxyType,
          new InetSocketAddress(this.proxyHost, this.proxyPort));
      String proxyUsername = conf.get("http.proxy.username");
      if (proxyUsername == null) {
        ProxySelector selector = new ProxySelector() {
          @SuppressWarnings("serial")
          private final List<Proxy> noProxyList = new ArrayList<Proxy>() {
            {
              add(Proxy.NO_PROXY);
            }
          };
          @SuppressWarnings("serial")
          private final List<Proxy> proxyList = new ArrayList<Proxy>() {
            {
              add(proxy);
            }
          };

          @Override
          public List<Proxy> select(URI uri) {
            if (useProxy(uri)) {
              return this.proxyList;
            }
            return this.noProxyList;
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa,
              IOException ioe) {
            LOG.error("Connection to proxy failed for {}: {}", uri, ioe);
          }
        };
        builder.proxySelector(selector);
      } else {
        /*
         * NOTE: the proxy exceptions list does NOT work with proxy
         * username/password because an okhttp3 bug
         * (https://github.com/square/okhttp/issues/3995) when using the
         * ProxySelector class with proxy auth. If a proxy username is present,
         * the configured proxy will be used for ALL requests.
         */
        if (this.proxyException.size() > 0) {
          LOG.warn(
              "protocol-okhttp does not respect 'http.proxy.exception.list' setting when "
                  + "'http.proxy.username' is set. This is a limitation of the current okhttp3 "
                  + "implementation, see NUTCH-2636");
        }
        builder.proxy(proxy);
        String proxyPassword = conf.get("http.proxy.password");
        Authenticator proxyAuthenticator = new Authenticator() {
          @Override
          public Request authenticate(okhttp3.Route route,
              okhttp3.Response response) throws IOException {
            String credential = okhttp3.Credentials.basic(proxyUsername,
                proxyPassword);
            return response.request().newBuilder()
                .header("Proxy-Authorization", credential).build();
          }
        };
        builder.proxyAuthenticator(proxyAuthenticator);
      }
    }

    if (this.storeIPAddress || this.storeHttpHeaders || this.storeHttpRequest) {
      builder.addNetworkInterceptor(new HTTPHeadersInterceptor());
    }

    // enable support for Brotli compression (Content-Encoding)
    builder.addInterceptor(BrotliInterceptor.INSTANCE);

    this.client = builder.build();
  }

  class HTTPHeadersInterceptor implements Interceptor {

    private String getNormalizedProtocolName(Protocol protocol) {
      String name = protocol.toString().toUpperCase(Locale.ROOT);
      if ("H2".equals(name)) {
        // back-ward compatible protocol version name
        name = "HTTP/2";
      }
      return name;
    }

    @Override
    public okhttp3.Response intercept(Interceptor.Chain chain)
        throws IOException {

      Connection connection = chain.connection();
      String ipAddress = null;
      if (OkHttp.this.storeIPAddress) {
        InetAddress address = connection.socket().getInetAddress();
        ipAddress = address.getHostAddress();
      }

      Request request = chain.request();
      okhttp3.Response response = chain.proceed(request);

      StringBuilder requestverbatim = null;
      StringBuilder responseverbatim = null;

      if (OkHttp.this.storeHttpRequest) {
        requestverbatim = new StringBuilder();

        requestverbatim.append(request.method()).append(' ');
        requestverbatim.append(request.url().encodedPath());
        String query = request.url().encodedQuery();
        if (query != null) {
          requestverbatim.append('?').append(query);
        }
        requestverbatim.append(' ')
            .append(getNormalizedProtocolName(connection.protocol()))
            .append("\r\n");

        Headers headers = request.headers();

        for (int i = 0, size = headers.size(); i < size; i++) {
          String key = headers.name(i);
          String value = headers.value(i);
          requestverbatim.append(key).append(": ").append(value)
              .append("\r\n");
        }

        requestverbatim.append("\r\n");
      }

      if (OkHttp.this.storeHttpHeaders) {
        responseverbatim = new StringBuilder();

        responseverbatim.append(getNormalizedProtocolName(response.protocol()))
            .append(' ').append(response.code()).append(' ')
            .append(response.message()).append("\r\n");

        Headers headers = response.headers();

        for (int i = 0, size = headers.size(); i < size; i++) {
          String key = headers.name(i);
          String value = headers.value(i);
          responseverbatim.append(key).append(": ").append(value)
              .append("\r\n");
        }

        responseverbatim.append("\r\n");
      }

      okhttp3.Response.Builder builder = response.newBuilder();

      if (ipAddress != null) {
        builder = builder.header(Response.IP_ADDRESS, ipAddress);
      }

      if (requestverbatim != null) {
        byte[] encodedBytesRequest = Base64.getEncoder()
            .encode(requestverbatim.toString().getBytes());
        builder = builder.header(Response.REQUEST,
            new String(encodedBytesRequest));
      }

      if (responseverbatim != null) {
        byte[] encodedBytesResponse = Base64.getEncoder()
            .encode(responseverbatim.toString().getBytes());
        builder = builder.header(Response.RESPONSE_HEADERS,
            new String(encodedBytesResponse));
      }

      // returns a modified version of the response
      return builder.build();
    }
  }

  protected List<String[]> getCustomRequestHeaders() {
    return this.customRequestHeaders;
  }

  protected OkHttpClient getClient() {
    return this.client;
  }

  @Override
  protected Response getResponse(URL url, CrawlDatum datum, boolean redirect)
      throws ProtocolException, IOException {
    return new OkHttpResponse(this, url, datum);
  }

  public static void main(String[] args) throws Exception {
    OkHttp okhttp = new OkHttp();
    okhttp.setConf(NutchConfiguration.create());
    main(okhttp, args);
  }

}
