package es.upv.comm.lokudemo;

public class AudioTools {

    public static int audioPower(short[] shorts){
        int sum = 0;
        for (short s: shorts) {
            sum += Math.abs(s);
        }
        return sum / shorts.length;
    }
}
