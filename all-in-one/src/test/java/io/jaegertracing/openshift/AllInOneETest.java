/**
 * Copyright 2017 The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.jaegertracing.openshift;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.arquillian.cube.kubernetes.annotations.Named;
import org.arquillian.cube.kubernetes.annotations.Port;
import org.arquillian.cube.kubernetes.annotations.PortForward;
import org.arquillian.cube.kubernetes.api.Session;
import org.arquillian.cube.requirement.ArquillianConditionalRunner;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;

import io.fabric8.kubernetes.api.model.v2_2.EndpointAddress;
import io.fabric8.kubernetes.api.model.v2_2.EndpointSubset;
import io.fabric8.kubernetes.api.model.v2_2.Endpoints;
import io.fabric8.kubernetes.api.model.v2_2.Pod;
import io.fabric8.kubernetes.api.model.v2_2.Service;
import io.fabric8.kubernetes.clnt.v2_2.KubernetesClient;
import io.opentracing.Span;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Pavol Loffay
 */
@RunWith(ArquillianConditionalRunner.class)
public class AllInOneETest {
    private static final String TRACER_SERVICE_NAME = "test-tracer";
    private static final String SERVICE_NAME = "jaeger";

    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .build();

    @Named(SERVICE_NAME)
    @PortForward
    @ArquillianResource
    private Service jaegerService;

    @Named(SERVICE_NAME)
    @PortForward
    @ArquillianResource
    private URL jaegerUiUrl;

    @Named(SERVICE_NAME)
    @PortForward
    @Port(name = "agent-compact")
    @ArquillianResource
    private URL agentCompactUrl;

    @ArquillianResource
    private KubernetesClient client;

    @ArquillianResource
    private Session session;

    @Test
    public void testUiResponds() throws IOException, InterruptedException {
        Request request = new Request.Builder()
                .url(jaegerUiUrl)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            Assert.assertEquals(200, response.code());
        }
    }

    @Test
    public void testSpanReported() throws InterruptedException, IOException {
        Service service = client.services().inNamespace(session.getNamespace()).withName(SERVICE_NAME).get();

        Pod randomPod = getRandomPod(client, SERVICE_NAME, session.getNamespace());

//        Sender sender = new UdpSender(service.getSpec().getClusterIP(), 0, 0);
//        Sender sender = new UdpSender(randomPod.getStatus().getPodIP(), 0, 0);
        Sender sender = new UdpSender(agentCompactUrl.getHost(), agentCompactUrl.getPort(), 0);
        Tracer tracer = new com.uber.jaeger.Tracer.Builder(TRACER_SERVICE_NAME,
                new RemoteReporter(sender, 100, 50,
                        new Metrics(new StatsFactoryImpl(new NullStatsReporter()))),
                new ProbabilisticSampler(1.0))
                .build();

        for (int i = 0; i < 2; i++) {
            Span span = tracer.buildSpan("span")
                    .withTag("foo", "bar").start();
            span.finish();
        }

        Thread.sleep(3000);
        tracer.close();

        Request request = new Request.Builder()
                .url(jaegerUiUrl + "api/traces?service=" + TRACER_SERVICE_NAME)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            Assert.assertEquals(200, response.code());

            String body = response.body().string();
            System.out.println(body);
            Assert.assertTrue(body.contains("foo"));
            Assert.assertTrue(body.contains("bar"));
        }

    }

    private static Pod getRandomPod(KubernetesClient client, String name, String namespace) {
        Endpoints endpoints = client.endpoints().inNamespace(namespace).withName(name).get();
        List<String> pods = new ArrayList<>();
        if (endpoints != null) {
            for (EndpointSubset subset : endpoints.getSubsets()) {
                for (EndpointAddress address : subset.getAddresses()) {
                    if (address.getTargetRef() != null && "Pod".equals(address.getTargetRef().getKind())) {
                        String pod = address.getTargetRef().getName();
                        if (pod != null && !pod.isEmpty()) {
                            pods.add(pod);
                        }
                    }
                }
            }
        }
        if (pods.isEmpty()) {
            return null;
        } else {
            String chosen = pods.get(new Random().nextInt(pods.size()));
            return client.pods().inNamespace(namespace).withName(chosen).get();
        }
    }
}
