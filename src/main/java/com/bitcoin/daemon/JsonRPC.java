package com.bitcoin.daemon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Cleanup;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Value
class JsonRpcRequest {
    String method;
    List<Object> params;
}

@CommonsLog
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JsonRPC implements Closeable {
    static JsonFactory jsonFactory = new JsonFactory();
    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response<ResultType> {
        @Data @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ErrorStatus {
            String message;
            int code;
        }
        ResultType result;
        ErrorStatus error;
        String id;
    }
    String rpcServerUrl;
    PoolingHttpClientConnectionManager connectionManager;
    HttpClient client;

    Thread monitorThread;

    public JsonRPC(String rpcHost, int rpcPort, String rpcUsername, String rpcPassword) {
        this.rpcServerUrl = String.format("http://%s:%d", rpcHost, rpcPort);

        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(20);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(rpcHost, rpcPort),
                new UsernamePasswordCredentials(rpcUsername, rpcPassword));

        final int timeout = 2 * 1000;
        RequestConfig.Builder requestBuilder = RequestConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout);

        HttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler(5, true);

        client = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestBuilder.build())
                .setDefaultCredentialsProvider(credentialsProvider)
                .setRetryHandler(retryHandler)
                .build();

        monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(TimeUnit.MINUTES.toMillis(30));
                    } catch (InterruptedException e) {
                        break;
                    }
                    log.info("Closing expired and idle connections...");
                    connectionManager.closeExpiredConnections();
                    connectionManager.closeIdleConnections(10, TimeUnit.MINUTES);
                }
            }
        });
        monitorThread.start();
    }
    private HttpResponse postRequest(String url, String json) throws IOException {
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(json));
        request.addHeader("Content-Type", "application/json");
        return client.execute(request);
    }
    private static String getResponseString(HttpResponse response) throws IOException {
        try(BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        } finally {
            EntityUtils.consume(response.getEntity());
        }
    }

    private static String prepareJsonRequest(String method, List<Object> args) throws IOException {
        ByteArrayOutputStream writer = new ByteArrayOutputStream();
        JsonGenerator generator = jsonFactory.createGenerator(writer);
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(generator, new JsonRpcRequest(method, args));
        return writer.toString();
    }

    public <T> T executeRpcRequest(String method, List<Object> args, TypeReference<Response<T>> typeReference) throws Exception {
        try {
            String request = prepareJsonRequest(method, args);
            String response = getResponseString(postRequest(rpcServerUrl, request));
            ObjectMapper mapper = new ObjectMapper();
            Response<T> rpcResponse = mapper.readValue(response, typeReference);
            if(rpcResponse.error != null) {
                throw new DaemonRpcException(rpcResponse.error.message);
            }
            return rpcResponse.result;
        } catch(Exception e) {
            e.printStackTrace();
            JsonRPC.log.error(e);
            throw e;
        }
    }

    @Override
    public void close() {
        monitorThread.interrupt();
        connectionManager.close();
    }
}
