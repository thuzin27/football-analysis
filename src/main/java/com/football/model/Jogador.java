package com.football.model;

import java.time.LocalDate;
import java.time.Period;

/**
 * JOGADOR — filho de Selecao
 * Tabela: jogador (id, nome, posicao, data_nascimento)
 *
 * Nota: sem selecao_id direto na tabela — a ligação é feita
 * via partida_jogador → partida → temporada → selecao
 */
public class Jogador {
    private int       id;
    private String    nome;
    private String    posicao;          // Goleiro, Defensor, Meio-campista, Atacante
    private LocalDate dataNascimento;

    public Jogador() {}
    public Jogador(int id, String nome, String posicao, LocalDate dataNascimento) {
        this.id              = id;
        this.nome            = nome;
        this.posicao         = posicao;
        this.dataNascimento  = dataNascimento;
    }

    public int       getId()             { return id; }
    public String    getNome()           { return nome; }
    public String    getPosicao()        { return posicao; }
    public LocalDate getDataNascimento() { return dataNascimento; }

    /** Calcula a idade atual com base na data de nascimento */
    public int getIdade() {
        if (dataNascimento == null) return 0;
        return Period.between(dataNascimento, LocalDate.now()).getYears();
    }

    public void setId(int id)                        { this.id = id; }
    public void setNome(String nome)                 { this.nome = nome; }
    public void setPosicao(String posicao)           { this.posicao = posicao; }
    public void setDataNascimento(LocalDate d)       { this.dataNascimento = d; }

    @Override
    public String toString() {
        return "Jogador{id=" + id + ", nome='" + nome + "', posicao='" + posicao
               + "', idade=" + getIdade() + "}";
    }
}
