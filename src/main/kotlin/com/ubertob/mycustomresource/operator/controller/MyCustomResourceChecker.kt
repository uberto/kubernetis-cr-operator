package com.ubertob.mycustomresource.operator.controller

import com.ubertob.mycustomresource.operator.crd.MyCustomResourceDoneable
import com.ubertob.mycustomresource.operator.crd.MyCustomResource
import com.ubertob.mycustomresource.operator.crd.MyCustomResourceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit



val syncIntervalInMillis = (20 * 1000).toLong()

val crdContext = CustomResourceDefinitionContext.Builder()
    .withVersion("v1")
    .withScope("Namespaced")
    .withGroup("demo.ubertob.com")
    .withPlural("mycustomresources")
    .build()

// a function that simulate a behavior like ReplicaSet for a custom resource, with the idea to possible expansions with specific logic

fun checkCustomResource(client: DefaultKubernetesClient) {

    val sharedInformerFactory = client.informers()
    val crdInformer: SharedIndexInformer<MyCustomResource> =
        sharedInformerFactory.sharedIndexInformerForCustomResource(
            crdContext,
            MyCustomResource::class.java,
            MyCustomResourceList::class.java,
            syncIntervalInMillis
        )
    println("Informer factory initialized.")
    crdInformer.addEventHandler(
        object : ResourceEventHandler<MyCustomResource> {
            override fun onAdd(pod: MyCustomResource) {
                println("${pod.getMetadata().getName()} MyCustomResource added")
                reconcile(client, pod)
            }

            override fun onUpdate(oldPod: MyCustomResource, newPod: MyCustomResource) {
                println("${oldPod.getMetadata().getName()} MyCustomResource updated")
                reconcile(client, newPod)
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


private fun reconcile(client: DefaultKubernetesClient, customResource: MyCustomResource) {
    val namespace = customResource.metadata.namespace

    val podNames = client.pods()
        .inNamespace(namespace)
        .list()
        .items
        .map { it.metadata.name }
        .filter { it.startsWith("example-mycustomresource") }

    val existingPods = podNames.size

    customResource.status.count = existingPods

    val expectedReplicas = customResource.spec.replicas

    println("existingPods: $existingPods  expectedReplicas: $expectedReplicas")

    if (existingPods < expectedReplicas) {
        val pod = client.pods().inNamespace(namespace)
            .create(
                createNewPod(customResource, customResource.spec.containerName, customResource.spec.containerImage)
            )
        println("created $pod")

    } else if (existingPods > expectedReplicas) {
        val pod = client.pods().inNamespace(namespace)
            .withName(podNames.first())
            .delete()
        println("deleted $pod")
    }

//    client.customResource(crdContext)
//        .updateStatus(namespace, customResource.metadata.name, """
//           { "metadata": {}, "kind": "List", "status": { "count": $existingPods } }
//        """.trimIndent())
}


fun createNewPod(customResource: MyCustomResource, containerName: String, containerImage: String): Pod =
    PodBuilder()
        .withNewMetadata()
        .withGenerateName(customResource.metadata.name.toString() + "-pod")
        .withNamespace(customResource.metadata.namespace)
        .withLabels(Collections.singletonMap("app", customResource.metadata.name))
        .addNewOwnerReference()
        .withController(true)
        .withKind("MyCustomResource")
        .withApiVersion("demo.ubertob.com/v1")
        .withName(customResource.metadata.name)
        .withNewUid(customResource.metadata.uid)
        .endOwnerReference()
        .endMetadata()
        .withNewSpec()
        .addNewContainer().withName(containerName).withImage(containerImage).endContainer()
        .endSpec()
        .build()