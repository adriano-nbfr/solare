package br.mp.mpf.solare.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.mp.mpf.solare.dominio.Recurso;
import br.mp.mpf.solare.dominio.TipoRecurso;
import br.mp.mpf.solare.dominio.repositorio.RecursoRepository;
import br.mp.mpf.solare.seguranca.Autorizacao;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.Papel;

/**
 * Caso de uso de cadastro de Recursos (F3). Orquestra autorizacao (apenas ADMIN —
 * NF3.2), validacao de forma por campo (F3.1), sanitizacao de entradas (NF3.3) e
 * persistencia via {@link RecursoRepository}, preservando o dominio puro.
 *
 * <p>A validacao e dependente do tipo: um recurso {@link TipoRecurso#LIMITADO}
 * exige {@code quantidadeTotal} inteira maior que zero, enquanto um recurso
 * {@link TipoRecurso#ILIMITADO} nao recebe quantidade (ignorada/normalizada para
 * nulo pelo dominio). Essa regra sustenta o motor de conflitos por quantidade
 * (RN10) implementado na tarefa 6.</p>
 *
 * <p>A listagem (F3.4) aplica paginacao e ordenacao em memoria sobre o resultado
 * do repositorio, coerente com o volume esperado do cadastro de recursos no MVP.</p>
 */
public final class RecursoService {

    private static final int NOME_MAX = 120;
    private static final int QUANTIDADE_MAX = 1_000_000;

    private final RecursoRepository repositorio;

    public RecursoService(RecursoRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Cria um novo Recurso (F3.1). Gera um identificador unico. Exige papel ADMIN.
     *
     * @throws br.mp.mpf.solare.seguranca.AutorizacaoException quando nao for ADMIN
     * @throws ValidacaoException quando os campos forem invalidos
     */
    public Recurso criar(Identidade identidade, DadosRecurso dados) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        Recurso recurso = montarValidado(UUID.randomUUID().toString(), dados);
        repositorio.salvar(recurso);
        return recurso;
    }

