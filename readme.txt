Creating an instance of the CRD should create a pod and delete it when the instance is deleted.

If you need to start a local k8s:
minikube start --extra-config=apiserver.authorization-mode=AlwaysAllow
minikube start

We can verify that something is running
kubectl get pods --all-namespaces

Then we load the definition of the new custom resource
kubectl apply -f yaml/crd.yml

We can verify that the new custom resource definition it has been correctly loaded (even if there are no resources found yet)
kubectl get mycustomresources

Now we can add an instance configured with with 5 instance of nginx hello
kubectl apply -f yaml/cr-example-5.yml

And we should see an instance
kubectl get mycustomresources

Or even get the config with the short syntax
kubectl get mcr -o yaml


At this point we can start the method main of the Main.kt which start the MyCustomResourceController
In the stdout of the program we can see if the informers are syncing with the k8s cluster and the events when we add or remove a custom resource.

For example doing
kubectl delete -f yaml/cr-example-5.yml



