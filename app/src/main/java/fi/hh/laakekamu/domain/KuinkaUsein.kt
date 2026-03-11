package fi.hh.laakekamu.domain

/**
 * How often a medication is taken. Room stores the enum name as TEXT, which matches the
 * CHECK (kuinka_usein IN ('PAIVITTAIN', 'TARVITTAESSA')) of the SQL design.
 */
enum class KuinkaUsein {
    /** Daily at the reminder times. Shown on Home and reminded. */
    PAIVITTAIN,

    /** As needed. Has no reminders and never appears on Home. */
    TARVITTAESSA
}
