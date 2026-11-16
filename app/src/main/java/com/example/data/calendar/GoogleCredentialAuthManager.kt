package com.example.data.calendar

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.data.model.GoogleCredentialSession
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class GoogleJwtClaims(
    val email: String = "",
    val name: String = "",
    @SerialName("given_name") val givenName: String = "",
    val picture: String = "",
    val sub: String = ""
)

sealed interface CredentialSignInOutcome {
    data class Success(val session: GoogleCredentialSession) : CredentialSignInOutcome
    data class Cancelled(val message: String) : CredentialSignInOutcome
    data class NoCredentialOrUnavailable(
        val reason: String,
        val fallbackToSystemPicker: Boolean = true
    ) : CredentialSignInOutcome
}

sealed interface CalendarTokenResult {
    data class Success(val accessToken: String) : CalendarTokenResult
    data class NeedsConsent(val intent: Intent) : CalendarTokenResult
    data class Failure(
        val reason: String,
        val isConfigurationError: Boolean = false
    ) : CalendarTokenResult
}

object GoogleCredentialAuthManager {

    const val CALENDAR_SCOPE_URL = "https://www.googleapis.com/auth/calendar"
    const val GOOGLE_AUTH_UTIL_CALENDAR_SCOPE = "oauth2:$CALENDAR_SCOPE_URL"

    val calendarScopes: List<Scope> by lazy {
        listOf(
            Scope(CALENDAR_SCOPE_URL),
            Scope("https://www.googleapis.com/auth/userinfo.email"),
            Scope("https://www.googleapis.com/auth/userinfo.profile")
        )
    }

