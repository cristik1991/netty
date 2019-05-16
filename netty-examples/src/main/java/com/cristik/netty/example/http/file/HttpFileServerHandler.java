package com.cristik.netty.example.http.file;

import com.cristik.common.utils.ResponseUtil;
import com.cristik.netty.http.HttpRequestParser;
import com.sun.istack.internal.NotNull;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author cristik
 */
public class HttpFileServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = LoggerFactory.getLogger(HttpFileServerHandler.class);

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    public static final String BASE_PATH = "D:/tmp/uploads/";
    public static final String UPLOAD_PATH = "/upload";
    public static String FILE_PARAM_NAME = "file";
    public static final String KEY = "key";

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
    private static final String CR = System.getProperty("line.separator");

    private ChannelHandlerContext ctx;
    private HttpRequest request;
    private Map<String, Object> params;
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
        logger.debug("channel registered");
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
        logger.debug("channel unregistered");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject httpObject) throws Exception {
        this.ctx = ctx;
        if (httpObject instanceof FullHttpRequest) {
            Map<String, String> parmMap = new HttpRequestParser((FullHttpRequest) httpObject).parse();
            System.out.println(parmMap);
        }
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
        //获取附件
        if (HttpMethod.GET.equals(method)) {
            File file = new File(BASE_PATH + uri);
            if (file.exists()) {
                if (file.isFile()) {
                    RandomAccessFile raf = new RandomAccessFile(file, "r");
                    long fileLength = raf.length();
                    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
                    HttpUtil.setContentLength(response, fileLength);
                    setContentTypeHeader(response, file);
                    setDateAndCacheHeaders(response, file);
                    if (!HttpUtil.isKeepAlive(request)) {
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                    } else if (request.protocolVersion().equals(HTTP_1_0)) {
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    }
                    // Write the initial line and the header.
                    ctx.write(response);
                    // Write the content.
                    ChannelFuture sendFileFuture;
                    ChannelFuture lastContentFuture;
                    if (ctx.pipeline().get(SslHandler.class) == null) {
                        sendFileFuture =
                                ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
                        // Write the end marker.
                        lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                    } else {
                        sendFileFuture =
                                ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)),
                                        ctx.newProgressivePromise());
                        // HttpChunkedInput will write the end marker (LastHttpContent) for us.
                        lastContentFuture = sendFileFuture;
                    }

                    sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                        @Override
                        public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                            // total unknown
                            if (total < 0) {
                                logger.debug("{} Transfer progress: {}", future.channel(), progress);
                                System.err.println();
                            } else {
                                logger.debug("{} Transfer progress: {} / {}", future.channel(), progress, total);
                                System.err.println();
                            }
                        }
                        @Override
                        public void operationComplete(ChannelProgressiveFuture future) {
                            System.err.println(future.channel() + " Transfer complete.");
                        }
                    });

                    // Decide whether to close the connection or not.
                    if (!HttpUtil.isKeepAlive(request)) {
                        // Close the connection when the whole content is written out.
                        lastContentFuture.addListener(ChannelFutureListener.CLOSE);
                    }
                } else {
                    sendError(ctx, FORBIDDEN);
                    return;
                }
            } else {
                sendError(ctx, NOT_FOUND);
                return;
            }
        } else if (HttpMethod.POST.equals(method)) {
            //上传图片
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
        if (uri.getPath().startsWith(UPLOAD_PATH)) {
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
            }
        }
    }

    private void writeHttpData(InterfaceHttpData data) throws IOException {
        if (data.getHttpDataType().equals(InterfaceHttpData.HttpDataType.FileUpload)) {
            if (!FILE_PARAM_NAME.equals(data.getName())) {
                writeResponse(ResponseUtil.error("could not resolve file body"));
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

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response    HTTP response
     * @param fileToCache file to extract content type
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(
                HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
    }

}
