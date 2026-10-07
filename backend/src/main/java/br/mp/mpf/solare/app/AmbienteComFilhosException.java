package br.mp.mpf.solare.app;

/**
 * Lancada ao tentar excluir um Ambiente que ainda possui filhos vinculados
 * (F2.5 — impede orfaos na arvore). O controller mapeia para HTTP 409 (conflito
 * de estado), orientando a remover ou religar os filhos antes.
 */
public final class AmbienteComFilhosException extends RuntimeException {

    private final String id;
    private final int quantidadeFilhos;

    public AmbienteComFilhosException(String id, int quantidadeFilhos) {
        super("Nao e possivel excluir o ambiente " + id + ": ha "
                + quantidadeFilhos + " ambiente(s) filho(s) vinculado(s).");
        this.id = id;
        this.quantidadeFilhos = quantidadeFilhos;
    }

    public String getId() {
        return id;
    }

    public int getQuantidadeFilhos() {
        return quantidadeFilhos;
    }
}
