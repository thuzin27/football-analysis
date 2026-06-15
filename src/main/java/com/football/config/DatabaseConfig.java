package com.football.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConfig {

    private static HikariDataSource dataSource;

    static {
        try {
            // System.getenv é verificado PRIMEIRO — garante que a DATABASE_URL
            // injetada pelo Render seja lida antes do dotenv (apenas para dev local).
            String dbUrl = System.getenv("DATABASE_URL");

            // Fallback: dotenv para desenvolvimento local com .env
            Dotenv dotenv = null;
            if (dbUrl == null || dbUrl.isBlank()) {
                dotenv = Dotenv.configure().ignoreIfMissing().load();
                dbUrl = dotenv.get("DATABASE_URL");
            }

            HikariConfig config = new HikariConfig();

            if (dbUrl != null && !dbUrl.isBlank()) {
                // Render injeta DATABASE_URL no formato postgres://user:pass@host:port/db
                System.out.println("[DB] Usando DATABASE_URL (Render/produção)");
                String normalized = dbUrl.replaceFirst("^postgres://", "postgresql://");
                java.net.URI uri = new java.net.URI(normalized);
                String host     = uri.getHost();
                int    port     = uri.getPort() > 0 ? uri.getPort() : 5432;
                String dbName   = uri.getPath().replaceFirst("^/", "");
                String[] ui     = uri.getUserInfo() != null
                                  ? uri.getUserInfo().split(":", 2)
                                  : new String[]{"", ""};
                String user     = java.net.URLDecoder.decode(
                                      ui[0], java.nio.charset.StandardCharsets.UTF_8);
                String password = ui.length > 1
                                  ? java.net.URLDecoder.decode(
                                        ui[1], java.nio.charset.StandardCharsets.UTF_8)
                                  : "";
                // Preserva query params da URL (ex: ?sslmode=require)
                String query    = uri.getQuery() != null ? "?" + uri.getQuery() : "";

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + query;
                System.out.println("[DB] JDBC: " + jdbcUrl);

                config.setJdbcUrl(jdbcUrl);
                config.setUsername(user);
                config.setPassword(password);

            } else {
                // Dev local: variáveis separadas do .env ou defaults
                System.out.println("[DB] DATABASE_URL ausente — usando variáveis separadas (dev local)");
                if (dotenv == null) dotenv = Dotenv.configure().ignoreIfMissing().load();

                String host     = getEnv(dotenv, "DB_HOST",     "localhost");
                String port     = getEnv(dotenv, "DB_PORT",     "5432");
                String dbName   = getEnv(dotenv, "DB_NAME",     "DataKick");
                String user     = getEnv(dotenv, "DB_USER",     "postgres");
                String password = getEnv(dotenv, "DB_PASSWORD", "");

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
                System.out.println("[DB] JDBC: " + jdbcUrl);

                config.setJdbcUrl(jdbcUrl);
                config.setUsername(user);
                config.setPassword(password);
            }

            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30_000);
            config.setIdleTimeout(600_000);
            config.setMaxLifetime(1_800_000);
            config.setPoolName("FootballPool");

            config.addDataSourceProperty("cachePrepStmts",       "true");
            config.addDataSourceProperty("prepStmtCacheSize",     "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts",    "true");

            dataSource = new HikariDataSource(config);
            System.out.println("[DB] Pool de conexões inicializado com sucesso.");

        } catch (Exception e) {
            throw new RuntimeException(
                "Falha ao inicializar o pool de conexões: " + e.getMessage(), e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DB] Pool de conexões encerrado.");
        }
    }

    // System.getenv tem prioridade sobre dotenv — Render injeta no ambiente do processo
    private static String getEnv(Dotenv dotenv, String key, String defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) val = dotenv.get(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
