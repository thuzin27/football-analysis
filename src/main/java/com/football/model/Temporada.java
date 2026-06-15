package com.football.model;

public class Temporada {
    private int id;
    private int ano;
    private int selecaoId;

    public Temporada() {}
    public Temporada(int id, int ano, int selecaoId) {
        this.id        = id;
        this.ano       = ano;
        this.selecaoId = selecaoId;
    }

    public int getId()        { return id; }
    public int getAno()       { return ano; }
    public int getSelecaoId() { return selecaoId; }

    public void setId(int id)        { this.id = id; }
    public void setAno(int ano)      { this.ano = ano; }
    public void setSelecaoId(int s)  { this.selecaoId = s; }

    @Override
    public String toString() {
        return "Temporada{id=" + id + ", ano=" + ano + ", selecaoId=" + selecaoId + "}";
    }
}
