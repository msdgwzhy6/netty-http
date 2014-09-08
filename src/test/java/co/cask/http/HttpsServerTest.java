/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.http;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Service;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * Test the HttpsServer.
 */
public class HttpsServerTest extends HttpServerTest {

  private static SSLClientContext sslClientContext;

  @BeforeClass
  public static void setup() throws Exception {
    List<HttpHandler> handlers = Lists.newArrayList();
    handlers.add(new TestHandler());

    NettyHttpService.Builder builder = NettyHttpService.builder();
    builder.addHttpHandlers(handlers);
    builder.setHttpChunkLimit(75 * 1024);

    File keyStore = tmpFolder.newFile();
    ByteStreams.copy(Resources.newInputStreamSupplier(Resources.getResource("cert.jks")),
                     Files.newOutputStreamSupplier(keyStore));

    /* IMPORTANT
     * Provide Certificate Configuration Here * *
     * enableSSL(<CertificatePath>,<KeyStorePassword>,<CertificatePassword>)
     * KeyStorePath : Path of SSL certificate
     * KeyStorePassword : Key Store Password
     * CertificatePassword : Certificate password if different from Key Store password or null
    */
    builder.enableSSL(keyStore, "secret", "secret");

    builder.modifyChannelPipeline(new Function<ChannelPipeline, ChannelPipeline>() {
      @Override
      public ChannelPipeline apply(ChannelPipeline channelPipeline) {
        channelPipeline.addAfter("decoder", "testhandler", new TestChannelHandler());
        return channelPipeline;
      }
    });

    sslClientContext = new SSLClientContext();
    service = builder.build();
    service.startAndWait();
    Service.State state = service.state();
    Assert.assertEquals(Service.State.RUNNING, state);

    int port = service.getBindAddress().getPort();
    baseURI = URI.create(String.format("https://localhost:%d", port));
  }
  
  @Override
  protected HttpURLConnection request(String path, HttpMethod method, boolean keepAlive) throws IOException {
    URL url = baseURI.resolve(path).toURL();
    HttpsURLConnection.setDefaultSSLSocketFactory(sslClientContext.getClientContext().getSocketFactory());
    HostnameVerifier allHostsValid = new HostnameVerifier() {
      public boolean verify(String hostname, SSLSession session) {
        return true;
      }
    };

    // Install the all-trusting host verifier
    HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    HttpsURLConnection urlConn = (HttpsURLConnection) url.openConnection();
    if (method == HttpMethod.POST || method == HttpMethod.PUT) {
      urlConn.setDoOutput(true);
    }
    urlConn.setRequestMethod(method.getName());
    if (!keepAlive) {
      urlConn.setRequestProperty(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
    }
    return urlConn;
  }
}
