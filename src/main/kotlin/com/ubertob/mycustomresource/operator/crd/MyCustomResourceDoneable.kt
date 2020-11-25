package com.ubertob.mycustomresource.operator.crd

import io.fabric8.kubernetes.api.builder.Function
import io.fabric8.kubernetes.client.CustomResourceDoneable

class MyCustomResourceDoneable(
        resource: MyCustomResource,
        function: Function<MyCustomResource, MyCustomResource>
) : CustomResourceDoneable<MyCustomResource>(resource, function)