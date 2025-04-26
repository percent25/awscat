package helpers;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import com.google.common.base.MoreObjects;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;

/**
 * ConcatenatedJsonWriter
 * 
 * <p>not thread-safe
 */
public class ConcatenatedJsonWriter {

    public interface Transport {
        /**
         * maximum transmission unit
         */
        int mtuBytes();

        /**
         * send bytes
         * 
         * <p>
         * ConcatenatedJsonWriter shall not ask Transport to send more than mtu bytes
         */
        ListenableFuture<?> send(byte[] bytes);
    }

    private class VoidFuture extends AbstractFuture<Void> {
        public boolean setVoid() {
            return super.set(null);
        }

        public boolean setException(Throwable throwable) {
            return super.setException(throwable);
        }
    }

    // current partition
    private ByteArrayOutputStream partitionBytes = new ByteArrayOutputStream();

    // partition -> futures
    private final Multimap<ByteArrayOutputStream, VoidFuture> partitionFutures = LinkedListMultimap.create();
    
    private final List<ListenableFuture<?>> flushFutures = Lists.newArrayList();

    // abstract write transport
    private final Transport transport;

    /**
     * ctor
     * 
     * @param transport
     */
    public ConcatenatedJsonWriter(Transport transport) {
        debug("ctor", transport);
        this.transport = transport;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("transport", transport).toString();
    }

    /**
     * write
     * 
     * @param jsonElement
     * @return
     */
    public ListenableFuture<?> write(JsonElement jsonElement) {
        trace("write", jsonElement);
        return new FutureRunner() {
            {
                run(() -> {
                    byte[] bytes = render(jsonElement);
                    if (bytes.length > transport.mtuBytes())
                        throw new IllegalArgumentException("jsonElement more than mtu bytes");
                    if (partitionBytes.size() + bytes.length > transport.mtuBytes())
                        flush();
                    partitionBytes.write(bytes, 0, bytes.length);

                    VoidFuture lf = new VoidFuture();
                    partitionFutures.put(partitionBytes, lf); // track futures on a per-baos/batch/partition basis
                    return lf;
                });
            }
        }.get();
    }

    /**
     * flush
     * 
     * @return
     */
    public ListenableFuture<?> flush() {
        debug("flush");
        return new FutureRunner() {
            {
                run(() -> {
                    if (partitionBytes.size() > 0)
                        partitionBytes = flush(partitionBytes, partitionFutures.get(partitionBytes));
                    return Futures.successfulAsList(flushFutures);
                });
            }
        }.get();
    }

    // returns new baos
    private ByteArrayOutputStream flush(ByteArrayOutputStream partitionBytes, Iterable<VoidFuture> futures) {
        debug("flush", partitionBytes.size());
        ListenableFuture<?> lf = new FutureRunner() {
            {
                run(() -> {
                    // request
                    return transport.send(partitionBytes.toByteArray());
                }, sendResponse -> {
                    // success
                    futures.forEach(lf -> lf.setVoid());
                }, e -> {
                    // failure
                    futures.forEach(lf -> lf.setException(e));
                });
            }
        }.get();
        flushFutures.add(lf);
        return new ByteArrayOutputStream();
    }

    private byte[] render(JsonElement jsonElement) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new PrintStream(baos, true).println(jsonElement);
        return baos.toByteArray();
    }

    private void debug(Object... args) {
        new LogHelper(this).debug(args);
    }

    private void trace(Object... args) {
        new LogHelper(this).trace(args);
    }

}