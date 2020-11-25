package com.ubertob.mycustomresource.operator

import com.ubertob.mycustomresource.operator.controller.checkCustomResource
import io.fabric8.kubernetes.client.DefaultKubernetesClient


fun main() {
    try {
        DefaultKubernetesClient().use { client ->
            checkCustomResource(client)
        }
    } catch (interruptedException: InterruptedException) {
        Thread.currentThread().interrupt()
        println("interrupted: ${interruptedException.message}")
    }
}