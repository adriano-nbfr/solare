package br.mp.mpf.solare.app.snp;

import java.security.SecureRandom;
import java.time.Year;
import java.time.ZoneId;
import java.util.Objects;

import br.mp.mpf.solare.dominio.Reserva;

/**
 * Implementacao simulada do {@link GeradorSnp} para o MVP (F8.5): gera um numero
 * de protocolo com formato plausivel, sem depender de servico externo. A troca
 * pela integracao real via MCP do SNP (Task 17) ocorre substituindo esta
 * implementacao, sem impacto nos consumidores.
 *
 * <p>Formato: {@code SNP-{ano}-{sequencialAleatorio}-{digitoVerificador}}, por
 * exemplo {@code SNP-2024-004873921-7}. O ano vem do inicio da reserva (ou do ano
 * corrente, como defesa), o bloco central e um numero de 9 digitos sorteado de
 * forma segura e o digito verificador e um modulo 11 simples sobre esse bloco,
 * conferindo verossimilhanca ao protocolo.</p>
 *
 * <p>Esta classe e thread-safe: usa um {@link SecureRandom} e nao mantem estado
 * mutavel compartilhado alem dele.</p>
 */
public final class GeradorSnpSimulado implements GeradorSnp {

    private static final ZoneId FUSO_BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final long LIMITE_SEQUENCIAL = 1_000_000_000L; // 9 digitos

    private final SecureRandom aleatorio;

    public GeradorSnpSimulado() {
        this(new SecureRandom());
    }

    GeradorSnpSimulado(SecureRandom aleatorio) {
        this.aleatorio = Objects.requireNonNull(aleatorio, "aleatorio nao pode ser nulo");
    }

    @Override
    public String gerar(Reserva reserva) {
        int ano = anoDaReserva(reserva);
        long sequencial = Math.floorMod(aleatorio.nextLong(), LIMITE_SEQUENCIAL);
        String bloco = String.format("%09d", sequencial);
        int verificador = digitoVerificador(bloco);
        return "SNP-" + ano + "-" + bloco + "-" + verificador;
    }

    private static int anoDaReserva(Reserva reserva) {
        if (reserva != null && reserva.getPeriodo() != null) {
            return reserva.getPeriodo().getInicio().getYear();
        }
        return anoCorrente();
    }

    /** Digito verificador modulo 11 (0-9; 10 e 11 colapsam para 0), comum em protocolos. */
    private static int digitoVerificador(String bloco) {
        int soma = 0;
        int peso = 2;
        for (int i = bloco.length() - 1; i >= 0; i--) {
            soma += (bloco.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = 11 - (soma % 11);
        return resto >= 10 ? 0 : resto;
    }

    /** Ano corrente no fuso de referencia; util para outras partes do sistema. */
    public static int anoCorrente() {
        return Year.now(FUSO_BRASILIA).getValue();
    }
}
