package com.football;

import com.football.config.DatabaseConfig;
import com.football.web.WebServer;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("""
            ╔══════════════════════════════════════════╗
            ║   ⚽  DataKick — Football Analysis       ║
            ╚══════════════════════════════════════════╝
            """);

        WebServer webServer = new WebServer();
        webServer.iniciar();

        System.out.println("Acesse o navegador para usar a interface.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            webServer.parar();
            DatabaseConfig.closePool();
            System.out.println("[Sistema] Encerrado.");
        }));

        Thread.currentThread().join();
    }
}
