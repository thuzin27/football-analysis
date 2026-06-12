package com.football.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.football.algorithm.PredictionAlgorithm;
import com.football.algorithm.PredictionAlgorithm.Predicao;
import com.football.api.ApiFootballClient;
import com.football.config.DatabaseConfig;
import com.football.model.Partida;
import com.football.model.Selecao;
import com.football.repository.Repositories.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * WebServer — dashboard HTML para a Copa do Mundo 2026.
 *
 * Inicia em http://localhost:8080
 *   GET /              → dashboard HTML
 *   GET /api/copa      → JSON com todas as seleções WC 2026 + estatísticas
 *   GET /api/predicao/{selecaoId} → JSON com predição DKP
 */
public class WebServer {

    private static final int PORT = 8080;

    private static final List<String> CONFS = List.of(
        "América do Sul", "Europa", "África", "América do Norte", "Ásia", "Oceania"
    );

    private static final ZoneId       BRT         = ZoneId.of("America/Sao_Paulo");
    private static final long         CACHE_TTL   = 5 * 60_000L; // 5 minutos

    private final ObjectMapper        mapper      = new ObjectMapper();
    private final PartidaRepository   partidaRepo = new PartidaRepository();
    private final SelecaoRepository   selecaoRepo = new SelecaoRepository();
    private final ApiFootballClient   apiClient   = new ApiFootballClient();
    private HttpServer                server;
    private volatile boolean          rodando     = false;

    // Cache em memória para os endpoints que chamam a football-data.org
    private volatile String cacheProximos    = null;
    private volatile long   tsProximos       = 0;
    private volatile String cacheGrupos      = null;
    private volatile long   tsGrupos         = 0;
    private volatile String cacheConfrontos  = null;
    private volatile long   tsConfrontos     = 0;

