import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RecursosService } from './recursos.service';
import {
  DadosRecurso,
  ErrosRecurso,
  Recurso,
  TipoRecurso,
  recursoValido,
  rotuloTipo,
  validarRecurso,
} from './recurso';

/**
 * Tela de cadastro e listagem de Recursos (F3), acessível (NF2): rótulos
 * associados, mensagens anunciadas via `aria-live`, operável por teclado, foco
 * visível e contraste herdado dos componentes DSMPF.
 *
 * O tipo é escolhido num `select`; o campo de quantidade só aparece (e só é
 * exigido) para recursos LIMITADO, espelhando a validação do backend (recurso
 * ilimitado não carrega quantidade).
 */
@Component({
  selector: 'app-recursos',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './recursos.html',
  styleUrl: './recursos.scss',
})
export class Recursos implements OnInit {
  private readonly servico = inject(RecursosService);
  private readonly fb = inject(FormBuilder);

  protected readonly recursos = signal<Recurso[]>([]);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  /** Mensagem de status para leitores de tela (aria-live). */
  protected readonly mensagem = signal<string>('');
  protected readonly erroGeral = signal<string>('');
  /** Erros por campo espelhando a validação; preenchidos no submit. */
  protected readonly errosCampo = signal<ErrosRecurso>({});

  /** Id em edição; `null` significa criação de um novo recurso. */
  protected readonly editandoId = signal<string | null>(null);

  /** Tipo selecionado, para alternar a exibição do campo de quantidade. */
  protected readonly tipoSelecionado = signal<TipoRecurso | ''>('');

  /** Quantidade só se aplica a recursos limitados. */
  protected readonly exigeQuantidade = computed(() => this.tipoSelecionado() === 'LIMITADO');

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(120)]],
    tipo: ['' as TipoRecurso | '', [Validators.required]],
    quantidadeTotal: [null as number | null],
  });

  ngOnInit(): void {
    this.carregar();
    // Mantém o estado do tipo sincronizado para controlar o campo de quantidade.
    this.form.controls.tipo.valueChanges.subscribe((tipo) => {
      this.tipoSelecionado.set(tipo ?? '');
      if (tipo !== 'LIMITADO') {
        // Recurso ilimitado não carrega quantidade: limpa o valor residual.
        this.form.controls.quantidadeTotal.setValue(null);
      }
    });
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erroGeral.set('');
    this.servico.listar({ size: 200, sort: 'nome,asc' }).subscribe({
      next: (pagina) => {
        this.recursos.set(pagina.conteudo ?? []);
        this.carregando.set(false);
        this.mensagem.set(`${pagina.total ?? 0} recurso(s) carregado(s).`);
      },
      error: () => {
        this.carregando.set(false);
        this.erroGeral.set('Não foi possível carregar os recursos. Tente novamente.');
      },
    });
  }

  protected novo(): void {
    this.editandoId.set(null);
    this.form.reset({ nome: '', tipo: '', quantidadeTotal: null });
    this.tipoSelecionado.set('');
    this.errosCampo.set({});
    this.erroGeral.set('');
  }

  protected editar(recurso: Recurso): void {
    this.editandoId.set(recurso.id);
    this.tipoSelecionado.set(recurso.tipo);
    this.form.setValue({
      nome: recurso.nome,
      tipo: recurso.tipo,
      quantidadeTotal: recurso.tipo === 'LIMITADO' ? recurso.quantidadeTotal ?? null : null,
    });
    this.errosCampo.set({});
    this.erroGeral.set('');
  }

  protected salvar(): void {
    const bruto = this.form.getRawValue();
    const limitado = bruto.tipo === 'LIMITADO';
    const dados: DadosRecurso = {
      nome: bruto.nome,
      tipo: bruto.tipo,
      // Só envia quantidade para recursos limitados (ilimitado não a carrega).
      quantidadeTotal: limitado ? bruto.quantidadeTotal : null,
    };

    const erros = validarRecurso(dados);
    this.errosCampo.set(erros);
    if (!recursoValido(erros)) {
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
        this.mensagem.set(id ? 'Recurso atualizado com sucesso.' : 'Recurso criado com sucesso.');
        this.novo();
        this.carregar();
      },
      error: (resp) => {
        this.salvando.set(false);
        this.aplicarErrosServidor(resp);
      },
    });
  }

  protected excluir(recurso: Recurso): void {
    this.servico.excluir(recurso.id).subscribe({
      next: () => {
        this.mensagem.set(`Recurso ${recurso.nome} excluído.`);
        if (this.editandoId() === recurso.id) {
          this.novo();
        }
        this.carregar();
      },
      error: (resp) => {
        const corpo = (resp as { error?: { mensagem?: string } })?.error;
        this.erroGeral.set(corpo?.mensagem ?? 'Não foi possível excluir o recurso.');
      },
    });
  }

  protected cancelarEdicao(): void {
    this.novo();
  }

  /** Rótulo legível do tipo para exibição na tabela. */
  protected rotuloTipo(tipo: TipoRecurso): string {
    return rotuloTipo(tipo);
  }

  /** Exibição da quantidade na tabela (traço para recursos ilimitados). */
  protected exibirQuantidade(recurso: Recurso): string {
    return recurso.tipo === 'LIMITADO' ? String(recurso.quantidadeTotal ?? '') : '—';
  }

  /** Mapeia erros de validação do servidor (400) para os campos do formulário. */
  private aplicarErrosServidor(resp: unknown): void {
    const corpo = (resp as { error?: { erros?: { campo: string; mensagem: string }[]; mensagem?: string } })?.error;
    if (corpo?.erros?.length) {
      const mapa: ErrosRecurso = {};
      for (const e of corpo.erros) {
        mapa[e.campo as keyof ErrosRecurso] = e.mensagem;
      }
      this.errosCampo.set(mapa);
      this.mensagem.set('Há campos inválidos no formulário.');
      return;
    }
    this.erroGeral.set(corpo?.mensagem ?? 'Não foi possível salvar o recurso.');
  }
}
