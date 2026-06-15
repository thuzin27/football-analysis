package com.football.algorithm;

import com.football.model.Partida;
import java.util.List;

/**
 * DataKick Predictor (DKP) — pipeline de 3 camadas:
 *
 *   1. Exponential Decay Weighting — peso w_i = e^(-λ·(n-1-i)); jogos recentes têm mais influência.
 *   2. Markov Chain (ordem 1) — matriz de transição 3×3 (V, E, D) captura momentum.
 *   3. Bayesian Blend — P_final = α·P_markov + (1-α)·P_historico_ponderado.
 *
 * Gols esperados via Poisson implícita: λ = média ponderada de gols.
 */
public class PredictionAlgorithm {

    private static final double LAMBDA_DECAY = 0.18;  // velocidade de decaimento exponencial
    private static final double ALPHA_MARKOV = 0.38;  // peso da cadeia de Markov vs prior

    public static class Predicao {
        public double probVitoria;
        public double probEmpate;
        public double probDerrota;
        public double golsEsperados;
        public double golsSofridosEsperados;
        public double confianca;              // 0..1, cresce com n amostras
        public double aproveitamentoRecente;  // últimos 5 jogos, 0..1
        public String ultimoResultado;
        public int    totalPartidas;
    }

    public static Predicao prever(List<Partida> historico) {
        if (historico == null || historico.isEmpty())
            throw new IllegalArgumentException("Histórico vazio — sincronize dados primeiro.");

        int n = historico.size();

        // 1. pesos por decaimento exponencial
        double[] pesos = new double[n];
        double somaPesos = 0;
        for (int i = 0; i < n; i++) {
            pesos[i] = Math.exp(-LAMBDA_DECAY * (n - 1 - i));
            somaPesos += pesos[i];
        }
        for (int i = 0; i < n; i++) pesos[i] /= somaPesos;

        // 2. prior histórico ponderado
        double hV = 0, hE = 0, hD = 0;
        double lambdaPro = 0, lambdaContra = 0;
        for (int i = 0; i < n; i++) {
            Partida p = historico.get(i);
            switch (p.getResultado()) {
                case "V" -> hV += pesos[i];
                case "E" -> hE += pesos[i];
                case "D" -> hD += pesos[i];
            }
            lambdaPro    += pesos[i] * p.getGolsPro();
            lambdaContra += pesos[i] * p.getGolsContra();
        }

        // 3. Markov Chain — matriz de transição 3×3 (índice: 0=V, 1=E, 2=D)
        double[][] T    = new double[3][3];
        int[]      freq = new int[3];
        for (int i = 0; i < n - 1; i++) {
            int r0 = idx(historico.get(i).getResultado());
            int r1 = idx(historico.get(i + 1).getResultado());
            if (r0 >= 0 && r1 >= 0) { T[r0][r1]++; freq[r0]++; }
        }
        for (int i = 0; i < 3; i++) {
            if (freq[i] > 0) {
                for (int j = 0; j < 3; j++) T[i][j] /= freq[i];
            } else {
                T[i][0] = T[i][1] = T[i][2] = 1.0 / 3.0;
            }
        }

        // 4. estado atual → vetor Markov
        int estadoAtual = idx(historico.get(n - 1).getResultado());
        double[] mV_mE_mD = (estadoAtual >= 0)
            ? T[estadoAtual]
            : new double[]{1.0/3, 1.0/3, 1.0/3};

        // 5. Bayesian Blend
        double pV = ALPHA_MARKOV * mV_mE_mD[0] + (1 - ALPHA_MARKOV) * hV;
        double pE = ALPHA_MARKOV * mV_mE_mD[1] + (1 - ALPHA_MARKOV) * hE;
        double pD = ALPHA_MARKOV * mV_mE_mD[2] + (1 - ALPHA_MARKOV) * hD;
        double soma = pV + pE + pD;
        pV /= soma; pE /= soma; pD /= soma;

        // 6. aproveitamento recente (últimos 5 jogos)
        int ultimos = Math.min(5, n);
        double pts = 0;
        for (int i = n - ultimos; i < n; i++) {
            pts += switch (historico.get(i).getResultado()) {
                case "V" -> 3;
                case "E" -> 1;
                default  -> 0;
            };
        }

        // 7. confiança: cresce com n, satura em 1
        double confianca = 1.0 - Math.exp(-0.12 * n);

        Predicao pred = new Predicao();
        pred.probVitoria           = pV;
        pred.probEmpate            = pE;
        pred.probDerrota           = pD;
        pred.golsEsperados         = lambdaPro;
        pred.golsSofridosEsperados = lambdaContra;
        pred.confianca             = confianca;
        pred.aproveitamentoRecente = pts / (ultimos * 3.0);
        pred.ultimoResultado       = historico.get(n - 1).getResultado();
        pred.totalPartidas         = n;
        return pred;
    }

    private static int idx(String r) {
        return switch (r) {
            case "V" -> 0;
            case "E" -> 1;
            case "D" -> 2;
            default  -> -1;
        };
    }
}
