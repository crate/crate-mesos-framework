package io.crate.frameworks.mesos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public final class CrateMessage<T extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateMessage.class);
    private final Type type;
    private final T data;

    public CrateMessage(Type type, T data) {
        this.type = type;
        this.data = data;
    }

    public Type type() {
        return type;
    }

    public T data() {
        return data;
    }

    public enum Type {
        MESSAGE_MISSING_RESOURCE
    }

    public static <E extends Serializable> CrateMessage<E> fromStream(byte[] value) throws IOException {
        if (value.length == 0) {
            return null;
        }
        ByteArrayInputStream in = new ByteArrayInputStream(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(in)) {
            return (CrateMessage<E>) objectInputStream.readObject();
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not deserialize CrateMessage:", e);
        }
        return null;
    }

    public byte[] toStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
            objOut.writeObject(this);
        } catch (IOException e){
            LOGGER.error("Could not serialize CrateMessage:", e);
        }
        return out.toByteArray();
    }
}
