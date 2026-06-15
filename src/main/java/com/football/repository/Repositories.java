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

        public List<Regiao> listarTodas() throws SQLException {
            List<Regiao> lista = new ArrayList<>();
            String sql = "SELECT id, nome FROM regiao ORDER BY id";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    lista.add(new Regiao(rs.getInt("id"), rs.getString("nome")));
            }
            return lista;
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

        public List<Selecao> listarPorRegiao(int regiaoId) throws SQLException {
            List<Selecao> lista = new ArrayList<>();
            String sql = "SELECT id, nome, codigo_fifa, regiao_id FROM selecao WHERE regiao_id = ? ORDER BY nome";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, regiaoId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) lista.add(mapear(rs));
                }
            }
            return lista;
        }

        public List<Selecao> listarTodasOrdenado() throws SQLException {
            List<Selecao> lista = new ArrayList<>();
            String sql = "SELECT id, nome, codigo_fifa, regiao_id FROM selecao ORDER BY id ASC";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
            return lista;
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

        public List<Selecao> buscarPorNome(String nome) throws SQLException {
            List<Selecao> lista = new ArrayList<>();
            String sql = "SELECT id, nome, codigo_fifa, regiao_id FROM selecao WHERE LOWER(nome) LIKE LOWER(?) ORDER BY nome";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "%" + nome + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) lista.add(mapear(rs));
                }
            }
            return lista;
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

        public List<Temporada> listarPorSelecao(int selecaoId) throws SQLException {
            List<Temporada> lista = new ArrayList<>();
            String sql = "SELECT id, ano, selecao_id FROM temporada WHERE selecao_id = ? ORDER BY ano DESC";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, selecaoId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        lista.add(new Temporada(rs.getInt("id"), rs.getInt("ano"), rs.getInt("selecao_id")));
                }
            }
            return lista;
        }

        public List<Temporada> listarTodasOrdenado() throws SQLException {
            List<Temporada> lista = new ArrayList<>();
            String sql = "SELECT id, ano, selecao_id FROM temporada ORDER BY id ASC";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    lista.add(new Temporada(rs.getInt("id"), rs.getInt("ano"), rs.getInt("selecao_id")));
            }
            return lista;
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

        // ordenada por id ASC — pronta para InterpolationSearch
        public List<Jogador> listarTodosOrdenado() throws SQLException {
            List<Jogador> lista = new ArrayList<>();
            String sql = "SELECT id, nome, posicao, data_nascimento FROM jogador ORDER BY id ASC";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
            return lista;
        }

        public Jogador buscarPorId(int id) throws SQLException {
            String sql = "SELECT id, nome, posicao, data_nascimento FROM jogador WHERE id = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapear(rs);
                }
            }
            return null;
        }

        private Jogador mapear(ResultSet rs) throws SQLException {
            Date d = rs.getDate("data_nascimento");
            return new Jogador(
                rs.getInt("id"),
                rs.getString("nome"),
                rs.getString("posicao"),
                d != null ? d.toLocalDate() : null
            );
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

        // ordenada por id ASC — pronta para InterpolationSearch
        public List<Partida> listarPorTemporadaOrdenado(int temporadaId) throws SQLException {
            List<Partida> lista = new ArrayList<>();
            String sql = "SELECT * FROM partida WHERE temporada_id = ? ORDER BY id ASC";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, temporadaId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) lista.add(mapear(rs));
                }
            }
            return lista;
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

        public List<Partida> listarTodasOrdenado() throws SQLException {
            List<Partida> lista = new ArrayList<>();
            String sql = "SELECT * FROM partida ORDER BY id ASC";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
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

        public List<PartidaJogador> listarPorPartida(int partidaId) throws SQLException {
            List<PartidaJogador> lista = new ArrayList<>();
            String sql = "SELECT id, partida_id, jogador_id FROM partida_jogador WHERE partida_id = ? ORDER BY id ASC";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, partidaId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        lista.add(new PartidaJogador(
                            rs.getInt("id"), rs.getInt("partida_id"), rs.getInt("jogador_id")));
                }
            }
            return lista;
        }

        public List<PartidaJogador> listarPorJogador(int jogadorId) throws SQLException {
            List<PartidaJogador> lista = new ArrayList<>();
            String sql = "SELECT id, partida_id, jogador_id FROM partida_jogador WHERE jogador_id = ? ORDER BY id ASC";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jogadorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        lista.add(new PartidaJogador(
                            rs.getInt("id"), rs.getInt("partida_id"), rs.getInt("jogador_id")));
                }
            }
            return lista;
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

        public Estatistica buscarPorPartidaJogador(int partidaJogadorId) throws SQLException {
            String sql = "SELECT * FROM estatistica WHERE partida_jogador_id = ?";
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, partidaJogadorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapear(rs);
                }
            }
            return null;
        }

        public List<Object[]> rankingArtilheiros(int temporadaId, int limite) throws SQLException {
            List<Object[]> lista = new ArrayList<>();
            String sql = """
                SELECT j.id, j.nome, j.posicao,
                       SUM(e.gols)         AS total_gols,
                       SUM(e.assistencias) AS total_assist,
                       SUM(e.minutos)      AS total_minutos,
                       SUM(e.amarelos)     AS total_amarelos,
                       COUNT(pj.id)        AS total_jogos
                FROM   estatistica e
                JOIN   partida_jogador pj ON pj.id    = e.partida_jogador_id
                JOIN   jogador         j  ON j.id     = pj.jogador_id
                JOIN   partida         p  ON p.id     = pj.partida_id
                WHERE  p.temporada_id = ?
                GROUP  BY j.id, j.nome, j.posicao
                ORDER  BY total_gols DESC, total_assist DESC
                LIMIT  ?
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, temporadaId);
                ps.setInt(2, limite);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(new Object[]{
                            rs.getInt("id"),
                            rs.getString("nome"),
                            rs.getString("posicao"),
                            rs.getInt("total_gols"),
                            rs.getInt("total_assist"),
                            rs.getInt("total_minutos"),
                            rs.getInt("total_amarelos"),
                            rs.getInt("total_jogos")
                        });
                    }
                }
            }
            return lista;
        }

        // ranking de artilheiros de uma seleção em TODOS os anos sincronizados
        public List<Object[]> rankingArtilheirosSelecao(int selecaoId, int limite) throws SQLException {
            List<Object[]> lista = new ArrayList<>();
            String sql = """
                SELECT j.id, j.nome, j.posicao,
                       SUM(e.gols)         AS total_gols,
                       SUM(e.assistencias) AS total_assist,
                       SUM(e.minutos)      AS total_minutos,
                       SUM(e.amarelos)     AS total_amarelos,
                       COUNT(pj.id)        AS total_jogos
                FROM   estatistica e
                JOIN   partida_jogador pj ON pj.id = e.partida_jogador_id
                JOIN   jogador         j  ON j.id  = pj.jogador_id
                JOIN   partida         p  ON p.id  = pj.partida_id
                JOIN   temporada       t  ON t.id  = p.temporada_id
                WHERE  t.selecao_id = ?
                GROUP  BY j.id, j.nome, j.posicao
                ORDER  BY total_gols DESC, total_assist DESC
                LIMIT  ?
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, selecaoId);
                ps.setInt(2, limite);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(new Object[]{
                            rs.getInt("id"), rs.getString("nome"), rs.getString("posicao"),
                            rs.getInt("total_gols"), rs.getInt("total_assist"),
                            rs.getInt("total_minutos"), rs.getInt("total_amarelos"),
                            rs.getInt("total_jogos")
                        });
                    }
                }
            }
            return lista;
        }

        private Estatistica mapear(ResultSet rs) throws SQLException {
            return new Estatistica(
                rs.getInt("id"),
                rs.getInt("partida_jogador_id"),
                rs.getInt("minutos"),
                rs.getInt("gols"),
                rs.getInt("assistencias"),
                rs.getInt("amarelos"),
                rs.getInt("vermelhos")
            );
        }
    }
}
