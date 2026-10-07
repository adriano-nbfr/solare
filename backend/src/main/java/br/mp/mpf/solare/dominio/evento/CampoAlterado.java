package br.mp.mpf.solare.dominio.evento;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Diferenca de um campo entre a versao anterior e a nova de uma reserva (F7.3),
 * carregada no evento {@link ReservaAlterada} para que a notificacao de alteracao
 * destaque visualmente o que mudou (F8.4).
 *
 * <p>Tipo de valor imutavel e puro. Os valores sao representacoes textuais ja
 * prontas para exibicao (o produtor do diff decide a formatacao); {@code null}
 * representa ausencia de valor (ex.: campo que passou a existir ou foi limpo).</p>
 *
 * <p>Privacidade (NF3.5/F8.6): guarde aqui apenas o estritamente necessario para
 * o acompanhamento (ex.: horario, ambiente, finalidade, recursos). Nao inclua
 * dados pessoais sensiveis.</p>
 */
public final class CampoAlterado {

    private final String campo;
    private final String rotulo;
    private final String valorAnterior;
    private final String valorNovo;

    /**
     * @param campo         identificador tecnico do campo (ex.: {@code inicio})
     * @param rotulo        rotulo amigavel para exibicao (ex.: {@code "Inicio"})
     * @param valorAnterior valor anterior (texto pronto para exibir, pode ser {@code null})
     * @param valorNovo     valor novo (texto pronto para exibir, pode ser {@code null})
     */
    @JsonCreator
    public CampoAlterado(@JsonProperty("campo") String campo,
                         @JsonProperty("rotulo") String rotulo,
                         @JsonProperty("valorAnterior") String valorAnterior,
                         @JsonProperty("valorNovo") String valorNovo) {
        this.campo = Objects.requireNonNull(campo, "campo nao pode ser nulo");
        this.rotulo = (rotulo == null || rotulo.isBlank()) ? campo : rotulo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public String getCampo() {
        return campo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CampoAlterado outro)) {
            return false;
        }
        return campo.equals(outro.campo)
                && Objects.equals(rotulo, outro.rotulo)
                && Objects.equals(valorAnterior, outro.valorAnterior)
                && Objects.equals(valorNovo, outro.valorNovo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(campo, rotulo, valorAnterior, valorNovo);
    }

    @Override
    public String toString() {
        return "CampoAlterado{campo=" + campo + ", de=" + valorAnterior + ", para=" + valorNovo + '}';
    }
}
