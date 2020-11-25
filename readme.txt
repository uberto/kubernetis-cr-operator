Creating an instance of the CRD should create a pod and delete it when the instance is deleted.

If you need to start a local k8s:
minikube start

Remeber to stop it at the end:
minikube stop

We can verify that something is running
kubectl get pods --all-namespaces

Then we load the definition of the new custom resource
kubectl apply -f yaml/crd.yml

We can verify that the new custom resource definition it has been correctly loaded (even if there are no resources found yet)
kubectl get mycustomresources

Now we can add an instance configured with with 5 instance of nginx hello
kubectl apply -f yaml/cr-example-5.yml

And we should see an instance also using the short name
kubectl get mcr

At this point we can start the method main of the Main.kt (even in the IDE) which start the function in MyCustomResourceChecker.
In the stdout of the program we can see if the informers are syncing with the k8s cluster and the events when we add or remove a custom resource.
As soon as the resource checker starts, it will create (or delete) pods of with the image specified in the spec (i.e. the cr-example-5.yml file) until their number is identical to the expected replicas.

You can delete or update the resource, to see the correct behavior. For example doing
kubectl delete -f yaml/cr-example-5.yml

Getting the config will also show the current config
kubectl get mcr -o yaml





