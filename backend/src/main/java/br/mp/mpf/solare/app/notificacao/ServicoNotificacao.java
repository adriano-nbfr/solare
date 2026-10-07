package br.mp.mpf.solare.app.notificacao;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.mp.mpf.solare.dominio.Setor;
import br.mp.mpf.solare.dominio.evento.ReservaAlterada;
import br.mp.mpf.solare.dominio.evento.ReservaCriada;
import br.mp.mpf.solare.dominio.repositorio.SetorRepository;

/**
 * Caso de uso de notificacao por e-mail (F8.3/F8.4), consumido pela Lambda de
 * notificacao. Orquestra: resolver o e-mail de notificacao do {@link Setor}
 * destino, montar o corpo HTML (via {@link GeradorTemplateEmail}) e enviar pelo
 * {@link EnviadorEmail}.
 *
 * <p>Depende apenas de portas ({@link SetorRepository}, {@link EnviadorEmail}),
 * mantendo-se testavel sem AWS. Nao registra dados pessoais sensiveis em log
 * (NF3.5): referencia a reserva pelo id/SNP e o setor pelo id.</p>
 */
public final class ServicoNotificacao {

    private static final Logger LOG = Logger.getLogger(ServicoNotificacao.class.getName());

    private final SetorRepository setorRepository;
    private final EnviadorEmail enviadorEmail;
    private final GeradorTemplateEmail gerador;

    public ServicoNotificacao(SetorRepository setorRepository, EnviadorEmail enviadorEmail) {
        this(setorRepository, enviadorEmail, new GeradorTemplateEmail());
    }

    public ServicoNotificacao(SetorRepository setorRepository, EnviadorEmail enviadorEmail,
                              GeradorTemplateEmail gerador) {
        this.setorRepository = Objects.requireNonNull(setorRepository, "setorRepository");
        this.enviadorEmail = Objects.requireNonNull(enviadorEmail, "enviadorEmail");
        this.gerador = gerador == null ? new GeradorTemplateEmail() : gerador;
    }

    /** Processa o evento de reserva criada (F8.3): envia e-mail HTML ao setor. */
    public void notificarCriacao(ReservaCriada evento) {
        Objects.requireNonNull(evento, "evento nao pode ser nulo");
        Optional<String> destino = resolverEmailDestino(evento.getSetorId(), evento.getReservaId());
        if (destino.isEmpty()) {
            return;
        }
        String assunto = gerador.assuntoCriacao(evento);
        String corpo = gerador.htmlCriacao(evento);
        enviadorEmail.enviarHtml(destino.get(), assunto, corpo);
    }

    /** Processa o evento de reserva alterada (F8.4): e-mail HTML com destaque do diff. */
    public void notificarAlteracao(ReservaAlterada evento) {
        Objects.requireNonNull(evento, "evento nao pode ser nulo");
        Optional<String> destino = resolverEmailDestino(evento.getSetorId(), evento.getReservaId());
        if (destino.isEmpty()) {
            return;
        }
        String assunto = gerador.assuntoAlteracao(evento);
        String corpo = gerador.htmlAlteracao(evento);
        enviadorEmail.enviarHtml(destino.get(), assunto, corpo);
    }

    /**
     * Resolve o e-mail de notificacao do setor. Retorna vazio (sem enviar) quando
     * o setor nao e encontrado ou nao possui e-mail, registrando apenas ids
     * tecnicos — nunca o endereco em si (NF3.5).
     */
    private Optional<String> resolverEmailDestino(String setorId, String reservaId) {
        if (setorId == null || setorId.isBlank()) {
            LOG.warning(() -> "Evento sem setorId; notificacao ignorada (reserva=" + reservaId + ").");
            return Optional.empty();
        }
        Optional<Setor> setor;
        try {
            setor = setorRepository.buscarPorId(setorId);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, e, () -> "Falha ao resolver setor " + setorId
                    + " para notificacao (reserva=" + reservaId + ").");
            throw e;
        }
        if (setor.isEmpty()) {
            LOG.warning(() -> "Setor " + setorId + " nao encontrado; notificacao ignorada (reserva="
                    + reservaId + ").");
            return Optional.empty();
        }
        String email = setor.get().getEmailNotificacao();
        if (email == null || email.isBlank()) {
            LOG.warning(() -> "Setor " + setorId + " sem e-mail de notificacao; ignorado (reserva="
                    + reservaId + ").");
            return Optional.empty();
        }
        return Optional.of(email.trim());
    }
}
