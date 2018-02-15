/**
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
package org.apache.nutch.metadata;

import org.apache.hadoop.io.Text;

/**
 * A collection of Nutch internal metadata constants.
 * 
 * @author Chris Mattmann
 * @author J&eacute;r&ocirc;me Charron
 */
public interface Nutch {

  public static final String ORIGINAL_CHAR_ENCODING = "OriginalCharEncoding";

  public static final String CHAR_ENCODING_FOR_CONVERSION = "CharEncodingForConversion";

  public static final String SIGNATURE_KEY = "nutch.content.digest";

  public static final String SEGMENT_NAME_KEY = "nutch.segment.name";

  public static final String SCORE_KEY = "nutch.crawl.score";

  public static final String GENERATE_TIME_KEY = "_ngt_";

  public static final Text WRITABLE_GENERATE_TIME_KEY = new Text(GENERATE_TIME_KEY);

  public static final Text PROTOCOL_STATUS_CODE_KEY = new Text("nutch.protocol.code");

  public static final String PROTO_STATUS_KEY = "_pst_";

  public static final Text WRITABLE_PROTO_STATUS_KEY = new Text(PROTO_STATUS_KEY);
  
  public static final String FETCH_TIME_KEY = "_ftk_";
  
  public static final String FETCH_STATUS_KEY = "_fst_";

  /** Sites may request that search engines don't provide access to cached documents. */
  public static final String CACHING_FORBIDDEN_KEY = "caching.forbidden";

  /** Show both original forbidden content and summaries (default). */
  public static final String CACHING_FORBIDDEN_NONE = "none";

  /** Don't show either original forbidden content or summaries. */
  public static final String CACHING_FORBIDDEN_ALL = "all";

  /** Don't show original forbidden content, but show summaries. */
  public static final String CACHING_FORBIDDEN_CONTENT = "content";

  public static final String REPR_URL_KEY = "_repr_";

  public static final Text WRITABLE_REPR_URL_KEY = new Text(REPR_URL_KEY);

  /** Used by AdaptiveFetchSchedule to maintain custom fetch interval */
  public static final String FIXED_INTERVAL_KEY = "fixedInterval";

  public static final Text WRITABLE_FIXED_INTERVAL_KEY = new Text(FIXED_INTERVAL_KEY);

  // time in ms it took us to fetch a page
  public static final String FETCH_DURATION_KEY = "cc.fetch.duration";

  public static final Text WRITABLE_FETCH_DURATION_KEY = new Text(FETCH_DURATION_KEY);

  // robots.txt crawl delay if it exists
  public static final String CRAWL_DELAY_KEY = "cc.crawl.delay";
  public static final Text WRITABLE_CRAWL_DELAY_KEY = new Text(CRAWL_DELAY_KEY);

  // IP address we fetched from
  public static final String FETCH_DEST_IP_KEY = "cc.dest.ip";

  // Verbatim request we fetched
  public static final String FETCH_REQUEST_VERBATIM_KEY = "cc.request.verbatim";

  // Verbatim headers we fetched
  public static final String FETCH_RESPONSE_VERBATIM_HEADERS_KEY = "cc.response.headers.verbatim";

  // Verbatim status line we fetched
  public static final String FETCH_RESPONSE_VERBATIM_STATUS_KEY = "cc.response.status.verbatim";

  // Raw status code we fetched
  public static final String FETCH_RESPONSE_STATUS_CODE_KEY = "cc.response.status.code";

  // Response truncated?
  public static final String FETCH_RESPONSE_TRUNCATED_KEY = "cc.response.truncated";
}
