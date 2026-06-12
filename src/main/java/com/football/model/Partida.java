package com.football.model;

import java.time.LocalDate;

/**
 * PARTIDA — filho de Temporada (folha da árvore)
 * Tabela: partida (id, temporada_id, data_partida, adversario, competicao, gols_pro, gols_contra)
 *
 * Estrutura focada na seleção principal:
 *   gols_pro     = gols marcados pela seleção
 *   gols_contra  = gols sofridos pela seleção
 *   adversario   = nome do time adversário
 *   competicao   = ex: "Copa América", "Eliminatórias"
 */
public class Partida {
    private int       id;
    private int       temporadaId;
    private LocalDate dataPartida;
    private String    adversario;
    private String    competicao;
    private int       golsPro;
    private int       golsContra;

    public Partida() {}
    public Partida(int id, int temporadaId, LocalDate dataPartida,
                   String adversario, String competicao, int golsPro, int golsContra) {
        this.id          = id;
        this.temporadaId = temporadaId;
        this.dataPartida = dataPartida;
        this.adversario  = adversario;
        this.competicao  = competicao;
        this.golsPro     = golsPro;
        this.golsContra  = golsContra;
    }

    public int       getId()          { return id; }
    public int       getTemporadaId() { return temporadaId; }
    public LocalDate getDataPartida() { return dataPartida; }
    public String    getAdversario()  { return adversario; }
    public String    getCompeticao()  { return competicao; }
    public int       getGolsPro()     { return golsPro; }
    public int       getGolsContra()  { return golsContra; }

    /** Resultado: "V", "E" ou "D" */
    public String getResultado() {
        if (golsPro > golsContra) return "V";
        if (golsPro < golsContra) return "D";
        return "E";
    }

    public void setId(int id)                   { this.id = id; }
    public void setTemporadaId(int t)           { this.temporadaId = t; }
    public void setDataPartida(LocalDate d)     { this.dataPartida = d; }
    public void setAdversario(String a)         { this.adversario = a; }
    public void setCompeticao(String c)         { this.competicao = c; }
    public void setGolsPro(int g)               { this.golsPro = g; }
    public void setGolsContra(int g)            { this.golsContra = g; }

    @Override
    public String toString() {
        return "Partida{id=" + id + ", vs='" + adversario + "', "
               + golsPro + "x" + golsContra + " (" + getResultado() + ")"
               + ", comp='" + competicao + "'}";
    }
}
