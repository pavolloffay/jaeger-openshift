package io.jaegertracing.openshift;

import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;

import io.opentracing.Span;

/**
 * @author Pavol Loffay
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        Sender sender = new UdpSender("localhost", 41663, 0);
        Tracer tracer = new com.uber.jaeger.Tracer.Builder("kok",
                new RemoteReporter(sender, 100, 50,
                        new Metrics(new StatsFactoryImpl(new NullStatsReporter()))),
                new ProbabilisticSampler(1.0))
                .build();

        for (int i = 0; i < 100; i++) {
            Span span = tracer.buildSpan("span")
                    .withTag("foo", "bar").start();
            span.finish();
        }
        tracer.close();

        Thread.sleep(1000);
    }
}
