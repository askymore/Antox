package chat.tox.antox;

public class Util
{

    public static int clamp(int value, int min, int max)
    {
        return Math.min(Math.max(value, min), max);
    }


    public static void wait(Object lock, long millis) {
        try {
            lock.wait(millis);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

}