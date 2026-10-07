package br.mp.mpf.solare.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.repositorio.AmbienteRepository;
import br.mp.mpf.solare.dominio.repositorio.SetorRepository;
import br.mp.mpf.solare.seguranca.Autorizacao;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.Papel;

/**
 * Caso de uso de cadastro de Ambientes com hierarquia pai/filho (F2). Orquestra
 * autorizacao (apenas ADMIN — NF3.2), validacao de forma por campo (F2.1),
 * sanitizacao de entradas (NF3.3), vinculo ao Setor (F2.1), relacao pai/filho
 * via GSI1 (F2.2) e a verificacao anti-ciclo (F2.3), preservando o dominio puro.
 *
 * <p>A verificacao anti-ciclo (F2.3) impede que a definicao de pai torne um
 * ambiente ancestral de si mesmo: sobe pela cadeia de pais a partir do pai
 * proposto; se reencontrar o proprio ambiente editado, rejeita a operacao. Um
 * ambiente tambem nao pode ser pai de si mesmo.</p>
 *
 * <p>A exclusao (F2.5) e bloqueada enquanto houver filhos vinculados, evitando
 * orfaos na arvore.</p>
 */
public final class AmbienteService {

    private static final int NOME_MAX = 160;
    private static final int CAPACIDADE_MAX = 1_000_000;

    /** Limite de seguranca para a subida na cadeia de pais (defesa contra dados ja corrompidos). */
    private static final int PROFUNDIDADE_MAXIMA = 1_000;

    private final AmbienteRepository repositorio;
    private final SetorRepository setorRepositorio;

    public AmbienteService(AmbienteRepository repositorio, SetorRepository setorRepositorio) {
        this.repositorio = repositorio;
        this.setorRepositorio = setorRepositorio;
    }

