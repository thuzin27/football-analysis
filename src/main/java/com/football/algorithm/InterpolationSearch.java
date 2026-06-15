package com.football.algorithm;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Busca por interpolação em listas ordenadas por chave numérica inteira.
 *
 * Complexidade: O(1) melhor caso, O(log log n) médio (distribuição uniforme), O(n) pior caso.
 * PRÉ-REQUISITO: lista ordenada pela chave.
 */
public class InterpolationSearch {

    private InterpolationSearch() {}

    public static <T> int buscar(List<T> lista, int alvo, ToIntFunction<T> chave) {
        if (lista == null || lista.isEmpty()) return -1;

        int baixo = 0;
        int alto  = lista.size() - 1;
        int iteracoes = 0;

        while (baixo <= alto
               && alvo >= chave.applyAsInt(lista.get(baixo))
               && alvo <= chave.applyAsInt(lista.get(alto))) {

            int chaveBaixo = chave.applyAsInt(lista.get(baixo));
            int chaveAlto  = chave.applyAsInt(lista.get(alto));

            // evita divisão por zero quando todos os valores são iguais
            if (chaveBaixo == chaveAlto)
                return chave.applyAsInt(lista.get(baixo)) == alvo ? baixo : -1;

            // pos = baixo + ((alvo - chaveBaixo) * (alto - baixo)) / (chaveAlto - chaveBaixo)
            int pos = baixo + ((alvo - chaveBaixo) * (alto - baixo)) / (chaveAlto - chaveBaixo);
            int chavePos = chave.applyAsInt(lista.get(pos));
            iteracoes++;

            if (chavePos == alvo) {
                System.out.printf("[InterpolationSearch] Encontrado em %d iterações (índice %d)%n",
                                  iteracoes, pos);
                return pos;
            }

            if (chavePos < alvo) baixo = pos + 1;
            else                 alto  = pos - 1;
        }

        System.out.printf("[InterpolationSearch] Não encontrado após %d iterações%n", iteracoes);
        return -1;
    }

    public static <T> T encontrar(List<T> lista, int alvo, ToIntFunction<T> chave) {
        int idx = buscar(lista, alvo, chave);
        return idx >= 0 ? lista.get(idx) : null;
    }
}
