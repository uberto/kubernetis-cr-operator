package com.ubertob.mycustomresource.operator.crd

import io.fabric8.kubernetes.client.CustomResource

data class MyCustomResource(
    var spec: MyCustomResourceSpec = MyCustomResourceSpec(),
    var status: MyCustomResourceStatus = MyCustomResourceStatus()) : CustomResource()