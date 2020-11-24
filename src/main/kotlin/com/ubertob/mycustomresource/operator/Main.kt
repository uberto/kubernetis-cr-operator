package com.ubertob.mycustomresource.operator

import com.ubertob.mycustomresource.operator.controller.MyCustomResourceController
import com.ubertob.mycustomresource.operator.crd.MyCustomResource
import com.ubertob.mycustomresource.operator.crd.MyCustomResourceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import java.util.concurrent.Executors


val millisInAMinute = (1 * 60 * 1000).toLong()

fun main() {
    val k8s = DefaultKubernetesClient()

    try {
        k8s.use { client ->
            client.pods()
                .inAnyNamespace()
                .list()
                .items
                .forEach { pod: Pod -> println(pod.metadata.name) }

        }
    } catch (ex: KubernetesClientException) {
        // Handle exception
        ex.printStackTrace()
    }

    k8s.use { client ->
        val namespace = client.namespace ?: "default"

        val customResourceDefinitionContext = CustomResourceDefinitionContext.Builder()
            .withVersion("v1")
            .withScope("Namespaced")
            .withGroup("demo.ubertob.com")
            .withPlural("mycustomresources")
            .build()
        val informerFactory = client.informers()
        val podInformer = informerFactory.sharedIndexInformerFor(
            Pod::class.java,
            PodList::class.java,
            millisInAMinute
        )

        val crdInformer = informerFactory.sharedIndexInformerForCustomResource(
            customResourceDefinitionContext,
            MyCustomResource::class.java,
            MyCustomResourceList::class.java,
            millisInAMinute
        )
        val resourceController = MyCustomResourceController(
            client,
            podInformer,
            crdInformer,
            customResourceDefinitionContext,
            namespace
        )

        resourceController.create()

        informerFactory.addSharedInformerEventListener { exception: Exception? ->
                exception?.printStackTrace()
                println("Exception occurred, but caught")
            }

        println("Starting all registered informers")
        informerFactory.startAllRegisteredInformers()
            Executors.newSingleThreadExecutor().submit {
                Thread.currentThread().name = "HAS_SYNCED_THREAD"
                try {
                    while (true) {
                        println("podInformer.hasSynced() : ${podInformer.hasSynced()}")
                        println("crdInformer.hasSynced() : ${crdInformer.hasSynced()}")
                        Thread.sleep(10 * 1000L)
                    }
                } catch (inEx: InterruptedException) {
                    Thread.currentThread().interrupt()
                    println("HAS_SYNCED_THREAD INTERRUPTED!")
                }
            }

        resourceController.run()
    }
}