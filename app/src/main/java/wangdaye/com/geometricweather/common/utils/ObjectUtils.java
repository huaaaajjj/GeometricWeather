package wangdaye.com.geometricweather.common.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

public class ObjectUtils {

    @SuppressWarnings("unchecked")
    public static <T> List<T> deepCopy(List<T> src) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(byteOut)) {
            out.writeObject(src);
        }
        ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
        try (ObjectInputStream in = new ObjectInputStream(byteIn)) {
            return (List<T>) in.readObject();
        }
    }
}
