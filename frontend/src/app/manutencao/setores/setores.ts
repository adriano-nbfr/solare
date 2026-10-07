import { ChangeDetectionStrategy, Component, OnInit, signal, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SetoresService } from './setores.service';
import { DadosSetor, ErrosSetor, Setor, setorValido, validarSetor } from './setor';

/**
 * Tela de cadastro e listagem de Setores (F1), acessível (NF2): rótulos
 * associados, mensagens de erro anunciadas via `aria-live`, operável por
 * teclado, foco visível e contraste adequado herdado dos componentes DSMPF.
 *
 * Mantém o formulário e a listagem numa única visão para o fluxo de administração.
 */
@Component({
  selector: 'app-setores',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './setores.html',
  styleUrl: './setores.scss',
})
export class Setores implements OnInit {
  private readonly servico = inject(SetoresService);
  private readonly fb = inject(FormBuilder);

  protected readonly setores = signal<Setor[]>([]);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  /** Mensagem de status para leitores de tela (aria-live). */
  protected readonly mensagem = signal<string>('');
  protected readonly erroGeral = signal<string>('');
  /** Erros por campo espelhando a validação; preenchidos no submit. */
  protected readonly errosCampo = signal<ErrosSetor>({});

  /** Id em edição; `null` significa criação de um novo setor. */
  protected readonly editandoId = signal<string | null>(null);

  /** Ordenação atual da listagem (campo e direção). */
  protected readonly ordenarPor = signal<string>('nome');
  protected readonly ascendente = signal<boolean>(true);

  protected readonly form = this.fb.nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(120)]],
    sigla: ['', [Validators.required, Validators.maxLength(20)]],
    emailNotificacao: ['', [Validators.required, Validators.email]],
  });

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erroGeral.set('');
    const sort = `${this.ordenarPor()},${this.ascendente() ? 'asc' : 'desc'}`;
    this.servico.listar({ sort }).subscribe({
      next: (pagina) => {
        this.setores.set(pagina.conteudo ?? []);
        this.carregando.set(false);
        this.mensagem.set(`${pagina.total ?? 0} setor(es) carregado(s).`);
      },
      error: () => {
        this.carregando.set(false);
        this.erroGeral.set('Não foi possível carregar os setores. Tente novamente.');
      },
    });
  }

  protected ordenar(campo: string): void {
    if (this.ordenarPor() === campo) {
      this.ascendente.update((v) => !v);
    } else {
      this.ordenarPor.set(campo);
      this.ascendente.set(true);
    }
    this.carregar();
  }

  /** Direção de ordenação do campo para `aria-sort`. */
  protected ariaSort(campo: string): 'ascending' | 'descending' | 'none' {
    if (this.ordenarPor() !== campo) {
      return 'none';
    }
    return this.ascendente() ? 'ascending' : 'descending';
  }

  protected novo(): void {
    this.editandoId.set(null);
    this.form.reset({ nome: '', sigla: '', emailNotificacao: '' });
    this.errosCampo.set({});
    this.erroGeral.set('');
  }

  protected editar(setor: Setor): void {
    this.editandoId.set(setor.id);
    this.form.setValue({
      nome: setor.nome,
      sigla: setor.sigla,
      emailNotificacao: setor.emailNotificacao,
    });
    this.errosCampo.set({});
    this.erroGeral.set('');
  }

  protected salvar(): void {
    const dados: DadosSetor = this.form.getRawValue();
    const erros = validarSetor(dados);
    this.errosCampo.set(erros);
    if (!setorValido(erros)) {
      this.form.markAllAsTouched();
      this.mensagem.set('Há campos inválidos no formulário.');
      return;
    }

    this.salvando.set(true);
    const id = this.editandoId();
    const requisicao = id
      ? this.servico.editar(id, dados)
      : this.servico.criar(dados);

    requisicao.subscribe({
      next: () => {
        this.salvando.set(false);
        this.mensagem.set(id ? 'Setor atualizado com sucesso.' : 'Setor criado com sucesso.');
        this.novo();
        this.carregar();
      },
      error: (resp) => {
        this.salvando.set(false);
        this.aplicarErrosServidor(resp);
      },
    });
  }

  protected excluir(setor: Setor): void {
    this.servico.excluir(setor.id).subscribe({
      next: () => {
        this.mensagem.set(`Setor ${setor.sigla} excluído.`);
        if (this.editandoId() === setor.id) {
          this.novo();
        }
        this.carregar();
      },
      error: () => this.erroGeral.set('Não foi possível excluir o setor.'),
    });
  }

  protected cancelarEdicao(): void {
    this.novo();
  }

  /** Mapeia erros de validação do servidor (400) para os campos do formulário. */
  private aplicarErrosServidor(resp: unknown): void {
    const corpo = (resp as { error?: { erros?: { campo: string; mensagem: string }[]; mensagem?: string } })?.error;
    if (corpo?.erros?.length) {
      const mapa: ErrosSetor = {};
      for (const e of corpo.erros) {
        mapa[e.campo as keyof ErrosSetor] = e.mensagem;
      }
      this.errosCampo.set(mapa);
      this.mensagem.set('Há campos inválidos no formulário.');
      return;
    }
    this.erroGeral.set(corpo?.mensagem ?? 'Não foi possível salvar o setor.');
  }
}
