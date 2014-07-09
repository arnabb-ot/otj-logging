package com.opentable.logging;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

/**
 * This class encodes log fields as a JSON object, and writes each as a separate line to the outputStream.
 * <p>
 * You can use this encoder by adding the following phrase to any appender:
 * <pre>
 * &lt;encoder class="com.opentable.logging.JsonLogEncoder"&gt;
 * </pre>
 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {
    private static final AtomicLong LOG_SEQUENCE_NUMBER = new AtomicLong(0);

    private final ObjectMapper mapper;

    public JsonLogEncoder() {
        // TODO: This sucks - - won't get the mapper customizations.  Find a way to inject this.
        this.mapper = new ObjectMapper().disable(SerializationFeature.WRITE_NULL_MAP_VALUES).setSerializationInclusion(Include.NON_NULL);
    }

    @Override
    public void doEncode(ILoggingEvent event) throws IOException
    {
        final ObjectNode logLine;

        if (event instanceof HttpLogFields) {
            final TokenBuffer buf = new TokenBuffer(mapper, false);
            mapper.writerWithType(HttpLogFields.class).writeValue(buf, event);
            logLine = mapper.readTree(buf.asParser());
        } else {
            logLine = mapper.valueToTree(new ApplicationLogEvent(event));
        }

        for (Entry<String, String> e : event.getMDCPropertyMap().entrySet()) {
            if (!logLine.has(e.getKey())) {
                logLine.put(e.getKey(), e.getValue());
            }
        }

        logLine.put("sequencenumber", LOG_SEQUENCE_NUMBER.incrementAndGet());

        byte[] bytes = mapper.writeValueAsBytes(logLine);

        synchronized (outputStream) {
            outputStream.write(bytes);
            outputStream.write('\n');
        }
    }

    @Override
    public void close() throws IOException {
        // Nothing to do here
    }
}
