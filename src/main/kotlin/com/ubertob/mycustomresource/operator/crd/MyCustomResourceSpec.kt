package com.ubertob.mycustomresource.operator.crd

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource

@JsonDeserialize
data class MyCustomResourceSpec(val replicas: Int = 0, val containerImage: String = "", val containerName: String = "") : KubernetesResource {
}