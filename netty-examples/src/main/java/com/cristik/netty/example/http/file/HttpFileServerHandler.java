package com.cristik.netty.example.http.file;

import com.cristik.common.utils.ResponseUtil;
import com.sun.istack.internal.NotNull;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

/**
 * @author cristik
 */
public class HttpFileServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final String BASE_PATH = "D:/tmp/uploads/";

    private ChannelHandlerContext ctx;

    private HttpRequest request;

    private static String KEY = "file";

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

    private HttpPostRequestDecoder decoder;


    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true;
        DiskFileUpload.baseDirectory = null;
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskAttribute.baseDirectory = null;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        System.out.println("channelRegistered");
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
        System.out.println("channelUnregistered");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject httpObject) throws Exception {
        this.ctx = ctx;

        if (httpObject instanceof HttpRequest) {
            request = (HttpRequest) httpObject;
        }
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        String uri = request.uri();
        if (uri == null) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        HttpMethod method = request.method();

        if (HttpMethod.GET.equals(method)) {

            File file = new File(BASE_PATH + uri);

            if (file.exists()) {
                if (file.isFile()) {
                    RandomAccessFile accessFile = new RandomAccessFile(file, "r");
                    long length = accessFile.length();
                    DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, OK);
                    HttpUtil.setContentLength(request, length);
                    setContentTypeHeader(response, file);
                    if (HttpUtil.isKeepAlive(request)) {
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    }
                    ctx.write(response);
                    ChannelFuture future = ctx.write(new ChunkedFile(accessFile, 0, length, 2 << 16), ctx.newProgressivePromise());
//                    future.addListener(new ChannelProgressiveFutureListener() {
//                        @Override
//                        public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) throws Exception {
//                            if (total < 0) {
//                                //TODO
//                            } else {
//                                //TODO
//                            }
//                        }
//
//                        @Override
//                        public void operationComplete(ChannelProgressiveFuture future) throws Exception {
//
//                        }
//                    });
                    ChannelFuture lastFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                    lastFuture.addListener(ChannelFutureListener.CLOSE);
                } else {
                    sendError(ctx, FORBIDDEN);
                    return;
                }
            } else {
                sendError(ctx, NOT_FOUND);
                return;
            }
        } else if (HttpMethod.POST.equals(method)) {
            try {
                decoder = new HttpPostRequestDecoder(factory, request);
                if (httpObject instanceof HttpContent) {
                    doHttpContent(ctx, ((HttpContent) httpObject));
                    return;
                }
            } catch (Exception e) {
                writeResponse(ResponseUtil.error());
                ctx.channel().close();
            }
        }
    }

    private void doHttpContent(ChannelHandlerContext ctx,
                               HttpContent httpContent) throws URISyntaxException {
        URI uri = new URI(request.uri());
        if (uri.getPath().startsWith("/upload")) {
            if (decoder != null) {
                try {
                    decoder.offer(httpContent);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                if (httpContent instanceof LastHttpContent) {
                    readHttpDataChunkByChunk();
                    reset();
                }
            }
        } else {
            writeResponse(ResponseUtil.error());
        }
    }

    private void reset() {
        if (decoder != null) {
            request = null;
            decoder.destroy();
            decoder = null;
        }
    }

    private void readHttpDataChunkByChunk() {
        while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            try {
                writeHttpData(data);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                data.release();
            }
        }
    }

    private void writeHttpData(InterfaceHttpData data) throws IOException {
        if (data.getHttpDataType().equals(InterfaceHttpData.HttpDataType.FileUpload)) {
            if (!KEY.equals(data.getName())) {
                writeResponse(ResponseUtil.error());
                return;
            }
            FileUpload fileUpload = (FileUpload) data;
            if (fileUpload.isCompleted()) {
                String filename = fileUpload.getFilename();
                try {
                    File file = new File(BASE_PATH + filename);
                    if (!file.getParentFile().exists()) {
                        file.getParentFile().mkdirs();
                    }
                    if (!file.exists()) {
                        file.createNewFile();
                    } else {
                        Map<String, Object> map = new HashMap<>(1);
                        map.put("file", filename);
                        writeResponse(ResponseUtil.success("file", filename));
                        return;
                    }
                    fileUpload.renameTo(file);
                    decoder.removeHttpDataFromClean(fileUpload);
                    Map<String, Object> map = new HashMap<>(1);
                    map.put("file", filename);
                    writeResponse(ResponseUtil.success("file", filename));
                } catch (Exception e) {
                    writeResponse(ResponseUtil.error());
                }
            } else {
                writeResponse(ResponseUtil.error());
            }
        }
    }


    private void writeResponse(@NotNull String resp) {
        ByteBuf buf = Unpooled.copiedBuffer(resp, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        ctx.write(response);
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
    }

}
