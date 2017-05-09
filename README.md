# Jaeger OpenShift Templates

Support for deploying Jaeger into OpenShift.

## All-in-one template
Template with in-memory storage with a limited functionality for local testing and development. 
Do not use this template in production environments.

To directly install everything you need:
```bash
oc process -f https://raw.githubusercontent.com/jaegertacing/jaeger-openshift/master/all-in-one/jaeger-all-in-one-template.yml | oc create -f -
oc delete all,template -l jaeger-infra    # to remove everything
```
