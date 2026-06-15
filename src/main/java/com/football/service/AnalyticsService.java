package com.football.service;

import com.football.algorithm.BTree;
import com.football.algorithm.InterpolationSearch;
import com.football.model.*;
import com.football.repository.Repositories.*;

import java.sql.SQLException;
import java.util.*;

public class AnalyticsService {

    private final JogadorRepository        jogadorRepo;
    private final PartidaRepository        partidaRepo;
    private final EstatisticaRepository    estatRepo;
    private final RegiaoRepository         regiaoRepo;
    private final SelecaoRepository        selecaoRepo;
    private final TemporadaRepository      temporadaRepo;
    private final PartidaJogadorRepository pjRepo;

    // Árvore B em memória: regiaoId → seleções daquela região
    private final BTree<Integer, List<Selecao>> arvoreRegioes;

    public AnalyticsService() {
        this.jogadorRepo   = new JogadorRepository();
        this.partidaRepo   = new PartidaRepository();
        this.estatRepo     = new EstatisticaRepository();
        this.regiaoRepo    = new RegiaoRepository();
        this.selecaoRepo   = new SelecaoRepository();
        this.temporadaRepo = new TemporadaRepository();
        this.pjRepo        = new PartidaJogadorRepository();
        this.arvoreRegioes = new BTree<>(3);
    }

    /** Carrega a hierarquia Regiao→Selecao na Árvore B (em memória). */
    public void carregarArvore() throws SQLException {
        List<Regiao> regioes = regiaoRepo.listarTodas();
        for (Regiao r : regioes) {
            List<Selecao> selecoes = selecaoRepo.listarPorRegiao(r.getId());
            arvoreRegioes.inserir(r.getId(), selecoes);
        }
        System.out.printf("[Árvore B] %d regiões carregadas.%n", regioes.size());
    }

    /** O(log n) via Árvore B. */
    public List<Selecao> navegarPorRegiao(int regiaoId) {
        List<Selecao> resultado = arvoreRegioes.buscar(regiaoId);
        return resultado != null ? resultado : Collections.emptyList();
    }

    public List<Selecao> buscarTimePorNome(String nome) throws SQLException {
        return selecaoRepo.buscarPorNome(nome);
    }

    /** Lista carregada do banco já ordenada por id ASC — necessário para InterpolationSearch. */
    public Jogador buscarJogadorPorId(int jogadorId) throws SQLException {
        List<Jogador> lista = jogadorRepo.listarTodosOrdenado();
        System.out.printf("%n[InterpolationSearch] Buscando jogador id=%d em lista de %d registros...%n",
                          jogadorId, lista.size());
        Jogador resultado = InterpolationSearch.encontrar(lista, jogadorId, Jogador::getId);
        if (resultado != null)
            System.out.println("[InterpolationSearch] ✔ Encontrado: " + resultado);
        else
            System.out.println("[InterpolationSearch] ✘ Jogador não encontrado.");
        return resultado;
    }

    /** Lista filtrada por temporada e ordenada por id ASC — necessário para InterpolationSearch. */
    public Partida buscarPartidaPorId(int temporadaId, int partidaId) throws SQLException {
        List<Partida> lista = partidaRepo.listarPorTemporadaOrdenado(temporadaId);
        System.out.printf("%n[InterpolationSearch] Buscando partida id=%d em lista de %d registros...%n",
                          partidaId, lista.size());
        Partida resultado = InterpolationSearch.encontrar(lista, partidaId, Partida::getId);
        if (resultado != null)
            System.out.println("[InterpolationSearch] ✔ Encontrado: " + resultado);
        else
            System.out.println("[InterpolationSearch] ✘ Partida não encontrada.");
        return resultado;
    }