    /**
     * Cria um novo Ambiente (F2.1/F2.2). Gera um identificador unico, valida os
     * campos, verifica o vinculo com o Setor e a hierarquia (anti-ciclo F2.3).
     * Exige papel ADMIN.
     */
    public Ambiente criar(Identidade identidade, DadosAmbiente dados) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        String id = UUID.randomUUID().toString();
        Ambiente ambiente = montarValidado(id, dados);
        verificarCiclo(id, ambiente.getAmbientePaiIdOuNulo());
        repositorio.salvar(ambiente);
        return ambiente;
    }

    /**
     * Edita um Ambiente existente (F2.1), preservando o identificador e
     * revalidando campos, vinculo e hierarquia. Exige papel ADMIN.
     *
     * @throws AmbienteNaoEncontradoException quando o id nao existe
     */
    public Ambiente editar(Identidade identidade, String id, DadosAmbiente dados) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        String idLimpo = exigirId(id);
        if (repositorio.buscarPorId(idLimpo).isEmpty()) {
            throw new AmbienteNaoEncontradoException(idLimpo);
        }
        Ambiente atualizado = montarValidado(idLimpo, dados);
        verificarCiclo(idLimpo, atualizado.getAmbientePaiIdOuNulo());
        repositorio.salvar(atualizado);
        return atualizado;
    }

    /**
     * Exclui um Ambiente (restrito a ADMIN). Impede a exclusao enquanto houver
     * filhos vinculados (F2.5).
     *
     * @throws AmbienteComFilhosException quando o ambiente ainda possui filhos
     */
    public void excluir(Identidade identidade, String id) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        String idLimpo = exigirId(id);
        if (repositorio.buscarPorId(idLimpo).isEmpty()) {
            throw new AmbienteNaoEncontradoException(idLimpo);
        }
        List<Ambiente> filhos = repositorio.listarFilhos(idLimpo);
        if (filhos != null && !filhos.isEmpty()) {
            throw new AmbienteComFilhosException(idLimpo, filhos.size());
        }
        repositorio.excluir(idLimpo);
    }

    /** Busca um Ambiente por id (restrito a ADMIN). */
    public Optional<Ambiente> buscar(Identidade identidade, String id) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        return repositorio.buscarPorId(exigirId(id));
    }

    /**
     * Lista Ambientes com paginacao e ordenacao (F2.4). Cada item indica seu pai
     * (quando houver). Exige papel ADMIN. Campos de ordenacao suportados:
     * {@code nome} (padrao), {@code setor}/{@code setorId}, {@code capacidade},
     * {@code pai}/{@code ambientePaiId}, {@code id}.
     */
    public Pagina<Ambiente> listar(Identidade identidade, ParametrosPagina parametros) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        ParametrosPagina p = parametros == null
                ? ParametrosPagina.de(null, null, null)
                : parametros;

        List<Ambiente> todos = new ArrayList<>(repositorio.listarTodos());
        todos.sort(comparadorDe(p.getOrdenarPor(), p.isAscendente()));

        long total = todos.size();
        int de = Math.min(p.getPagina() * p.getTamanho(), todos.size());
        int ate = Math.min(de + p.getTamanho(), todos.size());
        List<Ambiente> fatia = todos.subList(de, ate);

        return new Pagina<>(fatia, p.getPagina(), p.getTamanho(), total);
    }

    /**
     * Lista os filhos diretos de um ambiente-pai (F2.2), para montar a arvore na
     * UI. Exige papel ADMIN.
     */
    public List<Ambiente> listarFilhos(Identidade identidade, String ambientePaiId) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        return repositorio.listarFilhos(exigirId(ambientePaiId));
    }

    // --- internos ------------------------------------------------------------

    private Ambiente montarValidado(String id, DadosAmbiente dados) {
        if (dados == null) {
            throw new ValidacaoException(List.of(new ErroCampo("ambiente", "Dados do ambiente ausentes.")));
        }
        String nome = Sanitizador.texto(dados.nome());
        String setorId = normalizarId(dados.setorId());
        String ambientePaiId = normalizarId(dados.ambientePaiId());
        Integer capacidade = dados.capacidade();

        List<ErroCampo> erros = new ArrayList<>();
        validarNome(nome, erros);
        validarSetor(setorId, erros);
        validarCapacidade(capacidade, erros);
        validarPai(id, ambientePaiId, erros);

        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros);
        }
        return new Ambiente(id, nome, setorId, ambientePaiId, capacidade);
    }

    private static void validarNome(String nome, List<ErroCampo> erros) {
        if (nome == null || nome.isBlank()) {
            erros.add(new ErroCampo("nome", "O nome do ambiente e obrigatorio."));
        } else if (nome.length() > NOME_MAX) {
            erros.add(new ErroCampo("nome", "O nome deve ter no maximo " + NOME_MAX + " caracteres."));
        }
    }

    /** O vinculo ao Setor e obrigatorio (F2.1) e o Setor precisa existir. */
    private void validarSetor(String setorId, List<ErroCampo> erros) {
        if (setorId == null || setorId.isBlank()) {
            erros.add(new ErroCampo("setorId", "O setor do ambiente e obrigatorio."));
            return;
        }
        if (setorRepositorio.buscarPorId(setorId).isEmpty()) {
            erros.add(new ErroCampo("setorId", "Setor informado nao existe."));
        }
    }

    private static void validarCapacidade(Integer capacidade, List<ErroCampo> erros) {
        if (capacidade == null) {
            erros.add(new ErroCampo("capacidade", "A capacidade do ambiente e obrigatoria."));
        } else if (capacidade <= 0) {
            erros.add(new ErroCampo("capacidade", "A capacidade deve ser um inteiro maior que zero."));
        } else if (capacidade > CAPACIDADE_MAX) {
            erros.add(new ErroCampo("capacidade", "A capacidade informada e muito grande."));
        }
    }

    /** Um ambiente nao pode ser o proprio pai; o pai, quando informado, deve existir. */
    private void validarPai(String id, String ambientePaiId, List<ErroCampo> erros) {
        if (ambientePaiId == null) {
            return;
        }
        if (ambientePaiId.equals(id)) {
            erros.add(new ErroCampo("ambientePaiId", "Um ambiente nao pode ser pai de si mesmo."));
            return;
        }
        if (repositorio.buscarPorId(ambientePaiId).isEmpty()) {
            erros.add(new ErroCampo("ambientePaiId", "Ambiente-pai informado nao existe."));
        }
    }

    /**
     * Verifica se definir {@code ambientePaiId} como pai de {@code id} criaria um
     * ciclo na hierarquia (F2.3): sobe pela cadeia de ancestrais a partir do pai
     * proposto; se reencontrar {@code id}, ha ciclo. Protegida contra cadeias ja
     * corrompidas por um teto de profundidade e por deteccao de repeticao.
     *
     * @throws CicloHierarquiaException quando a definicao de pai formaria um ciclo
     */
    private void verificarCiclo(String id, String ambientePaiId) {
        if (ambientePaiId == null) {
            return;
        }
        Set<String> visitados = new HashSet<>();
        String atual = ambientePaiId;
        int passos = 0;
        while (atual != null) {
            if (atual.equals(id)) {
                throw new CicloHierarquiaException(id, ambientePaiId);
            }
            if (!visitados.add(atual) || ++passos > PROFUNDIDADE_MAXIMA) {
                // Ciclo preexistente nos dados ou cadeia anomala: aborta com seguranca.
                throw new CicloHierarquiaException(id, ambientePaiId);
            }
            Optional<Ambiente> ancestral = repositorio.buscarPorId(atual);
            if (ancestral.isEmpty()) {
                break;
            }
            atual = ancestral.get().getAmbientePaiIdOuNulo();
        }
    }

    private static Comparator<Ambiente> comparadorDe(String campo, boolean asc) {
        Comparator<Ambiente> base = switch (campo == null ? "nome" : campo.toLowerCase()) {
            case "setor", "setorid" -> Comparator.comparing(Ambiente::getSetorId, nullsSeguroTexto());
            case "capacidade" -> Comparator.comparing(Ambiente::getCapacidade, nullsSeguroInteiro());
            case "pai", "ambientepaiid" ->
                    Comparator.comparing(Ambiente::getAmbientePaiIdOuNulo, nullsSeguroTexto());
            case "id" -> Comparator.comparing(Ambiente::getId, nullsSeguroTexto());
            default -> Comparator.comparing(Ambiente::getNome, nullsSeguroTexto());
        };
        return asc ? base : base.reversed();
    }

    private static Comparator<String> nullsSeguroTexto() {
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    }

    private static Comparator<Integer> nullsSeguroInteiro() {
        return Comparator.nullsLast(Comparator.naturalOrder());
    }

    private static String normalizarId(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    private static String exigirId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidacaoException(List.of(new ErroCampo("id", "Identificador do ambiente e obrigatorio.")));
        }
        return id.trim();
    }

    /**
     * Dados de entrada para criacao/edicao de Ambiente. Desacopla o servico do
     * formato de transporte (JSON do controller). {@code ambientePaiId} e
     * opcional (ambiente de topo quando ausente).
     */
    public record DadosAmbiente(String nome, String setorId, String ambientePaiId, Integer capacidade) {
    }
}
