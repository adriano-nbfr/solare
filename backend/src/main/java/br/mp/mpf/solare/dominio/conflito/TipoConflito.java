package br.mp.mpf.solare.dominio.conflito;

/**
 * Tipos de conflito que o {@link MotorValidacaoConflitos} pode detectar. Cada
 * valor corresponde a um grupo de regras de negocio e e mapeado pela camada de
 * API para uma resposta 409 com mensagem legivel.
 */
public enum TipoConflito {

    /** Sobreposicao direta de periodos no mesmo ambiente (RN1, RN11). */
    CONFLITO_HORARIO,

    /** Violacao da margem minima de 30 minutos entre reservas (RN2, RN3, RN13). */
    CONFLITO_MARGEM,

    /** Indisponibilidade por ancestral/descendente reservado no periodo (RN4-RN6). */
    CONFLITO_HIERARQUIA,

    /** Somatorio de recurso limitado excede a quantidade total (RN7-RN9). */
    ESTOURO_RECURSO
}
