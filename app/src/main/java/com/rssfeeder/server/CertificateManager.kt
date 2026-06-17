package com.rssfeeder.server

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.rssfeeder.debug.DebugLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

object CertificateManager {

    private const val KEYSTORE_FILE = "server.p12"
    private const val KEYSTORE_PASSWORD = "rssfeeder"
    private const val KEY_SIZE = 2048
    private const val VALIDITY_YEARS = 10

    private fun getKeystoreFile(context: Context): File {
        return File(context.filesDir, KEYSTORE_FILE)
    }

    fun isCertGenerated(context: Context): Boolean {
        return getKeystoreFile(context).exists()
    }

    fun generate(context: Context) {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        val keyPair = generateKeyPair()
        val cert = generateCertificate(keyPair)

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            "server",
            keyPair.private,
            KEYSTORE_PASSWORD.toCharArray(),
            arrayOf(cert)
        )

        val file = getKeystoreFile(context)
        FileOutputStream(file).use { fos ->
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray())
        }

        DebugLogger.log("CertificateManager", "Certificate generated at ${file.absolutePath}")
    }

    fun getSslServerSocketFactory(context: Context): SSLServerSocketFactory? {
        return try {
            val keyStore = loadKeystore(context)
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray())
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, null, null)
            sslContext.serverSocketFactory
        } catch (e: Exception) {
            DebugLogger.log("CertificateManager", "Failed to create SSL factory: ${e.message}")
            null
        }
    }

    private fun loadKeystore(context: Context): KeyStore {
        val file = getKeystoreFile(context)
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(file).use { fis ->
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray())
        }
        return keyStore
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        generator.initialize(KEY_SIZE, SecureRandom())
        return generator.generateKeyPair()
    }

    private fun generateCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + VALIDITY_YEARS * 365L * 24L * 60L * 60L * 1000L)

        val subject = X500Name("CN=RSS-Feeder, O=RSS-Feeder")
        val serial = BigInteger(64, SecureRandom())

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        val sanList = arrayOf(
            GeneralName(GeneralName.iPAddress, "127.0.0.1"),
            GeneralName(GeneralName.dNSName, "localhost")
        )
        certBuilder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(sanList)
        )

        val sigGen = JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.private)

        val certHolder = certBuilder.build(sigGen)
        val cert = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certHolder)

        cert.verify(keyPair.public)
        return cert
    }

    private fun loadCertBytes(context: Context): ByteArray? {
        return try {
            val keyStore = loadKeystore(context)
            val cert = keyStore.getCertificate("server") as X509Certificate
            cert.encoded
        } catch (e: Exception) {
            DebugLogger.log("CertificateManager", "Failed to load cert bytes: ${e.message}")
            null
        }
    }

    fun saveToDownloads(context: Context): Uri? {
        val certBytes = loadCertBytes(context) ?: return null
        val fileName = "rss-feeder-ca.crt"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/x-x509-ca-cert")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(certBytes)
                }
            }
            uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val file = File(downloadsDir, fileName)
            file.writeBytes(certBytes)
            Uri.fromFile(file)
        }
    }

    fun getInstallIntent(context: Context): Intent? {
        val certBytes = loadCertBytes(context) ?: return null

        val certFile = File(context.cacheDir, "certs/rss-feeder-ca.crt")
        certFile.parentFile?.mkdirs()
        certFile.writeBytes(certBytes)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            certFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-x509-ca-cert")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
