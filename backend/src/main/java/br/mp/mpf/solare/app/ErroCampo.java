package br.mp.mpf.solare.app;

import java.util.Objects;

/**
 * Erro de validacao associado a um campo especifico (F1.2 — mensagem por campo).
 * Imutavel e livre de infraestrutura.
 */
public final class ErroCampo {

    private final String campo;
    private final String mensagem;

    public ErroCampo(String campo, String mensagem) {
        this.campo = Objects.requireNonNull(campo, "campo nao pode ser nulo");
        this.mensagem = Objects.requireNonNull(mensagem, "mensagem nao pode ser nula");
    }

    public String getCampo() {
        return campo;
    }

    public String getMensagem() {
        return mensagem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ErroCampo outro)) {
            return false;
        }
        return campo.equals(outro.campo) && mensagem.equals(outro.mensagem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(campo, mensagem);
    }

    @Override
    public String toString() {
        return "ErroCampo{campo=" + campo + ", mensagem=" + mensagem + '}';
    }
}
