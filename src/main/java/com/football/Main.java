package com.football;

import com.football.config.DatabaseConfig;
import com.football.model.*;
import com.football.service.AnalyticsService;
import com.football.service.DataSyncService;
import com.football.web.WebServer;

import com.football.api.ApiFootballClient;
import com.football.service.PredictionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {

    private static final DataSyncService   sync       = new DataSyncService();
    private static final AnalyticsService  analytics  = new AnalyticsService();
    private static final ApiFootballClient apiClient  = new ApiFootballClient();
    private static final PredictionService prediction = new PredictionService();
    private static final WebServer         webServer  = new WebServer();
    private static final Scanner           scanner    = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        cabecalho();

        // Modo headless: java -jar ... --web  →  só o servidor, sem menu
        if (args.length > 0 && "--web".equals(args[0])) {
            analytics.carregarArvore();
            webServer.iniciar();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                webServer.parar();
                DatabaseConfig.closePool();
                System.out.println("\n[Sistema] Encerrado.");
            }));
            Thread.currentThread().join(); // bloqueia até Ctrl+C / kill
            return;
        }

        try {
            analytics.carregarArvore();
            boolean rodando = true;
            while (rodando) {
                rodando = menu();
            }
        } catch (java.util.NoSuchElementException ignored) {
            // stdin fechou (EOF) — encerra silenciosamente
        } catch (Exception e) {
            System.err.println("\n[ERRO] " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (webServer.isRodando()) webServer.parar();
            DatabaseConfig.closePool();
            System.out.println("\n[Sistema] Encerrado.");
        }
    }

    private static boolean menu() {
        System.out.println("""

            ╔══════════════════════════════════════════╗
            ║           MENU PRINCIPAL                 ║
            ╠══════════════════════════════════════════╣
            ║  1. Sincronizar seleção via API          ║
            ║  2. Buscar time por nome                 ║
            ║  3. Buscar jogador por ID  [Interp.Srch] ║
            ║  4. Buscar partida por ID  [Interp.Srch] ║
            ║  5. Navegar por região     [Árvore B]    ║
            ║  6. Relatório de desempenho              ║
            ║  7. Top artilheiros de uma temporada     ║
            ║  8. Buscar ID do time na API             ║
            ║  9. Predição + artilheiros  [DKP]        ║
            ║ 10. Sincronizar Copa do Mundo (todas)    ║
            ║ 11. Servidor web  (porta 8080)           ║
            ║  0. Sair                                 ║
            ╚══════════════════════════════════════════╝
            Escolha: """);

        if (!scanner.hasNextLine()) return false; // stdin fechou — sai do loop
        String opcao = scanner.nextLine().trim();
        try {
            switch (opcao) {
                case "1" -> menuSync();
                case "2" -> menuBuscarTimePorNome();
                case "3" -> menuBuscarJogador();
                case "4" -> menuBuscarPartida();
                case "5" -> menuNavegacao();
                case "6" -> menuRelatorio();
                case "7" -> menuArtilheiros();
                case "8" -> menuBuscarIdTimeNaApi();
                case "9" -> menuPredicao();
                case "10" -> menuSincronizarCopa();
                case "11" -> menuIniciarServidor();
                case "0" -> { menuEncerrar(); return false; }
                default  -> System.out.println("Opção inválida.");
            }
        } catch (Exception e) {
            System.err.println("[ERRO] " + e.getMessage());
        }
        return true;
    }

    private static void menuSync() throws Exception {
        System.out.println("\n=== SINCRONIZAR SELEÇÃO ===");
        System.out.print("Nome da região (ex: South America): ");
        String regiao = scanner.nextLine().trim();

        System.out.print("Nome da seleção (ex: Brazil): ");
        String selecao = scanner.nextLine().trim();

        System.out.print("Código FIFA (ex: BRA): ");
        String codigoFifa = scanner.nextLine().trim().toUpperCase();

        System.out.print("ID da liga na API-Football (ex: 9): ");
        int leagueId = Integer.parseInt(scanner.nextLine().trim());

        System.out.print("ID do time na API-Football (ex: 6): ");
        int teamId = Integer.parseInt(scanner.nextLine().trim());

        System.out.print("Ano da temporada (ex: 2024): ");
        int ano = Integer.parseInt(scanner.nextLine().trim());

        sync.sincronizar(regiao, selecao, codigoFifa, leagueId, teamId, ano);
        analytics.carregarArvore();
    }

    private static void menuBuscarTimePorNome() throws Exception {
        System.out.println("\n=== BUSCAR TIME POR NOME ===");
        System.out.print("Nome do time (ou parte do nome): ");
        String nome = scanner.nextLine().trim();

        if (nome.isEmpty()) {
            System.out.println("Nome inválido. Por favor, informe um nome para buscar.");
            return;
        }

        List<Selecao> timesEncontrados = analytics.buscarTimePorNome(nome);

        if (timesEncontrados.isEmpty()) {
            System.out.println("  Nenhum time encontrado com o nome \"" + nome + "\".");
            System.out.println("  Dica: Sincronize dados primeiro (opção 1).");
        } else {
            System.out.printf("%n  Encontrados %d time(s) com \"%s\":%n", timesEncontrados.size(), nome);
            System.out.println("  " + "─".repeat(60));
            System.out.printf("  %-6s %-20s %-10s %-12s%n", "ID", "Nome", "Cód. FIFA", "Região ID");
            System.out.println("  " + "─".repeat(60));
            for (Selecao s : timesEncontrados) {
                System.out.printf("  %-6d %-20s %-10s %-12d%n",
                    s.getId(), s.getNome(), s.getCodigoFifa(), s.getRegiaoId());
            }
            System.out.println("  " + "─".repeat(60));
            System.out.println("  Use o ID listado acima para sincronizar (opção 1).");
        }
    }

    private static void menuBuscarJogador() throws Exception {
        System.out.println("\n=== BUSCAR JOGADOR [Interpolation Search] ===");
        System.out.print("ID do jogador: ");
        int id = Integer.parseInt(scanner.nextLine().trim());

        Jogador j = analytics.buscarJogadorPorId(id);
        if (j != null) {
            System.out.println("\n  ✔ Jogador encontrado:");
            System.out.printf("    ID         : %d%n",  j.getId());
            System.out.printf("    Nome       : %s%n",  j.getNome());
            System.out.printf("    Posição    : %s%n",  j.getPosicao());
            System.out.printf("    Nascimento : %s%n",  j.getDataNascimento());
            System.out.printf("    Idade      : %d anos%n", j.getIdade());
        }
    }

    private static void menuBuscarPartida() throws Exception {
        System.out.println("\n=== BUSCAR PARTIDA [Interpolation Search] ===");
        System.out.print("ID da temporada: ");
        int tempId = Integer.parseInt(scanner.nextLine().trim());

        System.out.print("ID da partida: ");
        int id = Integer.parseInt(scanner.nextLine().trim());

        Partida p = analytics.buscarPartidaPorId(tempId, id);
        if (p != null) {
            System.out.println("\n  ✔ Partida encontrada:");
            System.out.printf("    ID         : %d%n",  p.getId());
            System.out.printf("    Data       : %s%n",  p.getDataPartida());
            System.out.printf("    Adversário : %s%n",  p.getAdversario());
            System.out.printf("    Competição : %s%n",  p.getCompeticao());
            System.out.printf("    Placar     : %d x %d (%s)%n",
                              p.getGolsPro(), p.getGolsContra(), p.getResultado());
        }
    }

    private static void menuNavegacao() throws Exception {
        System.out.println("\n=== NAVEGAR POR REGIÃO [Árvore B] ===");
        System.out.print("ID da região: ");
        int regiaoId = Integer.parseInt(scanner.nextLine().trim());

        List<Selecao> selecoes = analytics.navegarPorRegiao(regiaoId);
        if (selecoes.isEmpty()) {
            System.out.println("  Nenhuma seleção encontrada. Sincronize dados primeiro (opção 1).");
        } else {
            System.out.printf("%n  Seleções da região %d:%n", regiaoId);
            selecoes.forEach(s ->
                System.out.printf("    [%d] %s (%s)%n", s.getId(), s.getNome(), s.getCodigoFifa()));
        }
    }

    private static void menuRelatorio() throws Exception {
        System.out.println("\n=== RELATÓRIO DE DESEMPENHO ===");
        System.out.print("ID da seleção: ");
        int selId = Integer.parseInt(scanner.nextLine().trim());

        System.out.print("ID da temporada: ");
        int tmpId = Integer.parseInt(scanner.nextLine().trim());

        System.out.println(analytics.gerarRelatorio(selId, tmpId));
    }

    private static void menuArtilheiros() throws Exception {
        System.out.println("\n=== TOP ARTILHEIROS ===");
        System.out.print("ID da temporada: ");
        int tmpId = Integer.parseInt(scanner.nextLine().trim());

        System.out.print("Quantos? (ex: 10): ");
        int top = Integer.parseInt(scanner.nextLine().trim());

        List<Object[]> lista = analytics.topArtilheiros(tmpId, top);
        if (lista.isEmpty()) {
            System.out.println("  Sem dados. Sincronize dados primeiro (opção 1).");
        } else {
            System.out.printf("%n  %-4s %-22s %-15s %5s %6s %6s %5s%n",
                "Pos", "Nome", "Posição", "Gols", "Assist", "Min", "Jogos");
            System.out.println("  " + "─".repeat(65));
            int rank = 1;
            for (Object[] row : lista) {
                System.out.printf("  %-4d %-22s %-15s %5d %6d %6d %5d%n",
                    rank++, row[1], row[2], row[3], row[4], row[5], row[7]);
            }
        }
    }

    private static void menuPredicao() throws Exception {
        System.out.println("\n=== PREDIÇÃO + ARTILHEIROS [DKP] ===");
        System.out.print("ID da seleção: ");
        int selId = Integer.parseInt(scanner.nextLine().trim());
        System.out.print("Quantos artilheiros mostrar? (ex: 10): ");
        int top = Integer.parseInt(scanner.nextLine().trim());
        System.out.println(prediction.gerarAnaliseMultiAno(selId, top));
    }

    private static void menuSincronizarCopa() throws Exception {
        System.out.println("\n=== SINCRONIZAR COPA DO MUNDO (TODAS AS SELEÇÕES) ===");
        System.out.print("Ano da Copa (ex: 2026): ");
        int ano = Integer.parseInt(scanner.nextLine().trim());
        System.out.println("Iniciando sincronização das 48 seleções — pode levar ~6 minutos...");
        sync.sincronizarCopaDoMundo(ano);
        analytics.carregarArvore();
    }

    private static void menuIniciarServidor() throws Exception {
        if (webServer.isRodando()) {
            System.out.println("Servidor já está rodando em http://localhost:8080");
            System.out.println("Use a opção 0 (Sair) para encerrar o servidor.");
        } else {
            webServer.iniciar();
            System.out.println("Servidor rodando. Use a opção 0 (Sair) para encerrar.");
        }
    }

    private static void menuEncerrar() {
        if (webServer.isRodando()) webServer.parar();
    }

    private static void menuBuscarIdTimeNaApi() throws Exception {
        System.out.println("\n=== BUSCAR ID DO TIME NA API ===");
        System.out.print("Nome do time (ex: Brazil, France): ");
        String nome = scanner.nextLine().trim();

        if (nome.isEmpty()) {
            System.out.println("Nome inválido.");
            return;
        }

        List<JsonNode> times = apiClient.buscarTimesPorNome(nome);

        if (times.isEmpty()) {
            System.out.println("  Nenhum time encontrado para \"" + nome + "\".");
            System.out.println("  Dica: tente o nome em inglês (ex: Brazil, France, Argentina).");
        } else {
            System.out.printf("%n  Encontrados %d time(s):%n", times.size());
            System.out.println("  " + "─".repeat(60));
            System.out.printf("  %-8s %-25s %-15s %-10s%n", "ID API", "Nome", "País", "Slug");
            System.out.println("  " + "─".repeat(60));
            for (JsonNode t : times) {
                int    id     = t.path("id").asInt();
                String tNome  = t.path("name").asText("-");
                String pais   = t.path("country").asText(t.path("area").path("name").asText("-"));
                String codigo = t.path("code").asText(t.path("tla").asText("-"));
                System.out.printf("  %-8d %-25s %-15s %-10s%n", id, tNome, pais, codigo);
            }
            System.out.println("  " + "─".repeat(60));
            System.out.println("  Use o \"ID API\" como 'ID do time na API-Football' na opção 1.");
        }
    }

    private static void cabecalho() {
        System.out.println("""
            ╔══════════════════════════════════════════╗
            ║   ⚽  DataKick — Football Analysis       ║
            ║        BTree + Interpolation Search      ║
            ║             Java 25 + PostgreSQL         ║
            ╚══════════════════════════════════════════╝
            """);
    }
}
