package com.cristik.netty.example.http.file;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author cristik
 */


public class HttpUploadFileServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private InternalLogger logger = InternalLoggerFactory.getInstance(HttpUploadFileServerHandler.class.getName());

    /**
     * Factory that writes to disk
     */
    private static final HttpDataFactory factory = new DefaultHttpDataFactory(true);
    private static final String FILE_UPLOAD_LOCN = "D:/tmp/uploads/";
    private HttpRequest httpRequest;
    private HttpPostRequestDecoder httpDecoder;

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject httpObject) {
        String filePath = null;
        if (httpObject instanceof HttpRequest) {
            httpRequest = (HttpRequest) httpObject;

            filePath = httpRequest.uri();
            //读取图片
            if (httpRequest.method() == GET) {

            }
            //上传图片
            if (httpRequest.method() == POST) {
                httpDecoder = new HttpPostRequestDecoder(factory, httpRequest);
                httpDecoder.setDiscardThreshold(0);
            } else {
                sendResponse(ctx, METHOD_NOT_ALLOWED, null);
            }
        }
        if (httpDecoder != null) {
            if (httpObject instanceof HttpContent) {
                final HttpContent chunk = (HttpContent) httpObject;
                httpDecoder.offer(chunk);
                readChunk(filePath, ctx);

                if (chunk instanceof LastHttpContent) {
                    resetPostRequestDecoder();
                }
            }
        }
    }

    private void readChunk(String filePath, ChannelHandlerContext ctx) {
        while (httpDecoder.hasNext()) {
            InterfaceHttpData data = httpDecoder.next();
            if (data != null) {
                try {
                    switch (data.getHttpDataType()) {
                        case Attribute:
                            break;
                        case FileUpload:
                            final FileUpload fileUpload = (FileUpload) data;
                            final File file = new File(FILE_UPLOAD_LOCN + fileUpload.getFilename());
                            File parentPath = file.getParentFile();
                            if (!parentPath.exists()) {
                                parentPath.mkdirs();
                            }
                            if (!file.exists()) {
                                file.createNewFile();
                            }
                            logger.info("Created file " + file);
                            try (
                                    FileChannel inputChannel = new FileInputStream(fileUpload.getFile()).getChannel();
                                    FileChannel outputChannel = new FileOutputStream(file).getChannel()) {
                                outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
                                sendResponse(ctx, CREATED, "file name: " + file.getAbsolutePath());
                            }
                            break;
                    }
                } catch (Exception exception) {
                    logger.error(exception.getMessage(), exception);
                } finally {
                    data.retain(1);
                    data.release();
                }
            }
        }
    }

    /**
     * Sends a response back.
     *
     * @param ctx
     * @param status
     * @param message
     */
    private static void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        final FullHttpResponse response;
        String msgDesc = message;
        if (message == null) {
            msgDesc = "Failure: " + status;
        }
        msgDesc += " \r\n";

        final ByteBuf buffer = Unpooled.copiedBuffer(msgDesc, CharsetUtil.UTF_8);
        if (status.code() >= HttpResponseStatus.BAD_REQUEST.code()) {
            response = new DefaultFullHttpResponse(HTTP_1_1, status, buffer);
        } else {
            response = new DefaultFullHttpResponse(HTTP_1_1, status, buffer);
        }
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);

        // Close the connection as soon as the response is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void resetPostRequestDecoder() {
        httpRequest = null;
        httpDecoder.destroy();
        httpDecoder = null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("Got exception " + cause);
        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (httpDecoder != null) {
            httpDecoder.cleanFiles();
        }
    }
}
