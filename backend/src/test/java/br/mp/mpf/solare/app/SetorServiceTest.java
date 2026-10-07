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

import br.mp.mpf.solare.app.SetorService.DadosSetor;
import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.seguranca.AutorizacaoException;
import br.mp.mpf.solare.seguranca.Identidade;
import br.mp.mpf.solare.seguranca.Papel;

/**
 * Testes de unidade do {@link SetorService} (F1): validacao por campo (F1.2),
 * sanitizacao (NF3.3), autorizacao ADMIN (F1.3), CRUD e listagem paginada (F1.4/F1.5).
 */
class SetorServiceTest {

    private SetorRepositorioEmMemoria repositorio;
    private SetorService servico;

    private static Identidade admin() {
        return new Identidade("adm", "Administrador", Set.of(Papel.ADMIN), Papel.ADMIN);
    }

    private static Identidade solicitante() {
        return new Identidade("sol", "Solicitante", Set.of(Papel.SOLICITANTE), Papel.SOLICITANTE);
    }

    private static DadosSetor dadosValidos() {
        return new DadosSetor("Secretaria de Comunicacao", "SECOM", "secom@mpf.mp.br");
    }

    @BeforeEach
    void preparar() {
        repositorio = new SetorRepositorioEmMemoria();
        servico = new SetorService(repositorio);
    }

    @Nested
    @DisplayName("Criacao (F1.1)")
    class Criacao {

        @Test
        @DisplayName("persiste setor valido e gera identificador unico")
        void criaValido() {
            Setor criado = servico.criar(admin(), dadosValidos());

            assertNotNull(criado.getId());
            assertFalse(criado.getId().isBlank());
            assertEquals("Secretaria de Comunicacao", criado.getNome());
            assertEquals("SECOM", criado.getSigla());
            assertEquals("secom@mpf.mp.br", criado.getEmailNotificacao());
            assertTrue(repositorio.buscarPorId(criado.getId()).isPresent());
        }

        @Test
        @DisplayName("gera ids distintos para setores diferentes")
        void idsDistintos() {
            Setor a = servico.criar(admin(), dadosValidos());
            Setor b = servico.criar(admin(), new DadosSetor("Gabinete", "GAB", "gab@mpf.mp.br"));
            assertFalse(a.getId().equals(b.getId()));
        }

        @Test
        @DisplayName("normaliza e-mail para minusculas e aparado")
        void normalizaEmail() {
            Setor criado = servico.criar(admin(),
                    new DadosSetor("Nucleo", "NUC", "  Nucleo@MPF.MP.BR  "));
            assertEquals("nucleo@mpf.mp.br", criado.getEmailNotificacao());
        }
    }

    @Nested
    @DisplayName("Validacao por campo (F1.2)")
    class Validacao {

        @Test
        @DisplayName("rejeita nome vazio com mensagem no campo nome")
        void nomeVazio() {
            ValidacaoException ex = assertThrows(ValidacaoException.class,
                    () -> servico.criar(admin(), new DadosSetor("   ", "SIG", "a@b.co")));
            assertTrue(contemCampo(ex, "nome"));
            assertEquals(0, repositorio.tamanho());
        }

        @Test
        @DisplayName("rejeita e-mail invalido com mensagem no campo emailNotificacao")
        void emailInvalido() {
            ValidacaoException ex = assertThrows(ValidacaoException.class,
                    () -> servico.criar(admin(), new DadosSetor("Nome", "SIG", "sem-arroba")));
            assertTrue(contemCampo(ex, "emailNotificacao"));
        }

        @Test
        @DisplayName("acumula multiplos erros de campo em uma unica excecao")
        void multiplosErros() {
            ValidacaoException ex = assertThrows(ValidacaoException.class,
                    () -> servico.criar(admin(), new DadosSetor("", "", "invalido")));
            assertTrue(contemCampo(ex, "nome"));
            assertTrue(contemCampo(ex, "sigla"));
            assertTrue(contemCampo(ex, "emailNotificacao"));
            assertEquals(3, ex.getErros().size());
        }

        @Test
        @DisplayName("sanitiza metacaracteres de HTML no nome (NF3.3)")
        void sanitizaNome() {
            Setor criado = servico.criar(admin(),
                    new DadosSetor("<script>alert(1)</script>Setor", "SIG", "a@b.co"));
            assertFalse(criado.getNome().contains("<"));
            assertFalse(criado.getNome().contains(">"));
            assertTrue(criado.getNome().contains("&lt;"));
        }
    }

    @Nested
    @DisplayName("Autorizacao ADMIN (F1.3 / NF3.2)")
    class Autorizacao {

