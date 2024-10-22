//
// Copyright (c) 2023 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

package io.zenoh.pubsub

import io.zenoh.*
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.IntoZBytes
import io.zenoh.exceptions.ZError
import io.zenoh.jni.JNIPublisher
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.qos.CongestionControl
import io.zenoh.qos.Priority
import io.zenoh.qos.QoS
import kotlin.Throws

/**
 * A Zenoh Publisher.
 *
 * A publisher is automatically dropped when using it with the 'try-with-resources' statement (i.e. 'use' in Kotlin).
 * The session from which it was declared will also keep a reference to it and undeclare it once the session is closed.
 *
 * In order to declare a publisher, [Session.declarePublisher] must be called, which returns a [Publisher.Builder] from
 * which we can specify the [Priority], and the [CongestionControl].
 *
 * Example:
 * ```java
 * try (Session session = Session.open()) {
 *     try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/java/greeting")) {
 *         System.out.println("Declaring publisher on '" + keyExpr + "'...");
 *         try (Publisher publisher = session.declarePublisher(keyExpr).res()) {
 *             int i = 0;
 *             while (true) {
 *                 publisher.put("Hello for the " + i + "th time!").res();
 *                 Thread.sleep(1000);
 *                 i++;
 *             }
 *         }
 *     }
 * } catch (ZError | InterruptedException e) {
 *     System.out.println("Error: " + e);
 * }
 * ```
 *
 * The publisher configuration parameters can be later changed using the setter functions.
 *
 * @property keyExpr The key expression the publisher will be associated to.
 * @property qos [QoS] configuration of the publisher.
 * @property jniPublisher Delegate class handling the communication with the native code.
 * @constructor Create empty Publisher with the default configuration.
 */
class Publisher internal constructor(
    val keyExpr: KeyExpr,
    private var qos: QoS,
    val encoding: Encoding,
    private var jniPublisher: JNIPublisher?,
) : SessionDeclaration, AutoCloseable {

    companion object {
        private val publisherNotValid = ZError("Publisher is not valid.")
    }

    /** Get the congestion control applied when routing the data. */
    fun congestionControl() = qos.congestionControl

    /** Get the priority of the written data. */
    fun priority() = qos.priority

    /** Performs a PUT operation on the specified [keyExpr] with the specified [payload]. */
    fun put(payload: IntoZBytes) = PutBuilder(jniPublisher, payload)

    /**
     * Performs a DELETE operation on the specified [keyExpr]
     *
     * @return A [Resolvable] operation.
     */
    fun delete() = DeleteBuilder(jniPublisher)

    override fun close() {
        undeclare()
    }

    override fun undeclare() {
        jniPublisher?.close()
        jniPublisher = null
    }

    @Suppress("removal")
    protected fun finalize() {
        jniPublisher?.close()
    }

    class PutBuilder internal constructor(
        private var jniPublisher: JNIPublisher?,
        val payload: IntoZBytes,
        val encoding: Encoding? = null,
        var attachment: IntoZBytes? = null
    ) {

        fun attachment(attachment: IntoZBytes) = apply { this.attachment = attachment }

        @Throws(ZError::class)
        fun res() {
            jniPublisher?.put(payload, encoding, attachment) ?: throw(publisherNotValid)
        }
    }

    class DeleteBuilder internal constructor(
        private var jniPublisher: JNIPublisher?,
        var attachment: IntoZBytes? = null
    ) {

        fun attachment(attachment: IntoZBytes) = apply { this.attachment = attachment }

        @Throws(ZError::class)
        fun res() {
            jniPublisher?.delete(attachment) ?: throw(publisherNotValid)
        }
    }

    /**
     * Publisher Builder.
     *
     * @property session The [Session] from which the publisher is declared.
     * @property keyExpr The key expression the publisher will be associated to.
     * @constructor Create empty Builder.
     */
    class Builder internal constructor(
        internal val session: Session,
        internal val keyExpr: KeyExpr,
    ) {
        private var qos = QoS.default()

        fun qos(qos: QoS) {
            this.qos = qos
        }

        fun res(): Publisher {
            return session.run { resolvePublisher(keyExpr, qos) }
        }
    }
}