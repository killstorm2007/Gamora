package com.gamora.core

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Espera una tarea de Firebase dentro de una corrutina. */
suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuacion ->
    addOnSuccessListener { continuacion.resume(it) }
    addOnFailureListener { continuacion.resumeWithException(it) }
    addOnCanceledListener { continuacion.cancel() }
}
