package br.mp.mpf.solare.infra.dynamo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Construcao centralizada das chaves single-table do DynamoDB (design.md).
 *
 * <pre>
 * | Entidade   | PK                | SK                              | GSI                                             |
 * | Setor      | SETOR#{id}        | META                            | -                                               |
 * | Ambiente   | AMB#{id}          | META                            | GSI1PK=PAI#{paiId}, GSI1SK=AMB#{id}             |
 * | Recurso    | REC#{id}          | META                            | -                                               |
 * | Reserva    | AMB#{ambienteId}  | RES#{inicioISO}#{reservaId}     | GSI2PK=SOLIC#{solicId}; GSI3PK=DATA#{yyyy-mm-dd} |
 * | UsoRecurso | REC#{recursoId}   | USO#{inicioISO}#{reservaId}     | -                                               |
 * </pre>
 */
public final class ChavesSolare {

    public static final String SK_META = "META";

    public static final String PREFIXO_SETOR = "SETOR#";
    public static final String PREFIXO_AMBIENTE = "AMB#";
    public static final String PREFIXO_RECURSO = "REC#";
    public static final String PREFIXO_PAI = "PAI#";
    public static final String PREFIXO_SOLIC = "SOLIC#";
    public static final String PREFIXO_DATA = "DATA#";
    public static final String PREFIXO_RES = "RES#";
    public static final String PREFIXO_USO = "USO#";

    /** ISO local sem offset, estavel para ordenacao lexicografica da SK. */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ISO_LOCAL_DATE;

    private ChavesSolare() {
    }

    // --- Setor ---------------------------------------------------------------
    public static String pkSetor(String id) {
        return PREFIXO_SETOR + id;
    }

    // --- Ambiente ------------------------------------------------------------
    public static String pkAmbiente(String id) {
        return PREFIXO_AMBIENTE + id;
    }

    public static String gsi1PkPai(String ambientePaiId) {
        return PREFIXO_PAI + ambientePaiId;
    }

    public static String gsi1SkAmbiente(String id) {
        return PREFIXO_AMBIENTE + id;
    }

    // --- Recurso -------------------------------------------------------------
    public static String pkRecurso(String id) {
        return PREFIXO_RECURSO + id;
    }

    // --- Reserva -------------------------------------------------------------
    public static String pkReserva(String ambienteId) {
        return PREFIXO_AMBIENTE + ambienteId;
    }

    public static String skReserva(LocalDateTime inicio, String reservaId) {
        return PREFIXO_RES + ISO.format(inicio) + "#" + reservaId;
    }

    public static String gsi2PkSolicitante(String solicitanteId) {
        return PREFIXO_SOLIC + solicitanteId;
    }

    public static String gsi3PkData(LocalDate data) {
        return PREFIXO_DATA + DATA.format(data);
    }

    // --- UsoRecurso ----------------------------------------------------------
    public static String pkUso(String recursoId) {
        return PREFIXO_RECURSO + recursoId;
    }

    public static String skUso(LocalDateTime inicio, String reservaId) {
        return PREFIXO_USO + ISO.format(inicio) + "#" + reservaId;
    }

    public static String formatarIso(LocalDateTime dt) {
        return ISO.format(dt);
    }
}
