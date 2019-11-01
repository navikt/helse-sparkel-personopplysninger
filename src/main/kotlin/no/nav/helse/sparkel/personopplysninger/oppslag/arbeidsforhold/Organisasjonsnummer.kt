package no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold


data class Organisasjonsnummer(val value : String) {
    init {
        if (!Organisasjonsnummervalidator.erGyldig(value)) {
            throw IllegalArgumentException("Organisasjonsnummer $value er ugyldig")
        }
    }
}
