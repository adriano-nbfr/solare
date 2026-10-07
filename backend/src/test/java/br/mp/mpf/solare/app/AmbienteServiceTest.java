package br.mp.mpf.solare.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.mp.mpf.solare.app.AmbienteService.DadosAmbiente;
import br.mp.mpf.solare.dominio.Ambiente;
import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.seguranca.AutorizacaoException;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.Papel;

/**
 * Testes de unidade do {@link AmbienteService} (F2): validacao por campo,
 * sanitizacao (NF3.3), autorizacao ADMIN (NF3.2), vinculo ao Setor, relacao
 * pai/filho e consulta de filhos (F2.2), verificacao anti-ciclo (F2.3), bloqueio
 * de exclusao com filhos (F2.5) e listagem paginada (F2.4).
 */
class AmbienteServiceTest {

    private AmbienteRepositorioEmMemoria repositorio;
    private SetorRepositorioEmMemoria setores;
    private AmbienteService servico;

    private static final String SETOR_ID = "S1";

    private static Identidade admin() {
        return new Identidade("adm", "Administrador", Set.of(Papel.ADMIN), Papel.ADMIN);
    }

    private static Identidade solicitante() {
        return new Identidade("sol", "Solicitante", Set.of(Papel.SOLICITANTE), Papel.SOLICITANTE);
    }

    private static DadosAmbiente dadosRaiz() {
        return new DadosAmbiente("Auditorio", SETOR_ID, null, 100);
    }

    @BeforeEach
    void preparar() {
        repositorio = new AmbienteRepositorioEmMemoria();
        setores = new SetorRepositorioEmMemoria();
        setores.salvar(new Setor(SETOR_ID, "Secretaria", "SEC", "sec@mpf.mp.br"));
        servico = new AmbienteService(repositorio, setores);
    }

    @Nested
    @DisplayName("Criacao e vinculo ao Setor (F2.1)")
    class Criacao {

        @Test
        @DisplayName("persiste ambiente valido vinculado ao setor e gera id unico")
        void criaValido() {
            Ambiente criado = servico.criar(admin(), dadosRaiz());

            assertNotNull(criado.getId());
            assertFalse(criado.getId().isBlank());
            assertEquals("Auditorio", criado.getNome());
            assertEquals(SETOR_ID, criado.getSetorId());
            assertEquals(100, criado.getCapacidade());
            assertFalse(criado.temPai());
            assertTrue(repositorio.buscarPorId(criado.getId()).isPresent());
        }

        @Test
        @DisplayName("rejeita quando o setor informado nao existe")
        void setorInexistente() {
            ValidacaoException ex = assertThrows(ValidacaoException.class,
                    () -> servico.criar(admin(), new DadosAmbiente("Sala", "SX", null, 10)));
            assertTrue(contemCampo(ex, "setorId"));
            assertEquals(0, repositorio.tamanho());
        }

        @Test
        @DisplayName("rejeita setor ausente, nome vazio e capacidade invalida acumulando erros")
        void camposObrigatorios() {
            ValidacaoException ex = assertThrows(ValidacaoException.class,
                    () -> servico.criar(admin(), new DadosAmbiente("  ", null, null, 0)));
            assertTrue(contemCampo(ex, "nome"));
            assertTrue(contemCampo(ex, "setorId"));
            assertTrue(contemCampo(ex, "capacidade"));
        }

        @Test
        @DisplayName("sanitiza metacaracteres de HTML no nome (NF3.3)")
        void sanitizaNome() {
            Ambiente criado = servico.criar(admin(),
                    new DadosAmbiente("<b>Sala</b>", SETOR_ID, null, 5));
            assertFalse(criado.getNome().contains("<"));
            assertTrue(criado.getNome().contains("&lt;"));
        }
    }

    @Nested
    @DisplayName("Relacao pai/filho e consulta de filhos (F2.2)")
    class Hierarquia {

        @Test
        @DisplayName("registra pai e permite consultar os filhos de um pai")
        void paiEFilhos() {
            Ambiente pai = servico.criar(admin(), new DadosAmbiente("Andar 1", SETOR_ID, null, 200));
            Ambiente filhoA = servico.criar(admin(),
                    new DadosAmbiente("Sala 101", SETOR_ID, pai.getId(), 20));
            Ambiente filhoB = servico.criar(admin(),
                    new DadosAmbiente("Sala 102", SETOR_ID, pai.getId(), 20));
            servico.criar(admin(), new DadosAmbiente("Outro andar", SETOR_ID, null, 50));

            assertEquals(pai.getId(), filhoA.getAmbientePaiIdOuNulo());

            List<Ambiente> filhos = servico.listarFilhos(admin(), pai.getId());
            assertEquals(2, filhos.size());
            assertTrue(filhos.stream().anyMatch(a -> a.getId().equals(filhoA.getId())));
            assertTrue(filhos.stream().anyMatch(a -> a.getId().equals(filhoB.getId())));
        }

