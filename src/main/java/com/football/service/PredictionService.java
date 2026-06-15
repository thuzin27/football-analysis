package com.football.service;

import com.football.algorithm.PredictionAlgorithm;
import com.football.algorithm.PredictionAlgorithm.Predicao;
import com.football.model.Partida;
import com.football.model.Selecao;
import com.football.repository.Repositories.*;

import java.sql.SQLException;
import java.util.List;

public class PredictionService {

    private final PartidaRepository      partidaRepo;
    private final SelecaoRepository      selecaoRepo;
    private final EstatisticaRepository  estatRepo;

    public PredictionService() {
        this.partidaRepo = new PartidaRepository();
        this.selecaoRepo = new SelecaoRepository();
        this.estatRepo   = new EstatisticaRepository();
    }

    public String gerarAnaliseMultiAno(int selecaoId, int top) throws SQLException {
        Selecao sel = selecaoRepo.buscarPorId(selecaoId);
        List<Partida> historico = partidaRepo.listarPorSelecaoOrdenado(selecaoId);

        if (historico.isEmpty())
            return "  Sem partidas. Sincronize dados primeiro (opção 1).";

        String anoInicio = historico.get(0).getDataPartida() != null
            ? String.valueOf(historico.get(0).getDataPartida().getYear()) : "?";
        String anoFim = historico.get(historico.size()-1).getDataPartida() != null
            ? String.valueOf(historico.get(historico.size()-1).getDataPartida().getYear()) : "?";

        Predicao pred = PredictionAlgorithm.prever(historico);
        List<Object[]> artilheiros = estatRepo.rankingArtilheirosSelecao(selecaoId, top);

        StringBuilder sb = new StringBuilder();

        sb.append("\n╔══════════════════════════════════════════════╗\n");
        sb.append(  "║   DKP — Análise Multi-Ano                    ║\n");
        sb.append(String.format("║   %s (%s)  %s–%s%-14s║%n",
            sel != null ? sel.getNome() : "?",
            sel != null ? sel.getCodigoFifa() : "?",
            anoInicio, anoFim, ""));
        sb.append(  "╠══════════════════════════════════════════════╣\n");
        sb.append(String.format("  Partidas analisadas: %d%n", pred.totalPartidas));
        sb.append(String.format("  Último resultado   : %s%n", pred.ultimoResultado));
        sb.append(String.format("  Forma recente (5j) : %.0f%%%n", pred.aproveitamentoRecente * 100));
        sb.append(String.format("  Confiança do modelo: %.0f%%%n", pred.confianca * 100));

        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append("  HISTÓRICO COMPLETO\n");
        sb.append("  " + "─".repeat(54) + "\n");
        sb.append(String.format("  %-12s %-22s %-8s %s%n", "Data", "Adversário", "Placar", "Res."));
        sb.append("  " + "─".repeat(54) + "\n");

        String anoAtual = "";
        for (Partida p : historico) {
            String ano = p.getDataPartida() != null
                ? String.valueOf(p.getDataPartida().getYear()) : "?";
            if (!ano.equals(anoAtual)) {
                sb.append(String.format("  ── %s ──%n", ano));
                anoAtual = ano;
            }
            sb.append(String.format("  %-12s %-22s %2d x %-2d  %s%n",
                p.getDataPartida() != null ? p.getDataPartida().toString() : "-",
                truncar(p.getAdversario(), 22),
                p.getGolsPro(), p.getGolsContra(),
                iconeResultado(p.getResultado())));
        }

        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append("  PREDIÇÃO — PRÓXIMO JOGO\n");
        sb.append("  " + "─".repeat(54) + "\n");
        sb.append(String.format("  P(Vitória) : %5.1f%%  %s%n",
            pred.probVitoria * 100, barra(pred.probVitoria, 24)));
        sb.append(String.format("  P(Empate)  : %5.1f%%  %s%n",
            pred.probEmpate * 100, barra(pred.probEmpate, 24)));
        sb.append(String.format("  P(Derrota) : %5.1f%%  %s%n",
            pred.probDerrota * 100, barra(pred.probDerrota, 24)));
        sb.append(String.format("%n  Gols esperados : %.2f marcados | %.2f sofridos%n",
            pred.golsEsperados, pred.golsSofridosEsperados));
        sb.append(String.format("  Veredicto      : %s%n", veredicto(pred)));

        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append(String.format("  TOP %d ARTILHEIROS (%s–%s)%n", top, anoInicio, anoFim));
        sb.append("  " + "─".repeat(54) + "\n");
        if (artilheiros.isEmpty()) {
            sb.append("  Sem dados de artilharia — jogadores não sincronizados.\n");
            sb.append("  Aguarde reset da cota da API e sincronize novamente.\n");
        } else {
            sb.append(String.format("  %-4s %-22s %-13s %5s %6s %5s%n",
                "Pos", "Nome", "Posição", "Gols", "Assist", "Jogos"));
            sb.append("  " + "─".repeat(54) + "\n");
            int rank = 1;
            for (Object[] r : artilheiros) {
                sb.append(String.format("  %-4d %-22s %-13s %5d %6d %5d%n",
                    rank++, r[1], r[2], r[3], r[4], r[7]));
            }
        }
        sb.append("╚══════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    private String barra(double valor, int tamanho) {
        int cheias = Math.max(0, Math.min((int) Math.round(valor * tamanho), tamanho));
        return "█".repeat(cheias) + "░".repeat(tamanho - cheias);
    }

    private String iconeResultado(String r) {
        return switch (r) {
            case "V" -> "V  ✔";
            case "E" -> "E  ─";
            case "D" -> "D  ✘";
            default  -> r;
        };
    }

    private String truncar(String s, int max) {
        if (s == null) return "-";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String veredicto(Predicao pred) {
        if (pred.totalPartidas < 3)
            return "AMOSTRA INSUFICIENTE — sincronize mais dados";
        double max = Math.max(pred.probVitoria, Math.max(pred.probEmpate, pred.probDerrota));
        if (max == pred.probVitoria && pred.probVitoria >= 0.50)
            return "FAVORITO A VENCER (" + fmt(pred.probVitoria) + ")";
        if (max == pred.probDerrota && pred.probDerrota >= 0.45)
            return "DESFAVORECIDO (" + fmt(pred.probDerrota) + " chance de derrota)";
        if (max == pred.probEmpate)
            return "TENDÊNCIA A EMPATE (" + fmt(pred.probEmpate) + ")";
        return "JOGO EQUILIBRADO — ligeira vantagem de " + fmt(max);
    }

    private String fmt(double v) {
        return String.format("%.1f%%", v * 100);
    }
}
