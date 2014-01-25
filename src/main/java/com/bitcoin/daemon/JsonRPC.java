package com.bitcoin.daemon;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class JsonRPC {
    public static class RPCDaemonException extends Exception {
        public RPCDaemonException(String message) {
            super(String.format("Daemon RPC-api exception: %s", message));
        }
        public RPCDaemonException(Throwable throwable) {
            super(String.format("Daemon RPC-api exception (%s)", throwable.getLocalizedMessage()), throwable);
        }
    }
    private static @Data class JsonRpcRequest {
        @NonNull String method;
        @NonNull List<Object> params;
    }
    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsonRpcResponse<ResultType> {
        @Data @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ErrorStatus {
            String message;
            int code;
        }
        ResultType result;
        ErrorStatus error;
        String id;
    }

    private final Log log = LogFactory.getLog(JsonRPC.class);
    private final String rpcServerUrl;
    private final HttpClient client;

    private final static JsonFactory jsonFactory = new JsonFactory();
    public JsonRPC(String rpcHost, int rpcPort, String rpcUsername, String rpcPassword) {
        this.rpcServerUrl = String.format("http://%s:%d", rpcHost, rpcPort);
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(rpcHost, rpcPort),
                new UsernamePasswordCredentials(rpcUsername, rpcPassword));
        int timeout = 20 * 1000;
        RequestConfig.Builder requestBuilder = RequestConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout);
        client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestBuilder.build())
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();
    }
    private HttpResponse postRequest(String url, String json) throws IOException {
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(json));
        request.addHeader("Content-Type", "application/json");
        synchronized (client) {
            return client.execute(request);
        }
    }
    private static String getResponseString(HttpResponse response) throws IOException {
        try(BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private static String prepareJsonRequest(String method, List<Object> args) throws IOException {
        ByteArrayOutputStream writer = new ByteArrayOutputStream();
        JsonGenerator generator = jsonFactory.createJsonGenerator(writer);
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(generator, new JsonRpcRequest(method, args));
        return writer.toString();
    }

    public <T> T executeRpcRequest(String method, List<Object> args, TypeReference<JsonRpcResponse<T>> typeReference) throws Exception {
        try {
            String request = prepareJsonRequest(method, args);
            String response = getResponseString(postRequest(rpcServerUrl, request));
            ObjectMapper mapper = new ObjectMapper();
            JsonRpcResponse<T> rpcResponse = mapper.readValue(response, typeReference);
            if(rpcResponse.error != null) {
                throw new RPCDaemonException(rpcResponse.error.message);
            }
            return rpcResponse.result;
        } catch(Exception e) {
            log.error(e);
            throw new RPCDaemonException(e);
        }
    }
}
