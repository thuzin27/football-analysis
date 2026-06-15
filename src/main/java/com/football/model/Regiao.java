package com.football.model;

public class Regiao {
    private int    id;
    private String nome;

    public Regiao() {}
    public Regiao(int id, String nome) {
        this.id   = id;
        this.nome = nome;
    }

    public int    getId()           { return id; }
    public String getNome()         { return nome; }
    public void   setId(int id)     { this.id = id; }
    public void   setNome(String n) { this.nome = n; }

    @Override
    public String toString() {
        return "Regiao{id=" + id + ", nome='" + nome + "'}";
    }
}