        @Test
        @DisplayName("nega criacao para perfil nao-ADMIN com 403")
        void negaCriacaoNaoAdmin() {
            AutorizacaoException ex = assertThrows(AutorizacaoException.class,
                    () -> servico.criar(solicitante(), dadosValidos()));
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
        @DisplayName("nega edicao e exclusao para perfil nao-ADMIN")
        void negaEdicaoExclusao() {
            Setor existente = servico.criar(admin(), dadosValidos());
            assertThrows(AutorizacaoException.class,
                    () -> servico.editar(solicitante(), existente.getId(), dadosValidos()));
            assertThrows(AutorizacaoException.class,
                    () -> servico.excluir(solicitante(), existente.getId()));
        }
    }

    @Nested
    @DisplayName("Edicao (F1.5)")
    class Edicao {

        @Test
        @DisplayName("preserva o id e aplica as alteracoes")
        void editaPreservandoId() {
            Setor original = servico.criar(admin(), dadosValidos());
            Setor editado = servico.editar(admin(), original.getId(),
                    new DadosSetor("Novo Nome", "NOVO", "novo@mpf.mp.br"));

            assertEquals(original.getId(), editado.getId());
            assertEquals("Novo Nome", editado.getNome());
            assertEquals("novo@mpf.mp.br", editado.getEmailNotificacao());
            assertEquals(1, repositorio.tamanho());
        }

        @Test
        @DisplayName("revalida campos na edicao")
        void revalidaNaEdicao() {
            Setor original = servico.criar(admin(), dadosValidos());
            assertThrows(ValidacaoException.class,
                    () -> servico.editar(admin(), original.getId(), new DadosSetor("", "X", "a@b.co")));
        }

        @Test
        @DisplayName("rejeita edicao de setor inexistente com 404")
        void editaInexistente() {
            assertThrows(SetorNaoEncontradoException.class,
                    () -> servico.editar(admin(), "nao-existe", dadosValidos()));
        }
    }

    @Nested
    @DisplayName("Exclusao")
    class Exclusao {

        @Test
        @DisplayName("remove setor existente")
        void excluiExistente() {
            Setor s = servico.criar(admin(), dadosValidos());
            servico.excluir(admin(), s.getId());
            assertEquals(0, repositorio.tamanho());
        }

        @Test
        @DisplayName("rejeita exclusao de setor inexistente com 404")
        void excluiInexistente() {
            assertThrows(SetorNaoEncontradoException.class,
                    () -> servico.excluir(admin(), "fantasma"));
        }
    }

    @Nested
    @DisplayName("Listagem paginada e ordenada (F1.4)")
    class Listagem {

        @BeforeEach
        void popular() {
            servico.criar(admin(), new DadosSetor("Charlie", "C", "c@mpf.mp.br"));
            servico.criar(admin(), new DadosSetor("Alfa", "A", "a@mpf.mp.br"));
            servico.criar(admin(), new DadosSetor("Bravo", "B", "b@mpf.mp.br"));
        }

        @Test
        @DisplayName("ordena por nome ascendente por padrao")
        void ordenaPorNomeAsc() {
            Pagina<Setor> pagina = servico.listar(admin(), ParametrosPagina.de(null, null, null));
            List<Setor> c = pagina.getConteudo();
            assertEquals("Alfa", c.get(0).getNome());
            assertEquals("Bravo", c.get(1).getNome());
            assertEquals("Charlie", c.get(2).getNome());
            assertEquals(3, pagina.getTotal());
        }

        @Test
        @DisplayName("ordena por nome descendente quando sort=nome,desc")
        void ordenaPorNomeDesc() {
            Pagina<Setor> pagina = servico.listar(admin(), ParametrosPagina.de(null, null, "nome,desc"));
            assertEquals("Charlie", pagina.getConteudo().get(0).getNome());
        }

        @Test
        @DisplayName("aplica paginacao: primeira pagina com tamanho 2")
        void paginaPrimeira() {
            Pagina<Setor> pagina = servico.listar(admin(), ParametrosPagina.de("0", "2", "nome,asc"));
            assertEquals(2, pagina.getConteudo().size());
            assertEquals("Alfa", pagina.getConteudo().get(0).getNome());
            assertEquals(3, pagina.getTotal());
            assertEquals(2, pagina.getTotalPaginas());
        }

        @Test
        @DisplayName("aplica paginacao: segunda pagina com o restante")
        void paginaSegunda() {
            Pagina<Setor> pagina = servico.listar(admin(), ParametrosPagina.de("1", "2", "nome,asc"));
            assertEquals(1, pagina.getConteudo().size());
            assertEquals("Charlie", pagina.getConteudo().get(0).getNome());
        }
    }

    private static boolean contemCampo(ValidacaoException ex, String campo) {
        return ex.getErros().stream().anyMatch(e -> e.getCampo().equals(campo));
    }
}
