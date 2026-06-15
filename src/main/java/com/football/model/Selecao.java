package com.football.model;

public class Selecao {
    private int    id;
    private String nome;
    private String codigoFifa;
    private int    regiaoId;

    public Selecao() {}
    public Selecao(int id, String nome, String codigoFifa, int regiaoId) {
        this.id         = id;
        this.nome       = nome;
        this.codigoFifa = codigoFifa;
        this.regiaoId   = regiaoId;
    }

    public int    getId()         { return id; }
    public String getNome()       { return nome; }
    public String getCodigoFifa() { return codigoFifa; }
    public int    getRegiaoId()   { return regiaoId; }

    public void setId(int id)             { this.id = id; }
    public void setNome(String nome)      { this.nome = nome; }
    public void setCodigoFifa(String c)   { this.codigoFifa = c; }
    public void setRegiaoId(int regiaoId) { this.regiaoId = regiaoId; }

    @Override
    public String toString() {
        return "Selecao{id=" + id + ", nome='" + nome + "', fifa='" + codigoFifa + "'}";
    }
}
