package fi.hh.laakekamu.data

/** Whether the encrypted database could be opened. */
enum class TietokannanTila {
    /** Not opened yet, or being opened. */
    AVAAMATTA,

    /** Open and working. */
    AUKI,

    /**
     * The key or the file can't be used (e.g. the Keystore key is gone). The data is kept; the
     * error screen offers a retry and a start-over the user must confirm.
     */
    EI_SAATAVILLA
}

/** The database couldn't be opened; [AppContainer.tietokannanTila] is then EI_SAATAVILLA. */
class TietokantaEiSaatavillaException(cause: Throwable) :
    Exception("The database can't be opened", cause)
