package no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold

import no.nav.helse.sparkel.personopplysninger.oppslag.inntekt.InntektClient
import no.nav.helse.sparkel.personopplysninger.oppslag.DatakvalitetProbe
import java.time.YearMonth

class FrilansArbeidsforholdService(private val inntektClient: InntektClient,
                                   private val datakvalitetProbe: DatakvalitetProbe) {

    fun hentFrilansarbeidsforhold(aktørId: String, fom: YearMonth, tom: YearMonth) =
            inntektClient.hentFrilansArbeidsforhold(aktørId, fom, tom)
                    .toEither(InntektskomponentenErrorMapper::mapToError)
                    .map {
                        it.map(ArbeidDomainMapper::toArbeidsforhold)
                                .filterNotNull()
                                .also {
                                    datakvalitetProbe.frilansArbeidsforhold(it)
                                }
                    }.map { arbeidsforholdliste ->
                        arbeidsforholdliste.onEach { arbeidsforhold ->
                            datakvalitetProbe.inspiserFrilans(arbeidsforhold)
                        }
                    }
}