    public void iniciar() throws IOException {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
        } catch (java.net.BindException e) {
            throw new IOException("Porta " + PORT + " já está em uso. Encerre o processo que a ocupa e tente novamente.", e);
        }
        server.createContext("/api/copa",        this::handleCopa);
        server.createContext("/api/predicao",   this::handlePredicao);
        server.createContext("/api/proximos",   this::handleProximos);
        server.createContext("/api/grupos",     this::handleGrupos);
        server.createContext("/api/confrontos", this::handleConfrontos);
        server.createContext("/",               this::handleRoot);
        server.setExecutor(null);
        server.start();
        rodando = true;
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║  Dashboard: http://localhost:" + PORT + "       ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println("Abra o link no navegador. Pressione ENTER para parar.");
    }

    public void parar() {
        if (server != null) {
            server.stop(0);
            rodando = false;
            System.out.println("[Web] Servidor encerrado.");
        }
    }

    public boolean isRodando() { return rodando; }

    // ----------------------------------------------------------------
    //  GET /
    // ----------------------------------------------------------------
    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { responder(ex, 405, "text/plain", "Method Not Allowed"); return; }
        try (InputStream is = getClass().getResourceAsStream("/web/index.html")) {
            if (is == null) { responder(ex, 404, "text/plain", "index.html not found"); return; }
            byte[] bytes = is.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    // ----------------------------------------------------------------
    //  GET /api/copa
    // ----------------------------------------------------------------
    private void handleCopa(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        try {
            String json = buildCopaJson();
            responderJson(ex, json);
        } catch (Exception e) {
            responderJson(ex, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String buildCopaJson() throws SQLException, com.fasterxml.jackson.core.JsonProcessingException {
        // Monta o IN com parâmetros JDBC — evita problemas de encoding de acentos em literais SQL
        String placeholders = CONFS.stream().map(c -> "?")
            .collect(java.util.stream.Collectors.joining(","));
        String sql = """
            SELECT s.id, s.nome, s.codigo_fifa, r.nome AS regiao,
                   COUNT(p.id)                                              AS partidas,
                   COALESCE(SUM(CASE WHEN p.gols_pro  > p.gols_contra THEN 1 ELSE 0 END),0) AS vitorias,
                   COALESCE(SUM(CASE WHEN p.gols_pro  = p.gols_contra THEN 1 ELSE 0 END),0) AS empates,
                   COALESCE(SUM(CASE WHEN p.gols_pro  < p.gols_contra THEN 1 ELSE 0 END),0) AS derrotas,
                   COALESCE(SUM(p.gols_pro),0)                              AS gols_pro,
                   COALESCE(SUM(p.gols_contra),0)                           AS gols_contra
            FROM selecao s
            JOIN regiao r     ON r.id = s.regiao_id
            JOIN temporada t  ON t.selecao_id = s.id
            LEFT JOIN partida p ON p.temporada_id = t.id
            WHERE t.ano = 2026
              AND r.nome IN (%s)
            GROUP BY s.id, s.nome, s.codigo_fifa, r.nome
            ORDER BY r.nome, s.nome
            """.formatted(placeholders);

        // Agrupa por confederação
        Map<String, List<ObjectNode>> mapa = new LinkedHashMap<>();
        for (String c : CONFS) mapa.put(c, new ArrayList<>());

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < CONFS.size(); i++) ps.setString(i + 1, CONFS.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String regiao = rs.getString("regiao");
                    if (!mapa.containsKey(regiao)) continue;

                    ObjectNode t = mapper.createObjectNode();
                    t.put("id",         rs.getInt("id"));
                    t.put("nome",       rs.getString("nome"));
                    t.put("tla",        rs.getString("codigo_fifa"));
                    t.put("partidas",   rs.getInt("partidas"));
                    t.put("vitorias",   rs.getInt("vitorias"));
                    t.put("empates",    rs.getInt("empates"));
                    t.put("derrotas",   rs.getInt("derrotas"));
                    t.put("golsPro",    rs.getInt("gols_pro"));
                    t.put("golsContra", rs.getInt("gols_contra"));
                    mapa.get(regiao).add(t);
                }
            }
        }

        ObjectNode root = mapper.createObjectNode();
        ArrayNode confs = mapper.createArrayNode();

        for (String nome : CONFS) {
            List<ObjectNode> sel = mapa.get(nome);
            if (sel == null || sel.isEmpty()) continue;
            ObjectNode conf = mapper.createObjectNode();
            conf.put("nome", nome);
            ArrayNode arr = mapper.createArrayNode();
            sel.forEach(arr::add);
            conf.set("selecoes", arr);
            confs.add(conf);
        }

        root.set("confederacoes", confs);
        return mapper.writeValueAsString(root);
    }

    // ----------------------------------------------------------------
    //  GET /api/predicao/{selecaoId}
    // ----------------------------------------------------------------
    private void handlePredicao(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }

        String path = ex.getRequestURI().getPath(); // /api/predicao/5
        String[] parts = path.split("/");
        if (parts.length < 4) { responderJson(ex, "{\"error\":\"ID ausente\"}"); return; }

        try {
            int selecaoId = Integer.parseInt(parts[3]);
            List<Partida> historico = partidaRepo.listarPorSelecaoOrdenado(selecaoId);
            Selecao sel = selecaoRepo.buscarPorId(selecaoId);

            if (historico.isEmpty()) {
                responderJson(ex, "{\"totalPartidas\":0,\"error\":\"sem dados\"}");
                return;
            }

            Predicao pred = PredictionAlgorithm.prever(historico);

            ObjectNode root = mapper.createObjectNode();
            root.put("selecao",    sel != null ? sel.getNome()      : "?");
            root.put("tla",        sel != null ? sel.getCodigoFifa(): "?");
            root.put("totalPartidas",         pred.totalPartidas);
            root.put("probVitoria",           pred.probVitoria);
            root.put("probEmpate",            pred.probEmpate);
            root.put("probDerrota",           pred.probDerrota);
            root.put("golsEsperados",         pred.golsEsperados);
            root.put("golsSofridosEsperados", pred.golsSofridosEsperados);
            root.put("confianca",             pred.confianca);
            root.put("aproveitamento",        pred.aproveitamentoRecente);
            root.put("ultimoResultado",       pred.ultimoResultado);
            root.put("veredicto",             veredicto(pred));

            int totalGolsPro    = historico.stream().mapToInt(Partida::getGolsPro).sum();
            int totalGolsContra = historico.stream().mapToInt(Partida::getGolsContra).sum();
            root.put("totalGolsPro",    totalGolsPro);
            root.put("totalGolsContra", totalGolsContra);

            // Últimas 5 partidas
            ArrayNode ultimas = mapper.createArrayNode();
            int ini = Math.max(0, historico.size() - 5);
            for (int i = ini; i < historico.size(); i++) {
                Partida p = historico.get(i);
                ObjectNode pm = mapper.createObjectNode();
                pm.put("data",       p.getDataPartida() != null ? p.getDataPartida().toString() : "-");
                pm.put("adversario", p.getAdversario() != null ? p.getAdversario() : "-");
                pm.put("golsPro",    p.getGolsPro());
                pm.put("golsContra", p.getGolsContra());
                pm.put("resultado",  p.getResultado());
                ultimas.add(pm);
            }
            root.set("ultimasPartidas", ultimas);

            responderJson(ex, mapper.writeValueAsString(root));

        } catch (NumberFormatException nfe) {
            responderJson(ex, "{\"error\":\"ID inválido\"}");
        } catch (Exception e) {
            responderJson(ex, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ----------------------------------------------------------------
    //  GET /api/proximos — próximas partidas da WC (status=SCHEDULED)
    // ----------------------------------------------------------------
    private void handleProximos(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        try {
            long now = System.currentTimeMillis();
            if (cacheProximos == null || now - tsProximos > CACHE_TTL) {
                cacheProximos = buildProximosJson();
                tsProximos    = now;
            }
            responderJson(ex, cacheProximos);
        } catch (Exception e) {
            responderJson(ex, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private static final int MAX_PROXIMOS = 5;

    private String buildProximosJson() throws Exception {
        JsonNode root = apiClient.getRaw("/v4/competitions/WC/matches?status=SCHEDULED");
        JsonNode matches = root.path("matches");

        DateTimeFormatter fmtIn  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
        DateTimeFormatter fmtOut = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        ArrayNode partidas = mapper.createArrayNode();
        if (matches.isArray()) {
            for (JsonNode m : matches) {
                if (partidas.size() >= MAX_PROXIMOS) break; // só os 5 mais próximos

                String utcStr = m.path("utcDate").asText("");
                if (utcStr.isBlank()) continue;

                ZonedDateTime brt = ZonedDateTime.parse(utcStr, fmtIn)
                                                 .withZoneSameInstant(BRT);

                ObjectNode partida = mapper.createObjectNode();
                partida.put("id",      m.path("id").asInt());
                partida.put("dataBrt", brt.format(fmtOut));
                partida.put("jornada", m.path("matchday").asInt(0));
                partida.put("fase",    traduzirFase(m.path("stage").asText("")));
                partida.put("grupo",   traduzirGrupo(m.path("group").asText("")));

                ObjectNode casa = mapper.createObjectNode();
                casa.put("nome", m.path("homeTeam").path("name").asText("?"));
                casa.put("tla",  m.path("homeTeam").path("tla").asText("?"));
                partida.set("casa", casa);

                ObjectNode fora = mapper.createObjectNode();
                fora.put("nome", m.path("awayTeam").path("name").asText("?"));
                fora.put("tla",  m.path("awayTeam").path("tla").asText("?"));
                partida.set("fora", fora);

                partidas.add(partida);
            }
        }

        ObjectNode out = mapper.createObjectNode();
        out.set("partidas", partidas);
        return mapper.writeValueAsString(out);
    }

    // ----------------------------------------------------------------
    //  GET /api/grupos — classificação por grupo da WC
    // ----------------------------------------------------------------
    private void handleGrupos(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        try {
            long now = System.currentTimeMillis();
            if (cacheGrupos == null || now - tsGrupos > CACHE_TTL) {
                cacheGrupos = buildGruposJson();
                tsGrupos    = now;
            }
            responderJson(ex, cacheGrupos);
        } catch (Exception e) {
            responderJson(ex, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String buildGruposJson() throws Exception {
        JsonNode root      = apiClient.getRaw("/v4/competitions/WC/standings");
        JsonNode standings = root.path("standings");

        ArrayNode grupos = mapper.createArrayNode();
        if (standings.isArray()) {
            for (JsonNode s : standings) {
                // WC 2026 retorna type=TOTAL com campo "group" populado para a fase de grupos
                String tipo  = s.path("type").asText("");
                String grupoRaw = s.path("group").asText("");
                if (grupoRaw.isBlank()) continue; // ignora entradas sem grupo (ex: TOTAL geral)
                if (!"TOTAL".equals(tipo) && !"GROUP".equals(tipo)) continue;

                ObjectNode grupo = mapper.createObjectNode();
                grupo.put("nome", traduzirGrupo(grupoRaw));

                ArrayNode times = mapper.createArrayNode();
                JsonNode table = s.path("table");
                if (table.isArray()) {
                    for (JsonNode row : table) {
                        ObjectNode time = mapper.createObjectNode();
                        time.put("pos",   row.path("position").asInt());
                        time.put("nome",  row.path("team").path("name").asText("?"));
                        time.put("tla",   row.path("team").path("tla").asText("?"));
                        time.put("j",     row.path("playedGames").asInt());
                        time.put("v",     row.path("won").asInt());
                        time.put("e",     row.path("draw").asInt());
                        time.put("d",     row.path("lost").asInt());
                        time.put("gp",    row.path("goalsFor").asInt());
                        time.put("gc",    row.path("goalsAgainst").asInt());
                        time.put("sg",    row.path("goalDifference").asInt());
                        time.put("pts",   row.path("points").asInt());
                        times.add(time);
                    }
                }
                grupo.set("times", times);
                grupos.add(grupo);
            }
        }

        ObjectNode out = mapper.createObjectNode();
        out.set("grupos", grupos);
        return mapper.writeValueAsString(out);
    }

    // ----------------------------------------------------------------
    //  GET /api/confrontos — todos os jogos SCHEDULED com análise DMI
    // ----------------------------------------------------------------
    private void handleConfrontos(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        try {
            long now = System.currentTimeMillis();
            if (cacheConfrontos == null || now - tsConfrontos > CACHE_TTL) {
                cacheConfrontos = buildConfrontosJson();
                tsConfrontos    = now;
            }
            responderJson(ex, cacheConfrontos);
        } catch (Exception e) {
            responderJson(ex, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    /**
     * Resultado do cálculo do DataKick Moment Index (DMI) para um time.
     * momento ∈ [0,1] — 0.5 = neutro/sem dados; >0.5 = boa forma; <0.5 = má forma.
     * confianca ∈ [0,1] — quanto o modelo confia no valor de momento.
     */
    private static class MomentoResult {
        final double momento;
        final double confianca;
        final int    totalPartidas;
        MomentoResult(double momento, double confianca, int total) {
            this.momento = momento; this.confianca = confianca; this.totalPartidas = total;
        }
    }

    /**
     * Computa o DataKick Moment Index (DMI) de um time a partir de seu histórico.
     *
     * Fórmula do momento bruto (rb):
     *
     *   rb = 0.35 × aproveitamentoRecente          // [0,1] — forma últimos 5 jogos
     *      + 0.35 × probVitoria_DKP                // [0,1] — modelo Markov+Bayesian
     *      + 0.20 × tanh(golsEsperados / 2.0)      // [0,1) — poder ofensivo
     *      + 0.10 × (1 − tanh(golsSofridos / 2.0)) // [0,1) — solidez defensiva
     *
     * Pesos e raciocínio:
     *   - aproveitamentoRecente (35%): indicador mais direto de momentum atual;
     *     já normalizado pelo DKP; captura a sequência dos últimos 5 jogos.
     *   - probVitoria DKP (35%): o modelo Markov+Bayesian pondera o histórico
     *     completo com decaimento exponencial; reflete tendência de longo prazo.
     *   - Poder ofensivo (20%): tanh(λ/2) mapeia gols/jogo para [0,1).
     *     λ=2g → tanh(1.0)≈0.76; gols vencem jogos — segundo fator mais relevante.
     *   - Solidez defensiva (10%): valor inverso de gols sofridos; peso menor
     *     pois defesas são mais estáveis e há menos variância entre jogos.
     *
     * Ajuste por confiança (regressão ao neutro):
     *   momento = confianca × rb + (1 − confianca) × 0.5
     *   → Times sem histórico ficam em 0.5 (incerteza total, nenhum viés).
     */
    private MomentoResult computarMomento(String tla, Map<String, List<Partida>> historicos) {
        List<Partida> hist = historicos.get(tla);
        if (hist == null || hist.isEmpty()) return new MomentoResult(0.5, 0.0, 0);
        try {
            Predicao pred = PredictionAlgorithm.prever(hist);
            double ofen = Math.tanh(pred.golsEsperados         / 2.0);
            double def  = 1.0 - Math.tanh(pred.golsSofridosEsperados / 2.0);
            double rb   = 0.35 * pred.aproveitamentoRecente
                        + 0.35 * pred.probVitoria
                        + 0.20 * ofen
                        + 0.10 * def;
            double final_ = pred.confianca * rb + (1.0 - pred.confianca) * 0.5;
            return new MomentoResult(final_, pred.confianca, pred.totalPartidas);
        } catch (Exception e) {
            return new MomentoResult(0.5, 0.0, 0);
        }
    }

    /**
     * Converte dois Momentos em probabilidades do confronto [pCasa, pEmpate, pFora].
     *
     * Método:
     *   1. diff = momentoCasa − momentoFora ∈ [−1, 1]
     *
     *   2. Vitória logística do mandante:
     *      pVitCasa = sigmoid(k × diff),  k = 6.0
     *      Sensibilidade (k=6): diff=+0.10 → ≈62%; diff=+0.25 → ≈82%.
     *      k escolhido para que 15-25% de diferença de momento seja decisiva.
     *
     *   3. Base de empate (média histórica no futebol internacional ≈ 28%):
     *      empate = 0.28 × (1 − |diff|)
     *      Quanto mais próximos os momentos, maior a chance de empate.
     *      Clampado a [5%, 40%] para evitar valores degenerados.
     *
     *   4. Distribuição final (soma exata = 1):
     *      pCasa  = pVitCasa  × (1 − empate)
     *      pFora  = (1 − pVitCasa) × (1 − empate)
     *      [pCasa + empate + pFora = 1 pela álgebra de sigmoid complementar]
     */
    private double[] confrontoProbs(MomentoResult mC, MomentoResult mF) {
        double diff     = mC.momento - mF.momento;
        double k        = 6.0;
        double pVitCasa = 1.0 / (1.0 + Math.exp(-k * diff));

        double empate = 0.28 * (1.0 - Math.abs(diff));
        empate = Math.max(0.05, Math.min(empate, 0.40));

        double pCasa = pVitCasa       * (1.0 - empate);
        double pFora = (1.0 - pVitCasa) * (1.0 - empate);
        double soma  = pCasa + empate + pFora;
        return new double[]{ pCasa / soma, empate / soma, pFora / soma };
    }

    private String buildConfrontosJson() throws Exception {
        JsonNode root    = apiClient.getRaw("/v4/competitions/WC/matches?status=SCHEDULED");
        JsonNode matches = root.path("matches");

        DateTimeFormatter fmtIn  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
        DateTimeFormatter fmtOut = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        // Coleta todos os TLAs únicos para carregar histórico do banco uma única vez por time
        Set<String> tlas = new LinkedHashSet<>();
        if (matches.isArray())
            for (JsonNode m : matches) {
                String ht = m.path("homeTeam").path("tla").asText("");
                String at = m.path("awayTeam").path("tla").asText("");
                if (!ht.isBlank()) tlas.add(ht);
                if (!at.isBlank()) tlas.add(at);
            }

        // Carrega histórico do banco — O(n_times) queries indexadas por codigo_fifa
        Map<String, List<Partida>> historicos = new LinkedHashMap<>();
        for (String tla : tlas) {
            try {
                Selecao sel = selecaoRepo.buscarPorCodigoFifa(tla);
                if (sel != null) {
                    List<Partida> hist = partidaRepo.listarPorSelecaoOrdenado(sel.getId());
                    if (!hist.isEmpty()) historicos.put(tla, hist);
                }
            } catch (Exception ignored) {}
        }

        ArrayNode confrontos = mapper.createArrayNode();
        if (matches.isArray()) {
            for (JsonNode m : matches) {
                String utcStr = m.path("utcDate").asText("");
                if (utcStr.isBlank()) continue;

                ZonedDateTime brt = ZonedDateTime.parse(utcStr, fmtIn).withZoneSameInstant(BRT);

                String casaTla  = m.path("homeTeam").path("tla").asText("?");
                String casaNome = m.path("homeTeam").path("name").asText("?");
                String foraTla  = m.path("awayTeam").path("tla").asText("?");
                String foraNome = m.path("awayTeam").path("name").asText("?");

                MomentoResult momCasa = computarMomento(casaTla, historicos);
                MomentoResult momFora = computarMomento(foraTla, historicos);
                double[] probs        = confrontoProbs(momCasa, momFora);

                ObjectNode c = mapper.createObjectNode();
                c.put("id",      m.path("id").asInt());
                c.put("dataBrt", brt.format(fmtOut));
                c.put("jornada", m.path("matchday").asInt(0));
                c.put("fase",    traduzirFase(m.path("stage").asText("")));
                c.put("grupo",   traduzirGrupo(m.path("group").asText("")));

                ObjectNode casa = mapper.createObjectNode();
                casa.put("nome",          casaNome);
                casa.put("tla",           casaTla);
                casa.put("momento",       momCasa.momento);
                casa.put("confianca",     momCasa.confianca);
                casa.put("totalPartidas", momCasa.totalPartidas);
                c.set("casa", casa);

                ObjectNode fora = mapper.createObjectNode();
                fora.put("nome",          foraNome);
                fora.put("tla",           foraTla);
                fora.put("momento",       momFora.momento);
                fora.put("confianca",     momFora.confianca);
                fora.put("totalPartidas", momFora.totalPartidas);
                c.set("fora", fora);

                c.put("probCasa",       probs[0]);
                c.put("probEmpate",     probs[1]);
                c.put("probFora",       probs[2]);
                c.put("confiancaGeral", Math.min(momCasa.confianca, momFora.confianca));
                // semDados: true apenas quando nenhum dos dois times tem partidas registradas
                c.put("semDados", momCasa.totalPartidas == 0 && momFora.totalPartidas == 0);

                confrontos.add(c);
            }
        }

        ObjectNode out = mapper.createObjectNode();
        out.set("confrontos", confrontos);
        return mapper.writeValueAsString(out);
    }

    // ----------------------------------------------------------------
    //  HELPERS — tradução de códigos da API para PT-BR
    // ----------------------------------------------------------------
    private String traduzirFase(String stage) {
        return switch (stage) {
            case "GROUP_STAGE"       -> "Fase de Grupos";
            case "ROUND_OF_16"       -> "Oitavas de Final";
            case "QUARTER_FINALS"    -> "Quartas de Final";
            case "SEMI_FINALS"       -> "Semifinais";
            case "THIRD_PLACE"       -> "Disputa 3º Lugar";
            case "FINAL"             -> "Final";
            default                  -> stage;
        };
    }

    private String traduzirGrupo(String group) {
        if (group == null || group.isBlank()) return "";
        if (group.startsWith("GROUP_")) return "Grupo " + group.substring(6); // "GROUP_A" → "Grupo A"
        if (group.startsWith("Group ")) return "Grupo " + group.substring(6); // "Group A" → "Grupo A"
        return group;
    }

    // ----------------------------------------------------------------
    //  VEREDICTO (replica PredictionService.veredicto)
    // ----------------------------------------------------------------
    private String veredicto(Predicao pred) {
        if (pred.totalPartidas < 3) return "AMOSTRA INSUFICIENTE (" + pred.totalPartidas + " partidas)";
        double max = Math.max(pred.probVitoria, Math.max(pred.probEmpate, pred.probDerrota));
        if (max == pred.probVitoria && pred.probVitoria >= 0.50)
            return "FAVORITO A VENCER (" + fmt(pred.probVitoria) + ")";
        if (max == pred.probDerrota && pred.probDerrota >= 0.45)
            return "DESFAVORECIDO (" + fmt(pred.probDerrota) + " chance de derrota)";
        if (max == pred.probEmpate)
            return "TENDÊNCIA A EMPATE (" + fmt(pred.probEmpate) + ")";
        return "JOGO EQUILIBRADO — vantagem de " + fmt(max);
    }

    private String fmt(double v) { return String.format("%.1f%%", v * 100); }

    // ----------------------------------------------------------------
    //  HTTP HELPERS
    // ----------------------------------------------------------------
    private void responder(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void responderJson(HttpExchange ex, String json) throws IOException {
        responder(ex, 200, "application/json", json);
    }

    private void setCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }
}
