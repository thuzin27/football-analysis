package com.football.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// football-data.org v4 — auth via X-Auth-Token; limite free tier: 10 req/min
public class ApiFootballClient {

    private static final String BASE_URL = "https://api.football-data.org";

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String       token;

    public ApiFootballClient() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.token = getEnv(dotenv, "FOOTBALL_DATA_TOKEN", "");
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30,  TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Estratégia 1 — endpoint direto do time (funciona para clubes; 403 para seleções não cobertas).
     * Estratégia 2 — varredura de competições TIER_ONE filtrada por teamId (só alcançada via 403).
     */
    public List<JsonNode> buscarPartidasDaSelecao(int teamId, int ano) throws IOException {
        try {
            JsonNode root = getRaw("/v4/teams/" + teamId
                + "/matches?season=" + ano + "&status=FINISHED");
            List<JsonNode> resultado = new ArrayList<>();
            JsonNode matches = root.path("matches");
            if (matches.isArray()) matches.forEach(resultado::add);
            System.out.println("[API] " + resultado.size() + " partidas (endpoint direto)");
            return resultado;
        } catch (IOException e) {
            if (!e.getMessage().startsWith("HTTP 403")) throw e;
            System.out.println("[API] 403 no endpoint direto — varrendo competições...");
        }

        List<JsonNode> resultado2 = new ArrayList<>();
        String[] comps = {"WC", "EC", "CL", "PL", "PD", "BL1", "SA", "FL1", "DED", "PPL", "BSA"};
        for (String comp : comps) {
            try {
                String url = "/v4/competitions/" + comp
                    + "/matches?season=" + ano + "&status=FINISHED";
                JsonNode matches = getRaw(url).path("matches");
                if (!matches.isArray()) continue;
                for (JsonNode m : matches) {
                    int hId = m.path("homeTeam").path("id").asInt(-1);
                    int aId = m.path("awayTeam").path("id").asInt(-1);
                    if (hId == teamId || aId == teamId) resultado2.add(m);
                }
                if (!resultado2.isEmpty()) {
                    System.out.println("[API] " + resultado2.size()
                        + " partidas via " + comp + " " + ano);
                    return resultado2;
                }
            } catch (IOException ignored) {}
        }

        System.out.println("[API] 0 partidas encontradas para team=" + teamId + " ano=" + ano);
        return resultado2;
    }

    public JsonNode buscarDetalhesPartida(int matchId) throws IOException {
        return getRaw("/v4/matches/" + matchId);
    }

    public JsonNode getRaw(String endpoint) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .addHeader("X-Auth-Token", token)
                .build();

        System.out.println("[API] GET " + endpoint);

        try (Response response = httpClient.newCall(request).execute()) {
            String available = response.header("X-Requests-Available-Minute");
            if (available != null && Integer.parseInt(available) <= 1) {
                System.out.println("[API] Rate limit próximo — aguardando 12s...");
                try { Thread.sleep(12_000); } catch (InterruptedException ignored) {}
            }

            String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                System.err.println("[API] HTTP " + response.code());
                System.err.println("[API] Body: " + body.substring(0, Math.min(300, body.length())));
                throw new IOException("HTTP " + response.code() + " → " + endpoint);
            }
            return mapper.readTree(body);
        }
    }

    private static String getEnv(Dotenv dotenv, String key, String fallback) {
        String val = dotenv.get(key);
        if (val == null) val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : fallback;
    }
}
