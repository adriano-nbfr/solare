import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SetoresService } from '../setores/setores.service';
import { Setor } from '../setores/setor';
import { AmbientesService } from './ambientes.service';
import {
  Ambiente,
  DadosAmbiente,
  ErrosAmbiente,
  NoArvoreAmbiente,
  achatarArvore,
  ambienteValido,
  montarArvore,
  validarAmbiente,
} from './ambiente';

/**
 * Tela de cadastro, listagem e visualização em árvore de Ambientes (F2),
 * acessível (NF2): rótulos associados, mensagens anunciadas via `aria-live`,
 * operável por teclado, foco visível e contraste herdado dos componentes DSMPF.
 *
 * Reúne numa única visão o formulário (com seleção de setor e de ambiente-pai),
 * a árvore pai/filho (F2.2) e a lista completa, para o fluxo de administração.
 */
@Component({
  selector: 'app-ambientes',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ambientes.html',
  styleUrl: './ambientes.scss',
})
export class Ambientes implements OnInit {
  private readonly servico = inject(AmbientesService);
  private readonly setoresServico = inject(SetoresService);
  private readonly fb = inject(FormBuilder);

  protected readonly ambientes = signal<Ambiente[]>([]);
  protected readonly setores = signal<Setor[]>([]);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  /** Mensagem de status para leitores de tela (aria-live). */
  protected readonly mensagem = signal<string>('');
  protected readonly erroGeral = signal<string>('');
  /** Erros por campo espelhando a validação; preenchidos no submit. */
  protected readonly errosCampo = signal<ErrosAmbiente>({});

  /** Id em edição; `null` significa criação de um novo ambiente. */
  protected readonly editandoId = signal<string | null>(null);

  /** Árvore pai/filho achatada (pré-ordem) para exibição indentada e acessível (F2.2). */
  protected readonly arvore = computed<NoArvoreAmbiente[]>(() =>
    achatarArvore(montarArvore(this.ambientes())),
  );

  /**
   * Opções válidas de ambiente-pai: todos os ambientes exceto o que está em
   * edição (impede o auto-ciclo mais óbvio já no cliente — F2.3). O servidor
   * continua sendo a autoridade para ciclos indiretos.
   */
  protected readonly opcoesPai = computed<Ambiente[]>(() => {
    const id = this.editandoId();
    return this.ambientes().filter((a) => a.id !== id);
  });

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(160)]],
    setorId: ['', [Validators.required]],
    ambientePaiId: [''],
    capacidade: [null as number | null, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    this.carregarSetores();
    this.carregar();
  }

  protected carregarSetores(): void {
    this.setoresServico.listar({ size: 200, sort: 'nome,asc' }).subscribe({
      next: (pagina) => this.setores.set(pagina.conteudo ?? []),
      error: () => this.erroGeral.set('Não foi possível carregar os setores.'),
    });
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erroGeral.set('');
    // Tamanho amplo para montar a árvore completa; o cadastro de ambientes do MVP é pequeno.
    this.servico.listar({ size: 200, sort: 'nome,asc' }).subscribe({
      next: (pagina) => {
        this.ambientes.set(pagina.conteudo ?? []);
        this.carregando.set(false);
        this.mensagem.set(`${pagina.total ?? 0} ambiente(s) carregado(s).`);
      },
      error: () => {
        this.carregando.set(false);
        this.erroGeral.set('Não foi possível carregar os ambientes. Tente novamente.');
      },
    });
  }

  protected novo(): void {
    this.editandoId.set(null);
    this.form.reset({ nome: '', setorId: '', ambientePaiId: '', capacidade: null });
    this.errosCampo.set({});
    this.erroGeral.set('');
  }

  protected editar(ambiente: Ambiente): void {
    this.editandoId.set(ambiente.id);
    this.form.setValue({
      nome: ambiente.nome,
      setorId: ambiente.setorId,
      ambientePaiId: ambiente.ambientePaiId ?? '',
      capacidade: ambiente.capacidade,
    });
    this.errosCampo.set({});
    this.erroGeral.set('');
  }

  protected salvar(): void {
    const bruto = this.form.getRawValue();
    const dados: DadosAmbiente = {
      nome: bruto.nome,
      setorId: bruto.setorId,
      ambientePaiId: bruto.ambientePaiId ? bruto.ambientePaiId : null,
      capacidade: bruto.capacidade,
    };

    const erros = validarAmbiente(dados, this.editandoId());
    this.errosCampo.set(erros);
    if (!ambienteValido(erros)) {
      this.form.markAllAsTouched();
      this.mensagem.set('Há campos inválidos no formulário.');
      return;
    }

    this.salvando.set(true);
    const id = this.editandoId();
    const requisicao = id ? this.servico.editar(id, dados) : this.servico.criar(dados);

    requisicao.subscribe({
      next: () => {
        this.salvando.set(false);
        this.mensagem.set(id ? 'Ambiente atualizado com sucesso.' : 'Ambiente criado com sucesso.');
        this.novo();
        this.carregar();
      },
      error: (resp) => {
        this.salvando.set(false);
        this.aplicarErrosServidor(resp);
      },
    });
  }

  protected excluir(ambiente: Ambiente): void {
    this.servico.excluir(ambiente.id).subscribe({
      next: () => {
        this.mensagem.set(`Ambiente ${ambiente.nome} excluído.`);
        if (this.editandoId() === ambiente.id) {
          this.novo();
        }
        this.carregar();
      },
      error: (resp) => {
        const corpo = (resp as { error?: { mensagem?: string }; status?: number })?.error;
        // 409 indica bloqueio por filhos vinculados (F2.5).
        this.erroGeral.set(corpo?.mensagem ?? 'Não foi possível excluir o ambiente.');
      },
    });
  }

  protected cancelarEdicao(): void {
    this.novo();
  }

  /** Nome do setor para exibição na lista/árvore (fallback para o id). */
  protected nomeSetor(setorId: string): string {
    return this.setores().find((s) => s.id === setorId)?.sigla ?? setorId;
  }

  /** Nome do ambiente-pai para exibição (vazio quando é raiz). */
  protected nomePai(paiId: string | null | undefined): string {
    if (!paiId) {
      return '—';
    }
    return this.ambientes().find((a) => a.id === paiId)?.nome ?? paiId;
  }

  /** Mapeia erros de validação do servidor (400) para os campos do formulário. */
  private aplicarErrosServidor(resp: unknown): void {
    const corpo = (resp as { error?: { erros?: { campo: string; mensagem: string }[]; mensagem?: string } })?.error;
    if (corpo?.erros?.length) {
      const mapa: ErrosAmbiente = {};
      for (const e of corpo.erros) {
        mapa[e.campo as keyof ErrosAmbiente] = e.mensagem;
      }
      this.errosCampo.set(mapa);
      this.mensagem.set('Há campos inválidos no formulário.');
      return;
    }
    this.erroGeral.set(corpo?.mensagem ?? 'Não foi possível salvar o ambiente.');
  }
}
