package no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold

sealed class Arbeidsgiver {
    data class Virksomhet(val virksomhetsnummer: Organisasjonsnummer): Arbeidsgiver()
    data class Person(val personnummer: String): Arbeidsgiver()
}