        @Test
        @DisplayName("rejeita pai inexistente")
        void paiInexistente() {
            ValidacaoException ex = assertThrows(ValidacaoException.class,
                    () -> servico.criar(admin(), new DadosAmbiente("Sala", SETOR_ID, "fantasma", 10)));
            assertTrue(contemCampo(ex, "ambientePaiId"));
        }
    }

    @Nested
    @DisplayName("Verificacao anti-ciclo (F2.3)")
    class AntiCiclo {

        @Test
        @DisplayName("rejeita um ambiente como pai de si mesmo (ciclo de tamanho 1) na edicao")
        void autoReferencia() {
            Ambiente a = servico.criar(admin(), dadosRaiz());
            // Edicao definindo o proprio id como pai → ciclo direto.
            ValidacaoException ex = assertThrows(ValidacaoException.class,
                    () -> servico.editar(admin(), a.getId(),
                            new DadosAmbiente("Auditorio", SETOR_ID, a.getId(), 100)));
            assertTrue(contemCampo(ex, "ambientePaiId"));
        }

        @Test
        @DisplayName("rejeita ciclo indireto A->B->C e tentativa de A virar pai de C")
        void cicloIndireto() {
            Ambiente a = servico.criar(admin(), new DadosAmbiente("A", SETOR_ID, null, 10));
            Ambiente b = servico.criar(admin(), new DadosAmbiente("B", SETOR_ID, a.getId(), 10));
            Ambiente c = servico.criar(admin(), new DadosAmbiente("C", SETOR_ID, b.getId(), 10));

            // Tornar A filho de C fecharia o ciclo A->B->C->A.
            assertThrows(CicloHierarquiaException.class,
                    () -> servico.editar(admin(), a.getId(),
                            new DadosAmbiente("A", SETOR_ID, c.getId(), 10)));

            // A hierarquia original permanece intacta.
            assertEquals(a.getId(), repositorio.buscarPorId(b.getId()).orElseThrow().getAmbientePaiIdOuNulo());
        }

        @Test
        @DisplayName("permite reparentar sem fechar ciclo (filho recebe novo pai valido)")
        void reparentarValido() {
            Ambiente raiz1 = servico.criar(admin(), new DadosAmbiente("Raiz1", SETOR_ID, null, 10));
            Ambiente raiz2 = servico.criar(admin(), new DadosAmbiente("Raiz2", SETOR_ID, null, 10));
            Ambiente filho = servico.criar(admin(), new DadosAmbiente("Filho", SETOR_ID, raiz1.getId(), 10));

            Ambiente movido = servico.editar(admin(), filho.getId(),
                    new DadosAmbiente("Filho", SETOR_ID, raiz2.getId(), 10));
            assertEquals(raiz2.getId(), movido.getAmbientePaiIdOuNulo());
        }

        @Test
        @DisplayName("rejeita ciclo profundo em cadeia de varios niveis")
        void cicloProfundo() {
            // Cadeia N0 (raiz) -> N1 -> ... -> N5
            String paiAnterior = null;
            String[] ids = new String[6];
            for (int i = 0; i < 6; i++) {
                Ambiente n = servico.criar(admin(),
                        new DadosAmbiente("N" + i, SETOR_ID, paiAnterior, 10));
                ids[i] = n.getId();
                paiAnterior = n.getId();
            }
            // Tornar a raiz (N0) filha do ultimo (N5) fecharia o ciclo.
            assertThrows(CicloHierarquiaException.class,
                    () -> servico.editar(admin(), ids[0],
                            new DadosAmbiente("N0", SETOR_ID, ids[5], 10)));
        }
    }

    @Nested
    @DisplayName("Autorizacao ADMIN (NF3.2)")
    class Autorizacao {

        @Test
        @DisplayName("nega criacao para perfil nao-ADMIN com 403")
        void negaCriacaoNaoAdmin() {
            AutorizacaoException ex = assertThrows(AutorizacaoException.class,
                    () -> servico.criar(solicitante(), dadosRaiz()));
            assertFalse(ex.isNaoAutenticado());
            assertEquals(0, repositorio.tamanho());
        }

        @Test
        @DisplayName("nega acesso para identidade anonima com 401")
        void negaAnonimo() {
            AutorizacaoException ex = assertThrows(AutorizacaoException.class,
                    () -> servico.listar(Identidade.anonima(), ParametrosPagina.de(null, null, null)));
            assertTrue(ex.isNaoAutenticado());
        }

