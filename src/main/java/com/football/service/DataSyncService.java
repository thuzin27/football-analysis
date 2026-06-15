package com.football.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.football.api.ApiFootballClient;
import com.football.model.*;
import com.football.repository.Repositories.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DataSyncService {

    private final ApiFootballClient        apiClient;
    private final RegiaoRepository         regiaoRepo;
    private final SelecaoRepository        selecaoRepo;
    private final TemporadaRepository      temporadaRepo;
    private final JogadorRepository        jogadorRepo;
    private final PartidaRepository        partidaRepo;
    private final PartidaJogadorRepository pjRepo;
    private final EstatisticaRepository    estatRepo;

    public DataSyncService() {
        this.apiClient     = new ApiFootballClient();
        this.regiaoRepo    = new RegiaoRepository();
        this.selecaoRepo   = new SelecaoRepository();
        this.temporadaRepo = new TemporadaRepository();
        this.jogadorRepo   = new JogadorRepository();
        this.partidaRepo   = new PartidaRepository();
        this.pjRepo        = new PartidaJogadorRepository();
        this.estatRepo     = new EstatisticaRepository();
    }

    // teamId football-data.org → confederação/região
    private static final java.util.Map<Integer, String> CONFEDERACAO = new java.util.HashMap<>();
    static {
        for (int id : new int[]{758,761,762,764,791,818})
            CONFEDERACAO.put(id, "América do Sul");
        for (int id : new int[]{759,760,765,770,773,788,792,798,799,803,805,816,1060,8601,8872,8873})
            CONFEDERACAO.put(id, "Europa");
        for (int id : new int[]{763,774,778,802,804,815,825,1930,1934,1935})
            CONFEDERACAO.put(id, "África");
        for (int id : new int[]{769,771,828,836,1836,9460})
            CONFEDERACAO.put(id, "América do Norte");
        for (int id : new int[]{766,772,779,801,840,8030,8049,8062,8070})
            CONFEDERACAO.put(id, "Ásia");
        for (int id : new int[]{783})
            CONFEDERACAO.put(id, "Oceania");
    }

    public void sincronizarCopaDoMundo(int ano) throws java.io.IOException, java.sql.SQLException {
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.printf ("║  SYNC COPA DO MUNDO %d — todas as seleções  ║%n", ano);
        System.out.println("╚══════════════════════════════════════════════╝");

        java.util.List<com.fasterxml.jackson.databind.JsonNode> times = apiClient.buscarTimesDaCopa();
        System.out.println("[Copa] " + times.size() + " seleções encontradas.\n");

        int countTotal = 0;
        int countErros = 0;

        for (int i = 0; i < times.size(); i++) {
            com.fasterxml.jackson.databind.JsonNode t = times.get(i);
            int    teamId = t.path("id").asInt(-1);
            String nome   = t.path("name").asText("?");
            String tla    = t.path("tla").asText("?");
            String regiao = CONFEDERACAO.getOrDefault(teamId, "Outros");

            System.out.printf("[%2d/%d] %s (%s) → %s%n", i + 1, times.size(), nome, tla, regiao);

            try {
                sincronizar(regiao, nome, tla, 0, teamId, ano);
                countTotal++;
            } catch (Exception e) {
                System.err.println("[ERRO] " + nome + ": " + e.getMessage());
                countErros++;
            }

            // pausa para respeitar o rate limit da API (10 req/min)
            if (i < times.size() - 1) {
                try { Thread.sleep(7_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }

        System.out.printf("%n╔══════════════════════════════════════════════╗%n");
        System.out.printf("║  Copa sincronizada: %d OK | %d erros          ║%n", countTotal, countErros);
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    public void sincronizar(String nomeRegiao, String nomeSelecao,
                             String codigoFifa, int leagueId, int teamId, int ano)
            throws IOException, SQLException {

        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.printf ("║  SYNC: %-30s║%n", nomeSelecao + " " + ano);
        System.out.println("╚══════════════════════════════════════╝");

        Regiao regiao = new Regiao();
        regiao.setNome(nomeRegiao);
        regiao = regiaoRepo.salvar(regiao);
        System.out.println("[1] Região: " + regiao);

        Selecao selecao = new Selecao();
        selecao.setNome(nomeSelecao);
        selecao.setCodigoFifa(codigoFifa);
        selecao.setRegiaoId(regiao.getId());
        selecao = selecaoRepo.salvar(selecao);
        System.out.println("[2] Seleção: " + selecao);

        Temporada temporada = new Temporada();
        temporada.setAno(ano);
        temporada.setSelecaoId(selecao.getId());
        temporada = temporadaRepo.salvar(temporada);
        System.out.println("[3] Temporada: " + temporada);

        // limpa partidas antigas para evitar duplicatas no re-sync
        if (temporada.getId() > 0) {
            int removidas = partidaRepo.deletarPorTemporada(temporada.getId());
            if (removidas > 0)
                System.out.println("[3.1] " + removidas + " partidas antigas removidas (re-sync).");
        }

        System.out.println("[4] Buscando partidas na football-data.org...");
        java.util.List<JsonNode> partidas = apiClient.buscarPartidasDaSelecao(teamId, ano);

        int countPartidas  = 0;
        int countJogadores = 0;

        for (JsonNode match : partidas) {
            try {
                Partida partida = parsePartida(match, temporada.getId(), teamId);
                partida = partidaRepo.salvar(partida);
                countPartidas++;

                int matchId = match.path("id").asInt(0);
                if (matchId > 0) {
                    countJogadores += sincronizarJogadoresDaPartida(matchId, partida.getId(), teamId);
                }
            } catch (Exception e) {
                System.err.println("[WARN] Erro ao processar partida: " + e.getMessage());
            }
        }

        System.out.printf("[5] %d partidas e %d registros de jogadores sincronizados.%n",
                          countPartidas, countJogadores);
        System.out.println("\n✔ Sincronização concluída!");
    }

    private Partida parsePartida(JsonNode match, int temporadaId, int teamId) {
        Partida p = new Partida();
        p.setTemporadaId(temporadaId);

        boolean ehCasa = match.path("homeTeam").path("id").asInt() == teamId;
        p.setAdversario(ehCasa
            ? match.path("awayTeam").path("name").asText("?")
            : match.path("homeTeam").path("name").asText("?"));

        p.setCompeticao(match.path("competition").path("name").asText("?"));

        int golsCasa = match.path("score").path("fullTime").path("home").asInt(0);
        int golsFora = match.path("score").path("fullTime").path("away").asInt(0);
        p.setGolsPro(    ehCasa ? golsCasa : golsFora);
        p.setGolsContra( ehCasa ? golsFora : golsCasa);

        String utcDate = match.path("utcDate").asText(null);
        if (utcDate != null && utcDate.length() >= 10) {
            try { p.setDataPartida(java.time.LocalDate.parse(utcDate.substring(0, 10))); }
            catch (Exception ignored) {}
        }
        return p;
    }

    private int sincronizarJogadoresDaPartida(int matchId, int partidaId, int teamId)
            throws IOException, SQLException {

        JsonNode match = apiClient.buscarDetalhesPartida(matchId);
        int count = 0;

        boolean ehCasa = match.path("homeTeam").path("id").asInt() == teamId;
        String  lado   = ehCasa ? "homeTeam" : "awayTeam";

        Map<Integer, Integer> golsMap      = new HashMap<>();
        Map<Integer, Integer> assistsMap   = new HashMap<>();
        Map<Integer, Integer> amarelosMap  = new HashMap<>();
        Map<Integer, Integer> vermelhosMap = new HashMap<>();

        JsonNode goals = match.path("goals");
        if (goals.isArray()) {
            for (JsonNode g : goals) {
                if (g.path("team").path("id").asInt() != teamId) continue;
                int sid = g.path("scorer").path("id").asInt(-1);
                if (sid > 0) golsMap.merge(sid, 1, Integer::sum);
                int aid = g.path("assist").path("id").asInt(-1);
                if (aid > 0) assistsMap.merge(aid, 1, Integer::sum);
            }
        }

        JsonNode bookings = match.path("bookings");
        if (bookings.isArray()) {
            for (JsonNode b : bookings) {
                if (b.path("team").path("id").asInt() != teamId) continue;
                int pid  = b.path("player").path("id").asInt(-1);
                String card = b.path("card").asText("");
                if (pid < 0) continue;
                if ("YELLOW_CARD".equals(card)) amarelosMap.merge(pid, 1, Integer::sum);
                if ("RED_CARD".equals(card) || "YELLOW_RED_CARD".equals(card))
                    vermelhosMap.merge(pid, 1, Integer::sum);
            }
        }

        JsonNode teamNode = match.path(lado);
        JsonNode lineup   = teamNode.path("lineup");
        JsonNode bench    = teamNode.path("bench");

        if (lineup.isArray()) {
            for (JsonNode entry : lineup)
                count += salvarJogadorPartida(entry, partidaId, 90,
                    golsMap, assistsMap, amarelosMap, vermelhosMap);
        }
        if (bench.isArray()) {
            for (JsonNode entry : bench) {
                int pid = entry.path("id").asInt(-1);
                if (golsMap.containsKey(pid) || assistsMap.containsKey(pid)
                        || amarelosMap.containsKey(pid) || vermelhosMap.containsKey(pid))
                    count += salvarJogadorPartida(entry, partidaId, 45,
                        golsMap, assistsMap, amarelosMap, vermelhosMap);
            }
        }
        return count;
    }

    private int salvarJogadorPartida(JsonNode entry, int partidaId, int minutos,
                                      Map<Integer, Integer> golsMap,
                                      Map<Integer, Integer> assistsMap,
                                      Map<Integer, Integer> amarelosMap,
                                      Map<Integer, Integer> vermelhosMap) throws SQLException {
        int    pid  = entry.path("id").asInt(-1);
        String nome = entry.path("name").asText("?");
        if (pid < 0 || nome.equals("?")) return 0;

        Jogador j = new Jogador();
        j.setNome(nome);
        j.setPosicao(traduzirPosicao(entry.path("position").asText("")));
        j = jogadorRepo.salvar(j);

        PartidaJogador pj = new PartidaJogador();
        pj.setPartidaId(partidaId);
        pj.setJogadorId(j.getId());
        pj = pjRepo.salvar(pj);
        if (pj.getId() == 0) return 0;

        Estatistica e = new Estatistica();
        e.setPartidaJogadorId(pj.getId());
        e.setMinutos(minutos);
        e.setGols(golsMap.getOrDefault(pid, 0));
        e.setAssistencias(assistsMap.getOrDefault(pid, 0));
        e.setAmarelos(amarelosMap.getOrDefault(pid, 0));
        e.setVermelhos(vermelhosMap.getOrDefault(pid, 0));
        estatRepo.salvar(e);
        return 1;
    }

    private String traduzirPosicao(String pos) {
        if (pos == null || pos.isBlank()) return "?";
        return switch (pos.toUpperCase()) {
            case "G", "GK", "GOALKEEPER"             -> "Goleiro";
            case "D", "DF", "DEFENDER", "CENTRE-BACK",
                 "LEFT-BACK", "RIGHT-BACK"           -> "Defensor";
            case "M", "MF", "MIDFIELDER",
                 "CENTRAL MIDFIELD", "DEFENSIVE MIDFIELD",
                 "ATTACKING MIDFIELD", "LEFT MIDFIELD",
                 "RIGHT MIDFIELD"                    -> "Meio-campista";
            case "F", "FW", "ATTACKER", "FORWARD",
                 "CENTRE-FORWARD", "LEFT WINGER",
                 "RIGHT WINGER", "SECONDARY STRIKER" -> "Atacante";
            default -> pos;
        };
    }
}
