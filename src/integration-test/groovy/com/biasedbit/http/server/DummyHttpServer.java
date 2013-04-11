/*
 * Copyright 2013 BiasedBit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.biasedbit.http.server;

import com.biasedbit.http.client.ssl.BogusSslContextFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.CharsetUtil;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple HttpServer with configurable error introduction.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
@RequiredArgsConstructor
public class DummyHttpServer {

    // configuration defaults -----------------------------------------------------------------------------------------

    public static final boolean USE_SSL             = false;
    public static final float   FAILURE_PROBABILITY = 0.0f;
    public static final long    RESPONSE_LATENCY    = 0;
    public static final boolean USE_OLD_IO          = false;
    // Taken from http://www.w3schools.com/XML/xml_examples.asp
    public static final String  CONTENT             =
            "<breakfast_menu> \n" +
            "\t<food> \n" +
            "\t\t<name>Belgian Waffles</name> \n" +
            "\t\t<price>$5.95</price> \n" +
            "\t\t<description>two of our famous Belgian Waffles with plenty of real maple syrup</description> \n" +
            "\t\t<calories>650</calories> \n" +
            "\t</food> \n" +
            "\t<food> \n" +
            "\t\t<name>Strawberry Belgian Waffles</name> \n" +
            "\t\t<price>$7.95</price> \n" +
            "\t\t<description>light Belgian waffles covered with strawberries and whipped cream</description> \n" +
            "\t\t<calories>900</calories> \n" +
            "\t</food> \n" +
            "\t<food> \n" +
            "\t\t<name>Berry-Berry Belgian Waffles</name> \n" +
            "\t\t<price>$8.95</price> \n" +
            "\t\t<description>light Belgian waffles covered with an assortment of fresh berries and " +
            "whipped cream</description> \n" +
            "\t\t<calories>900</calories> \n" +
            "\t</food> \n" +
            "\t<food> \n" +
            "\t\t<name>French Toast</name> \n" +
            "\t\t<price>$4.50</price> \n" +
            "\t\t<description>thick slices made from our homemade sourdough bread</description> \n" +
            "\t\t<calories>600</calories> \n" +
            "\t</food> \n" +
            "\t<food> \n" +
            "\t\t<name>Homestyle Breakfast</name> \n" +
            "\t\t<price>$6.95</price> \n" +
            "\t\t<description>two eggs, bacon or sausage, toast, and our ever-popular hash browns</description> \n" +
            "\t\t<calories>950</calories> \n" +
            "\t</food> \n" +
            "</breakfast_menu> ";

    // properties -----------------------------------------------------------------------------------------------------

    @Getter private final String host;
    @Getter private final int    port;

    @Getter @Setter private boolean verbose;
    @Getter @Setter private boolean useSsl          = USE_SSL;
    @Getter @Setter private long    responseLatency = RESPONSE_LATENCY;
    @Getter @Setter private boolean useOldIo        = USE_OLD_IO;
    @Getter @Setter private String  content         = CONTENT;

    @Getter private float failureProbability = FAILURE_PROBABILITY;

    // internal vars --------------------------------------------------------------------------------------------------

    private ServerBootstrap     bootstrap;
    private DefaultChannelGroup channelGroup;
    private boolean             running;

    private final AtomicInteger errors = new AtomicInteger();

    // constructors ---------------------------------------------------------------------------------------------------

    public DummyHttpServer(int port) { this(null, port); }

    // interface ------------------------------------------------------------------------------------------------------

    public boolean init() {
        ChannelFactory factory;
        Executor bossExecutor = Executors.newCachedThreadPool();
        Executor workerExecutor = Executors.newCachedThreadPool();

        if (useOldIo) factory = new OioServerSocketChannelFactory(bossExecutor, workerExecutor);
        else factory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);

        bootstrap = new ServerBootstrap(factory);
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override public ChannelPipeline getPipeline()
                    throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();

                if (useSsl) {
                    SSLEngine engine = BogusSslContextFactory.getInstance().getServerContext().createSSLEngine();
                    engine.setUseClientMode(false);
                    pipeline.addLast("ssl", new SslHandler(engine));
                }

                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("aggregator", new HttpChunkAggregator(5242880)); // 5MB
                pipeline.addLast("handler", new RequestHandler());

                return pipeline;
            }
        });
        channelGroup = new DefaultChannelGroup("hotpotato-dummy-server-" + Integer.toHexString(hashCode()));

        SocketAddress bindAddress = (host != null) ? new InetSocketAddress(host, port) : new InetSocketAddress(port);
        Channel serverChannel = bootstrap.bind(bindAddress);
        channelGroup.add(serverChannel);

        return (running = serverChannel.isBound());
    }

    public void terminate() {
        if (!running) return;

        running = false;
        channelGroup.close().awaitUninterruptibly();
        bootstrap.releaseExternalResources();
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public void setFailureProbability(float failureProbability) {
        if (failureProbability < 0) this.failureProbability = 0;
        else if (failureProbability > 1.0) this.failureProbability = 1;
        else this.failureProbability = failureProbability;
    }

    // private classes ------------------------------------------------------------------------------------------------

    private final class RequestHandler
            extends SimpleChannelUpstreamHandler {

        private final ChannelBuffer contentBuffer = ChannelBuffers.copiedBuffer(content, CharsetUtil.UTF_8);

        // SimpleChannelUpstreamHandler -------------------------------------------------------------------------------

        @Override public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channelGroup.add(e.getChannel());
        }

        @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            if (Math.random() <= failureProbability) {
                errors.incrementAndGet();
                e.getChannel().close();
                return;
            }

            if (responseLatency > 0) try { Thread.sleep(responseLatency); } catch (InterruptedException ignored) { }

            HttpRequest request = (HttpRequest) e.getMessage();
            if (verbose) System.err.println(request);

            if ((request.getContent().readableBytes() > 0) && verbose) {
                System.err.println("-------------");
                System.err.println("Body has " + request.getContent().readableBytes() + " readable bytes.");
                System.err.println("--- BODY START ----------");
                System.err.println(request.getContent().toString(CharsetUtil.UTF_8));
                System.err.println("--- BODY END ------------\n");
            }

            // Build the response object.
            HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.ACCEPTED);
            response.setContent(contentBuffer);
            response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/xml; charset=UTF-8");

            boolean keepAlive = HttpHeaders.isKeepAlive(request);
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, contentBuffer.readableBytes());

            ChannelFuture f = e.getChannel().write(response);
            // Write the response & close the connection after the write operation.
            if (!keepAlive) f.addListener(ChannelFutureListener.CLOSE);
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            if (e.getChannel().isConnected()) e.getChannel().close();
        }
    }

    // main -----------------------------------------------------------------------------------------------------------

    public static void main(String[] args) {
        String host = null;
        int port = 80;
        float failureProbability = 0.0f;
        boolean useOio = false;
        boolean verbose = false;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        if (args.length >= 3) failureProbability = Float.parseFloat(args[2]);
        if (args.length == 4) useOio = ("useOio".equals(args[3]));
        if (args.length == 5) verbose = ("verbose".equals(args[4]));

        final DummyHttpServer server = new DummyHttpServer(host, port);
        server.setVerbose(verbose);
        server.setFailureProbability(failureProbability);
        server.setUseOldIo(useOio);
        //server.setResponseLatency(50L);
        if (!server.init()) {
            System.err.println("Failed to bind server to " + (host == null ? '*' : host) + ":" + port +
                               (useOio ? " (Oio)" : " (Nio)") + " (FP: " + failureProbability + ")");
        } else {
            System.out.println("Server bound to " + (host == null ? '*' : host) + ":" + port +
                               (useOio ? " (Oio)" : " (Nio)") + " (FP: " + failureProbability + ")");
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() { server.terminate(); }
        });
    }
}