    public Selecao buscarSelecaoPorId(int selecaoId) throws SQLException {
        List<Selecao> lista = selecaoRepo.listarTodasOrdenado();
        System.out.printf("%n[InterpolationSearch] Buscando seleção id=%d em lista de %d registros...%n",
                          selecaoId, lista.size());
        return InterpolationSearch.encontrar(lista, selecaoId, Selecao::getId);
    }

    public Temporada buscarTemporadaPorId(int temporadaId) throws SQLException {
        List<Temporada> lista = temporadaRepo.listarTodasOrdenado();
        System.out.printf("%n[InterpolationSearch] Buscando temporada id=%d em lista de %d registros...%n",
                          temporadaId, lista.size());
        return InterpolationSearch.encontrar(lista, temporadaId, Temporada::getId);
    }

    public List<Object[]> topArtilheiros(int temporadaId, int top) throws SQLException {
        return estatRepo.rankingArtilheiros(temporadaId, top);
    }

    public Map<String, Object> desempenhoSelecao(int selecaoId, int temporadaId) throws SQLException {
        List<Temporada> temporadas = temporadaRepo.listarPorSelecao(selecaoId);

        boolean encontrada = false;
        for (Temporada t : temporadas) {
            if (t.getId() == temporadaId) { encontrada = true; break; }
        }
        if (!encontrada) return Collections.emptyMap();

        List<Partida> partidas = partidaRepo.listarPorTemporadaOrdenado(temporadaId);

        int v = 0, e = 0, d = 0, gp = 0, gc = 0;
        for (Partida p : partidas) {
            gp += p.getGolsPro();
            gc += p.getGolsContra();
            switch (p.getResultado()) {
                case "V" -> v++;
                case "E" -> e++;
                case "D" -> d++;
            }
        }
        int total = partidas.size();
        double aprov = total > 0 ? ((v * 3.0 + e) / (total * 3.0)) * 100.0 : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_jogos",      total);
        stats.put("vitorias",         v);
        stats.put("empates",          e);
        stats.put("derrotas",         d);
        stats.put("gols_pro",         gp);
        stats.put("gols_contra",      gc);
        stats.put("saldo_gols",       gp - gc);
        stats.put("aproveitamento_%", String.format("%.1f%%", aprov));
        return stats;
    }

    public String gerarRelatorio(int selecaoId, int temporadaId) throws SQLException {
        Map<String, Object> stats = desempenhoSelecao(selecaoId, temporadaId);
        List<Object[]>      top5  = topArtilheiros(temporadaId, 5);
        Selecao             sel   = buscarSelecaoPorId(selecaoId);
        Temporada           tmp   = buscarTemporadaPorId(temporadaId);

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════╗\n");
        sb.append(  "║       RELATÓRIO DE DESEMPENHO            ║\n");
        sb.append(  "╠══════════════════════════════════════════╣\n");
        sb.append(String.format("  Seleção   : %s (%s)%n",
            sel  != null ? sel.getNome()       : "?",
            sel  != null ? sel.getCodigoFifa() : "?"));
        sb.append(String.format("  Temporada : %s%n",
            tmp != null ? tmp.getAno() : "?"));
        sb.append("──────────────────────────────────────────\n");

        if (stats.isEmpty()) {
            sb.append("  Sem dados para esta seleção/temporada.\n");
        } else {
            stats.forEach((k, v2) ->
                sb.append(String.format("  %-22s: %s%n", k.replace("_", " "), v2)));
        }

        sb.append("╠══════════════════════════════════════════╣\n");
        sb.append("║  TOP 5 ARTILHEIROS                       ║\n");
        sb.append("╚══════════════════════════════════════════╝\n");

        if (top5.isEmpty()) {
            sb.append("  Sem dados de artilharia.\n");
        } else {
            int rank = 1;
            for (Object[] row : top5) {
                sb.append(String.format("  %d. %-20s %2d gols | %2d assist | %d jogos%n",
                    rank++, row[1], row[3], row[4], row[7]));
            }
        }
        return sb.toString();
    }
}
