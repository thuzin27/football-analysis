package com.football.algorithm;

import java.util.*;

/**
 * Árvore B genérica — busca O(log n) para navegação na hierarquia Regiao→Selecao.
 *
 * grauMinimo (t): nó tem no mínimo t-1 chaves e no máximo 2t-1 chaves.
 */
public class BTree<K extends Comparable<K>, V> {

    private final int grauMinimo;
    private BTreeNode raiz;

    private class BTreeNode {
        List<K>         chaves  = new ArrayList<>();
        List<V>         valores = new ArrayList<>();
        List<BTreeNode> filhos  = new ArrayList<>();
        boolean         ehFolha;

        BTreeNode(boolean ehFolha) {
            this.ehFolha = ehFolha;
        }

        int numChaves() { return chaves.size(); }
    }

    public BTree(int grauMinimo) {
        if (grauMinimo < 2) throw new IllegalArgumentException("Grau mínimo deve ser >= 2");
        this.grauMinimo = grauMinimo;
        this.raiz = new BTreeNode(true);
    }

    public V buscar(K chave) {
        return buscarNo(raiz, chave);
    }

    private V buscarNo(BTreeNode no, K chave) {
        int i = 0;
        while (i < no.numChaves() && chave.compareTo(no.chaves.get(i)) > 0) i++;

        if (i < no.numChaves() && chave.compareTo(no.chaves.get(i)) == 0)
            return no.valores.get(i);
        if (no.ehFolha) return null;

        return buscarNo(no.filhos.get(i), chave);
    }

    public void inserir(K chave, V valor) {
        BTreeNode r = raiz;

        if (r.numChaves() == 2 * grauMinimo - 1) {
            // raiz cheia: cria nova raiz e divide
            BTreeNode s = new BTreeNode(false);
            raiz = s;
            s.filhos.add(r);
            dividirFilho(s, 0);
            inserirNaoCheio(s, chave, valor);
        } else {
            inserirNaoCheio(r, chave, valor);
        }
    }

    private void inserirNaoCheio(BTreeNode no, K chave, V valor) {
        int i = no.numChaves() - 1;

        if (no.ehFolha) {
            no.chaves.add(null);
            no.valores.add(null);
            while (i >= 0 && chave.compareTo(no.chaves.get(i)) < 0) {
                no.chaves.set(i + 1, no.chaves.get(i));
                no.valores.set(i + 1, no.valores.get(i));
                i--;
            }
            no.chaves.set(i + 1, chave);
            no.valores.set(i + 1, valor);
        } else {
            while (i >= 0 && chave.compareTo(no.chaves.get(i)) < 0) i--;
            i++;
            if (no.filhos.get(i).numChaves() == 2 * grauMinimo - 1) {
                dividirFilho(no, i);
                if (chave.compareTo(no.chaves.get(i)) > 0) i++;
            }
            inserirNaoCheio(no.filhos.get(i), chave, valor);
        }
    }

    private void dividirFilho(BTreeNode pai, int i) {
        BTreeNode filho = pai.filhos.get(i);
        BTreeNode novo  = new BTreeNode(filho.ehFolha);
        int t = grauMinimo;

        for (int j = t; j < 2 * t - 1; j++) {
            novo.chaves.add(filho.chaves.get(j));
            novo.valores.add(filho.valores.get(j));
        }
        if (!filho.ehFolha) {
            for (int j = t; j < 2 * t; j++)
                novo.filhos.add(filho.filhos.get(j));
        }

        // a chave do meio sobe para o pai
        K chaveMeio = filho.chaves.get(t - 1);
        V valorMeio = filho.valores.get(t - 1);

        filho.chaves  = new ArrayList<>(filho.chaves.subList(0, t - 1));
        filho.valores = new ArrayList<>(filho.valores.subList(0, t - 1));
        if (!filho.ehFolha)
            filho.filhos = new ArrayList<>(filho.filhos.subList(0, t));

        pai.chaves.add(i, chaveMeio);
        pai.valores.add(i, valorMeio);
        pai.filhos.add(i + 1, novo);
    }

    public List<Map.Entry<K, V>> emOrdem() {
        List<Map.Entry<K, V>> resultado = new ArrayList<>();
        emOrdemNo(raiz, resultado);
        return resultado;
    }

    private void emOrdemNo(BTreeNode no, List<Map.Entry<K, V>> resultado) {
        for (int i = 0; i < no.numChaves(); i++) {
            if (!no.ehFolha) emOrdemNo(no.filhos.get(i), resultado);
            resultado.add(Map.entry(no.chaves.get(i), no.valores.get(i)));
        }
        if (!no.ehFolha) emOrdemNo(no.filhos.get(no.numChaves()), resultado);
    }

    public int tamanho() {
        return emOrdem().size();
    }
}
