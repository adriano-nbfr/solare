import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RecursosService } from '../manutencao/recursos/recursos.service';
import { Recurso } from '../manutencao/recursos/recurso';
import { ReservasService } from './reservas.service';
import {
  Conflito,
  DadosReserva,
  ErrosReserva,
  RecursoSolicitado,
  Reserva,
  combinarDataHora,
  reservaValida,
  validarReserva,
} from './reserva';

/**
 * Formulário simples de criação de reserva (F4), composto pela grade do
 * Solicitante (F5): recebe o ambiente, a data e o período (`inicio`/`fim` em
 * `HH:mm`) já selecionados ao clicar num slot livre, e cuida apenas da
 * finalidade e dos recursos opcionais antes de chamar `POST /api/reservas`.
 *
 * Acessível (NF2): rótulos associados, mensagens anunciadas por `aria-live`,
 * operável por teclado, foco visível (herdado do SCSS) e conflitos legíveis
 * expostos como `alert`.
 */
@Component({
  selector: 'app-formulario-reserva',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './formulario-reserva.html',
  styleUrl: './formulario-reserva.scss',
})
export class FormularioReservaComponent {
  private readonly servico = inject(ReservasService);
  private readonly recursosServico = inject(RecursosService);
  private readonly fb = inject(FormBuilder);

  /** Id do ambiente da reserva (vindo da grade). */
  readonly ambienteId = input.required<string>();
  /** Nome do ambiente para exibição legível no formulário. */
  readonly ambienteNome = input<string>('');
  /** Data da reserva no formato `yyyy-MM-dd`. */
  readonly data = input.required<string>();
  /** Início do slot selecionado no formato `HH:mm`. */
  readonly inicio = input.required<string>();
  /** Fim do slot selecionado no formato `HH:mm`. */
  readonly fim = input.required<string>();

  /** Emite a reserva criada para a grade atualizar a disponibilidade. */
  readonly criada = output<Reserva>();
  /** Emite quando o usuário cancela a criação. */
  readonly cancelado = output<void>();

  protected readonly recursos = signal<Recurso[]>([]);
  protected readonly salvando = signal(false);
  /** Mensagem de status anunciada por leitores de tela (aria-live). */
  protected readonly mensagem = signal<string>('');
  protected readonly erroGeral = signal<string>('');
  /** Conflitos tipados retornados em 409 (horário, hierarquia ou recurso). */
  protected readonly conflitos = signal<Conflito[]>([]);
  protected readonly errosCampo = signal<ErrosReserva>({});

  /** Rótulo do período selecionado, para o cabeçalho e para o anúncio ARIA. */
  protected readonly periodoRotulo = computed(() => `${this.inicio()} às ${this.fim()}`);

  protected readonly form = this.fb.nonNullable.group({
    finalidade: ['', [Validators.required, Validators.maxLength(500)]],
    recursoId: [''],
    quantidade: [1 as number | null, [Validators.min(1)]],
  });

  constructor() {
    this.carregarRecursos();
  }

  private carregarRecursos(): void {
    this.recursosServico.listar({ size: 200, sort: 'nome,asc' }).subscribe({
      next: (pagina) => this.recursos.set(pagina.conteudo ?? []),
      // Recursos são opcionais na reserva; falha não bloqueia a finalidade.
      error: () => this.recursos.set([]),
    });
  }

  protected salvar(): void {
    const bruto = this.form.getRawValue();

    const erros = validarReserva({
      ambienteId: this.ambienteId(),
      data: this.data(),
      finalidade: bruto.finalidade,
    });
    this.errosCampo.set(erros);
    if (!reservaValida(erros)) {
      this.form.markAllAsTouched();
      this.mensagem.set('Há campos inválidos no formulário.');
      return;
    }

    const inicioIso = combinarDataHora(this.data(), this.inicio());
    const fimIso = combinarDataHora(this.data(), this.fim());
    if (!inicioIso || !fimIso) {
      this.erroGeral.set('Período selecionado inválido. Escolha outro horário na grade.');
      return;
    }

    const recursos: RecursoSolicitado[] = [];
    const recursoId = (bruto.recursoId ?? '').trim();
    if (recursoId) {
      const quantidade = bruto.quantidade == null ? 1 : bruto.quantidade;
      recursos.push({ recursoId, quantidade });
    }

    const dados: DadosReserva = {
      ambienteId: this.ambienteId(),
      inicio: inicioIso,
      fim: fimIso,
      finalidade: bruto.finalidade.trim(),
      recursos,
    };

    this.salvando.set(true);
    this.erroGeral.set('');
    this.conflitos.set([]);
    this.servico.criar(dados).subscribe({
      next: (reserva) => {
        this.salvando.set(false);
        this.mensagem.set(
          `Reserva criada com sucesso${reserva.snp ? ` (SNP ${reserva.snp})` : ''}.`,
        );
        this.criada.emit(reserva);
      },
      error: (resp) => {
        this.salvando.set(false);
        this.aplicarErro(resp);
      },
    });
  }

  protected cancelar(): void {
    this.cancelado.emit();
  }

  /** Mapeia respostas de erro (400 campos / 409 conflitos / genérico) para a UI. */
  private aplicarErro(resp: unknown): void {
    const corpo = (resp as {
      error?: {
        mensagem?: string;
        erros?: { campo: string; mensagem: string }[];
        conflitos?: Conflito[];
      };
    })?.error;

    if (corpo?.conflitos?.length) {
      this.conflitos.set(corpo.conflitos);
      this.mensagem.set('A reserva está em conflito. Veja os detalhes.');
      return;
    }
    if (corpo?.erros?.length) {
      const mapa: ErrosReserva = {};
      for (const e of corpo.erros) {
        if (e.campo === 'finalidade' || e.campo === 'ambienteId') {
          mapa[e.campo] = e.mensagem;
        }
      }
      this.errosCampo.set(mapa);
      this.erroGeral.set(corpo.mensagem ?? 'Há campos inválidos no formulário.');
      return;
    }
    this.erroGeral.set(corpo?.mensagem ?? 'Não foi possível criar a reserva. Tente novamente.');
  }
}
