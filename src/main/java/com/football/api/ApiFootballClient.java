package com.football.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    // IDs confirmados no football-data.org para seleções nacionais
    private static final java.util.Map<String, Integer> NAC_IDS = new java.util.LinkedHashMap<>();
    static {
        NAC_IDS.put("brazil",          764);
        NAC_IDS.put("brasil",          764);
        NAC_IDS.put("argentina",       974);
        NAC_IDS.put("france",          773);
        NAC_IDS.put("franca",          773);
        NAC_IDS.put("portugal",        765);
        NAC_IDS.put("germany",         759);
        NAC_IDS.put("alemanha",        759);
        NAC_IDS.put("spain",           760);
        NAC_IDS.put("espanha",         760);
        NAC_IDS.put("england",         770);
        NAC_IDS.put("inglaterra",      770);
        NAC_IDS.put("italy",           784);
        NAC_IDS.put("italia",          784);
        NAC_IDS.put("netherlands",     786);
        NAC_IDS.put("holanda",         786);
        NAC_IDS.put("belgium",         803);
        NAC_IDS.put("belgica",         803);
        NAC_IDS.put("colombia",        779);
        NAC_IDS.put("chile",           777);
        NAC_IDS.put("mexico",          758);
        NAC_IDS.put("uruguay",         788);
        NAC_IDS.put("usa",             768);
        NAC_IDS.put("estados unidos",  768);
        NAC_IDS.put("japan",           783);
        NAC_IDS.put("japao",           783);
        NAC_IDS.put("south korea",     772);
        NAC_IDS.put("coreia do sul",   772);
        NAC_IDS.put("morocco",         799);
        NAC_IDS.put("marrocos",        799);
        NAC_IDS.put("croatia",         776);
        NAC_IDS.put("croacia",         776);
        NAC_IDS.put("switzerland",     788);
        NAC_IDS.put("suica",           788);
        NAC_IDS.put("denmark",         778);
        NAC_IDS.put("dinamarca",       778);
        NAC_IDS.put("poland",          794);
        NAC_IDS.put("polonia",         794);
        NAC_IDS.put("australia",       775);
        NAC_IDS.put("ecuador",         780);
        NAC_IDS.put("senegal",         800);
        NAC_IDS.put("ghana",           781);
        NAC_IDS.put("serbia",          795);
        NAC_IDS.put("servia",          795);
        NAC_IDS.put("wales",           771);
        NAC_IDS.put("gales",           771);
        NAC_IDS.put("saudi arabia",    756);
        NAC_IDS.put("arabia saudita",  756);
        NAC_IDS.put("iran",            782);
        NAC_IDS.put("cameroon",        790);
        NAC_IDS.put("scotland",        769);
        NAC_IDS.put("escocia",         769);
        NAC_IDS.put("haiti",           476);
    }

    /**
     * Estratégia 1: lookup local de seleções nacionais → GET /v4/teams/{id}
     * Estratégia 2: varredura de ligas europeias para clubes
     */
    public List<JsonNode> buscarTimesPorNome(String nome) throws IOException {
        String q = nome.toLowerCase().trim();
        List<JsonNode> encontrados = new ArrayList<>();
        java.util.Set<Integer> vistos = new java.util.HashSet<>();

        for (java.util.Map.Entry<String, Integer> entry : NAC_IDS.entrySet()) {
            if (entry.getKey().contains(q) || q.contains(entry.getKey())) {
                int id = entry.getValue();
                if (vistos.contains(id)) continue;
                try {
                    JsonNode t = getRaw("/v4/teams/" + id);
                    if (t.path("id").asInt(-1) > 0) {
                        vistos.add(id);
                        encontrados.add(t);
                    }
                } catch (Exception e) {
                    System.err.println("[API] teams/" + id + ": " + e.getMessage());
                }
                if (!encontrados.isEmpty()) break;
            }
        }

        if (encontrados.isEmpty()) {
            String[] comps = {"/v4/competitions/PL/teams?season=2024",
                              "/v4/competitions/PD/teams?season=2024",
                              "/v4/competitions/BL1/teams?season=2024",
                              "/v4/competitions/SA/teams?season=2024",
                              "/v4/competitions/FL1/teams?season=2024",
                              "/v4/competitions/CL/teams?season=2024"};
            for (String endpoint : comps) {
                try {
                    JsonNode arr = getRaw(endpoint).path("teams");
                    if (!arr.isArray()) continue;
                    for (JsonNode t : arr) {
                        int id = t.path("id").asInt(-1);
                        if (id < 0 || vistos.contains(id)) continue;
                        String n = t.path("name").asText("").toLowerCase();
                        String s = t.path("shortName").asText("").toLowerCase();
                        String c = t.path("tla").asText("").toLowerCase();
                        if (n.contains(q) || s.contains(q) || c.contains(q)) {
                            vistos.add(id);
                            encontrados.add(t);
                        }
                    }
                    if (!encontrados.isEmpty()) break;
                } catch (Exception ignored) {}
            }
        }
        return encontrados;
    }

    public List<JsonNode> buscarTimesDaCopa() throws IOException {
        JsonNode root = getRaw("/v4/competitions/WC/teams");
        List<JsonNode> times = new ArrayList<>();
        JsonNode arr = root.path("teams");
        if (arr.isArray()) arr.forEach(times::add);
        return times;
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