    /**
     * Edita um Recurso existente (F3.5), preservando o identificador e revalidando
     * os campos conforme o tipo. Exige papel ADMIN.
     *
     * @throws RecursoNaoEncontradoException quando o id nao existe
     */
    public Recurso editar(Identidade identidade, String id, DadosRecurso dados) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        String idLimpo = exigirId(id);
        if (repositorio.buscarPorId(idLimpo).isEmpty()) {
            throw new RecursoNaoEncontradoException(idLimpo);
        }
        Recurso atualizado = montarValidado(idLimpo, dados);
        repositorio.salvar(atualizado);
        return atualizado;
    }

    /** Exclui um Recurso (restrito a ADMIN). */
    public void excluir(Identidade identidade, String id) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        String idLimpo = exigirId(id);
        if (repositorio.buscarPorId(idLimpo).isEmpty()) {
            throw new RecursoNaoEncontradoException(idLimpo);
        }
        repositorio.excluir(idLimpo);
    }

    /** Busca um Recurso por id (restrito a ADMIN). */
    public Optional<Recurso> buscar(Identidade identidade, String id) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        return repositorio.buscarPorId(exigirId(id));
    }

    /**
     * Lista Recursos com paginacao e ordenacao (F3.4). Exige papel ADMIN.
     * Campos de ordenacao suportados: {@code nome} (padrao), {@code tipo},
     * {@code quantidade}/{@code quantidadeTotal}, {@code id}.
     */
    public Pagina<Recurso> listar(Identidade identidade, ParametrosPagina parametros) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        ParametrosPagina p = parametros == null
                ? ParametrosPagina.de(null, null, null)
                : parametros;

        List<Recurso> todos = new ArrayList<>(repositorio.listarTodos());
        todos.sort(comparadorDe(p.getOrdenarPor(), p.isAscendente()));

        long total = todos.size();
        int de = Math.min(p.getPagina() * p.getTamanho(), todos.size());
        int ate = Math.min(de + p.getTamanho(), todos.size());
        List<Recurso> fatia = todos.subList(de, ate);

        return new Pagina<>(fatia, p.getPagina(), p.getTamanho(), total);
    }

    // --- internos ------------------------------------------------------------

    private Recurso montarValidado(String id, DadosRecurso dados) {
        if (dados == null) {
            throw new ValidacaoException(List.of(new ErroCampo("recurso", "Dados do recurso ausentes.")));
        }
        String nome = Sanitizador.texto(dados.nome());
        TipoRecurso tipo = TipoRecurso.deTexto(dados.tipo());
        Integer quantidade = dados.quantidadeTotal();

        List<ErroCampo> erros = new ArrayList<>();
        validarNome(nome, erros);
        validarTipoEQuantidade(tipo, dados.tipo(), quantidade, erros);

        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros);
        }
        // Para ILIMITADO o dominio normaliza a quantidade para nulo; nao a repassamos.
        return new Recurso(id, nome, tipo, tipo == TipoRecurso.LIMITADO ? quantidade : null);
    }

    private static void validarNome(String nome, List<ErroCampo> erros) {
        if (nome == null || nome.isBlank()) {
            erros.add(new ErroCampo("nome", "O nome do recurso e obrigatorio."));
        } else if (nome.length() > NOME_MAX) {
            erros.add(new ErroCampo("nome", "O nome deve ter no maximo " + NOME_MAX + " caracteres."));
        }
    }

    /**
     * Valida o tipo e, conforme ele, a quantidade: LIMITADO exige inteiro maior
     * que zero; ILIMITADO nao aceita quantidade (deve vir ausente/nula).
     */
    private static void validarTipoEQuantidade(
            TipoRecurso tipo, String tipoBruto, Integer quantidade, List<ErroCampo> erros) {
        if (tipo == null) {
            String informado = tipoBruto == null || tipoBruto.isBlank() ? "" : " informado";
            erros.add(new ErroCampo("tipo",
                    "O tipo do recurso" + informado + " e invalido. Use LIMITADO ou ILIMITADO."));
            return;
        }
        if (tipo == TipoRecurso.LIMITADO) {
            if (quantidade == null) {
                erros.add(new ErroCampo("quantidadeTotal",
                        "Recurso limitado exige a quantidade total."));
            } else if (quantidade <= 0) {
                erros.add(new ErroCampo("quantidadeTotal",
                        "A quantidade total deve ser um inteiro maior que zero."));
            } else if (quantidade > QUANTIDADE_MAX) {
                erros.add(new ErroCampo("quantidadeTotal",
                        "A quantidade total informada e muito grande."));
            }
        } else { // ILIMITADO
            if (quantidade != null) {
                erros.add(new ErroCampo("quantidadeTotal",
                        "Recurso ilimitado nao deve ter quantidade total."));
            }
        }
    }

    private static Comparator<Recurso> comparadorDe(String campo, boolean asc) {
        Comparator<Recurso> base = switch (campo == null ? "nome" : campo.toLowerCase()) {
            case "tipo" -> Comparator.comparing(r -> r.getTipo().name(), nullsSeguroTexto());
            case "quantidade", "quantidadetotal" ->
                    Comparator.comparing(Recurso::getQuantidadeTotal, nullsSeguroInteiro());
            case "id" -> Comparator.comparing(Recurso::getId, nullsSeguroTexto());
            default -> Comparator.comparing(Recurso::getNome, nullsSeguroTexto());
        };
        return asc ? base : base.reversed();
    }

    private static Comparator<String> nullsSeguroTexto() {
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    }

    private static Comparator<Integer> nullsSeguroInteiro() {
        return Comparator.nullsLast(Comparator.naturalOrder());
    }

    private static String exigirId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidacaoException(List.of(new ErroCampo("id", "Identificador do recurso e obrigatorio.")));
        }
        return id.trim();
    }

    /**
     * Dados de entrada para criacao/edicao de Recurso. Desacopla o servico do
     * formato de transporte (JSON do controller). {@code tipo} aceita texto livre
     * tolerante a caixa ({@code LIMITADO}/{@code ILIMITADO}); {@code quantidadeTotal}
     * so e usada para o tipo LIMITADO.
     */
    public record DadosRecurso(String nome, String tipo, Integer quantidadeTotal) {
    }
}
