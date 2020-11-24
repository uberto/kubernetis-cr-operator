package com.ubertob.mycustomresource.operator.controller

import com.ubertob.mycustomresource.operator.crd.MyCustomResource
import com.ubertob.mycustomresource.operator.crd.MyCustomResourceList
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


//simple Informer to get notification on MyCustorResource

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

private fun checkCustomResource(client: DefaultKubernetesClient) {
    val crdContext = CustomResourceDefinitionContext.Builder()
        .withVersion("v1")
        .withScope("Namespaced")
        .withGroup("demo.ubertob.com")
        .withPlural("mycustomresources")
        .build()
    val sharedInformerFactory = client.informers()
    val crdInformer: SharedIndexInformer<MyCustomResource> =
        sharedInformerFactory.sharedIndexInformerForCustomResource(
            crdContext,
            MyCustomResource::class.java,
            MyCustomResourceList::class.java,
            (1 * 60 * 1000).toLong()
        )
    println("Informer factory initialized.")
    crdInformer.addEventHandler(
        object : ResourceEventHandler<MyCustomResource> {
            override fun onAdd(pod: MyCustomResource) {
                println("${pod.getMetadata().getName()} MyCustomResource added")
            }

            override fun onUpdate(oldPod: MyCustomResource, newPod: MyCustomResource) {
                println("${oldPod.getMetadata().getName()} MyCustomResource updated")
            }

            override fun onDelete(pod: MyCustomResource, deletedFinalStateUnknown: Boolean) {
                println("${pod.getMetadata().getName()} MyCustomResource deleted")
            }
        }
    )
    sharedInformerFactory.addSharedInformerEventListener { exception: Exception? ->
        exception?.printStackTrace()
        println("Exception occurred, but caught")
    }
    println("Starting all registered informers")
    sharedInformerFactory.startAllRegisteredInformers()
    Executors.newSingleThreadExecutor().submit {
        Thread.currentThread().name = "HAS_SYNCED_THREAD"
        try {
            while (true) {
                println("podInformer.hasSynced() : ${crdInformer.hasSynced()}")
                Thread.sleep(10 * 1000L)
            }
        } catch (inEx: InterruptedException) {
            Thread.currentThread().interrupt()
            println("HAS_SYNCED_THREAD INTERRUPTED!")
        }
    }

    // Wait for some time now
    TimeUnit.MINUTES.sleep(60)
}