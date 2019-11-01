package no.nav.helse.sparkel.personopplysninger.oppslag

import no.nav.cxf.metrics.MetricFeature

import no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold.ArbeidsforholdFactory

import no.nav.helse.sparkel.personopplysninger.oppslag.sts.configureFor
import no.nav.helse.sparkel.personopplysninger.oppslag.arbeidsforhold.ArbeidsforholdClient
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.cxf.ws.security.trust.STSClient


class WsClients(private val stsClientWs: STSClient,
                private val callIdGenerator: () -> String) {

    private val features get() = listOf(WSAddressingFeature(), LoggingFeature(), MetricFeature())
    private val outInterceptors get() = listOf(CallIdInterceptor(callIdGenerator))

    companion object {
        init {
            System.setProperty("javax.xml.soap.SAAJMetaFactory", "com.sun.xml.messaging.saaj.soap.SAAJMetaFactoryImpl")
        }
    }

    private fun <ServicePort : Any> ServicePort.withSts() =
            apply {
                stsClientWs.configureFor(this)
            }


    fun arbeidsforhold(endpointUrl: String) =
            ArbeidsforholdFactory.create(endpointUrl, features, outInterceptors)
                    .withSts().let { port ->
                        ArbeidsforholdClient(port)
                    }

}
