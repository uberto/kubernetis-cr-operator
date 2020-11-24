package com.ubertob.mycustomresource.operator.controller

import com.ubertob.mycustomresource.operator.crd.DoneableMyCustomResource
import com.ubertob.mycustomresource.operator.crd.MyCustomResource
import com.ubertob.mycustomresource.operator.crd.MyCustomResourceList
import io.fabric8.kubernetes.api.model.OwnerReference
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import io.fabric8.kubernetes.client.informers.cache.Cache
import io.fabric8.kubernetes.client.informers.cache.Lister
import java.util.*
import java.util.AbstractMap.SimpleEntry
import java.util.concurrent.LinkedBlockingDeque

// Controller that simulate a ReplicaSet for a custom resource, with the idea to possible expansions with specific logic


class MyCustomResourceController(
    private val kubernetesClient: KubernetesClient,
    private val podInformer: SharedIndexInformer<Pod>,
    private val crdInformer: SharedIndexInformer<MyCustomResource>,
    private val definitionContext: CustomResourceDefinitionContext,
    private val namespace: String
) {

    private val appLabel = "app"
    private val lister = Lister(crdInformer.indexer, namespace)
    private val podLister = Lister(podInformer.indexer, namespace)
    private val workQueue = LinkedBlockingDeque<String>()

    fun create() {
        crdInformer.addEventHandler(object : ResourceEventHandler<MyCustomResource> {
            override fun onAdd(myCustomResource: MyCustomResource) {
                println("${myCustomResource.getMetadata().getName()} MyCustomResource added")
                enqueueMyCustomResource(myCustomResource)
            }

            override fun onUpdate(myCustomResource: MyCustomResource, newMyCustomResource: MyCustomResource) {
                println("${myCustomResource.getMetadata().getName()} MyCustomResource updated")
                enqueueMyCustomResource(newMyCustomResource)
            }

            override fun onDelete(myCustomResource: MyCustomResource, deletedFinalStateUnknown: Boolean) {}
        })

        podInformer.addEventHandler(object : ResourceEventHandler<Pod> {
            override fun onAdd(pod: Pod) {
                println("${pod.getMetadata().getName()} Pod added")
                handlePodObject(pod)
            }

            override fun onUpdate(oldPod: Pod, newPod: Pod) {
                if (oldPod.metadata.resourceVersion != newPod.metadata.resourceVersion) {
                    println("${newPod.getMetadata().getName()} Pod updated")
                    handlePodObject(newPod)
                }
            }

            override fun onDelete(pod: Pod, deletedFinalStateUnknown: Boolean) {}
        })
    }

    private fun enqueueMyCustomResource(customResource: MyCustomResource) {
        val key: String = Cache.metaNamespaceKeyFunc(customResource)
        if (key.isNotEmpty()) {
            workQueue.addLast(key)
        }
    }

    private fun handlePodObject(pod: Pod) {
        val ownerReference = getControllerOf(pod)

        if (ownerReference?.kind?.equals("MyCustomResource", ignoreCase = true) != true) {
            return
        }

        lister
            .get(ownerReference.name)
            ?.also { enqueueMyCustomResource(it) }
    }

    private fun getControllerOf(pod: Pod): OwnerReference? =
        pod.metadata.ownerReferences.firstOrNull { it.controller }

    private fun reconcile(customResource: MyCustomResource) {
        val podNames = podCountByLabel(appLabel, customResource.metadata.name)
        val existingPods = podNames.size

        customResource.status.count = existingPods
        updateStatus(customResource)

        if (existingPods < customResource.spec.replicas) {
            addNewPod(createNewPod(customResource, customResource.spec.containerName, customResource.spec.containerImage))
        } else if (existingPods > customResource.spec.replicas) {
            deletePod(customResource, podNames.first())
        }
    }

    private fun deletePod(
        customResource: MyCustomResource,
        podName: String
    ) = kubernetesClient
        .pods()
        .inNamespace(customResource.metadata.namespace)
        .withName(podName)
        .delete()


    private fun updateStatus(customResource: MyCustomResource) =
        kubernetesClient.customResources(
            definitionContext,
            MyCustomResource::class.java,
            MyCustomResourceList::class.java,
            DoneableMyCustomResource::class.java
        )
            .inNamespace(customResource.metadata.namespace)
            .withName(customResource.metadata.name)
            .updateStatus(customResource)

    private fun podCountByLabel(label: String, resourceName: String): List<String> =
        podLister.list()
            .filter { it.metadata.labels.entries.contains(SimpleEntry(label, resourceName)) }
            .filter { it.status.phase == "Running" || it.status.phase == "Pending" }
            .map { it.metadata.name }

    private fun addNewPod(pod: Pod) =
        kubernetesClient.pods().inNamespace(pod.metadata.namespace).create(pod)

    private fun createNewPod(customResource: MyCustomResource, containerName: String, containerImage: String): Pod =
        PodBuilder()
            .withNewMetadata()
            .withGenerateName(customResource.metadata.name.toString() + "-pod")
            .withNamespace(customResource.metadata.namespace)
            .withLabels(Collections.singletonMap(appLabel, customResource.metadata.name))
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

    fun run() {
        blockUntilSynced()
        while (true) {
            try {
                workQueue
                    .takeFirst()
                    .onEach { println("!!!polled $it") }
                    .split("/")
                    .firstOrNull()
                    ?.let { lister.get(it) }
                    ?.let { reconcile(it) }
            } catch (interruptedException: InterruptedException) {
                // ignored
            }
        }
    }

    private fun blockUntilSynced() {
        while (!podInformer.hasSynced() || !crdInformer.hasSynced()) {}
    }
}