package com.football.model;

/**
 * PARTIDA_JOGADOR — tabela de junção partida ↔ jogador
 * Tabela: partida_jogador (id, partida_id, jogador_id)
 */
public class PartidaJogador {
    private int id;
    private int partidaId;
    private int jogadorId;

    public PartidaJogador() {}
    public PartidaJogador(int id, int partidaId, int jogadorId) {
        this.id        = id;
        this.partidaId = partidaId;
        this.jogadorId = jogadorId;
    }

    public int getId()        { return id; }
    public int getPartidaId() { return partidaId; }
    public int getJogadorId() { return jogadorId; }

    public void setId(int id)           { this.id = id; }
    public void setPartidaId(int p)     { this.partidaId = p; }
    public void setJogadorId(int j)     { this.jogadorId = j; }

    @Override
    public String toString() {
        return "PartidaJogador{id=" + id + ", partidaId=" + partidaId
               + ", jogadorId=" + jogadorId + "}";
    }
}
