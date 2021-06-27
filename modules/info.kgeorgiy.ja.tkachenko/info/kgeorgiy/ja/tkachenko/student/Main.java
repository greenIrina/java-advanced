package info.kgeorgiy.ja.tkachenko.student;

import java.io.IOException;
import java.lang.reflect.Method;

interface Lol {
    public void get() throws IOException, NoSuchMethodException;
}

class Kek implements Lol {

    @Override
    public void get() throws IOException, NoSuchMethodException {

    }
}
public class Main {
    public static void main(String[] args) {
        Lol lol = new Kek();
        Method[] methods = lol.getClass().getMethods();
        Method method = methods[0];
        Class<?>[] exceptions = method.getExceptionTypes();
        for (Class<?> exception : exceptions) {
            System.out.println(exception.getCanonicalName() + " ");
        }

    }
}
