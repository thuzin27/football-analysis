package com.football.algorithm;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * InterpolationSearch
 * ---------------------------------------------------------------
 * Algoritmo de busca por interpolação adaptado para listas de
 * objetos com chave numérica inteira.
 *
 * Complexidade:
 *   Melhor caso  → O(1)
 *   Médio caso   → O(log log n)  — distribuição uniforme
 *   Pior caso    → O(n)          — distribuição muito assimétrica
 *
 * PRÉ-REQUISITO: a lista deve estar ORDENADA pela chave numérica.
 *
 * Uso genérico:
 *   List<Jogador> jogadores = ...;  // ordenado por id
 *   int idx = InterpolationSearch.buscar(jogadores, 42, Jogador::getId);
 *
 * ---------------------------------------------------------------
 */
public class InterpolationSearch {

    private InterpolationSearch() { /* utilitário estático */ }

    /**
     * Busca um elemento em uma lista ordenada usando interpolação.
     *
     * @param lista    lista ordenada pelo campo numérico
     * @param alvo     valor inteiro a ser encontrado
     * @param chave    função que extrai o valor inteiro do objeto (ex: Jogador::getId)
     * @param <T>      tipo do objeto
     * @return índice do elemento encontrado, ou -1 se não encontrado
     */
    public static <T> int buscar(List<T> lista, int alvo, ToIntFunction<T> chave) {
        if (lista == null || lista.isEmpty()) return -1;

        int baixo = 0;
        int alto  = lista.size() - 1;

        int iteracoes = 0; // para debug/análise de desempenho

        while (baixo <= alto
               && alvo >= chave.applyAsInt(lista.get(baixo))
               && alvo <= chave.applyAsInt(lista.get(alto))) {

            // Evita divisão por zero quando todos os valores são iguais
            int chaveBaixo = chave.applyAsInt(lista.get(baixo));
            int chaveAlto  = chave.applyAsInt(lista.get(alto));

            if (chaveBaixo == chaveAlto) {
                // Todos os valores são iguais: verifica direto
                return chave.applyAsInt(lista.get(baixo)) == alvo ? baixo : -1;
            }

            // -------------------------------------------------------
            // Fórmula de interpolação:
            //   pos = baixo + ((alvo - chaveBaixo) * (alto - baixo))
            //                  / (chaveAlto - chaveBaixo)
            // -------------------------------------------------------
            int pos = baixo + ((alvo - chaveBaixo) * (alto - baixo))
                             / (chaveAlto - chaveBaixo);

            int chavePos = chave.applyAsInt(lista.get(pos));
            iteracoes++;

            if (chavePos == alvo) {
                System.out.printf("[InterpolationSearch] Encontrado em %d iterações (índice %d)%n",
                                  iteracoes, pos);
                return pos;
            }

            if (chavePos < alvo) {
                baixo = pos + 1;   // busca na metade superior
            } else {
                alto = pos - 1;    // busca na metade inferior
            }
        }

        System.out.printf("[InterpolationSearch] Não encontrado após %d iterações%n", iteracoes);
        return -1; // não encontrado
    }

    /**
     * Versão que retorna o objeto diretamente (null se não encontrado).
     */
    public static <T> T encontrar(List<T> lista, int alvo, ToIntFunction<T> chave) {
        int idx = buscar(lista, alvo, chave);
        return idx >= 0 ? lista.get(idx) : null;
    }

    /**
     * Versão para busca em array primitivo int[] (uso interno/utilitário).
     */
    public static int buscarArray(int[] arr, int alvo) {
        if (arr == null || arr.length == 0) return -1;

        int baixo = 0;
        int alto  = arr.length - 1;

        while (baixo <= alto && alvo >= arr[baixo] && alvo <= arr[alto]) {
            if (arr[baixo] == arr[alto]) {
                return arr[baixo] == alvo ? baixo : -1;
            }

            int pos = baixo + ((alvo - arr[baixo]) * (alto - baixo))
                             / (arr[alto] - arr[baixo]);

            if (arr[pos] == alvo) return pos;

            if (arr[pos] < alvo) baixo = pos + 1;
            else                 alto  = pos - 1;
        }
        return -1;
    }
}
