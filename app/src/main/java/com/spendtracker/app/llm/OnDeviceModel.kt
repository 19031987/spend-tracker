package com.spendtracker.app.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * A Gemma .litertlm model run inside this app with LiteRT-LM, on the CPU. Fully offline: the
 * model file is read from the phone's storage and nothing is sent anywhere.
 *
 * One question at a time. The model is loaded on first use and freed after a quiet minute, so it
 * only holds memory while alerts are being read. Nothing here may wait forever: loading and every
 * reply have a time limit, and a model that stops answering is dropped.
 */
object OnDeviceModel {
    private const val TAG = "OnDeviceModel"
    private const val MAX_TOKENS = 2048
    private const val MAX_REPLY_TOKENS = 96
    private const val LOAD_TIMEOUT_MS = 180_000L
    private const val REPLY_TIMEOUT_MS = 90_000L
    private const val IDLE_RELEASE_MS = 60_000L

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    private var loadedPath: String? = null
    private var releaseJob: Job? = null

    /** The model's reply to one question, or null when it could not be loaded or did not answer in time. */
    suspend fun generate(ctx: Context, modelPath: String, system: String, prompt: String): String? = lock.withLock {
        releaseJob?.cancel()
        val reply: String? = try {
            val current = load(ctx.applicationContext, modelPath)
            if (current == null) null else ask(current, system, prompt)
        } catch (t: Throwable) {
            Log.w(TAG, "Model call failed: ${t.message}")
            dropEngine()
            null
        }
        releaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            lock.withLock { dropEngine() }
        }
        reply
    }

    private fun dropEngine() {
        val old = engine
        engine = null
        loadedPath = null
        if (old != null) runCatching { old.close() }
    }

    private suspend fun load(ctx: Context, path: String): Engine? {
        engine?.let { if (loadedPath == path) return it }
        dropEngine()
        if (!File(path).canRead()) return null
        val cache = File(ctx.filesDir, "llm_cache").apply { mkdirs() }.path
        var made: Engine? = null
        val job = scope.async {
            Engine(EngineConfig(modelPath = path, backend = Backend.CPU(), audioBackend = null, maxNumTokens = MAX_TOKENS, cacheDir = cache))
                .also { it.initialize(); made = it }
        }
        val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) { job.await() }
        if (loaded == null) {
            Log.w(TAG, "Model did not finish loading in ${LOAD_TIMEOUT_MS / 1000}s")
            job.invokeOnCompletion { made?.let { e -> runCatching { e.close() } } }
            return null
        }
        engine = loaded
        loadedPath = path
        return loaded
    }

    private suspend fun ask(e: Engine, system: String, prompt: String): String? {
        val conversation = e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = 1, topP = 0.95, temperature = 0.1),
                maxOutputToken = MAX_REPLY_TOKENS
            )
        )
        val job = scope.async { conversation.sendMessage(prompt).toString().trim() }
        val reply = withTimeoutOrNull(REPLY_TIMEOUT_MS) { job.await() }
        if (reply != null) {
            runCatching { conversation.close() }
            return reply
        }
        // No answer in time: ask it to stop and let go of this engine; it is closed once the call returns.
        Log.w(TAG, "No reply after ${REPLY_TIMEOUT_MS / 1000}s; dropping the model")
        runCatching { conversation.cancelProcess() }
        val stuck = engine
        engine = null
        loadedPath = null
        job.invokeOnCompletion {
            runCatching { conversation.close() }
            runCatching { stuck?.close() }
        }
        return null
    }
}