    val calendarOnlyScopes: List<Scope> by lazy {
        listOf(Scope(CALENDAR_SCOPE_URL))
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()

    fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength.coerceIn(16, 64))
        secureRandom.nextBytes(bytes)
        return encodeBase64UrlNoPadding(bytes)
    }

    fun parseJwtClaims(idToken: String): GoogleJwtClaims {
        val parts = idToken.split('.')
        if (parts.size < 2 || parts[1].isBlank()) return GoogleJwtClaims()
        return runCatching {
            val decoded = decodeBase64Url(parts[1]).toString(Charsets.UTF_8)
            json.decodeFromString<GoogleJwtClaims>(decoded)
        }.getOrDefault(GoogleJwtClaims())
    }

    suspend fun trySilentAuthorizedSignIn(activity: Activity): GoogleCredentialSession? {
        val webClientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.trim()
        if (webClientId.isBlank()) return null

        val mutableContext = MutableContextWrapper(activity)
        val credentialManager = CredentialManager.create(mutableContext)
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .setNonce(generateSecureRandomNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = runCatching {
            credentialManager.getCredential(context = mutableContext, request = request)
        }.getOrNull() ?: return null

        return extractGoogleCredential(response.credential)
    }

    suspend fun signInWithCredentialManager(
        activity: Activity,
        forceAccountChooser: Boolean = false
    ): CredentialSignInOutcome {
        val webClientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            return CredentialSignInOutcome.NoCredentialOrUnavailable(
                reason = "Brak skonfigurowanego identyfikatora OAuth dla Credential Manager.",
                fallbackToSystemPicker = true
            )
        }

        val mutableContext = MutableContextWrapper(activity)
        val credentialManager = CredentialManager.create(mutableContext)

        if (forceAccountChooser) {
            runCatching {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
        }

        // Primary: Explicit "Sign in with Google" button flow (supports all accounts + re-auth)
        val signInButtonOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
            .setNonce(generateSecureRandomNonce())
            .build()
        val buttonRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInButtonOption)
            .build()

        val buttonResult = runCatching {
            credentialManager.getCredential(context = mutableContext, request = buttonRequest)
        }
        val buttonResponse = buttonResult.getOrNull()
        if (buttonResponse != null) {
            val session = extractGoogleCredential(buttonResponse.credential)
            if (session != null) {
                return CredentialSignInOutcome.Success(session)
            }
        }

        val buttonError = buttonResult.exceptionOrNull()
        if (buttonError is GetCredentialCancellationException) {
            return CredentialSignInOutcome.Cancelled(
                "Anulowano logowanie w oknie Google Credential Manager."
            )
        }

        // Secondary: Credential Manager bottom sheet with all available Google accounts
        val bottomSheetOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .setNonce(generateSecureRandomNonce())
            .build()
        val sheetRequest = GetCredentialRequest.Builder()
            .addCredentialOption(bottomSheetOption)
            .build()

        val sheetResult = runCatching {
            credentialManager.getCredential(context = mutableContext, request = sheetRequest)
        }
        val sheetResponse = sheetResult.getOrNull()
        if (sheetResponse != null) {
            val session = extractGoogleCredential(sheetResponse.credential)
            if (session != null) {
                return CredentialSignInOutcome.Success(session)
            }
        }

        val sheetError = sheetResult.exceptionOrNull()
        if (sheetError is GetCredentialCancellationException) {
            return CredentialSignInOutcome.Cancelled(
                "Anulowano wybór konta Google."
            )
        }

        val effectiveError = sheetError ?: buttonError
        return CredentialSignInOutcome.NoCredentialOrUnavailable(
            reason = describeCredentialException(effectiveError),
            fallbackToSystemPicker = true
        )
    }

    suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    fun buildCalendarAuthorizationRequest(accountEmail: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(calendarScopes)
        if (!accountEmail.isNullOrBlank()) {
            builder.setAccount(Account(accountEmail.trim(), "com.google"))
        }
        return builder.build()
    }

    fun buildCalendarScopeOnlyAuthorizationRequest(accountEmail: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(calendarOnlyScopes)
        if (!accountEmail.isNullOrBlank()) {
            builder.setAccount(Account(accountEmail.trim(), "com.google"))
        }
        return builder.build()
    }

    suspend fun requestCalendarTokenViaGoogleAuthUtil(
        context: Context,
        accountEmail: String
    ): CalendarTokenResult = withContext(Dispatchers.IO) {
        val cleanEmail = accountEmail.trim()
        if (cleanEmail.isBlank()) {
            return@withContext CalendarTokenResult.Failure(
                reason = "Brak adresu e-mail konta Google do autoryzacji."
            )
        }
        try {
            val token = GoogleAuthUtil.getToken(
                context.applicationContext,
                Account(cleanEmail, "com.google"),
                GOOGLE_AUTH_UTIL_CALENDAR_SCOPE
            )
            if (token.isNotBlank()) {
                CalendarTokenResult.Success(token)
            } else {
                CalendarTokenResult.Failure("Google zwróciło pusty token dostępu do kalendarza.")
            }
        } catch (recoverable: UserRecoverableAuthException) {
            val intent = recoverable.intent
            if (intent != null) {
                CalendarTokenResult.NeedsConsent(intent)
            } else {
                CalendarTokenResult.Failure(
                    reason = recoverable.message ?: "Wymagana zgoda użytkownika na dostęp do Kalendarza Google."
                )
            }
        } catch (authEx: GoogleAuthException) {
            val rawMsg = authEx.message.orEmpty()
            val isConfigErr = rawMsg.contains("UNREGISTERED", ignoreCase = true) ||
                rawMsg.contains("Unknown", ignoreCase = true) ||
                rawMsg.contains("INVALID_AUDIENCE", ignoreCase = true) ||
                rawMsg.contains("10")
            CalendarTokenResult.Failure(
                reason = rawMsg.ifBlank { "Usługi Google odrzuciły prośbę o token kalendarza." },
                isConfigurationError = isConfigErr
            )
        } catch (ioEx: IOException) {
            CalendarTokenResult.Failure(
                reason = "Błąd sieci podczas łączenia z kontem Google (${ioEx.message ?: "brak połączenia"})."
            )
        } catch (t: Throwable) {
            CalendarTokenResult.Failure(
                reason = t.message ?: "Nie udało się uzyskać dostępu do Kalendarza Google."
            )
        }
    }

    suspend fun invalidateGoogleAuthToken(context: Context, token: String) {
        if (token.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                GoogleAuthUtil.clearToken(context.applicationContext, token)
            }
        }
    }

    fun formatSha1ColonHex(sha1Bytes: ByteArray): String {
        if (sha1Bytes.isEmpty()) return ""
        return sha1Bytes.joinToString(":") { "%02X".format(it) }
    }

    @Suppress("DEPRECATION")
    fun getAppSigningSha1(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val firstCert = signatures?.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(firstCert.toByteArray())
            formatSha1ColonHex(digest)
        }.getOrNull()
    }

    private fun extractGoogleCredential(credential: Credential): GoogleCredentialSession? {
        if (credential !is CustomCredential) return null
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
        val googleCred = runCatching { GoogleIdTokenCredential.createFrom(credential.data) }.getOrNull()
            ?: return null
        val claims = parseJwtClaims(googleCred.idToken)
        val email = googleCred.id.trim().ifBlank { claims.email.trim() }
        if (email.isBlank()) return null

        val displayName = googleCred.displayName?.trim().orEmpty().ifBlank { claims.name.trim() }
        val givenName = googleCred.givenName?.trim().orEmpty().ifBlank { claims.givenName.trim() }
        val picture = googleCred.profilePictureUri?.toString()?.takeIf { it.isNotBlank() }
            ?: claims.picture.takeIf { it.isNotBlank() }

        return GoogleCredentialSession(
            email = email,
            displayName = displayName,
            givenName = givenName,
            profilePictureUrl = picture,
            idToken = googleCred.idToken
        )
    }

    private fun describeCredentialException(err: Throwable?): String = when (err) {
        is NoCredentialException ->
            "Credential Manager nie znalazł konta gotowego do logowania — użyj systemowego wyboru konta Google."
        is GetCredentialProviderConfigurationException ->
            "Dostawca Google Credential Manager jest niedostępny — przełączam na systemowy wybór konta."
        is GetCredentialUnsupportedException ->
            "Urządzenie nie obsługuje Credential Manager — używam systemowego wyboru konta Google."
        is GetCredentialInterruptedException ->
            "Logowanie zostało przerwane — spróbuj ponownie lub wybierz konto z telefonu."
        is GetCredentialCustomException ->
            err.errorMessage?.toString()?.takeIf { it.isNotBlank() }
                ?: "Usługi Google odrzuciły żądanie Credential Manager (${err.type})."
        is GetCredentialException ->
            err.errorMessage?.toString()?.takeIf { it.isNotBlank() }
                ?: "Nie udało się zalogować przez Google Credential Manager."
        else ->
            err?.message?.takeIf { it.isNotBlank() }
                ?: "Nie udało się połączyć przez Google Credential Manager."
    }

    private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0

            val triple = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(BASE64_URL_ALPHABET[(triple ushr 18) and 0x3F])
            sb.append(BASE64_URL_ALPHABET[(triple ushr 12) and 0x3F])
            if (hasB1) sb.append(BASE64_URL_ALPHABET[(triple ushr 6) and 0x3F])
            if (hasB2) sb.append(BASE64_URL_ALPHABET[triple and 0x3F])
            i += 3
        }
        return sb.toString()
    }

    private fun decodeBase64Url(input: String): ByteArray {
        val clean = input.trim().trimEnd('=')
        val out = ArrayList<Byte>((clean.length * 3) / 4)
        var buffer = 0
        var bitsLeft = 0
        for (ch in clean) {
            val val6 = when (ch) {
                in 'A'..'Z' -> ch.code - 'A'.code
                in 'a'..'z' -> ch.code - 'a'.code + 26
                in '0'..'9' -> ch.code - '0'.code + 52
                '-', '+' -> 62
                '_', '/' -> 63
                else -> -1
            }
            if (val6 < 0) continue
            buffer = (buffer shl 6) or val6
            bitsLeft += 6
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer ushr bitsLeft) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
