package com.football.repository;

import com.football.config.DatabaseConfig;
import com.football.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Repositories {

    // --- Regiao ---

    public static class RegiaoRepository {

        public Regiao salvar(Regiao r) throws SQLException {
            String sql = """
                INSERT INTO regiao (nome)
                VALUES (?)
                ON CONFLICT (nome) DO UPDATE SET nome = EXCLUDED.nome
                RETURNING id
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, r.getNome());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) r.setId(rs.getInt("id"));
                }
            }
            return r;
        }

        public Regiao buscarPorId(int id) throws SQLException {
            String sql = "SELECT id, nome FROM regiao WHERE id = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new Regiao(rs.getInt("id"), rs.getString("nome"));
                }
            }
            return null;
        }
    }

    // --- Selecao ---

    public static class SelecaoRepository {

        public Selecao salvar(Selecao s) throws SQLException {
            String sel = "SELECT id FROM selecao WHERE nome = ? AND regiao_id = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setString(1, s.getNome());
                ps.setInt(2, s.getRegiaoId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { s.setId(rs.getInt("id")); return s; }
                }
            }
            String ins = "INSERT INTO selecao (nome, codigo_fifa, regiao_id) VALUES (?, ?, ?) RETURNING id";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(ins)) {
                ps.setString(1, s.getNome());
                ps.setString(2, s.getCodigoFifa());
                ps.setInt(3, s.getRegiaoId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) s.setId(rs.getInt("id"));
                }
            }
            return s;
        }

        public Selecao buscarPorId(int id) throws SQLException {
            String sql = "SELECT id, nome, codigo_fifa, regiao_id FROM selecao WHERE id = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapear(rs);
                }
            }
            return null;
        }

        public Selecao buscarPorCodigoFifa(String codigoFifa) throws SQLException {
            String sql = "SELECT id, nome, codigo_fifa, regiao_id FROM selecao WHERE codigo_fifa = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codigoFifa);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapear(rs);
                }
            }
            return null;
        }

        private Selecao mapear(ResultSet rs) throws SQLException {
            return new Selecao(
                rs.getInt("id"),
                rs.getString("nome"),
                rs.getString("codigo_fifa"),
                rs.getInt("regiao_id")
            );
        }
    }

    // --- Temporada ---

    public static class TemporadaRepository {

        public Temporada salvar(Temporada t) throws SQLException {
            String sel = "SELECT id FROM temporada WHERE ano = ? AND selecao_id = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setInt(1, t.getAno());
                ps.setInt(2, t.getSelecaoId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { t.setId(rs.getInt("id")); return t; }
                }
            }
            String ins = "INSERT INTO temporada (ano, selecao_id) VALUES (?, ?) RETURNING id";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(ins)) {
                ps.setInt(1, t.getAno());
                ps.setInt(2, t.getSelecaoId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) t.setId(rs.getInt("id"));
                }
            }
            return t;
        }
    }

    // --- Jogador ---

    public static class JogadorRepository {

        public Jogador salvar(Jogador j) throws SQLException {
            String sql = """
                INSERT INTO jogador (nome, posicao, data_nascimento)
                VALUES (?, ?, ?)
                RETURNING id
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, j.getNome());
                ps.setString(2, j.getPosicao());
                ps.setDate(3, j.getDataNascimento() != null
                    ? Date.valueOf(j.getDataNascimento()) : null);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) j.setId(rs.getInt("id"));
                }
            }
            return j;
        }

    }

    // --- Partida ---

    public static class PartidaRepository {

        public Partida salvar(Partida p) throws SQLException {
            String sql = """
                INSERT INTO partida (temporada_id, data_partida, adversario, competicao, gols_pro, gols_contra)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, p.getTemporadaId());
                ps.setDate(2, p.getDataPartida() != null
                    ? Date.valueOf(p.getDataPartida()) : null);
                ps.setString(3, p.getAdversario());
                ps.setString(4, p.getCompeticao());
                ps.setInt(5, p.getGolsPro());
                ps.setInt(6, p.getGolsContra());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) p.setId(rs.getInt("id"));
                }
            }
            return p;
        }

        // Remove partidas e estatísticas/partida_jogador vinculadas — usado no re-sync
        public int deletarPorTemporada(int temporadaId) throws SQLException {
            try (Connection conn = DatabaseConfig.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        DELETE FROM estatistica
                        WHERE partida_jogador_id IN (
                            SELECT pj.id FROM partida_jogador pj
                            JOIN partida p ON p.id = pj.partida_id
                            WHERE p.temporada_id = ?)""")) {
                    ps.setInt(1, temporadaId); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("""
                        DELETE FROM partida_jogador
                        WHERE partida_id IN (SELECT id FROM partida WHERE temporada_id = ?)""")) {
                    ps.setInt(1, temporadaId); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM partida WHERE temporada_id = ?")) {
                    ps.setInt(1, temporadaId);
                    return ps.executeUpdate();
                }
            }
        }

        // multi-temporada, em ordem cronológica
        public List<Partida> listarPorSelecaoOrdenado(int selecaoId) throws SQLException {
            List<Partida> lista = new ArrayList<>();
            String sql = """
                SELECT p.* FROM partida p
                JOIN temporada t ON t.id = p.temporada_id
                WHERE t.selecao_id = ?
                ORDER BY p.data_partida ASC, p.id ASC
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, selecaoId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) lista.add(mapear(rs));
                }
            }
            return lista;
        }

        private Partida mapear(ResultSet rs) throws SQLException {
            Date d = rs.getDate("data_partida");
            return new Partida(
                rs.getInt("id"),
                rs.getInt("temporada_id"),
                d != null ? d.toLocalDate() : null,
                rs.getString("adversario"),
                rs.getString("competicao"),
                rs.getInt("gols_pro"),
                rs.getInt("gols_contra")
            );
        }
    }

    // --- PartidaJogador ---

    public static class PartidaJogadorRepository {

        public PartidaJogador salvar(PartidaJogador pj) throws SQLException {
            String sql = """
                INSERT INTO partida_jogador (partida_id, jogador_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                RETURNING id
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pj.getPartidaId());
                ps.setInt(2, pj.getJogadorId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) pj.setId(rs.getInt("id"));
                }
            }
            return pj;
        }
    }

    // --- Estatistica ---

    public static class EstatisticaRepository {

        public Estatistica salvar(Estatistica e) throws SQLException {
            String sql = """
                INSERT INTO estatistica (partida_jogador_id, minutos, gols, assistencias, amarelos, vermelhos)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                RETURNING id
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, e.getPartidaJogadorId());
                ps.setInt(2, e.getMinutos());
                ps.setInt(3, e.getGols());
                ps.setInt(4, e.getAssistencias());
                ps.setInt(5, e.getAmarelos());
                ps.setInt(6, e.getVermelhos());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) e.setId(rs.getInt("id"));
                }
            }
            return e;
        }
    }
}
