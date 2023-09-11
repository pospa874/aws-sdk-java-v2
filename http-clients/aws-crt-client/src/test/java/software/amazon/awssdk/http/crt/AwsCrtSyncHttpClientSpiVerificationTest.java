/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.http.crt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.emptyMap;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static software.amazon.awssdk.http.HttpTestUtils.createProvider;
import static software.amazon.awssdk.http.crt.CrtHttpClientTestUtils.createRequest;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.crt.CrtResource;
import software.amazon.awssdk.crt.http.HttpException;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.RecordingResponseHandler;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.utils.Logger;

public class AwsCrtSyncHttpClientSpiVerificationTest {
    private static final Logger log = Logger.loggerFor(AwsCrtSyncHttpClientSpiVerificationTest.class);
    private static final int TEST_BODY_LEN = 1024;

    @Rule
    public WireMockRule mockServer = new WireMockRule(wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort());

    private static SdkHttpClient client;

    @BeforeClass
    public static void setup() throws Exception {
        client = AwsCrtSyncHttpClient.builder()
                                      .connectionHealthConfiguration(b -> b.minimumThroughputInBps(4068L)
                                                                           .minimumThroughputTimeout(Duration.ofSeconds(3)))
                                      .build();
    }

    @AfterClass
    public static void tearDown() {
        client.close();
        CrtResource.waitForNoResources();
    }

    private byte[] generateRandomBody(int size) {
        byte[] randomData = new byte[size];
        new Random().nextBytes(randomData);
        return randomData;
    }


    @Test(expected = IOException.class)
    public void requestFailed_connectionTimeout_shouldWrapException() throws IOException {
        try (SdkHttpClient client = AwsCrtSyncHttpClient.builder().connectionTimeout(Duration.ofNanos(1)).build()) {
            URI uri = URI.create("http://localhost:" + mockServer.port());
            stubFor(any(urlPathEqualTo("/")).willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));
            SdkHttpRequest request = createRequest(uri);
            HttpExecuteRequest.Builder executeRequestBuilder = HttpExecuteRequest.builder();
            executeRequestBuilder.request(request);
            ExecutableHttpRequest executableRequest = client.prepareRequest(executeRequestBuilder.build());

            HttpExecuteResponse response = executableRequest.call();
        }
    }


    @Test(expected = HttpException.class)
    public void requestFailed_notRetryable_shouldNotWrapException() throws IOException {
        try (SdkHttpClient client = AwsCrtSyncHttpClient.builder().build()) {
            URI uri = URI.create("http://localhost:" + mockServer.port());
            // make it invalid by doing a non-zero content length with no request body...
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("host", Collections.singletonList(uri.getHost()));

            List<String> contentLengthValues = new LinkedList<>();
            contentLengthValues.add("1");
            headers.put("content-length", contentLengthValues);

            SdkHttpRequest request = createRequest(uri).toBuilder().headers(headers).build();

            HttpExecuteRequest.Builder executeRequestBuilder = HttpExecuteRequest.builder();
            executeRequestBuilder.request(request);
            executeRequestBuilder.contentStreamProvider(() -> new ByteArrayInputStream(new byte[0]));
            ExecutableHttpRequest executableRequest = client.prepareRequest(executeRequestBuilder.build());
            HttpExecuteResponse response = executableRequest.call();
        }
    }

    @Test
    public void callsOnStreamForEmptyResponseContent() throws Exception {
        stubFor(any(urlEqualTo("/")).willReturn(aResponse().withStatus(204).withHeader("foo", "bar")));

        SdkHttpRequest request = CrtHttpClientTestUtils.createRequest(URI.create("http://localhost:" + mockServer.port()));
        HttpExecuteRequest.Builder executeRequestBuilder = HttpExecuteRequest.builder();
        executeRequestBuilder.request(request);
        executeRequestBuilder.contentStreamProvider(() -> new ByteArrayInputStream(new byte[0]));
        ExecutableHttpRequest executableRequest = client.prepareRequest(executeRequestBuilder.build());
        HttpExecuteResponse response = executableRequest.call();

        assertThat(response.responseBody().isPresent()).isTrue();
        assertThat(response.responseBody().get().available() == 0).isTrue();
        assertThat(response.httpResponse() != null).isTrue();
        assertThat(response.httpResponse().statusCode() == 204).isTrue();
        assertThat(response.httpResponse().headers().get("foo").isEmpty()).isFalse();
    }

    @Test
    public void testGetRequest() throws Exception {
        String path = "/testGetRequest";
        byte[] body = generateRandomBody(TEST_BODY_LEN);
        stubFor(any(urlEqualTo(path)).willReturn(aResponse().withStatus(200)
                                                           .withHeader("Content-Length", Integer.toString(TEST_BODY_LEN))
                                                           .withHeader("foo", "bar")
                                                           .withBody(body)));

        URI uri = URI.create("http://localhost:" + mockServer.port());
        SdkHttpRequest request = CrtHttpClientTestUtils.createRequest(uri, path, null, SdkHttpMethod.GET, emptyMap());

        HttpExecuteRequest.Builder executeRequestBuilder = HttpExecuteRequest.builder();
        executeRequestBuilder.request(request);
        ExecutableHttpRequest executableRequest = client.prepareRequest(executeRequestBuilder.build());
        HttpExecuteResponse response = executableRequest.call();

        assertThat(response.responseBody().isPresent()).isTrue();
        assertThat(response.responseBody().get().available()).isEqualTo(TEST_BODY_LEN);

        byte[] readBody = new byte[TEST_BODY_LEN];
        assertThat(response.responseBody().get().read(readBody)).isEqualTo(TEST_BODY_LEN);
        assertThat(readBody).isEqualTo(body);
        assertThat(response.httpResponse().statusCode()).isEqualTo(200);
        assertThat(response.httpResponse().headers().get("foo").isEmpty()).isFalse();
    }


    private void makePutRequest(String path, byte[] reqBody, int expectedStatus) throws Exception {
        URI uri = URI.create("http://localhost:" + mockServer.port());
        SdkHttpRequest request = CrtHttpClientTestUtils.createRequest(uri, path, reqBody, SdkHttpMethod.PUT, emptyMap());
        HttpExecuteRequest.Builder executeRequestBuilder = HttpExecuteRequest.builder();
        executeRequestBuilder.request(request);
        executeRequestBuilder.contentStreamProvider(() -> new ByteArrayInputStream(reqBody));
        ExecutableHttpRequest executableRequest = client.prepareRequest(executeRequestBuilder.build());
        HttpExecuteResponse response = executableRequest.call();

        assertThat(response.responseBody().isPresent()).isTrue();
        assertThat(response.httpResponse().statusCode()).isEqualTo(expectedStatus);
    }

    @Test
    public void testPutRequest() throws Exception {
        String pathExpect200 = "/testPutRequest/return_200_on_exact_match";
        byte[] expectedBody = generateRandomBody(TEST_BODY_LEN);
        stubFor(any(urlEqualTo(pathExpect200)).withRequestBody(binaryEqualTo(expectedBody)).willReturn(aResponse().withStatus(200)));
        makePutRequest(pathExpect200, expectedBody, 200);

        String pathExpect404 = "/testPutRequest/return_404_always";
        byte[] randomBody = generateRandomBody(TEST_BODY_LEN);
        stubFor(any(urlEqualTo(pathExpect404)).willReturn(aResponse().withStatus(404)));
        makePutRequest(pathExpect404, randomBody, 404);
    }
}