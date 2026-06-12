package com.football.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConfig
 * ---------------------------------------------------------------
 * Gerencia o pool de conexões com o PostgreSQL via HikariCP.
 *
 * Variáveis necessárias no arquivo .env (ou variáveis de ambiente):
 *   DB_HOST     → ex: localhost
 *   DB_PORT     → ex: 5432
 *   DB_NAME     → ex: football_db
 *   DB_USER     → ex: postgres
 *   DB_PASSWORD → ex: sua_senha
 * ---------------------------------------------------------------
 */
public class DatabaseConfig {

    private static HikariDataSource dataSource;

    static {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

            HikariConfig config = new HikariConfig();

            String dbUrl = getEnv(dotenv, "DATABASE_URL", null);
            if (dbUrl != null && !dbUrl.isBlank()) {
                // Render injeta DATABASE_URL no formato postgres://user:pass@host:port/db
                String normalized = dbUrl.replaceFirst("^postgres://", "postgresql://");
                java.net.URI uri  = new java.net.URI(normalized);
                String host       = uri.getHost();
                int    port       = uri.getPort() > 0 ? uri.getPort() : 5432;
                String dbName     = uri.getPath().replaceFirst("^/", "");
                String[] ui       = uri.getUserInfo() != null
                                    ? uri.getUserInfo().split(":", 2)
                                    : new String[]{"", ""};
                String user       = java.net.URLDecoder.decode(ui[0], java.nio.charset.StandardCharsets.UTF_8);
                String password   = ui.length > 1
                                    ? java.net.URLDecoder.decode(ui[1], java.nio.charset.StandardCharsets.UTF_8)
                                    : "";
                // Preserva query params da URL (ex: ?sslmode=require)
                String query      = uri.getQuery() != null ? "?" + uri.getQuery() : "";

                config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbName + query);
                config.setUsername(user);
                config.setPassword(password);
            } else {
                // Dev local: variáveis separadas do .env
                String host     = getEnv(dotenv, "DB_HOST",     "localhost");
                String port     = getEnv(dotenv, "DB_PORT",     "5432");
                String dbName   = getEnv(dotenv, "DB_NAME",     "DataKick");
                String user     = getEnv(dotenv, "DB_USER",     "postgres");
                String password = getEnv(dotenv, "DB_PASSWORD", "");

                config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbName);
                config.setUsername(user);
                config.setPassword(password);
            }

            // Pool settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30_000);   // 30s
            config.setIdleTimeout(600_000);        // 10min
            config.setMaxLifetime(1_800_000);      // 30min
            config.setPoolName("FootballPool");

            // Performance
            config.addDataSourceProperty("cachePrepStmts",          "true");
            config.addDataSourceProperty("prepStmtCacheSize",        "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit",    "2048");
            config.addDataSourceProperty("useServerPrepStmts",       "true");

            dataSource = new HikariDataSource(config);
            System.out.println("[DB] Pool de conexões inicializado com sucesso.");

        } catch (Exception e) {
            throw new RuntimeException("Falha ao inicializar o pool de conexões: " + e.getMessage(), e);
        }
    }

    /** Retorna uma conexão do pool. Lembre-se de fechar com try-with-resources. */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Fecha o pool ao encerrar a aplicação. */
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DB] Pool de conexões encerrado.");
        }
    }

    /** Helper: lê variável do dotenv, fallback para System.getenv, depois para o default. */
    private static String getEnv(Dotenv dotenv, String key, String defaultValue) {
        String val = dotenv.get(key);
        if (val == null) val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
