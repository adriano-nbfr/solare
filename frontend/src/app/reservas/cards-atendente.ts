import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AtendenteService } from './atendente.service';
import {
  CardReserva,
  GrupoPorData,
  agruparPorData,
  dataValida,
  hojeIso,
  horaDeIso,
  rotuloCard,
} from './atendente';

/**
 * Painel do Atendente (F6): consulta as reservas de uma data em
 * `GET /api/reservas/atendente` e as exibe em cards agrupados por data (F6.1),
 * cada card com o nome do solicitante, a finalidade e o número do SNP, além do
 * ambiente e do horário (F6.2). O filtro por data (F6.4) controla a consulta.
 *
 * Acessibilidade (NF2): cabeçalhos hierárquicos (h1/h2/h3) estruturam a página
 * e os grupos; o campo de data tem rótulo associado; o status é anunciado por
 * `aria-live`; cada card é um `article` com rótulo acessível; o foco é sempre
 * visível (SCSS) e o layout é responsivo (grade fluida de cards).
 */
@Component({
  selector: 'app-cards-atendente',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cards-atendente.html',
  styleUrl: './cards-atendente.scss',
})
export class CardsAtendenteComponent implements OnInit {
  private readonly servico = inject(AtendenteService);
  private readonly fb = inject(FormBuilder);

  protected readonly carregando = signal(false);
  /** Mensagem de status para leitores de tela (aria-live). */
  protected readonly mensagem = signal<string>('');
  protected readonly erroGeral = signal<string>('');
  /** Cards retornados pela consulta corrente. */
  protected readonly cards = signal<CardReserva[]>([]);
  /** Data efetivamente consultada (para o cabeçalho). */
  protected readonly dataConsultada = signal<string>('');

  /** Grupos de cards por data, derivados de forma reativa (F6.1). */
  protected readonly grupos = computed<GrupoPorData[]>(() => agruparPorData(this.cards()));
  /** Total de cards exibidos, para o resumo de status. */
  protected readonly total = computed(() => this.cards().length);

  protected readonly form = this.fb.nonNullable.group({
    data: [hojeIso()],
  });

  /** Exposto ao template para rótulos ARIA e exibição consistentes. */
  protected readonly rotuloCard = rotuloCard;
  protected readonly horaDeIso = horaDeIso;

  ngOnInit(): void {
    this.consultar();
  }

  /** Consulta as reservas da data informada (F6.4). */
  protected consultar(): void {
    const { data } = this.form.getRawValue();
    if (!dataValida(data)) {
      this.erroGeral.set('Informe uma data válida para ver as reservas.');
      return;
    }

    this.carregando.set(true);
    this.erroGeral.set('');
    this.servico.listarPorData(data).subscribe({
      next: (painel) => {
        this.carregando.set(false);
        this.cards.set(painel.cards ?? []);
        this.dataConsultada.set(painel.data ?? data);
        const n = painel.cards?.length ?? 0;
        this.mensagem.set(
          n === 0
            ? 'Nenhuma reserva encontrada para a data selecionada.'
            : `${n} reserva(s) carregada(s).`,
        );
      },
      error: (resp) => {
        this.carregando.set(false);
        this.cards.set([]);
        const corpo = (resp as { error?: { mensagem?: string } })?.error;
        this.erroGeral.set(corpo?.mensagem ?? 'Não foi possível carregar as reservas.');
      },
    });
  }

  /** Faixa de horário de um card no formato `HH:mm – HH:mm` (ou vazio). */
  protected faixaHorario(card: CardReserva): string {
    const inicio = horaDeIso(card.inicio);
    const fim = horaDeIso(card.fim);
    return inicio && fim ? `${inicio} – ${fim}` : '';
  }
}
