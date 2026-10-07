package br.mp.mpf.solare.app;

/**
 * Lancada quando um Setor referenciado por id nao existe. O controller mapeia
 * para HTTP 404.
 */
public final class SetorNaoEncontradoException extends RuntimeException {

    private final String id;

    public SetorNaoEncontradoException(String id) {
        super("Setor nao encontrado: " + id);
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
