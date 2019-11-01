package no.nav.helse.sparkel.personopplysninger.oppslag

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import no.nav.helse.sparkel.personopplysninger.probe.InfluxMetricReporter
import no.nav.helse.sparkel.personopplysninger.probe.SensuClient
import no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold.Arbeidsavtale
import no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold.Arbeidsforhold
import no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold.Permisjon
import no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold.Virksomhet
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate

class DatakvalitetProbe(sensuClient: SensuClient) {

    private val influxMetricReporter = InfluxMetricReporter(sensuClient, "sparkel-events", mapOf(
            "application" to (System.getenv("NAIS_APP_NAME") ?: "sparkel"),
            "cluster" to (System.getenv("NAIS_CLUSTER_NAME") ?: "dev-fss"),
            "namespace" to (System.getenv("NAIS_NAMESPACE") ?: "default")
    ))

    companion object {
        private val log = LoggerFactory.getLogger(DatakvalitetProbe::class.java)

        private val arbeidsforholdHistogram = Histogram.build()
                .buckets(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 15.0, 20.0, 40.0, 60.0, 80.0, 100.0)
                .name("arbeidsforhold_sizes")
                .help("fordeling over hvor mange arbeidsforhold en arbeidstaker har, både frilans og vanlig arbeidstaker")
                .register()

        private val frilansCounter = Counter.build()
                .name("arbeidsforhold_frilans_totals")
                .help("antall frilans arbeidsforhold")
                .register()

        private val arbeidsforholdISammeVirksomhetCounter = Counter.build()
                .name("arbeidsforhold_i_samme_virksomhet_totals")
                .help("antall arbeidsforhold i samme virksomhet")
                .register()

        private val arbeidsforholdAvviksCounter = Counter.build()
                .name("arbeidsforhold_avvik_totals")
                .labelNames("type")
                .help("antall arbeidsforhold som ikke har noen tilhørende inntekter")
                .register()

        private val inntektAvviksCounter = Counter.build()
                .name("inntekt_avvik_totals")
                .help("antall inntekter som ikke har noen tilhørende arbeidsforhold")
                .register()

        private val arbeidsforholdPerInntektHistogram = Histogram.build()
                .buckets(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 10.0)
                .name("arbeidsforhold_per_inntekt_sizes")
                .help("fordeling over hvor mange potensielle arbeidsforhold en inntekt har")
                .register()

        private val inntektCounter = Counter.build()
                .name("inntekt_totals")
                .help("antall inntekter mottatt, fordelt på inntektstype")
                .labelNames("type")
                .register()
        private val andreAktørerCounter = Counter.build()
                .name("inntekt_andre_aktorer_totals")
                .help("antall inntekter mottatt med andre aktører enn den det ble gjort oppslag på")
                .register()
        private val inntekterUtenforPeriodeCounter = Counter.build()
                .name("inntekt_utenfor_periode_totals")
                .help("antall inntekter med periode (enten opptjeningsperiode eller utbetaltIPeriode) utenfor søkeperioden")
                .register()
        private val inntektArbeidsgivertypeCounter = Counter.build()
                .name("inntekt_arbeidsgivertype_totals")
                .labelNames("type")
                .help("antall inntekter fordelt på ulike arbeidsgivertyper")
                .register()
    }

    enum class Observasjonstype {
        ErStørreEnnHundre,
        FraOgMedDatoErStørreEnnTilOgMedDato,
        StartdatoStørreEnnSluttdato,
        DatoErIFremtiden,
        VerdiMangler,
        VerdiManglerIkke,
        TomVerdi,
        HarVerdi,
        FlereArbeidsforholdPerInntekt,
        ArbeidsforholdISammeVirksomhet,
        IngenArbeidsforholdForInntekt,
        ErMindreEnnNull,
        InntektGjelderEnAnnenAktør,
        UlikeYrkerForArbeidsforhold,
        UlikeArbeidsforholdMedSammeYrke,
        UlikeArbeidsforholdMedUlikYrke,
        VirksomhetErNavAktør,
        VirksomhetErPerson,
        VirksomhetErOrganisasjon,
        OrganisasjonErJuridiskEnhet,
        OrganisasjonErVirksomhet,
        OrganisasjonErOrganisasjonsledd
    }

    fun inspiserArbeidstaker(arbeidsforhold: Arbeidsforhold.Arbeidstaker) {
        sjekkOmStartdatoErStørreEnnSluttdato(arbeidsforhold, "startdato,sluttdato", arbeidsforhold.startdato, arbeidsforhold.sluttdato)
        sjekkOmDatoErIFremtiden(arbeidsforhold, "sluttdato", arbeidsforhold.sluttdato)
        sjekkArbeidsgiver(arbeidsforhold)

        arbeidsforhold.permisjon.forEach { permisjon ->
            inspiserPermisjon(permisjon)
        }

        arbeidsforhold.arbeidsavtaler.forEach { arbeidsavtale ->
            inspiserArbeidsavtale(arbeidsavtale)
        }

        val ulikeYrkerForArbeidsforhold = arbeidsforhold.arbeidsavtaler.distinctBy { arbeidsavtale ->
            arbeidsavtale.yrke
        }.size

        if (ulikeYrkerForArbeidsforhold > 1) {
            sendDatakvalitetEvent(arbeidsforhold, "arbeidsavtale", Observasjonstype.UlikeYrkerForArbeidsforhold, "arbeidsforhold har $ulikeYrkerForArbeidsforhold forskjellige yrkeskoder")
        }
    }
    fun inspiserFrilans(arbeidsforhold: Arbeidsforhold.Frilans) {
        sjekkOmStartdatoErStørreEnnSluttdato(arbeidsforhold, "startdato,sluttdato", arbeidsforhold.startdato, arbeidsforhold.sluttdato)
        sjekkOmDatoErIFremtiden(arbeidsforhold, "sluttdato", arbeidsforhold.sluttdato)
        sjekkOmFeltErBlank(arbeidsforhold, "yrke", arbeidsforhold.yrke)
        sjekkArbeidsgiver(arbeidsforhold)
    }

