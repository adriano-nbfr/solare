package br.mp.mpf.solare.app;

/**
 * Lancada quando um Recurso referenciado por id nao existe. O controller mapeia
 * para HTTP 404.
 */
public final class RecursoNaoEncontradoException extends RuntimeException {

    private final String id;

    public RecursoNaoEncontradoException(String id) {
        super("Recurso nao encontrado: " + id);
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
