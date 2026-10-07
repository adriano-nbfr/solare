import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AssistenteService } from './assistente.service';
import { RespostaAssistente, SugestaoReserva, rotuloSugestao, validarPedido } from './assistente';
import { FormularioReservaComponent } from './formulario-reserva';
import { Reserva } from './reserva';

/** Sugestão escolhida para iniciar a criação de reserva (INOV1 → F4). */
interface SelecaoSugestao {
  ambienteId: string;
  ambienteNome: string;
  data: string;
  inicio: string;
  fim: string;
}

/**
 * Assistente de reserva em linguagem natural (INOV1): o solicitante descreve o
 * que precisa ("sala para 10 pessoas amanhã das 14h às 16h com projetor") e o
 * assistente devolve sugestões de (ambiente, horário) disponíveis. Selecionar
 * uma sugestão abre o {@link FormularioReservaComponent} já pré-preenchido,
 * encaminhando ao fluxo padrão de criação (`POST /api/reservas`).
 *
 * Quando o modelo não entende o pedido com segurança (fallback), a mensagem
 * orienta o uso da grade de disponibilidade para o preenchimento manual (F5).
 *
 * Acessibilidade (NF2): `textarea` com rótulo associado; status anunciado por
 * `aria-live`; cada sugestão é um `<button>` operável por teclado com
 * `aria-label` descritivo; foco sempre visível (SCSS) e layout responsivo.
 */
@Component({
  selector: 'app-assistente-reserva',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, FormularioReservaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './assistente-reserva.html',
  styleUrl: './assistente-reserva.scss',
})
export class AssistenteReservaComponent {
  private readonly servico = inject(AssistenteService);
  private readonly fb = inject(FormBuilder);

  protected readonly consultando = signal(false);
  /** Mensagem de status para leitores de tela (aria-live). */
  protected readonly mensagem = signal<string>('');
  protected readonly erroGeral = signal<string>('');
  /** Sugestões retornadas pela consulta corrente. */
  protected readonly sugestoes = signal<SugestaoReserva[]>([]);
  /** `true` quando a resposta foi um fallback (orienta o modo manual). */
  protected readonly fallback = signal(false);
  /** Indica que já houve ao menos uma consulta, para distinguir "sem opções". */
  protected readonly consultou = signal(false);
  /** Sugestão em criação; `null` oculta o formulário. */
  protected readonly selecao = signal<SelecaoSugestao | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    pedido: [''],
  });

  /** Exposto ao template para rótulos ARIA consistentes. */
  protected readonly rotuloSugestao = rotuloSugestao;

  /** Envia o pedido em linguagem natural ao assistente (INOV1). */
  protected consultar(): void {
    const { pedido } = this.form.getRawValue();
    const erro = validarPedido(pedido);
    if (erro) {
      this.erroGeral.set(erro);
      return;
    }

    this.consultando.set(true);
    this.erroGeral.set('');
    this.selecao.set(null);
    this.servico.sugerir(pedido.trim()).subscribe({
      next: (resposta) => this.aplicarResposta(resposta),
      error: (resp) => {
        this.consultando.set(false);
        this.consultou.set(true);
        this.sugestoes.set([]);
        this.fallback.set(false);
        const corpo = (resp as { error?: { mensagem?: string } })?.error;
        this.erroGeral.set(
          corpo?.mensagem ?? 'Não foi possível consultar o assistente. Tente novamente.',
        );
      },
    });
  }

  private aplicarResposta(resposta: RespostaAssistente): void {
    this.consultando.set(false);
    this.consultou.set(true);
    this.fallback.set(resposta.fallback);
    this.sugestoes.set(resposta.sugestoes ?? []);

    if (resposta.fallback) {
      this.mensagem.set(
        resposta.mensagem ??
          'Não entendi o pedido com segurança. Use a grade de disponibilidade para escolher manualmente.',
      );
      return;
    }

    const n = resposta.sugestoes?.length ?? 0;
    this.mensagem.set(
      resposta.mensagem ??
        (n === 0 ? 'Nenhuma opção encontrada para o pedido.' : `${n} opção(ões) encontrada(s).`),
    );
  }

  /** Inicia a criação de reserva a partir de uma sugestão (INOV1 → F4). */
  protected selecionar(sugestao: SugestaoReserva): void {
    this.selecao.set({
      ambienteId: sugestao.ambienteId,
      ambienteNome: sugestao.ambienteNome ?? sugestao.ambienteId,
      data: sugestao.data,
      inicio: sugestao.horaInicio,
      fim: sugestao.horaFim,
    });
    this.mensagem.set(
      `Sugestão selecionada: ${sugestao.ambienteNome ?? sugestao.ambienteId}, ` +
        `${sugestao.data} das ${sugestao.horaInicio} às ${sugestao.horaFim}.`,
    );
  }

  /** Indica se a sugestão é a atualmente selecionada (para `aria-pressed`/estilo). */
  protected estaSelecionada(sugestao: SugestaoReserva): boolean {
    const sel = this.selecao();
    return (
      !!sel &&
      sel.ambienteId === sugestao.ambienteId &&
      sel.inicio === sugestao.horaInicio &&
      sel.fim === sugestao.horaFim
    );
  }

  protected aoCriar(reserva: Reserva): void {
    this.selecao.set(null);
    this.sugestoes.set([]);
    this.mensagem.set(
      `Reserva confirmada${reserva.snp ? ` (SNP ${reserva.snp})` : ''}.`,
    );
  }

  protected aoCancelar(): void {
    this.selecao.set(null);
    this.mensagem.set('Criação de reserva cancelada.');
  }
}
