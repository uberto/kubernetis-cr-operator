package com.ubertob.mycustomresource.operator.crd

import io.fabric8.kubernetes.client.CustomResourceList

class MyCustomResourceList : CustomResourceList<MyCustomResource>()