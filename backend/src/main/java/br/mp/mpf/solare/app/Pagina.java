package br.mp.mpf.solare.app;

import java.util.List;
import java.util.Objects;

/**
 * Fatia paginada de resultados, no padrao do demo DSMPF (conteudo + metadados
 * de paginacao). Imutavel.
 *
 * @param <T> tipo do conteudo
 */
public final class Pagina<T> {

    private final List<T> conteudo;
    private final int pagina;
    private final int tamanho;
    private final long total;

    public Pagina(List<T> conteudo, int pagina, int tamanho, long total) {
        this.conteudo = conteudo == null ? List.of() : List.copyOf(conteudo);
        this.pagina = pagina;
        this.tamanho = tamanho;
        this.total = total;
    }

    public List<T> getConteudo() {
        return conteudo;
    }

    public int getPagina() {
        return pagina;
    }

    public int getTamanho() {
        return tamanho;
    }

    public long getTotal() {
        return total;
    }

    /** Numero total de paginas (>= 1 quando ha tamanho definido). */
    public int getTotalPaginas() {
        if (tamanho <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / tamanho);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pagina<?> outra)) {
            return false;
        }
        return pagina == outra.pagina
                && tamanho == outra.tamanho
                && total == outra.total
                && conteudo.equals(outra.conteudo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conteudo, pagina, tamanho, total);
    }
}
