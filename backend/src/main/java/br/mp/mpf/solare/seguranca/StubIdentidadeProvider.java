package br.mp.mpf.solare.seguranca;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Provedor de identidade de desenvolvimento. Resolve usuario e papel a partir
 * dos cabecalhos {@code X-Dev-User} e {@code X-Dev-Role}, sem Cognito.
 *
 * <p>Por seguranca (NF3.7), o stub so pode ser instanciado habilitado quando o
 * ambiente NAO e producao. Em producao ele ignora os cabecalhos de desenvolvimento
 * e sempre retorna {@link Identidade#anonima()}.</p>
 */
public final class StubIdentidadeProvider implements IdentidadeProvider {

    public static final String HEADER_USUARIO = "X-Dev-User";
    public static final String HEADER_PAPEL = "X-Dev-Role";

    private final boolean habilitado;

    /**
     * @param habilitado deve refletir "ambiente != producao". Quando {@code false},
     *                   os cabecalhos {@code X-Dev-*} sao ignorados.
     */
    public StubIdentidadeProvider(boolean habilitado) {
        this.habilitado = habilitado;
    }

    /**
     * Fabrica a partir do nome do ambiente. O stub e habilitado para qualquer
     * ambiente diferente de {@code "prod"} (comparacao case-insensitive).
     */
    public static StubIdentidadeProvider paraAmbiente(String ambiente) {
        boolean producao = ambiente != null && "prod".equalsIgnoreCase(ambiente.trim());
        return new StubIdentidadeProvider(!producao);
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    @Override
    public Identidade identidadeAtual(Requisicao req) {
        // Em producao o stub e inerte: nunca resolve identidade a partir dos headers.
        if (!habilitado || req == null) {
            return Identidade.anonima();
        }

        String usuario = valorLimpo(req.header(HEADER_USUARIO));
        if (usuario == null) {
            return Identidade.anonima();
        }

        Papel papel = Papel.deTexto(req.header(HEADER_PAPEL));
        if (papel == null) {
            // Sem papel explicito, assume o perfil de menor privilegio.
            papel = Papel.SOLICITANTE;
        }

        Set<Papel> papeis = new LinkedHashSet<>();
        papeis.add(papel);
        return new Identidade(usuario, usuario, papeis, papel);
    }

    private static String valorLimpo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
