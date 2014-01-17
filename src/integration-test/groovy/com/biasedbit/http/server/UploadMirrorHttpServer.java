package com.biasedbit.http.server;

import com.biasedbit.http.client.ssl.BogusSslContextFactory;
import lombok.*;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.CharsetUtil;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.jboss.netty.handler.codec.http.HttpHeaders.*;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
@RequiredArgsConstructor
public class UploadMirrorHttpServer {

    // properties -----------------------------------------------------------------------------------------------------

    @Getter private final String host;
    @Getter private final int    port;

    @Getter @Setter private boolean verbose                = false;
    @Getter @Setter private boolean useSsl                 = false;
    @Getter @Setter private long    pauseBefore100Continue = 0;

    // internal vars --------------------------------------------------------------------------------------------------

    private ServerBootstrap     bootstrap;
    private DefaultChannelGroup channelGroup;
    private boolean             running;

    // constructors ---------------------------------------------------------------------------------------------------

    public UploadMirrorHttpServer(int port) { this(null, port); }

    // interface ------------------------------------------------------------------------------------------------------

    public boolean init() {
        Executor bossExecutor = Executors.newCachedThreadPool();
        Executor workerExecutor = Executors.newCachedThreadPool();
        ChannelFactory factory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);

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

                pipeline.addLast("codec", new HttpServerCodec());
                pipeline.addLast("handler", new RequestHandler());

                return pipeline;
            }
        });
        channelGroup = new DefaultChannelGroup("hotpotato-upload-server-" + Integer.toHexString(hashCode()));

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

    // private classes ------------------------------------------------------------------------------------------------

    private final class RequestHandler
            extends SimpleChannelUpstreamHandler {

        private HttpRequest request;
        private Channel channel;
        private ChannelBuffer buffer;

        // SimpleChannelUpstreamHandler -------------------------------------------------------------------------------

        @Override public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channel = e.getChannel();
            channelGroup.add(e.getChannel());
        }

        @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            if (e.getMessage() instanceof HttpRequest) {
                request = (HttpRequest) e.getMessage();
                System.out.println(request);
                System.out.println(request.getContent().toString(CharsetUtil.UTF_8));
                handleRequest();
            } else if (e.getMessage() instanceof HttpChunk) {
                handleChunk((HttpChunk) e.getMessage());
            } else {
                System.err.println("Unknown message received: " + e.getMessage().getClass().getSimpleName());
                e.getChannel().close();
            }
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            System.err.println("*** Exception caught");
            e.getCause().printStackTrace();
            if (e.getChannel().isConnected()) e.getChannel().close();
        }

        // private helpers --------------------------------------------------------------------------------------------

        private void handleRequest() {
            if (verbose) System.err.println("\n*** Got request\n" + request);

            if (!request.isChunked()) {
                buffer = request.getContent();
                sendFinalResponse();
                return;
            }

            String continueHeader = getHeader(request, Names.EXPECT);
            if ((continueHeader != null) &&
                Values.CONTINUE.equalsIgnoreCase(continueHeader) &&
                (pauseBefore100Continue > 0)) {
                System.err.println("*** Pausing before sending 100 continue...");
                try { Thread.sleep(pauseBefore100Continue); } catch (InterruptedException ignored) { }
                send100Continue();
                if (verbose) System.err.println("\n*** Sent 100 continue");
            }
        }

        private void handleChunk(HttpChunk chunk) {
            if (verbose) {
                System.err.println("*** Got chunk with " + chunk.getContent().readableBytes() + " bytes");
                System.err.println(chunk.getContent().toString(CharsetUtil.UTF_8));
            }

            if (buffer == null) buffer = ChannelBuffers.dynamicBuffer((int) getContentLength(request));
            buffer.writeBytes(chunk.getContent());

            if (chunk.isLast()) sendFinalResponse();
        }

        private void send100Continue() {
            sendResponse(new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.CONTINUE));
        }

        private void sendFinalResponse() {
            HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
            if ((buffer != null) && (buffer.readableBytes() > 0)) {
                String contentType = getHeader(request, Names.CONTENT_TYPE);
                if (contentType == null) contentType = "application/octet-stream";
                addHeader(response, Names.CONTENT_TYPE, contentType);
                addHeader(response, Names.CONTENT_LENGTH, buffer.readableBytes());
                response.setContent(buffer);
            }

            System.err.println("\n*** Sending response to client\n" + response);
            sendResponse(response);
        }

        private void sendResponse(HttpResponse response) {
            addHeader(response, Names.SERVER, "UploadMirrorHttpServer");
            if (response.getContent() == null) addHeader(response, Names.CONTENT_LENGTH, 0);

            boolean keepAlive = isKeepAlive(request);
            ChannelFuture f = channel.write(response);
            // Write the response & close the connection after the write operation.
            if (!keepAlive) f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    // main -----------------------------------------------------------------------------------------------------------

    public static void main(String[] args) {
        String host = null;
        int port = 8080;
        boolean verbose = false;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        if (args.length >= 3) verbose = ("verbose".equals(args[2]));

        final UploadMirrorHttpServer server = new UploadMirrorHttpServer(host, port);
        server.verbose = verbose;

        if (!server.init()) System.err.println("Failed to bind server to " + (host == null ? '*' : host) + ":" + port);
        else System.out.println("Server bound to " + (host == null ? '*' : host) + ":" + port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() { server.terminate(); }
        });
    }
}
