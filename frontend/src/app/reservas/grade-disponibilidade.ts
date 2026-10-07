import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AmbientesService } from '../manutencao/ambientes/ambientes.service';
import { Ambiente } from '../manutencao/ambientes/ambiente';
import { FormularioReservaComponent } from './formulario-reserva';
import { ReservasService } from './reservas.service';
import { Reserva, Slot, hojeIso, rotuloSlot, slotLivre } from './reserva';

/** Slot selecionado para iniciar a criação de uma reserva (F5 → F4). */
interface SlotSelecionado {
  inicio: string;
  fim: string;
}

/**
 * Painel do Solicitante (F5): escolhe ambiente e data, consulta a grade de
 * disponibilidade (slots de 30 min) e a renderiza com slots livres/ocupados
 * visualmente distintos e operáveis. Clicar num slot livre abre o
 * {@link FormularioReservaComponent} já pré-preenchido com o período do slot,
 * que chama `POST /api/reservas`.
 *
 * Acessibilidade (NF2): cada slot é um `<button>` (operável por teclado,
 * foco nativo), com `aria-pressed` indicando a seleção e `aria-label`
 * descrevendo horário e estado; o status é anunciado por `aria-live`; o foco é
 * sempre visível (SCSS) e o layout é responsivo (grade fluida).
 */
@Component({
  selector: 'app-grade-disponibilidade',
  standalone: true,
  imports: [ReactiveFormsModule, FormularioReservaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './grade-disponibilidade.html',
  styleUrl: './grade-disponibilidade.scss',
})
export class GradeDisponibilidadeComponent implements OnInit {
  private readonly servico = inject(ReservasService);
  private readonly ambientesServico = inject(AmbientesService);
  private readonly fb = inject(FormBuilder);

  protected readonly ambientes = signal<Ambiente[]>([]);
  protected readonly slots = signal<Slot[]>([]);
  protected readonly carregando = signal(false);
  /** Mensagem de status para leitores de tela (aria-live). */
  protected readonly mensagem = signal<string>('');
  protected readonly erroGeral = signal<string>('');
  /** Slot em criação; `null` oculta o formulário. */
  protected readonly slotSelecionado = signal<SlotSelecionado | null>(null);
  /** Ambiente/data da consulta corrente, repassados ao formulário. */
  protected readonly ambienteConsultado = signal<string>('');
  protected readonly dataConsultada = signal<string>('');

  protected readonly form = this.fb.nonNullable.group({
    ambienteId: [''],
    data: [hojeIso()],
  });

  /** Exposto para o template montar rótulos ARIA consistentes. */
  protected readonly rotuloSlot = rotuloSlot;
  protected readonly slotLivre = slotLivre;

  ngOnInit(): void {
    this.carregarAmbientes();
  }

  private carregarAmbientes(): void {
    this.ambientesServico.listar({ size: 200, sort: 'nome,asc' }).subscribe({
      next: (pagina) => this.ambientes.set(pagina.conteudo ?? []),
      error: () => this.erroGeral.set('Não foi possível carregar os ambientes.'),
    });
  }

  /** Consulta a disponibilidade do ambiente/data informados (F5). */
  protected consultar(): void {
    const { ambienteId, data } = this.form.getRawValue();
    if (!ambienteId) {
      this.erroGeral.set('Selecione um ambiente para ver a disponibilidade.');
      return;
    }
    if (!data) {
      this.erroGeral.set('Selecione uma data para ver a disponibilidade.');
      return;
    }

    this.carregando.set(true);
    this.erroGeral.set('');
    this.slotSelecionado.set(null);
    this.servico.disponibilidade(ambienteId, data).subscribe({
      next: (grade) => {
        this.carregando.set(false);
        this.slots.set(grade.slots ?? []);
        this.ambienteConsultado.set(ambienteId);
        this.dataConsultada.set(data);
        const livres = (grade.slots ?? []).filter((s) => !s.ocupado).length;
        this.mensagem.set(
          `${grade.slots?.length ?? 0} horários carregados, ${livres} livre(s).`,
        );
      },
      error: (resp) => {
        this.carregando.set(false);
        this.slots.set([]);
        const corpo = (resp as { error?: { mensagem?: string } })?.error;
        this.erroGeral.set(corpo?.mensagem ?? 'Não foi possível consultar a disponibilidade.');
      },
    });
  }

  /** Inicia a criação de reserva a partir de um slot livre (ignora ocupados). */
  protected selecionar(slot: Slot): void {
    if (!slotLivre(slot)) {
      return;
    }
    this.slotSelecionado.set({ inicio: slot.inicio, fim: slot.fim });
    this.mensagem.set(`Horário das ${slot.inicio} às ${slot.fim} selecionado para reserva.`);
  }

  /** Indica se o slot é o atualmente selecionado (para `aria-pressed`/estilo). */
  protected estaSelecionado(slot: Slot): boolean {
    const sel = this.slotSelecionado();
    return !!sel && sel.inicio === slot.inicio && sel.fim === slot.fim;
  }

  /** Nome do ambiente consultado, para exibição no formulário. */
  protected nomeAmbiente(ambienteId: string): string {
    return this.ambientes().find((a) => a.id === ambienteId)?.nome ?? ambienteId;
  }

  protected aoCriar(reserva: Reserva): void {
    this.slotSelecionado.set(null);
    this.mensagem.set(
      `Reserva confirmada${reserva.snp ? ` (SNP ${reserva.snp})` : ''}. Atualizando a grade…`,
    );
    // Reconsulta para refletir o novo bloqueio na grade.
    this.consultar();
  }

  protected aoCancelar(): void {
    this.slotSelecionado.set(null);
    this.mensagem.set('Criação de reserva cancelada.');
  }
}
