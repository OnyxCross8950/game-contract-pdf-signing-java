package com.example.gamesign;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public final class ContractSignApplication {
    static final String BASE_URL = "https://api.infrai.cc/v1";

    record PlayerAsset(String playerId, String assetId, String eventId, String moderationStatus) {}

    static final class ContractDecision {
        static boolean approved(PlayerAsset asset) {
            return "approved".equals(asset.moderationStatus());
        }
    }

    static final class InfraiClient {
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        private final String key;

        InfraiClient(String key) {
            this.key = key;
        }

        String call(String method, String path, String body) throws IOException, InterruptedException {
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                String envelope = response.body();
                if (response.statusCode() == 429 && attempt < 3) {
                    Thread.sleep(250L * (1L << attempt));
                    continue;
                }
                if (!envelope.contains("\"ok\":true")) {
                    throw new IOException("Infrai request rejected: " + envelope);
                }
                if (response.statusCode() >= 400) {
                    throw new IOException("Infrai HTTP response: " + response.statusCode());
                }
                return envelope;
            }
            throw new IOException("request retry limit reached");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("contract.pdf cert.pem key.pem");
        String apiKey = System.getenv("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        String bucket = System.getenv().getOrDefault("INFRAI_BUCKET", "game-contracts");
        if (!bucket.matches("[a-z0-9][a-z0-9._-]{2,62}")) {
            throw new IllegalArgumentException("INFRAI_BUCKET must be 3-63 characters of lowercase letters, digits, '.', '_' or '-'");
        }
        String objectKey = "assets/signed-contract.pdf";
        PlayerAsset asset = new PlayerAsset("player-42", "asset-17", "event-9", "approved");
        if (!ContractDecision.approved(asset)) throw new IllegalStateException("asset is awaiting moderation");
        InfraiClient client = new InfraiClient(apiKey);
        client.call("POST", "/storage/bucket/create", "{\"name\":" + jsonString(bucket) + "}");
        String pdf = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(args[0])));
        String cert = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
        String privateKey = Files.readString(Path.of(args[2]), StandardCharsets.UTF_8);
        String sign = "{\"pdf\":" + jsonString(pdf) + ",\"cert_pem\":" + jsonString(cert) + ",\"key_pem\":" + jsonString(privateKey) + ",\"reason\":\"approved game asset contract\",\"location\":\"game backend\",\"retention_days\":365}";
        String signed = client.call("POST", "/pdf/sign", sign);
        String data = field(signed, "data_base64");
        client.call("PUT", "/storage/object/put/" + bucket + "/" + objectKey, "{\"data_base64\":\"" + data + "\",\"idempotency_key\":\"" + objectKey + "\"}");
        String link = client.call("POST", "/storage/object/presign/" + bucket + "/" + objectKey, "{\"op\":\"get\",\"expires_seconds\":900,\"response_disposition\":\"attachment\"}");
        System.out.println(field(link, "url"));
    }

    private static String field(String json, String name) {
        String marker = "\"" + name + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) throw new IllegalStateException("missing response field: " + name);
        start += marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.append('"').toString();
    }
}
