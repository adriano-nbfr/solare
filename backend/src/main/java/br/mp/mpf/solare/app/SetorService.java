package br.mp.mpf.solare.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.dominio.repositorio.SetorRepository;
import br.mp.mpf.solare.seguranca.Autorizacao;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.Papel;

/**
 * Caso de uso de cadastro de Setores (F1). Orquestra autorizacao (apenas ADMIN —
 * F1.3), validacao de forma por campo (F1.2), sanitizacao de entradas (NF3.3) e
 * persistencia via {@link SetorRepository}, preservando o dominio puro.
 *
 * <p>A listagem (F1.4) aplica paginacao e ordenacao em memoria sobre o resultado
 * do repositorio, coerente com o volume esperado do cadastro de setores no MVP.</p>
 */
public final class SetorService {

    /**
     * Validacao de e-mail pragmatica: parte local, {@code @}, dominio com ao
     * menos um ponto. Suficiente para F1 sem a complexidade da RFC completa.
     */
    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final int NOME_MAX = 120;
    private static final int SIGLA_MAX = 20;
    private static final int EMAIL_MAX = 254;

    private final SetorRepository repositorio;

    public SetorService(SetorRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Cria um novo Setor (F1.1). Gera um identificador unico. Exige papel ADMIN.
     *
     * @throws br.mp.mpf.solare.seguranca.AutorizacaoException quando nao for ADMIN
     * @throws ValidacaoException quando os campos forem invalidos
     */
    public Setor criar(Identidade identidade, DadosSetor dados) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        Setor setor = montarValidado(UUID.randomUUID().toString(), dados);
        repositorio.salvar(setor);
        return setor;
    }

    /**
     * Edita um Setor existente (F1.5), preservando o identificador e revalidando
     * os campos. Exige papel ADMIN.
     *
     * @throws SetorNaoEncontradoException quando o id nao existe
     */
    public Setor editar(Identidade identidade, String id, DadosSetor dados) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        String idLimpo = exigirId(id);
        if (repositorio.buscarPorId(idLimpo).isEmpty()) {
            throw new SetorNaoEncontradoException(idLimpo);
        }
        Setor atualizado = montarValidado(idLimpo, dados);
        repositorio.salvar(atualizado);
        return atualizado;
    }

    /** Exclui um Setor (F1.3 — restrito a ADMIN). */
    public void excluir(Identidade identidade, String id) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        String idLimpo = exigirId(id);
        if (repositorio.buscarPorId(idLimpo).isEmpty()) {
            throw new SetorNaoEncontradoException(idLimpo);
        }
        repositorio.excluir(idLimpo);
    }

    /** Busca um Setor por id (restrito a ADMIN). */
    public Optional<Setor> buscar(Identidade identidade, String id) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        return repositorio.buscarPorId(exigirId(id));
    }

    /**
     * Lista Setores com paginacao e ordenacao (F1.4). Exige papel ADMIN.
     * Campos de ordenacao suportados: {@code nome} (padrao), {@code sigla},
     * {@code email}/{@code emailNotificacao}, {@code id}.
     */
    public Pagina<Setor> listar(Identidade identidade, ParametrosPagina parametros) {
        Autorizacao.exigirPapel(identidade, Papel.ADMIN);
        ParametrosPagina p = parametros == null
                ? ParametrosPagina.de(null, null, null)
                : parametros;

        List<Setor> todos = new ArrayList<>(repositorio.listarTodos());
        todos.sort(comparadorDe(p.getOrdenarPor(), p.isAscendente()));

        long total = todos.size();
        int de = Math.min(p.getPagina() * p.getTamanho(), todos.size());
        int ate = Math.min(de + p.getTamanho(), todos.size());
        List<Setor> fatia = todos.subList(de, ate);

        return new Pagina<>(fatia, p.getPagina(), p.getTamanho(), total);
    }

    // --- internos ------------------------------------------------------------

    private Setor montarValidado(String id, DadosSetor dados) {
        if (dados == null) {
            throw new ValidacaoException(List.of(new ErroCampo("setor", "Dados do setor ausentes.")));
        }
        String nome = Sanitizador.texto(dados.nome());
        String sigla = Sanitizador.texto(dados.sigla());
        String email = normalizarEmail(dados.emailNotificacao());

        List<ErroCampo> erros = new ArrayList<>();
        validarNome(nome, erros);
        validarSigla(sigla, erros);
        validarEmail(email, erros);

        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros);
        }
        return new Setor(id, nome, sigla, email);
    }

    private static void validarNome(String nome, List<ErroCampo> erros) {
        if (nome == null || nome.isBlank()) {
            erros.add(new ErroCampo("nome", "O nome do setor e obrigatorio."));
        } else if (nome.length() > NOME_MAX) {
            erros.add(new ErroCampo("nome", "O nome deve ter no maximo " + NOME_MAX + " caracteres."));
        }
    }

    private static void validarSigla(String sigla, List<ErroCampo> erros) {
        if (sigla == null || sigla.isBlank()) {
            erros.add(new ErroCampo("sigla", "A sigla do setor e obrigatoria."));
        } else if (sigla.length() > SIGLA_MAX) {
            erros.add(new ErroCampo("sigla", "A sigla deve ter no maximo " + SIGLA_MAX + " caracteres."));
        }
    }

    private static void validarEmail(String email, List<ErroCampo> erros) {
        if (email == null || email.isBlank()) {
            erros.add(new ErroCampo("emailNotificacao", "O e-mail de notificacao e obrigatorio."));
        } else if (email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches()) {
            erros.add(new ErroCampo("emailNotificacao", "Informe um e-mail de notificacao valido."));
        }
    }

    /** E-mail nao recebe escape de HTML (nao e reexibido como markup) — apenas aparado e minusculo. */
    private static String normalizarEmail(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim().toLowerCase();
        return limpo.isEmpty() ? null : limpo;
    }

    private static Comparator<Setor> comparadorDe(String campo, boolean asc) {
        Comparator<Setor> base = switch (campo == null ? "nome" : campo.toLowerCase()) {
            case "sigla" -> Comparator.comparing(Setor::getSigla, nullsSeguro());
            case "email", "emailnotificacao" ->
                    Comparator.comparing(Setor::getEmailNotificacao, nullsSeguro());
            case "id" -> Comparator.comparing(Setor::getId, nullsSeguro());
            default -> Comparator.comparing(Setor::getNome, nullsSeguro());
        };
        return asc ? base : base.reversed();
    }

    private static Comparator<String> nullsSeguro() {
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    }

    private static String exigirId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidacaoException(List.of(new ErroCampo("id", "Identificador do setor e obrigatorio.")));
        }
        return id.trim();
    }

    /**
     * Dados de entrada para criacao/edicao de Setor. Desacopla o servico do
     * formato de transporte (JSON do controller).
     */
    public record DadosSetor(String nome, String sigla, String emailNotificacao) {
    }
}
