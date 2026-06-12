package com.football.model;

/**
 * ESTATISTICA — estatísticas individuais de um jogador em uma partida
 * Tabela: estatistica (id, partida_jogador_id, minutos, gols, assistencias, amarelos, vermelhos)
 *
 * Ligação: estatistica → partida_jogador → (partida, jogador)
 */
public class Estatistica {
    private int id;
    private int partidaJogadorId;   // FK → partida_jogador.id
    private int minutos;
    private int gols;
    private int assistencias;
    private int amarelos;
    private int vermelhos;

    public Estatistica() {}
    public Estatistica(int id, int partidaJogadorId, int minutos,
                       int gols, int assistencias, int amarelos, int vermelhos) {
        this.id                = id;
        this.partidaJogadorId  = partidaJogadorId;
        this.minutos           = minutos;
        this.gols              = gols;
        this.assistencias      = assistencias;
        this.amarelos          = amarelos;
        this.vermelhos         = vermelhos;
    }

    public int getId()               { return id; }
    public int getPartidaJogadorId() { return partidaJogadorId; }
    public int getMinutos()          { return minutos; }
    public int getGols()             { return gols; }
    public int getAssistencias()     { return assistencias; }
    public int getAmarelos()         { return amarelos; }
    public int getVermelhos()        { return vermelhos; }

    public void setId(int id)                       { this.id = id; }
    public void setPartidaJogadorId(int p)          { this.partidaJogadorId = p; }
    public void setMinutos(int m)                   { this.minutos = m; }
    public void setGols(int g)                      { this.gols = g; }
    public void setAssistencias(int a)              { this.assistencias = a; }
    public void setAmarelos(int a)                  { this.amarelos = a; }
    public void setVermelhos(int v)                 { this.vermelhos = v; }

    @Override
    public String toString() {
        return "Estatistica{pjId=" + partidaJogadorId + ", gols=" + gols
               + ", assist=" + assistencias + ", min=" + minutos
               + ", amarelos=" + amarelos + ", vermelhos=" + vermelhos + "}";
    }
}