        @Test
        @DisplayName("nega edicao, exclusao e listagem de filhos para nao-ADMIN")
        void negaMutacoes() {
            Ambiente existente = servico.criar(admin(), dadosRaiz());
            assertThrows(AutorizacaoException.class,
                    () -> servico.editar(solicitante(), existente.getId(), dadosRaiz()));
            assertThrows(AutorizacaoException.class,
                    () -> servico.excluir(solicitante(), existente.getId()));
            assertThrows(AutorizacaoException.class,
                    () -> servico.listarFilhos(solicitante(), existente.getId()));
        }
    }

    @Nested
    @DisplayName("Edicao (F2.1)")
    class Edicao {

        @Test
        @DisplayName("preserva o id e aplica as alteracoes")
        void editaPreservandoId() {
            Ambiente original = servico.criar(admin(), dadosRaiz());
            Ambiente editado = servico.editar(admin(), original.getId(),
                    new DadosAmbiente("Auditorio Nobre", SETOR_ID, null, 150));

            assertEquals(original.getId(), editado.getId());
            assertEquals("Auditorio Nobre", editado.getNome());
            assertEquals(150, editado.getCapacidade());
            assertEquals(1, repositorio.tamanho());
        }

        @Test
        @DisplayName("rejeita edicao de ambiente inexistente com 404")
        void editaInexistente() {
            assertThrows(AmbienteNaoEncontradoException.class,
                    () -> servico.editar(admin(), "nao-existe", dadosRaiz()));
        }
    }

    @Nested
    @DisplayName("Exclusao com bloqueio por filhos (F2.5)")
    class Exclusao {

        @Test
        @DisplayName("remove ambiente folha existente")
        void excluiFolha() {
            Ambiente a = servico.criar(admin(), dadosRaiz());
            servico.excluir(admin(), a.getId());
            assertEquals(0, repositorio.tamanho());
        }

        @Test
        @DisplayName("impede exclusao de pai com filhos vinculados (409)")
        void bloqueiaPaiComFilhos() {
            Ambiente pai = servico.criar(admin(), new DadosAmbiente("Pai", SETOR_ID, null, 50));
            servico.criar(admin(), new DadosAmbiente("Filho", SETOR_ID, pai.getId(), 10));

            AmbienteComFilhosException ex = assertThrows(AmbienteComFilhosException.class,
                    () -> servico.excluir(admin(), pai.getId()));
            assertEquals(1, ex.getQuantidadeFilhos());
            assertTrue(repositorio.buscarPorId(pai.getId()).isPresent());
        }

        @Test
        @DisplayName("rejeita exclusao de ambiente inexistente com 404")
        void excluiInexistente() {
            assertThrows(AmbienteNaoEncontradoException.class,
                    () -> servico.excluir(admin(), "fantasma"));
        }
    }

    @Nested
    @DisplayName("Listagem paginada e ordenada (F2.4)")
    class Listagem {

        @BeforeEach
        void popular() {
            servico.criar(admin(), new DadosAmbiente("Charlie", SETOR_ID, null, 30));
            servico.criar(admin(), new DadosAmbiente("Alfa", SETOR_ID, null, 10));
            servico.criar(admin(), new DadosAmbiente("Bravo", SETOR_ID, null, 20));
        }

        @Test
        @DisplayName("ordena por nome ascendente por padrao")
        void ordenaPorNomeAsc() {
            Pagina<Ambiente> pagina = servico.listar(admin(), ParametrosPagina.de(null, null, null));
            List<Ambiente> c = pagina.getConteudo();
            assertEquals("Alfa", c.get(0).getNome());
            assertEquals("Bravo", c.get(1).getNome());
            assertEquals("Charlie", c.get(2).getNome());
            assertEquals(3, pagina.getTotal());
        }

        @Test
        @DisplayName("ordena por capacidade descendente")
        void ordenaPorCapacidadeDesc() {
            Pagina<Ambiente> pagina = servico.listar(admin(), ParametrosPagina.de(null, null, "capacidade,desc"));
            assertEquals(30, pagina.getConteudo().get(0).getCapacidade());
        }

        @Test
        @DisplayName("aplica paginacao: primeira pagina com tamanho 2")
        void paginaPrimeira() {
            Pagina<Ambiente> pagina = servico.listar(admin(), ParametrosPagina.de("0", "2", "nome,asc"));
            assertEquals(2, pagina.getConteudo().size());
            assertEquals(3, pagina.getTotal());
            assertEquals(2, pagina.getTotalPaginas());
        }
    }

    private static boolean contemCampo(ValidacaoException ex, String campo) {
        return ex.getErros().stream().anyMatch(e -> e.getCampo().equals(campo));
    }
}
