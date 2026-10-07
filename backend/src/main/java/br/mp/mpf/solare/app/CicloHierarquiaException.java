package br.mp.mpf.solare.app;

/**
 * Lancada quando a definicao de ambiente-pai formaria um ciclo na hierarquia
 * (F2.3 — um ambiente nao pode ser ancestral de si mesmo). O controller mapeia
 * para HTTP 400 (requisicao invalida), por ser um erro de forma da relacao.
 */
public final class CicloHierarquiaException extends RuntimeException {

    private final String ambienteId;
    private final String ambientePaiId;

    public CicloHierarquiaException(String ambienteId, String ambientePaiId) {
        super("A definicao de pai cria um ciclo na hierarquia de ambientes.");
        this.ambienteId = ambienteId;
        this.ambientePaiId = ambientePaiId;
    }

    public String getAmbienteId() {
        return ambienteId;
    }

    public String getAmbientePaiId() {
        return ambientePaiId;
    }
}
