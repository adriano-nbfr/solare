package br.mp.mpf.solare.app;

/**
 * Lancada quando um Ambiente referenciado por id nao existe. O controller mapeia
 * para HTTP 404.
 */
public final class AmbienteNaoEncontradoException extends RuntimeException {

    private final String id;

    public AmbienteNaoEncontradoException(String id) {
        super("Ambiente nao encontrado: " + id);
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
