package no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold


import arrow.core.flatMap
import java.time.LocalDate
import java.time.YearMonth

class ArbeidsforholdService(private val arbeidstakerService: ArbeidstakerService,
                            private val frilansArbeidsforholdService: FrilansArbeidsforholdService) {

    fun finnArbeidsforhold(aktørId: String, fom: LocalDate, tom: LocalDate) =
            frilansArbeidsforholdService.hentFrilansarbeidsforhold(aktørId, YearMonth.from(fom), YearMonth.from(tom)).flatMap { frilansArbeidsforholdliste ->
                arbeidstakerService.finnArbeidstakerarbeidsforhold(aktørId, fom, tom).map { arbeidsforholdliste ->
                    arbeidsforholdliste.plus(frilansArbeidsforholdliste)
                }
            }
}