    fun frilansArbeidsforhold(arbeidsforholdliste: List<Arbeidsforhold.Frilans>) {
        frilansCounter.inc(arbeidsforholdliste.size.toDouble())
    }

    private fun sjekkArbeidsgiver(arbeidsforhold: Arbeidsforhold) {
        when (arbeidsforhold.arbeidsgiver) {
            is Virksomhet.NavAktør -> sendDatakvalitetEvent(arbeidsforhold, "arbeidsgiver", DatakvalitetProbe.Observasjonstype.VirksomhetErNavAktør, "arbeidsgiver er en NavAktør")
            is Virksomhet.Person -> sendDatakvalitetEvent(arbeidsforhold, "arbeidsgiver", DatakvalitetProbe.Observasjonstype.VirksomhetErPerson, "arbeidsgiver er en person")
        }
    }


    private fun inspiserArbeidsavtale(arbeidsavtale: Arbeidsavtale) {
        with(arbeidsavtale) {
            sjekkOmFeltErBlank(this, "yrke", yrke)
            sjekkOmFeltErNull(this, "stillingsprosent", stillingsprosent)
            if (stillingsprosent != null) {
                sjekkProsent(this, "stillingsprosent", stillingsprosent)
            }

            if (this is Arbeidsavtale.Historisk) {
                sjekkOmFraOgMedDatoErStørreEnnTilOgMedDato(this, "fom,tom", fom, tom)
            }
        }
    }

    private fun inspiserPermisjon(permisjon: Permisjon) {
        with(permisjon) {
            sjekkProsent(this, "permisjonsprosent", permisjonsprosent)
            sjekkOmFraOgMedDatoErStørreEnnTilOgMedDato(this, "fom,tom", fom, tom)
        }
    }


    private fun sendDatakvalitetEvent(objekt: Any, felt: String, observasjonstype: DatakvalitetProbe.Observasjonstype, beskrivelse: String) {
        log.info("objekt=${objekt.javaClass.name} felt=$felt feil=$observasjonstype: $beskrivelse")
        influxMetricReporter.sendDataPoint("datakvalitet.event",
                mapOf(
                        "beskrivelse" to beskrivelse
                ),
                mapOf(
                        "objekt" to objekt.javaClass.name,
                        "felt" to felt,
                        "type" to observasjonstype.name
                ))
    }

    private fun sjekkOmFeltErNull(objekt: Any, felt: String, value: Any?) {
        if (value == null) {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.VerdiMangler, "felt manger: $felt er null")
        } else {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.VerdiManglerIkke, "$felt er ikke null")
        }
    }

    private fun sjekkOmFeltErBlank(objekt: Any, felt: String, value: String) {
        if (value.isBlank()) {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.TomVerdi, "tomt felt: $felt er tom, eller består bare av whitespace")
        } else {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.HarVerdi, "$felt har verdi")
        }
    }

    private fun sjekkProsent(objekt: Any, felt: String, percent: BigDecimal) {
        if (percent < BigDecimal.ZERO) {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.ErMindreEnnNull, "ugyldig prosent: $percent % er mindre enn 0 %")
        }

        if (percent > BigDecimal(100)) {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.ErStørreEnnHundre, "ugyldig prosent: $percent % er større enn 100 %")
        }
    }

    private fun sjekkOmDatoErIFremtiden(objekt: Any, felt: String, dato: LocalDate?) {
        if (dato != null && dato > LocalDate.now()) {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.DatoErIFremtiden, "ugyldig dato: $dato er i fremtiden")
        }
    }

    private fun sjekkOmFraOgMedDatoErStørreEnnTilOgMedDato(objekt: Any, felt: String, fom: LocalDate, tom: LocalDate?) {
        if (tom != null && fom > tom) {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.FraOgMedDatoErStørreEnnTilOgMedDato, "ugyldig dato: $fom er større enn $tom")
        }
    }

    private fun sjekkOmStartdatoErStørreEnnSluttdato(objekt: Any, felt: String, startdato: LocalDate, sluttdato: LocalDate?) {
        if (sluttdato != null && startdato > sluttdato) {
            sendDatakvalitetEvent(objekt, felt, DatakvalitetProbe.Observasjonstype.StartdatoStørreEnnSluttdato, "ugyldig dato: $startdato er større enn $sluttdato")
        }
    }
}

