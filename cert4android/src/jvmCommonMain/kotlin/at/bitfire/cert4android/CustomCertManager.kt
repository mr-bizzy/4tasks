/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.cert4android

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.logging.Logger
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * TrustManager to handle custom certificates.
 *
 * @param certStore certificate store with (un)trusted certificates
 * @param settings  settings provider to get settings from
 */
class CustomCertManager @JvmOverloads constructor(
    private val certStore: CertStore,
    private val settings: SettingsProvider
): X509TrustManager {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
        throw CertificateException("cert4android doesn't validate client certificates")
    }

    /**
     * Checks whether a certificate is trusted. Allows user to explicitly accept untrusted certificates.
     *
     * @param chain        certificate chain to check
     * @param authType     authentication type (ignored)
     *
     * @throws CertificateException in case of an untrusted or questionable certificate
     */
    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (!certStore.isTrusted(
                chain,
                authType,
                trustSystemCerts = settings.trustSystemCerts,
                appInForeground = settings.appInForeground,
                allowUserTrust = settings.allowUserTrust,
            )
        )
            throw CertificateException("Certificate chain not trusted")
    }

    override fun getAcceptedIssuers() = emptyArray<X509Certificate>()


    /**
     * A HostnameVerifier that ALWAYS requires the host name to match the certificate.
     *
     * 4Tasks change (owner's ruling, 2026-10-03): upstream cert4android also accepted a certificate the user
     * had trusted under a name it does not carry, and even asked the user about system-trusted ones. Here,
     * trusting a certificate only lets it stand in for the system's CA check ([checkServerTrusted]); it never
     * excuses a wrong host name. The check passed in (OkHttp's) decides; with none passed, nothing verifies.
     */
    inner class HostnameVerifier(
        private val defaultHostnameVerifier: javax.net.ssl.HostnameVerifier? = null
    ): javax.net.ssl.HostnameVerifier {

        override fun verify(hostname: String, session: SSLSession): Boolean {
            if (defaultHostnameVerifier != null && defaultHostnameVerifier.verify(hostname, session))
                return true

            logger.warning("Host name \"$hostname\" does not match the certificate, refusing (user trust does not excuse a wrong host name)")
            return false
        }

    }

}
