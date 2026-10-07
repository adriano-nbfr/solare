package br.mp.mpf.solare.app;

/**
 * Parametros de paginacao e ordenacao vindos da query string, no padrao do
 * projeto demo DSMPF ({@code page}, {@code size}, {@code sort}).
 *
 * <p>{@code sort} aceita o formato {@code campo} ou {@code campo,asc|desc}
 * (ex.: {@code nome,desc}).</p>
 */
public final class ParametrosPagina {

    /** Limite de seguranca para o tamanho de pagina, evitando varreduras enormes. */
    public static final int TAMANHO_MAXIMO = 200;
    public static final int TAMANHO_PADRAO = 20;

    private final int pagina;
    private final int tamanho;
    private final String ordenarPor;
    private final boolean ascendente;

    public ParametrosPagina(int pagina, int tamanho, String ordenarPor, boolean ascendente) {
        this.pagina = Math.max(0, pagina);
        this.tamanho = normalizarTamanho(tamanho);
        this.ordenarPor = (ordenarPor == null || ordenarPor.isBlank()) ? null : ordenarPor.trim();
        this.ascendente = ascendente;
    }

    /**
     * Constroi a partir dos valores crus de query string. Valores ausentes ou
     * invalidos caem para os padroes.
     *
     * @param page  numero da pagina (0-based) como texto
     * @param size  tamanho da pagina como texto
     * @param sort  ordenacao no formato {@code campo} ou {@code campo,asc|desc}
     */
    public static ParametrosPagina de(String page, String size, String sort) {
        int p = inteiro(page, 0);
        int s = inteiro(size, TAMANHO_PADRAO);

        String campo = null;
        boolean asc = true;
        if (sort != null && !sort.isBlank()) {
            String[] partes = sort.split(",", 2);
            campo = partes[0].trim();
            if (partes.length > 1) {
                asc = !"desc".equalsIgnoreCase(partes[1].trim());
            }
        }
        return new ParametrosPagina(p, s, campo, asc);
    }

    private static int normalizarTamanho(int tamanho) {
        if (tamanho <= 0) {
            return TAMANHO_PADRAO;
        }
        return Math.min(tamanho, TAMANHO_MAXIMO);
    }

    private static int inteiro(String valor, int padrao) {
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    public int getPagina() {
        return pagina;
    }

    public int getTamanho() {
        return tamanho;
    }

    public String getOrdenarPor() {
        return ordenarPor;
    }

    public boolean isAscendente() {
        return ascendente;
    }
}
